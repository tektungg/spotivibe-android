package com.tglabs.spotivibe.data

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.tglabs.spotivibe.data.provider.LrclibProvider
import com.tglabs.spotivibe.data.provider.LyricsProvider
import com.tglabs.spotivibe.data.provider.TrackQuery
import com.tglabs.spotivibe.domain.LrclibSearchEntry
import com.tglabs.spotivibe.domain.LyricsLookup
import com.tglabs.spotivibe.domain.LyricsLookupEvent
import com.tglabs.spotivibe.domain.LyricsOverrideSource
import com.tglabs.spotivibe.domain.LyricsResult
import com.tglabs.spotivibe.domain.LyricsSearchState
import com.tglabs.spotivibe.domain.LyricsSource
import com.tglabs.spotivibe.domain.LyricsState
import com.tglabs.spotivibe.domain.LyricsStatsRecorder
import com.tglabs.spotivibe.domain.ProbeSource
import com.tglabs.spotivibe.domain.ProviderAnswer
import com.tglabs.spotivibe.domain.ProviderCooldown
import com.tglabs.spotivibe.domain.ProviderId
import com.tglabs.spotivibe.domain.ProviderProbe
import com.tglabs.spotivibe.domain.cacheEntryFor
import com.tglabs.spotivibe.domain.combineProviderAnswers
import com.tglabs.spotivibe.domain.isRaceSettled
import com.tglabs.spotivibe.domain.outcomeOf
import com.tglabs.spotivibe.domain.resolveWithStale
import com.tglabs.spotivibe.domain.retryBackoffMs
import com.tglabs.spotivibe.domain.toSearchResults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Orchestrator untuk fetch lyrics dengan 3-layer caching:
 *
 * 1. **In-memory** ([memCache]) — paling cepat, hanya valid per session.
 * 2. **Disk** ([diskCache]) — JSON file, survive process restart, dengan TTL
 *    30 hari (positive) / 1 hari (negative).
 * 3. **Network** — hanya kalau [network] bilang online. LRCLIB ([LrclibProvider])
 *    dan sumber komunitas ([providers]) berlomba bersamaan; yang timing-nya
 *    paling halus menang (lihat `domain/ProviderRace.kt`).
 *
 * Semua exception ditelan — return [LyricsResult] kosong (hasContent=false)
 * kalau gagal. Caller cukup cek [LyricsResult.hasContent].
 *
 * Mode offline: kalau jaringan tidak bisa menjawab (offline, atau online tapi
 * semua percobaan gagal), entri disk yang sudah lewat TTL dipakai sebagai
 * cadangan lewat [resolveWithStale]. Saat offline jaringan tidak disentuh sama
 * sekali, jadi lirik tersimpan tampil seketika, bukan setelah tiga percobaan
 * yang masing-masing menunggu timeout.
 */
