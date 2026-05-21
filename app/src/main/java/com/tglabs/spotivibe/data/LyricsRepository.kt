package com.tglabs.spotivibe.data

import android.content.Context
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.tglabs.spotivibe.domain.LyricsResult
import com.tglabs.spotivibe.domain.parseLrc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Orchestrator untuk fetch lyrics dengan 3-layer caching:
 *
 * 1. **In-memory** ([memCache]) — paling cepat, hanya valid per session.
 * 2. **Disk** ([diskCache]) — JSON file, survive process restart, dengan TTL
 *    30 hari (positive) / 1 hari (negative).
 * 3. **Network** — LRCLIB API. Fire `/get` dan `/search` PARALEL via
 *    [coroutineScope] + [async]. Prefer `/get` kalau selesai dalam
 *    [PREFER_GET_TIMEOUT_MS]ms dengan hasil ber-content, else pakai mana saja
 *    yang berhasil duluan dan ber-content.
 *
 * Semua exception ditelan — return [LyricsResult] kosong (hasContent=false)
 * kalau gagal. Caller cukup cek [LyricsResult.hasContent].
 */
class LyricsRepository(private val context: Context) {

    // LRU bounded — max 50 tracks. Eviction otomatis dengan LinkedHashMap accessOrder.
    // Thread-safe via synchronized block (low contention, single Activity scope).
    private val memCache = object : LinkedHashMap<String, LyricsResult>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, LyricsResult>?): Boolean {
            return size > MEM_CACHE_MAX
        }
    }

    private fun memCacheGet(key: String): LyricsResult? = synchronized(memCache) { memCache[key] }
    private fun memCachePut(key: String, value: LyricsResult) {
        synchronized(memCache) { memCache[key] = value }
    }
    private val diskCache = LyricsCache(context)

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .build()
            chain.proceed(req)
        }
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val api: LrclibApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(httpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(LrclibApi::class.java)

    /**
     * Fetch lirik untuk track tertentu. Cascade: memory → disk → network.
     *
     * @param trackId identifier unik (biasanya Spotify URI `spotify:track:xxx`).
     *   Dipakai sebagai cache key — sanitize otomatis untuk filename.
     * @param durationMs durasi track dalam millisecond. Akan dikonversi ke
     *   detik untuk LRCLIB `/get` param.
     */
    suspend fun fetchLyrics(
        trackId: String,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
    ): LyricsResult = withContext(Dispatchers.IO) {
        // Layer 1: in-memory (LRU bounded)
        memCacheGet(trackId)?.let {
            Log.d(TAG, "mem-cache hit: $trackId")
            return@withContext it
        }

        // Layer 2: disk
        diskCache.get(trackId)?.let { cached ->
            Log.d(TAG, "disk-cache hit: $trackId (hasContent=${cached.hasContent})")
            memCachePut(trackId, cached)
            return@withContext cached
        }

        // Layer 3: network — race /get vs /search
        val result = try {
            raceLrclib(trackId, title, artist, album, durationMs)
        } catch (t: Throwable) {
            Log.w(TAG, "Network fetch failed for $trackId: ${t.message}", t)
            LyricsResult(trackId = trackId, synced = null, plain = null)
        }

        // Cache walaupun kosong (negative cache, biar nggak spam LRCLIB)
        memCachePut(trackId, result)
        diskCache.put(result)
        result
    }

    /**
     * Fire `/get` dan `/search` paralel.
     *
     * Strategi: tunggu `/get` sampai [PREFER_GET_TIMEOUT_MS]ms. Kalau berhasil
     * dan ber-content, pakai itu. Kalau tidak, baru tunggu `/search`. Begitu
     * pun kalau /get 404, fallback ke /search.
     */
    private suspend fun raceLrclib(
        trackId: String,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
    ): LyricsResult = coroutineScope {
        val getDeferred = async {
            runCatching { fetchViaGet(trackId, title, artist, album, durationMs) }
                .getOrElse {
                    Log.w(TAG, "/get error: ${it.message}")
                    null
                }
        }
        val searchDeferred = async {
            runCatching { fetchViaSearch(trackId, title, artist) }
                .getOrElse {
                    Log.w(TAG, "/search error: ${it.message}")
                    null
                }
        }

        // Prefer /get kalau ready cepat & ber-content
        val getQuick = withTimeoutOrNull(PREFER_GET_TIMEOUT_MS) { getDeferred.await() }
        if (getQuick != null && getQuick.hasContent) {
            Log.d(TAG, "race winner: /get (fast path) for $trackId")
            // /search masih running di background — biarkan complete biar
            // tidak leak, tapi hasilnya kita buang.
            runCatching { searchDeferred.await() }
            return@coroutineScope getQuick
        }

        // /get belum selesai atau tidak ber-content — tunggu kedua-duanya
        val getResult = getQuick ?: runCatching { getDeferred.await() }.getOrNull()
        val searchResult = runCatching { searchDeferred.await() }.getOrNull()

        val chosen = when {
            getResult?.hasContent == true -> {
                Log.d(TAG, "race winner: /get (slow path) for $trackId")
                getResult
            }
            searchResult?.hasContent == true -> {
                Log.d(TAG, "race winner: /search for $trackId")
                searchResult
            }
            else -> {
                Log.d(TAG, "race: no content for $trackId")
                LyricsResult(trackId = trackId, synced = null, plain = null)
            }
        }
        chosen
    }

    /** Hit `/api/get`. 404 di-treat sebagai "no result" (return empty). */
    private suspend fun fetchViaGet(
        trackId: String,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
    ): LyricsResult {
        val durationSec = (durationMs / 1000L).toInt().coerceAtLeast(0)
        val resp = api.get(
            trackName = title,
            artistName = artist,
            albumName = album,
            duration = durationSec,
        )
        if (!resp.isSuccessful) {
            Log.d(TAG, "/get returned ${resp.code()} for $trackId")
            return LyricsResult(trackId = trackId, synced = null, plain = null)
        }
        val dto = resp.body() ?: return LyricsResult(trackId, null, null)
        return dto.toLyricsResult(trackId)
    }

    /** Hit `/api/search`, pilih item pertama yang punya syncedLyrics. */
    private suspend fun fetchViaSearch(
        trackId: String,
        title: String,
        artist: String,
    ): LyricsResult {
        val resp = api.search(trackName = title, artistName = artist)
        if (!resp.isSuccessful) {
            Log.d(TAG, "/search returned ${resp.code()} for $trackId")
            return LyricsResult(trackId = trackId, synced = null, plain = null)
        }
        val items = resp.body().orEmpty()
        if (items.isEmpty()) return LyricsResult(trackId, null, null)
        // Prefer item dengan syncedLyrics; fallback ke item pertama
        val pick = items.firstOrNull { !it.syncedLyrics.isNullOrBlank() } ?: items.first()
        return pick.toLyricsResult(trackId)
    }

    private fun LrclibDto.toLyricsResult(trackId: String): LyricsResult {
        val synced = syncedLyrics?.takeIf { it.isNotBlank() }?.let { parseLrc(it) }
        val plain = plainLyrics?.takeIf { it.isNotBlank() }
        return LyricsResult(
            trackId = trackId,
            synced = synced?.takeIf { it.isNotEmpty() },
            plain = plain,
        )
    }

    companion object {
        private const val TAG = "LyricsRepository"
        private const val BASE_URL = "https://lrclib.net"
        private const val USER_AGENT = "spotivibe-android (https://github.com/tektungg/spotivibe)"
        private const val PREFER_GET_TIMEOUT_MS = 1500L
        private const val MEM_CACHE_MAX = 50
    }
}
