package com.tglabs.spotivibe.data

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import com.tglabs.spotivibe.domain.LyricsResult
import com.tglabs.spotivibe.domain.NowPlaying
import com.tglabs.spotivibe.domain.SyncedLine
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
    private val _lyrics = MutableStateFlow<LyricsResult?>(null)
    val lyrics: StateFlow<LyricsResult?> = _lyrics.asStateFlow()

    // ── Current line index (ticker-driven) ───────────────────────
    private val _currentLineIndex = MutableStateFlow(-1)
    val currentLineIndex: StateFlow<Int> = _currentLineIndex.asStateFlow()

    val currentLine: StateFlow<SyncedLine?> = combine(
        _lyrics,
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

    // ── Premium detection (Web API /me) — cached per session ─────
    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()
    @Volatile private var premiumChecked = false

    private var accentJob: Job? = null
    private var lastAccentBitmap: Bitmap? = null  // ref equality cache

    // ── Baseline untuk extrapolation progress ────────────────────
    @Volatile private var baselineProgressMs: Long = 0L
    @Volatile private var baselineTimestampMs: Long = 0L
    @Volatile private var baselinePaused: Boolean = true

    init {
        // 1. Fetch lyrics tiap track ID berubah + preload queue
        scope.launch {
            connection.nowPlaying
                .map { it?.id }
                .distinctUntilChanged()
                .collect { trackId ->
                    _lyrics.value = null
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
                        _lyrics.value = fetched
                    }
                    // Premium detection — retry per track change sampai dapat hasil valid.
                    // Awalnya bisa fail kalau access token belum loaded dari DataStore
                    // (race condition saat cold start + auto-reconnect).
                    if (!premiumChecked) {
                        scope.launch {
                            val product = webApiClient.getProduct()
                            if (product != null) {
                                premiumChecked = true
                                _isPremium.value = (product == "premium")
                                Log.d(TAG, "Premium check: product=$product, isPremium=${_isPremium.value}")
                            } else {
                                Log.d(TAG, "Premium check returned null — will retry next track change")
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
                            )
                            // Result populates mem + disk cache; tidak set _lyrics
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
                baselineProgressMs = t.progressMs
                baselineTimestampMs = t.capturedAtMs
                baselinePaused = t.isPaused
            }
        }

        // 3. Ticker 500ms — recompute current line index dari extrapolated progress
        scope.launch {
            while (isActive) {
                delay(500)
                val synced = _lyrics.value?.synced
                if (synced.isNullOrEmpty()) {
                    if (_currentLineIndex.value != -1) _currentLineIndex.value = -1
                    continue
                }
                val now = computeExtrapolatedProgressMs()
                val idx = findActiveIndex(synced, now)
                if (idx != _currentLineIndex.value) {
                    _currentLineIndex.value = idx
                }
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
                _lyrics,
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

    private fun computeExtrapolatedProgressMs(): Long {
        if (baselinePaused) return baselineProgressMs
        val elapsed = System.currentTimeMillis() - baselineTimestampMs
        return baselineProgressMs + elapsed
    }

    private fun findActiveIndex(lines: List<SyncedLine>, ms: Long): Int {
        if (lines.isEmpty()) return -1
        var lo = 0
        var hi = lines.size - 1
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= ms) {
                ans = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return ans
    }

    /**
     * Extract dominant color dari album bitmap. Clamp ke range readable di
     * dark theme (saturation 0.55-0.95, lightness 0.65-0.85).
     * MUST be called from non-main dispatcher — Palette.generate() blocking.
     */
    private fun extractAccent(bitmap: Bitmap): Color {
        return try {
            val palette = Palette.from(bitmap).maximumColorCount(16).generate()
            val argb = palette.getVibrantColor(0)
                .takeIf { it != 0 }
                ?: palette.getLightVibrantColor(0).takeIf { it != 0 }
                ?: palette.getDominantColor(0xFFB8A4FF.toInt())
            clampForDarkTheme(Color(argb))
        } catch (t: Throwable) {
            Log.w(TAG, "Palette extract failed: ${t.message}")
            Color(0xFFB8A4FF)
        }
    }

    private fun clampForDarkTheme(c: Color): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(
            (c.red * 255).toInt(),
            (c.green * 255).toInt(),
            (c.blue * 255).toInt(),
            hsv,
        )
        hsv[1] = hsv[1].coerceIn(0.55f, 0.95f)
        hsv[2] = hsv[2].coerceIn(0.65f, 0.85f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    companion object {
        private const val TAG = "PlaybackController"
    }
}
