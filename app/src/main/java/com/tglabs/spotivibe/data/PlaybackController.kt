package com.tglabs.spotivibe.data

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import com.tglabs.spotivibe.domain.LyricsResult
import com.tglabs.spotivibe.domain.LyricsState
import com.tglabs.spotivibe.domain.LyricsSyncEngine
import com.tglabs.spotivibe.domain.NowPlaying
import com.tglabs.spotivibe.domain.PlayerControls
import com.tglabs.spotivibe.domain.SourceReport
import com.tglabs.spotivibe.domain.SyncOffsets
import com.tglabs.spotivibe.domain.SyncedLine
import com.tglabs.spotivibe.domain.lineAt
import com.tglabs.spotivibe.domain.pickAccentArgb
import com.tglabs.spotivibe.domain.shouldApplyAccent
import com.tglabs.spotivibe.domain.shouldApplyFetchedLyrics
import com.tglabs.spotivibe.domain.shouldPreload
import com.tglabs.spotivibe.domain.shouldPublishLineIndex
import com.tglabs.spotivibe.domain.shouldRecomputeAccent
import com.tglabs.spotivibe.domain.shouldRefetchOnReconnect
import com.tglabs.spotivibe.domain.shouldRematchForDuration
import com.tglabs.spotivibe.domain.syncOffsetsFlow
import com.tglabs.spotivibe.ui.theme.AccentLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Application-scoped controller. Single source of truth untuk:
 * - Track yang sedang main (via [PlaybackSource], player apa pun)
 * - Album bitmap (via [PlaybackSource])
 * - Lyrics yang sudah di-fetch (via LyricsRepository)
 * - Current active lyric line — ticker adaptif
 * - Romaji map per baris — auto-compute reactive saat lyrics atau toggle berubah
 *
 * Diobserve oleh ViewModel (UI Activity), NotificationService, dan OverlayManager
 * secara paralel — single fetch + single ticker, no double-work.
 */
