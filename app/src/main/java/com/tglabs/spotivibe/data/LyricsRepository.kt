package com.tglabs.spotivibe.data

import android.content.Context
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.tglabs.spotivibe.domain.LrclibProbe
import com.tglabs.spotivibe.domain.LyricsLookup
import com.tglabs.spotivibe.domain.LyricsResult
import com.tglabs.spotivibe.domain.LyricsState
import com.tglabs.spotivibe.domain.cacheEntryFor
import com.tglabs.spotivibe.domain.combineProbes
import com.tglabs.spotivibe.domain.parseLrc
import com.tglabs.spotivibe.domain.probeFromHttpFailure
import com.tglabs.spotivibe.domain.retryBackoffMs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
     * Yang di-cache HANYA jawaban yang sungguhan: ada lirik, atau LRCLIB
     * memastikan tidak ada. Kegagalan transport tidak masuk cache manapun,
     * karena dulu justru itu yang bikin satu lagu yang kebetulan diputar saat
     * sinyal hilang kehilangan liriknya selama 24 jam penuh.
     *
     * @param trackId identifier unik (biasanya Spotify URI `spotify:track:xxx`).
     *   Dipakai sebagai cache key — sanitize otomatis untuk filename.
     * @param durationMs durasi track dalam millisecond. Akan dikonversi ke
     *   detik untuk LRCLIB `/get` param.
     * @param allowRetry false untuk preload latar belakang. Preload tidak boleh
     *   berebut jaringan dengan lagu yang sedang diputar; kalau gagal, biarkan
     *   saja dan biar fetch normal yang mengurusnya nanti.
     */
    suspend fun fetchLyrics(
        trackId: String,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        allowRetry: Boolean = true,
    ): LyricsState = withContext(Dispatchers.IO) {
        // Layer 1: in-memory (LRU bounded)
        memCacheGet(trackId)?.let {
            Log.d(TAG, "mem-cache hit: $trackId")
            return@withContext it.toState()
        }

        // Layer 2: disk
        diskCache.get(trackId)?.let { cached ->
            Log.d(TAG, "disk-cache hit: $trackId (hasContent=${cached.hasContent})")
            memCachePut(trackId, cached)
            return@withContext cached.toState()
        }

        // Layer 3: network — /get + /search, dengan retry untuk gangguan sesaat
        val maxAttempts = if (allowRetry) MAX_ATTEMPTS else 1
        var lookup = probeNetwork(trackId, title, artist, album, durationMs)
        var attempt = 1
        while (lookup is LyricsLookup.Unavailable && attempt < maxAttempts) {
            val backoff = retryBackoffMs(attempt)
            Log.d(TAG, "Retry $attempt untuk $trackId dalam ${backoff}ms (${lookup.reason})")
            delay(backoff)
            lookup = probeNetwork(trackId, title, artist, album, durationMs)
            attempt++
        }

        // Satu titik tulis, dan syaratnya diputuskan oleh fungsi murni yang
        // sudah diuji. Versi lama memanggil diskCache.put() tanpa syarat, dan
        // di situlah kegagalan jaringan ikut tersimpan sebagai "tidak ada".
        cacheEntryFor(trackId, lookup)?.let { entry ->
            memCachePut(trackId, entry)
            diskCache.put(entry)
        }

        when (val l = lookup) {
            is LyricsLookup.Found -> LyricsState.Ready(l.result)
            LyricsLookup.NotFound -> LyricsState.NotFound
            is LyricsLookup.Unavailable -> {
                Log.w(TAG, "Lirik tidak tersedia untuk $trackId: ${l.reason}")
                LyricsState.Unavailable(l.reason)
            }
        }
    }

    private fun LyricsResult.toState(): LyricsState =
        if (hasContent) LyricsState.Ready(this) else LyricsState.NotFound

    /**
     * Tanya `/get` dan `/search` paralel, lalu gabungkan jadi satu keputusan.
     *
     * Strategi: tunggu `/get` sampai [PREFER_GET_TIMEOUT_MS]ms. Kalau sudah
     * ber-content, pakai itu dan jangan tunggu `/search`. Kalau tidak, tunggu
     * keduanya, karena [combineProbes] butuh tahu apakah probe yang lain
     * BERHASIL bilang tidak ada, atau justru gagal dijangkau. Beda itu yang
     * menentukan boleh tidaknya hasilnya di-cache.
     */
    private suspend fun probeNetwork(
        trackId: String,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
    ): LyricsLookup = coroutineScope {
        val getDeferred = async { probeGet(trackId, title, artist, album, durationMs) }
        val searchDeferred = async { probeSearch(trackId, title, artist) }

        // Jalur cepat: /get sudah punya konten, tidak perlu menunggu /search.
        val getQuick = withTimeoutOrNull(PREFER_GET_TIMEOUT_MS) { getDeferred.await() }
        if (getQuick is LrclibProbe.Content) {
            Log.d(TAG, "/get menang (jalur cepat) untuk $trackId")
            // Biarkan /search selesai supaya tidak menggantung, hasilnya dibuang.
            runCatching { searchDeferred.await() }
            return@coroutineScope LyricsLookup.Found(getQuick.result)
        }

        val getProbe = getQuick ?: runCatching { getDeferred.await() }
            .getOrElse { LrclibProbe.Failed(it.message ?: "get gagal") }
        val searchProbe = runCatching { searchDeferred.await() }
            .getOrElse { LrclibProbe.Failed(it.message ?: "search gagal") }

        combineProbes(getProbe, searchProbe).also {
            Log.d(TAG, "Lookup $trackId: get=$getProbe search=$searchProbe -> $it")
        }
    }

    /**
     * Hit `/api/get`. 404 berarti kombinasi judul + artis + album + durasi
     * memang tidak ada di database, jadi [LrclibProbe.Absent]. Status lain yang
     * tidak sukses bisa berubah, jadi [LrclibProbe.Failed].
     */
    private suspend fun probeGet(
        trackId: String,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
    ): LrclibProbe = runCatching {
        val durationSec = (durationMs / 1000L).toInt().coerceAtLeast(0)
        val resp = api.get(
            trackName = title,
            artistName = artist,
            albumName = album,
            duration = durationSec,
        )
        if (!resp.isSuccessful) return@runCatching probeFromHttpFailure(resp.code())
        val dto = resp.body() ?: return@runCatching LrclibProbe.Absent
        dto.toLyricsResult(trackId).asProbe()
    }.getOrElse { t ->
        // IOException, timeout, parse error: semuanya sementara.
        Log.w(TAG, "/get error untuk $trackId: ${t.message}")
        LrclibProbe.Failed(t.message ?: "get gagal")
    }

    /** Hit `/api/search`. Array kosong adalah jawaban sungguhan "tidak ada". */
    private suspend fun probeSearch(
        trackId: String,
        title: String,
        artist: String,
    ): LrclibProbe = runCatching {
        val resp = api.search(trackName = title, artistName = artist)
        if (!resp.isSuccessful) return@runCatching probeFromHttpFailure(resp.code())
        val items = resp.body().orEmpty()
        if (items.isEmpty()) return@runCatching LrclibProbe.Absent
        // Prefer item dengan syncedLyrics; fallback ke item pertama
        val pick = items.firstOrNull { !it.syncedLyrics.isNullOrBlank() } ?: items.first()
        pick.toLyricsResult(trackId).asProbe()
    }.getOrElse { t ->
        Log.w(TAG, "/search error untuk $trackId: ${t.message}")
        LrclibProbe.Failed(t.message ?: "search gagal")
    }

    /** Hasil tanpa isi sama artinya dengan "tidak ada", bukan konten kosong. */
    private fun LyricsResult.asProbe(): LrclibProbe =
        if (hasContent) LrclibProbe.Content(this) else LrclibProbe.Absent

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

        /** Total percobaan untuk fetch normal. Preload selalu 1. */
        private const val MAX_ATTEMPTS = 3
    }
}
