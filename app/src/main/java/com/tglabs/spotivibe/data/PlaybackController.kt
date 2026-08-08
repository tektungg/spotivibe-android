package com.tglabs.spotivibe.data

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import com.tglabs.spotivibe.domain.LyricsResult
import com.tglabs.spotivibe.domain.LyricsState
import com.tglabs.spotivibe.domain.NowPlaying
import com.tglabs.spotivibe.domain.PlaybackCapability
import com.tglabs.spotivibe.domain.SyncedLine
import com.tglabs.spotivibe.domain.LyricsSyncEngine
import com.tglabs.spotivibe.domain.resolveCapability
import com.tglabs.spotivibe.ui.theme.AccentLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Application-scoped controller. Single source of truth untuk:
 * - Track yang sedang main (via SpotifyConnection)
 * - Album bitmap (via SpotifyConnection)
 * - Lyrics yang sudah di-fetch (via LyricsRepository)
 * - Current active lyric line — extrapolated 500ms tick
 * - Romaji map per timeMs — auto-compute reactive saat lyrics atau toggle berubah
 *
 * Diobserve oleh ViewModel (UI Activity), NotificationService, dan OverlayManager
 * secara paralel — single fetch + single ticker, no double-work.
 */
class PlaybackController(
    private val connection: SpotifyConnection,
    private val lyricsRepository: LyricsRepository,
    private val romanizationService: RomanizationService,
    private val preferencesRepository: PreferencesRepository,
    private val webApiClient: WebApiClient,
    private val scope: CoroutineScope,
) {
    // ── Pass-through dari SpotifyConnection ──────────────────────
    val track: StateFlow<NowPlaying?> = connection.nowPlaying
    val albumBitmap = connection.albumBitmap
    val connectionState = connection.connectionState

    // ── Lyrics state ─────────────────────────────────────────────
    //
    // lyricsState membawa ALASAN kosongnya, bukan cuma kosong. "LRCLIB bilang
    // lagu ini tidak berlirik" dan "jaringan lagi mati" tampil beda di UI dan
    // diperlakukan beda oleh cache.
    private val _lyricsState = MutableStateFlow<LyricsState>(LyricsState.Loading)
    val lyricsState: StateFlow<LyricsState> = _lyricsState.asStateFlow()

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
        lyrics?.synced?.getOrNull(idx)
    }.stateIn(scope, SharingStarted.Eagerly, null)

    // ── Romaji state (key = SyncedLine.timeMs) ───────────────────
    private val _romaji = MutableStateFlow<Map<Long, String?>>(emptyMap())
    val romaji: StateFlow<Map<Long, String?>> = _romaji.asStateFlow()

    private var romajiJob: Job? = null

    // ── Accent color from album bitmap (Palette extraction off main) ──
    private val _accent = MutableStateFlow<Color?>(null)
    val accent: StateFlow<Color?> = _accent.asStateFlow()

    // ── Kemampuan kontrol playback ───────────────────────────────
    //
    // Dua sumber bukti, digabung lewat resolveCapability():
    //   1. field `product` dari Web API /me (null = belum tahu)
    //   2. penolakan nyata dari App Remote saat kita kirim perintah
    //
    // Selama keduanya belum bicara, hasilnya Unknown dan UI menampilkan kontrol
    // secara optimistis. Ini yang memperbaiki bug lama: `isPremium` boolean
    // memulai hidup sebagai `false`, dan karena token implicit-grant tidak bisa
    // di-refresh, pengecekan /me tidak pernah berhasil setelah satu jam,
    // sehingga user Premium permanen dianggap bukan Premium.
    private val _product = MutableStateFlow<String?>(null)

    val capability: StateFlow<PlaybackCapability> = combine(
        _product,
        connection.controlRejected,
    ) { product, rejected ->
        resolveCapability(product, rejected)
    }.stateIn(scope, SharingStarted.Eagerly, PlaybackCapability.Unknown)

    private var accentJob: Job? = null
    private var lastAccentBitmap: Bitmap? = null  // ref equality cache

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
        // 1. Fetch lyrics tiap track ID berubah + preload queue
        scope.launch {
            connection.nowPlaying
                .map { it?.id }
                .distinctUntilChanged()
                .collect { trackId ->
                    _lyricsState.value = LyricsState.Loading
                    // Engine WAJIB dikosongkan bareng lirik. Kalau tidak,
                    // ticker masih memegang baris lagu SEBELUMNYA selama fetch
                    // berjalan dan sempat menerbitkan index dari lagu yang salah.
                    syncEngine.onLyrics(null)
                    _currentLineIndex.value = -1
                    if (trackId.isNullOrBlank()) return@collect
                    val t = connection.nowPlaying.value ?: return@collect
                    Log.d(TAG, "Fetching lyrics for ${t.title}")
                    val fetched = lyricsRepository.fetchLyrics(
                        trackId = t.id,
                        title = t.title,
                        artist = t.artist,
                        album = t.album,
                        durationMs = t.durationMs,
                    )
                    if (connection.nowPlaying.value?.id == trackId) {
                        _lyricsState.value = fetched
                        // Jangan tunggu tick berikutnya: kalau lirik datang dari
                        // cache, baris aktif harus langsung benar.
                        syncEngine.onLyrics((fetched as? LyricsState.Ready)?.result?.synced)
                        publishLineIndex()
                    }
                    // Cek /me sampai dapat jawaban. Gagal di sini tidak lagi
                    // menyembunyikan kontrol: capability tetap Unknown, dan
                    // Unknown menampilkan transport.
                    if (_product.value == null) {
                        scope.launch {
                            val product = webApiClient.getProduct()
                            if (product != null) {
                                _product.value = product
                                Log.d(TAG, "Product check: $product -> ${capability.value}")
                            } else {
                                Log.d(TAG, "Product check null — retry saat track berikutnya")
                            }
                        }
                    }
                    // Preload queue — fetch lyrics untuk 3 track berikutnya
                    scope.launch {
                        val queue = webApiClient.getQueue(limit = 3)
                        queue.forEach { next ->
                            if (next.id.isBlank()) return@forEach
                            Log.d(TAG, "Preload lyrics: ${next.title}")
                            lyricsRepository.fetchLyrics(
                                trackId = next.id,
                                title = next.title,
                                artist = next.artist,
                                album = next.album,
                                durationMs = next.durationMs,
                                // Preload tidak boleh berebut jaringan dengan
                                // lagu yang sedang diputar.
                                allowRetry = false,
                            )
                            // Hasilnya cuma mengisi cache; tidak menyentuh state.
                        }
                    }
                }
        }

        // 2. Update baseline tiap kali Spotify push event.
        //    Pakai t.capturedAtMs (wall clock saat snapshot di-take di
        //    SpotifyConnection callback), bukan currentTimeMillis() di sini —
        //    biar konsisten dengan UI extrapolation dan akurat walau collect
        //    fires sedikit terlambat dari callback Spotify.
        scope.launch {
            connection.nowPlaying.collect { t ->
                if (t == null) return@collect
                syncEngine.onPlayerState(
                    progressMs = t.progressMs,
                    capturedAtMs = t.capturedAtMs,
                    isPaused = t.isPaused,
                )
                // Seek dan play/pause harus terasa seketika, bukan menunggu
                // tick berikutnya.
                publishLineIndex()
            }
        }

        // 2b. Offset sync manual — sumbernya preference, dipakai semua permukaan.
        scope.launch {
            preferencesRepository.lyricsOffsetMs
                .distinctUntilChanged()
                .collect { offset ->
                    syncEngine.onOffset(offset)
                    publishLineIndex()
                }
        }

        // 3. Ticker adaptif — tidur tepat sampai batas baris berikutnya.
        //    Interval tetap salah di dua arah: terlalu jarang saat lagu jalan
        //    (baris telat menyala sampai setengah detik), terlalu sering saat
        //    pause atau saat lagu tidak punya lirik (bangun tanpa hasil).
        scope.launch {
            while (isActive) {
                publishLineIndex()
                delay(syncEngine.nextDelayMs(idleMs = IDLE_TICK_MS))
            }
        }

        // 4. Compute accent color dari bitmap — off main thread.
        //    Cache via reference equality supaya tidak recompute untuk bitmap sama.
        scope.launch {
            connection.albumBitmap.collect { bitmap ->
                if (bitmap === lastAccentBitmap) return@collect
                lastAccentBitmap = bitmap
                accentJob?.cancel()
                if (bitmap == null) {
                    _accent.value = null
                    return@collect
                }
                accentJob = launch {
                    val accent = withContext(Dispatchers.Default) {
                        extractAccent(bitmap)
                    }
                    if (lastAccentBitmap === bitmap) {
                        _accent.value = accent
                    }
                }
            }
        }

        // 5. Compute romaji reactive — saat lyrics berubah atau toggle berubah
        scope.launch {
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
                        val texts = lines.map { it.text }
                        val results = romanizationService.romanizeLines(texts)
                        _romaji.value = lines.mapIndexed { idx, line ->
                            line.timeMs to results[idx]
                        }.toMap()
                    }
                }
        }
    }

    /** Tanya engine, publikasikan kalau berubah. */
    private fun publishLineIndex() {
        val idx = syncEngine.activeIndex()
        if (idx != _currentLineIndex.value) _currentLineIndex.value = idx
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
            val argb = palette.getVibrantColor(0)
                .takeIf { it != 0 }
                ?: palette.getLightVibrantColor(0).takeIf { it != 0 }
                ?: palette.getDominantColor(0xFFEC6A5C.toInt()) // Coral fallback
            AccentLock.lockedAccent(Color(argb), dark = true)
        } catch (t: Throwable) {
            Log.w(TAG, "Palette extract failed: ${t.message}")
            Color(0xFFEC6A5C) // Coral fallback
        }
    }

    companion object {
        private const val TAG = "PlaybackController"

        /** Jeda saat tidak ada lirik synced untuk diikuti. */
        private const val IDLE_TICK_MS = 500L
    }
}