class PlaybackController(
    private val source: PlaybackSource,
    private val lyricsRepository: LyricsRepository,
    private val romanizationService: RomanizationService,
    private val preferencesRepository: PreferencesRepository,
    private val scope: CoroutineScope,
    private val network: NetworkStatusSource = NetworkStatusSource.AlwaysOnline,
    /** Perangkat output aktif, kunci offset per perangkat. */
    private val audioOutput: AudioOutputSource = AudioOutputSource.Unknown,
) {
    /** Diteruskan ke UI untuk badge "Offline". */
    val isOnline: StateFlow<Boolean> = network.isOnline

    /**
     * Ketiga lapis offset yang sedang berlaku. [SyncOffsets.totalMs] adalah
     * nilai yang dipakai engine; UI memakainya untuk progress per kata dan
     * kompensasi seek, supaya keduanya sepakat dengan baris aktif.
     */
    private val _syncOffsets = MutableStateFlow(SyncOffsets())
    val syncOffsets: StateFlow<SyncOffsets> = _syncOffsets.asStateFlow()

    // ── Pass-through dari PlaybackSource ─────────────────────────
    val track: StateFlow<NowPlaying?> = source.nowPlaying
    val albumBitmap: StateFlow<Bitmap?> = source.albumBitmap
    val hasAccess: StateFlow<Boolean> = source.hasAccess
    val controls: StateFlow<PlayerControls> = source.controls
    val playerLabel: StateFlow<String?> = source.playerLabel

    /** Nama player yang barusan mengabaikan seek. UI memberi tahu user sekali per lagu. */
    val seekRefused: SharedFlow<String> = source.seekRefused

    // ── Lyrics state ─────────────────────────────────────────────
    //
    // lyricsState membawa ALASAN kosongnya, bukan cuma kosong. "LRCLIB bilang
    // lagu ini tidak berlirik" dan "jaringan lagi mati" tampil beda di UI dan
    // diperlakukan beda oleh cache.
    private val _lyricsState = MutableStateFlow<LyricsState>(LyricsState.Loading)
    val lyricsState: StateFlow<LyricsState> = _lyricsState.asStateFlow()

    /** Apa yang dijawab tiap sumber lirik untuk lagu aktif, null kalau lagunya dijawab cache. */
    val sourceReport: StateFlow<SourceReport?> = combine(source.nowPlaying, lyricsRepository.reports) { t, reports ->
        t?.id?.let { reports[it] }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /** Konten saja, untuk konsumen yang tidak peduli kenapa kosong. */
    val lyrics: StateFlow<LyricsResult?> = _lyricsState
        .map { (it as? LyricsState.Ready)?.result }
        .stateIn(scope, SharingStarted.Eagerly, null)

    // ── Current line index (ticker-driven) ───────────────────────
    private val _currentLineIndex = MutableStateFlow(-1)
    val currentLineIndex: StateFlow<Int> = _currentLineIndex.asStateFlow()

    val currentLine: StateFlow<SyncedLine?> = combine(
        lyrics,
        _currentLineIndex,
    ) { lyrics, idx ->
        lineAt(lyrics?.synced, idx)
    }.stateIn(scope, SharingStarted.Eagerly, null)

    // ── Romaji state (key = SyncedLine.id) ───────────────────────
    //
    // Dulu key-nya timeMs, dan dua baris dengan timestamp identik saling
    // menimpa: satu baris kehilangan romaji-nya, atau lebih buruk, memakai
    // romaji milik baris lain yang teksnya berbeda.
    private val _romaji = MutableStateFlow<Map<Int, String?>>(emptyMap())
    val romaji: StateFlow<Map<Int, String?>> = _romaji.asStateFlow()

    private var romajiJob: Job? = null

    // ── Accent color from album bitmap (Palette extraction off main) ──
    private val _accent = MutableStateFlow<Color?>(null)
    val accent: StateFlow<Color?> = _accent.asStateFlow()

    private var accentJob: Job? = null
    private var lastAccentBitmap: Bitmap? = null  // ref equality cache

    /**
     * Durasi yang dipakai fetch terakhir untuk lagu aktif. YouTube Music
     * mengirim judul lebih dulu dan durasi belakangan; lirik yang dipilih tanpa
     * durasi bisa versi lain lagunya, jadi begitu durasinya datang lirik dicari
     * ulang (lihat [shouldRematchForDuration]).
     */
    private var fetchedWithDurationMs: Long? = null

    /**
     * Satu-satunya penentu baris aktif untuk SEMUA permukaan: layar utama,
     * notification, dan overlay. Offset user diterapkan di dalam engine ini,
     * bukan di layer UI. Sebelumnya offset hanya dipakai di dua Composable
     * layar, jadi notification dan overlay diam-diam memakai timing berbeda.
     *
     * Semua akses terjadi di [scope] (Main.immediate), jadi aman walau engine
     * sendiri tidak thread-safe.
     */
    private val syncEngine = LyricsSyncEngine()

    init {
        refetchWhenOnlineAgain()
        fetchLyricsOnTrackChange()
        rematchWhenDurationArrives()
        followPlayerPosition()
        followSyncOffsets()
        runLineTicker()
        computeAccentFromArtwork()
        computeRomanization()
    }

    // ── Kontrol playback, diteruskan ke player yang sedang diikuti ──

    fun play() = source.play()
    fun pause() = source.pause()
    fun skipNext() = source.skipNext()
    fun skipPrevious() = source.skipPrevious()
    fun seekTo(positionMs: Long) = source.seekTo(positionMs)

    /** Baca ulang izin notification access, misalnya setelah kembali dari Settings sistem. */
    fun refreshAccess() = source.refreshAccess()

    /** Jaringan kembali: ambil ulang yang tadi gagal karena offline. */
    private fun refetchWhenOnlineAgain() = scope.launch {
        var wasOnline = network.isOnline.value
        network.isOnline.collect { online ->
            if (shouldRefetchOnReconnect(wasOnline, online, _lyricsState.value)) {
                Log.d(TAG, "Online lagi, ambil ulang lirik lagu aktif")
                reloadLyrics()
            }
            wasOnline = online
        }
    }

    /** Fetch lirik tiap lagu berganti, lalu preload antrean. */
    private fun fetchLyricsOnTrackChange() = scope.launch {
        source.nowPlaying
            .map { it?.id }
            .distinctUntilChanged()
            .collect { trackId ->
                _lyricsState.value = LyricsState.Loading
                // Engine WAJIB dikosongkan bareng lirik. Kalau tidak,
                // ticker masih memegang baris lagu SEBELUMNYA selama fetch
                // berjalan dan sempat menerbitkan index dari lagu yang salah.
                syncEngine.onLyrics(null)
                _currentLineIndex.value = -1
                fetchedWithDurationMs = null
                if (trackId.isNullOrBlank()) return@collect
                awaitDuration(trackId)
                val t = source.nowPlaying.value?.takeIf { it.id == trackId } ?: return@collect
                Log.d(TAG, "Fetching lyrics for ${t.title}")
                fetchedWithDurationMs = t.durationMs
                val fetched = fetchFor(t, allowRetry = true)
                if (shouldApplyFetchedLyrics(trackId, source.nowPlaying.value?.id)) applyLyrics(fetched)
                preloadQueue()
            }
    }

    /**
     * Tunggu sebentar kalau durasi lagu belum diketahui. YouTube Music mengirim
     * judul lebih dulu dan durasi beberapa ratus milidetik kemudian; tanpa
     * durasi, pemilihan lirik tidak bisa membedakan versi asli dari versi live.
     * Dibatasi [DURATION_WAIT_MS] supaya player yang memang tidak pernah
     * mengirim durasi tetap dapat lirik.
     */
    private suspend fun awaitDuration(trackId: String) {
        if ((source.nowPlaying.value?.durationMs ?: 0L) > 0L) return
        withTimeoutOrNull(DURATION_WAIT_MS) {
            source.nowPlaying.first { it?.id != trackId || it.durationMs > 0L }
        }
    }

    /**
     * Preload lirik 3 lagu berikutnya dari antrean player, kalau player
     * membaginya. Offline dilewati: semua sumber lirik pasti gagal.
     */
    private fun preloadQueue() {
        if (!network.isOnline.value) return
        val queue = source.upcoming(limit = PRELOAD_COUNT)
        scope.launch {
            queue.forEach { next ->
                if (!shouldPreload(next.id)) return@forEach
                Log.d(TAG, "Preload lyrics: ${next.title}")
                lyricsRepository.fetchLyrics(
                    trackId = next.id,
                    title = next.title,
                    artist = next.artist,
                    album = next.album,
                    durationMs = next.durationMs,
                    // Preload tidak boleh berebut jaringan dengan lagu yang
                    // sedang diputar. Hasilnya cuma mengisi cache.
                    allowRetry = false,
                )
            }
        }
    }

    /** Lagu yang sama, tapi durasinya baru diketahui: cari ulang dengan durasi. */
    private fun rematchWhenDurationArrives() = scope.launch {
        source.nowPlaying
            .map { it?.id to (it?.durationMs ?: 0L) }
            .distinctUntilChanged()
            .collect { (_, durationMs) ->
                if (!shouldRematchForDuration(fetchedWithDurationMs, durationMs)) return@collect
                Log.d(TAG, "Durasi baru diketahui ($durationMs ms), cari ulang lirik")
                fetchedWithDurationMs = durationMs
                // Melewati cache: entri yang ada dipilih tanpa durasi, justru itu yang diganti.
                reloadLyrics(forceRefresh = true)
            }
    }

    /**
     * Baseline posisi tiap kali player melapor. Pakai `capturedAtMs` (jam
     * dinding saat snapshot dibuat di sumber), bukan jam saat collect, supaya
     * konsisten dengan ekstrapolasi UI walau collector telat sedikit.
     */
    private fun followPlayerPosition() = scope.launch {
        source.nowPlaying.collect { t ->
            if (t == null) return@collect
            syncEngine.onPlayerState(
                progressMs = t.progressMs,
                capturedAtMs = t.capturedAtMs,
                isPaused = t.isPaused,
            )
            // Seek dan play/pause harus terasa seketika, bukan menunggu tick berikutnya.
            publishLineIndex()
        }
    }

    /** Offset sync: global + perangkat output + lagu ini. Dibaca ulang saat perangkat atau lagu berganti. */
    private fun followSyncOffsets() = scope.launch {
        syncOffsetsFlow(
            globalMs = preferencesRepository.lyricsOffsetMs,
            output = audioOutput.current,
            trackId = source.nowPlaying.map { it?.id },
            deviceOffsetMs = preferencesRepository::deviceOffsetMs,
            songOffsetMs = preferencesRepository::songOffsetMs,
        ).collect { offsets ->
            _syncOffsets.value = offsets
            syncEngine.onOffset(offsets.totalMs)
            publishLineIndex()
        }
    }

    /**
     * Ticker adaptif — tidur tepat sampai batas baris berikutnya. Interval tetap
     * salah di dua arah: terlalu jarang saat lagu jalan (baris telat menyala
     * sampai setengah detik), terlalu sering saat pause atau saat lagu tidak
     * punya lirik (bangun tanpa hasil).
     */
    private fun runLineTicker() = scope.launch {
        while (isActive) {
            publishLineIndex()
            delay(syncEngine.nextDelayMs(idleMs = IDLE_TICK_MS))
        }
    }

    /** Warna aksen dari cover, off main thread. Cache via reference equality. */
    private fun computeAccentFromArtwork() = scope.launch {
        source.albumBitmap.collect { bitmap ->
            if (!shouldRecomputeAccent(bitmap, lastAccentBitmap)) return@collect
            lastAccentBitmap = bitmap
            accentJob?.cancel()
            if (bitmap == null) {
                _accent.value = null
                return@collect
            }
            accentJob = launch {
                val accent = withContext(Dispatchers.Default) { extractAccent(bitmap) }
                if (shouldApplyAccent(bitmap, lastAccentBitmap)) _accent.value = accent
            }
        }
    }

    /** Romaji reactive — saat lirik berubah atau toggle berubah. */
    private fun computeRomanization() = scope.launch {
        combine(
            lyrics,
            preferencesRepository.romanizationEnabled,
        ) { lyrics, enabled -> lyrics to enabled }
            .collect { (lyrics, enabled) ->
                romajiJob?.cancel()
                if (!enabled || lyrics?.synced.isNullOrEmpty()) {
                    _romaji.value = emptyMap()
                    return@collect
                }
                val lines = lyrics.synced
                romajiJob = launch {
                    val results = romanizationService.romanizeLines(lines.map { it.text })
                    _romaji.value = lines.mapIndexed { idx, line -> line.id to results[idx] }.toMap()
                }
            }
    }

    private fun applyLyrics(state: LyricsState) {
        _lyricsState.value = state
        // Jangan tunggu tick berikutnya: kalau lirik datang dari cache atau
        // override, baris aktif harus langsung benar.
        syncEngine.onLyrics((state as? LyricsState.Ready)?.result?.synced)
        publishLineIndex()
    }

    /**
     * Muat ulang lirik untuk track yang sedang diputar.
     *
     * Dipanggil setelah user menerapkan atau melupakan override, saat jaringan
     * kembali, dan saat durasi lagu baru diketahui. Karena override dicek
     * paling awal di repository, pemanggilan ini otomatis mengambil pilihan
     * terbaru.
     */
    fun reloadLyrics(forceRefresh: Boolean = false) {
        val t = source.nowPlaying.value ?: return
        scope.launch {
            val fetched = fetchFor(t, allowRetry = false, forceRefresh = forceRefresh)
            if (shouldApplyFetchedLyrics(t.id, source.nowPlaying.value?.id)) applyLyrics(fetched)
        }
    }

    private suspend fun fetchFor(t: NowPlaying, allowRetry: Boolean, forceRefresh: Boolean = false): LyricsState =
        lyricsRepository.fetchLyrics(
            trackId = t.id,
            title = t.title,
            artist = t.artist,
            album = t.album,
            durationMs = t.durationMs,
            allowRetry = allowRetry,
            forceRefresh = forceRefresh,
        )

    /** Simpan koreksi sync untuk lagu yang sedang diputar. Tanpa lagu, tidak ada efek. */
    suspend fun setSongOffsetMs(ms: Int) {
        val trackId = source.nowPlaying.value?.id?.takeIf { it.isNotBlank() } ?: return
        preferencesRepository.setSongOffsetMs(trackId, ms)
    }

    /** Simpan koreksi sync untuk perangkat output aktif. Tanpa perangkat, tidak ada efek. */
    suspend fun setDeviceOffsetMs(ms: Int) {
        val key = audioOutput.current.value?.key ?: return
        preferencesRepository.setDeviceOffsetMs(key, ms)
    }

    /** Tanya engine, publikasikan kalau berubah. */
    private fun publishLineIndex() {
        val idx = syncEngine.activeIndex()
        if (shouldPublishLineIndex(idx, _currentLineIndex.value)) _currentLineIndex.value = idx
    }

    /**
     * Extract dominant color dari album bitmap → AccentLock.lockedAccent().
     * Hue dipertahankan dari album, chroma + lightness DIKUNCI ke nilai
     * editorial-magazine spec (L=0.74 C=0.17 dark / L=0.55 C=0.13 light).
     * Hasil: accent tetap readable terhadap ink-1 untuk lagu apapun.
     *
     * MUST be called from non-main dispatcher — Palette.generate() blocking.
     *
     * Note: kita selalu output dark-mode locked accent dari sini. Theme
     * layer (SpotivibeTheme) yang re-lock ke light variant kalau user
     * pilih light mode. Compromise simplicity vs accuracy — di praktek
     * track change paling sering daripada theme toggle.
     */
    private fun extractAccent(bitmap: Bitmap): Color {
        return try {
            val palette = Palette.from(bitmap).maximumColorCount(16).generate()
            val argb = pickAccentArgb(
                vibrant = palette.getVibrantColor(0),
                lightVibrant = palette.getLightVibrantColor(0),
                dominant = palette.getDominantColor(0),
                fallback = CORAL_FALLBACK_ARGB,
            )
            AccentLock.lockedAccent(Color(argb), dark = true)
        } catch (t: Throwable) {
            Log.w(TAG, "Palette extract failed: ${t.message}")
            Color(CORAL_FALLBACK_ARGB)
        }
    }

    companion object {
        private const val TAG = "PlaybackController"

        /** Jeda saat tidak ada lirik synced untuk diikuti. */
        private const val IDLE_TICK_MS = 500L

        /** Batas menunggu durasi lagu sebelum mencari lirik tanpa durasi. */
        private const val DURATION_WAIT_MS = 2_000L

        /** Berapa lagu antrean yang liriknya diambil lebih dulu. */
        private const val PRELOAD_COUNT = 3

        /** Dipakai saat album art tidak punya swatch yang bisa dipakai sama sekali. */
        private val CORAL_FALLBACK_ARGB = 0xFFEC6A5C.toInt()
    }
}