class LyricsRepository(
    private val diskCache: LyricsDiskCache,
    /**
     * Pencatat statistik lookup. Default no-op supaya konstruksi tanpa
     * pencatat tetap sah, misalnya di test.
     */
    private val statsRecorder: LyricsStatsRecorder = LyricsStatsRecorder.NoOp,
    /**
     * Lirik pilihan user. Dicek PALING AWAL, menang atas mem cache, disk cache,
     * dan jaringan. Default tidak pernah punya override supaya konstruksi tanpa
     * ini tetap sah.
     */
    private val overrides: LyricsOverrideSource = LyricsOverrideSource.None,
    /**
     * Status konektivitas. Default selalu online supaya konstruksi tanpa
     * monitor tetap sah dan perilakunya sama persis dengan sebelum mode offline.
     */
    private val network: NetworkStatusSource = NetworkStatusSource.AlwaysOnline,
    /** Seam untuk test. Null = bangun klien Retrofit LRCLIB sungguhan. */
    lrclibApi: LrclibApi? = null,
    /**
     * Sumber komunitas yang ikut berlomba dengan LRCLIB. Default kosong supaya
     * konstruksi tanpa ini tetap sah dan perilakunya sama dengan LRCLIB saja.
     */
    private val providers: List<LyricsProvider> = emptyList(),
    /** Setelan "Community lyrics sources". Dibaca per lookup, jadi berlaku seketika. */
    private val communitySources: () -> Boolean = { true },
    private val cooldown: ProviderCooldown = ProviderCooldown(),
    /** Lama menunggu sumber yang lebih halus setelah konten pertama ada. Seam untuk test. */
    private val contentGraceMs: Long = DEFAULT_CONTENT_GRACE_MS,
) {

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

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val api: LrclibApi = lrclibApi ?: Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(HttpClients.lyrics)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(LrclibApi::class.java)

    private val lrclib = LrclibProvider(api)

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
     * @param forceRefresh true untuk mencari ulang walau cache punya entri,
     *   misalnya saat durasi lagu baru diketahui setelah fetch pertama.
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
        forceRefresh: Boolean = false,
    ): LyricsState = withContext(Dispatchers.IO) {
        // Layer 0: lirik pilihan user. Menang atas segalanya, dan sengaja tidak
        // menyentuh jaringan sama sekali: lagu yang sudah diperbaiki manual
        // berhenti menanyakan LRCLIB selamanya.
        overrides.overrideFor(trackId)?.let { pilihan ->
            Log.d(TAG, "override hit: $trackId")
            val state = pilihan.toState()
            record(LyricsSource.Override, state, null)
            return@withContext state
        }

        // Layer 1: in-memory (LRU bounded). forceRefresh melewati kedua cache
        // (tapi tidak override): entri yang ada dipilih tanpa durasi lagu.
        if (!forceRefresh) memCacheGet(trackId)?.let {
            Log.d(TAG, "mem-cache hit: $trackId")
            return@withContext it.toState().also { s -> record(LyricsSource.MemCache, s, null) }
        }

        // Layer 2: disk
        if (!forceRefresh) diskCache.get(trackId)?.let { cached ->
            Log.d(TAG, "disk-cache hit: $trackId (hasContent=${cached.hasContent})")
            memCachePut(trackId, cached)
            return@withContext cached.toState()
                .also { s -> record(LyricsSource.DiskCache, s, null) }
        }

        // Layer 3a: offline. Jaringan sengaja tidak disentuh sama sekali; yang
        // tersisa cuma cadangan basi dari disk.
        if (!network.isOnline.value) {
            return@withContext offlineFallback(trackId, LyricsState.Offline)
        }

        // Layer 3b: network — semua sumber berlomba, dengan retry untuk gangguan sesaat
        val query = TrackQuery(trackId, title, artist, album, durationMs)
        val maxAttempts = if (allowRetry) MAX_ATTEMPTS else 1
        var lookup = probeNetwork(query)
        var attempt = 1
        // Berhenti retry begitu jaringan hilang: percobaan berikutnya pasti
        // gagal, dan user menunggu lirik yang sebenarnya bisa datang dari disk.
        while (lookup is LyricsLookup.Unavailable && attempt < maxAttempts &&
            network.isOnline.value
        ) {
            val backoff = retryBackoffMs(attempt)
            Log.d(TAG, "Retry $attempt untuk $trackId dalam ${backoff}ms (${lookup.reason})")
            delay(backoff)
            lookup = probeNetwork(query)
            attempt++
        }

        // Satu titik tulis, dan syaratnya diputuskan oleh fungsi murni yang
        // sudah diuji. Versi lama memanggil diskCache.put() tanpa syarat, dan
        // di situlah kegagalan jaringan ikut tersimpan sebagai "tidak ada".
        cacheEntryFor(trackId, lookup)?.let { entry ->
            memCachePut(trackId, entry)
            diskCache.put(entry)
        }

        val state = when (val l = lookup) {
            is LyricsLookup.Found -> LyricsState.Ready(l.result)
            LyricsLookup.NotFound -> LyricsState.NotFound
            is LyricsLookup.Unavailable -> {
                Log.w(TAG, "Lirik tidak tersedia untuk $trackId: ${l.reason}")
                // Jaringan hilang di tengah percobaan: tampilkan sebagai
                // offline, supaya UI jujur dan fetch ulang terpicu saat online.
                if (network.isOnline.value) LyricsState.Unavailable(l.reason)
                else LyricsState.Offline
            }
        }
        if (state is LyricsState.Unavailable || state is LyricsState.Offline) {
            return@withContext offlineFallback(trackId, state)
        }
        record(LyricsSource.Network, state, (lookup as? LyricsLookup.Found)?.probe)
        state
    }

    /**
     * Jaringan tidak bisa menjawab: pakai lirik basi dari disk kalau ada.
     *
     * Hasil basi SENGAJA tidak dimasukkan ke mem cache. Mem cache dicek lebih
     * dulu dari jaringan, jadi memasukkannya berarti lirik basi itu menang
     * terus sepanjang sesi walaupun jaringan sudah kembali.
     *
     * Statistik: cadangan basi dicatat sebagai [LyricsSource.DiskCache], karena
     * itulah sumber yang benar-benar menyajikan liriknya. Offline tanpa cadangan
     * TIDAK dicatat sama sekali; statistik mengukur coverage LRCLIB, dan lookup
     * yang tidak pernah bertanya ke LRCLIB tidak bilang apa-apa soal coverage.
     * Kegagalan jaringan saat online tetap dicatat seperti sebelumnya.
     */
    private suspend fun offlineFallback(trackId: String, reason: LyricsState): LyricsState {
        val state = resolveWithStale(reason, diskCache.getStale(trackId))
        when {
            state is LyricsState.Ready -> {
                Log.d(TAG, "stale disk hit: $trackId (alasan: $reason)")
                record(LyricsSource.DiskCache, state, null)
            }
            state is LyricsState.Unavailable -> record(LyricsSource.Network, state, null)
            else -> Log.d(TAG, "offline tanpa cadangan: $trackId")
        }
        return state
    }

    /**
     * Pencarian lirik manual, untuk saat auto-match memuat lirik dari lagu yang
     * sama sekali berbeda.
     *
     * SENGAJA tidak menyentuh cache maupun statistik. Ini penjelajahan yang
     * dipandu user, bukan lookup: mencatatnya akan mengotori angka coverage
     * dengan percobaan yang memang diharapkan meleset, dan menyimpannya ke
     * cache akan menimpa entry track lain karena hasil pencarian bisa milik
     * lagu apa pun.
     *
     * Tidak ada retry di sini. User sedang menatap layar dan bisa menekan tombol
     * cari lagi sendiri, jadi backoff otomatis cuma membuatnya terasa macet.
     */
    suspend fun searchLyrics(title: String, artist: String): LyricsSearchState =
        withContext(Dispatchers.IO) {
            if (title.isBlank() && artist.isBlank()) return@withContext LyricsSearchState.Empty
            // Langsung gagal saat offline daripada membuat user menatap spinner
            // sampai timeout koneksi.
            if (!network.isOnline.value) return@withContext LyricsSearchState.Failed("offline")
            try {
                val resp = api.search(trackName = title, artistName = artist)
                if (!resp.isSuccessful) {
                    Log.w(TAG, "/search untuk pencarian manual gagal: ${resp.code()}")
                    return@withContext LyricsSearchState.Failed("http_${resp.code()}")
                }
                val hasil = toSearchResults(
                    dtos = resp.body().orEmpty().map { it.toSearchEntry() },
                    // trackId Spotify yang sedang diputar; hasil pencarian akan
                    // dipasang ke track ITU, bukan ke track milik entry LRCLIB.
                    trackId = "",
                )
                if (hasil.isEmpty()) LyricsSearchState.Empty else LyricsSearchState.Ready(hasil)
            } catch (t: Throwable) {
                Log.w(TAG, "Pencarian manual gagal: ${t.message}")
                LyricsSearchState.Failed(t.message ?: "gagal")
            }
        }

    /** Bentuk netral supaya pemetaan hasil bisa diuji tanpa Moshi. */
    private fun LrclibDto.toSearchEntry(): LrclibSearchEntry = LrclibSearchEntry(
        id = id ?: 0L,
        trackName = (trackName ?: name).orEmpty(),
        artistName = artistName.orEmpty(),
        albumName = albumName.orEmpty(),
        durationSec = duration?.toInt() ?: 0,
        instrumental = instrumental == true,
        syncedLyrics = syncedLyrics,
        plainLyrics = plainLyrics,
    )

    /**
     * Catat satu lookup. Kegagalan pencatatan tidak boleh menjatuhkan fetch
     * lirik: statistik itu pengamatan, bukan fitur.
     */
    private suspend fun record(source: LyricsSource, state: LyricsState, probe: ProbeSource?) {
        runCatching {
            statsRecorder.record(
                LyricsLookupEvent(source = source, outcome = outcomeOf(state), probe = probe)
            )
        }.onFailure { Log.w(TAG, "Gagal mencatat statistik lirik: ${it.message}") }
    }

    private fun LyricsResult.toState(): LyricsState =
        if (hasContent) LyricsState.Ready(this) else LyricsState.NotFound

    /** Peserta perlombaan: LRCLIB selalu, sumber komunitas kalau aktif dan tidak sedang dijeda. */
    private fun racers(): List<LyricsProvider> {
        if (!communitySources()) return listOf(lrclib)
        return listOf(lrclib) + providers.filterNot { cooldown.isCoolingDown(it.id) }
    }

    /**
     * Tanya semua sumber bersamaan, lalu putuskan dari yang sudah menjawab.
     *
     * Berhenti saat [isRaceSettled] (pemenang per kata tanpa saingan
     * berperingkat lebih tinggi yang masih jalan), saat semua sudah menjawab,
     * atau [contentGraceMs] setelah konten PERTAMA datang. Tenggat itu yang
     * menjaga satu sumber lambat tidak menahan lirik yang sudah ada di tangan.
     * Tanpa konten sama sekali tidak ada tenggat: menunggu semua sumber masih
     * lebih baik daripada menyerah, dan tiap request sudah dibatasi timeout
     * HTTP-nya sendiri. Sumber yang belum menjawab dibatalkan.
     */
    private suspend fun probeNetwork(query: TrackQuery): LyricsLookup = coroutineScope {
        val peserta = racers()
        val events = Channel<RaceEvent>(Channel.UNLIMITED)
        val jobs = peserta.map { p ->
            launch { events.send(RaceEvent.Answer(ProviderAnswer(p.id, p.lookup(query)))) }
        }
        val answers = collectRace(events, peserta.map { it.id }.toSet(), query.durationSec)
        jobs.forEach { it.cancel() }
        combineProviderAnswers(answers, query.durationSec).also {
            Log.d(TAG, "Lookup ${query.trackId}: ${answers.map { a -> a.id to a.probe::class.simpleName }} -> $it")
        }
    }

    private suspend fun CoroutineScope.collectRace(
        events: Channel<RaceEvent>,
        ids: Set<ProviderId>,
        targetSec: Double?,
    ): List<ProviderAnswer> {
        val answers = mutableListOf<ProviderAnswer>()
        val pending = ids.toMutableSet()
        var grace: Job? = null
        while (!isRaceSettled(answers, pending, targetSec)) {
            val answer = (events.receive() as? RaceEvent.Answer)?.answer ?: break
            answers += answer
            pending -= answer.id
            if (answer.id != ProviderId.Lrclib) cooldown.record(answer.id, answer.probe)
            if (grace == null && answer.probe is ProviderProbe.Content) {
                grace = launch {
                    delay(contentGraceMs)
                    events.send(RaceEvent.GraceOver)
                }
            }
        }
        grace?.cancel()
        return answers
    }

    private sealed interface RaceEvent {
        data class Answer(val answer: ProviderAnswer) : RaceEvent
        data object GraceOver : RaceEvent
    }

    companion object {
        private const val TAG = "LyricsRepository"
        private const val BASE_URL = "https://lrclib.net"

        /**
         * Tiga detik: cukup untuk satu request lambat ke sumber komunitas,
         * cukup pendek supaya lirik LRCLIB yang sudah ada tidak terasa ditahan.
         */
        private const val DEFAULT_CONTENT_GRACE_MS = 3_000L
        private const val MEM_CACHE_MAX = 50

        /** Total percobaan untuk fetch normal. Preload selalu 1. */
        private const val MAX_ATTEMPTS = 3
    }
}
