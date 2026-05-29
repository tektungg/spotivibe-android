package com.tglabs.spotivibe.ui.screen

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tglabs.spotivibe.domain.UiState
import com.tglabs.spotivibe.ui.component.AmbientBg
import com.tglabs.spotivibe.ui.component.LyricsList
import com.tglabs.spotivibe.ui.component.NPHeader
import com.tglabs.spotivibe.ui.component.SelectionActionBar
import com.tglabs.spotivibe.ui.component.SelectionHeader
import com.tglabs.spotivibe.ui.component.Transport
import com.tglabs.spotivibe.ui.theme.AccentDefault
import com.tglabs.spotivibe.ui.theme.LocalSvColors
import com.tglabs.spotivibe.ui.theme.SvSpace
import com.tglabs.spotivibe.ui.theme.SvType
import com.tglabs.spotivibe.ui.theme.SpotivibeTheme
import com.tglabs.spotivibe.ui.theme.AccentLock
import com.tglabs.spotivibe.util.LyricShareCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * NowPlayingScreen — orchestrator. Layout per orientation:
 *
 * Portrait:
 *  ┌────────────────────────┐
 *  │ NPHeader / Selection   │
 *  │ ─── hairline ──────────│
 *  │                        │
 *  │ LyricsList             │
 *  │                        │
 *  │ ─── hairline ──────────│
 *  │ Transport / Selection  │
 *  └────────────────────────┘
 *
 * Landscape: see NowPlayingTabletScreen (TBD task 17).
 *
 * Background = AmbientBg di belakang semua. Theme di-wrap dengan accent
 * dinamis dari state.accentColor → AccentLock.lockedAccent() (sudah di
 * controller, jadi pass langsung).
 */
@Composable
fun NowPlayingScreen(
    state: UiState.Playing,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleRomanization: () -> Unit,
    onToggleOverlay: () -> Unit,
    onToggleDarkMode: () -> Unit,
    onBumpFontSize: (Int) -> Unit,
    onLogout: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Theme wrapping dipindahkan ke MainActivity — di sini cukup baca SvColors
    val sv = LocalSvColors.current
    val track = state.track
    val context = LocalContext.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // ── Progress extrapolation (per fix di NowPlaying.extrapolatedProgressMs) ──
    val displayProgressMs by produceState(
        initialValue = track.extrapolatedProgressMs(),
        track,
    ) {
        value = track.extrapolatedProgressMs()
        if (!track.isPaused) {
            while (isActive) {
                delay(200)
                value = track.extrapolatedProgressMs()
            }
        }
    }
    var draggingValue by remember(track.id) { mutableLongStateOf(-1L) }
    val rawProgressMs = if (draggingValue >= 0L) draggingValue else displayProgressMs
    val effectiveProgressMs = (rawProgressMs + state.lyricsOffsetMs).coerceAtLeast(0L)

    // ── Karaoke fullscreen state ──
    var karaokeMode by remember { mutableStateOf(false) }
    if (karaokeMode) {
        KaraokeView(
            lyrics = state.lyrics,
            progressMs = effectiveProgressMs,
            accent = state.accentColor ?: sv.accent,
            romaji = state.romaji,
            fontSize = state.lyricsFontSize,
            onExit = { karaokeMode = false },
        )
        return
    }

    // ── Selection mode state ──
    var selectedTimes by remember(track.id) { mutableStateOf<Set<Long>>(emptySet()) }
    val isSelecting = selectedTimes.isNotEmpty()
    val maxSelect = 5
    BackHandler(enabled = isSelecting) { selectedTimes = emptySet() }

    val toggleSelect: (Long) -> Unit = { timeMs ->
        selectedTimes = when {
            selectedTimes.contains(timeMs)    -> selectedTimes - timeMs
            selectedTimes.size < maxSelect    -> selectedTimes + timeMs
            else                              -> selectedTimes
        }
    }
    val handleLineTap: (Long) -> Unit = { ms ->
        if (isSelecting) toggleSelect(ms) else onSeek(ms)
    }
    val handleLineLongPress: (com.tglabs.spotivibe.domain.SyncedLine) -> Unit = { line ->
        if (isSelecting) toggleSelect(line.timeMs)
        else selectedTimes = setOf(line.timeMs)
    }

    val handleShareSelected: () -> Unit = handleShare@{
        val synced = state.lyrics?.synced ?: return@handleShare
        val ordered = synced
            .filter { it.timeMs in selectedTimes }
            .sortedBy { it.timeMs }
            .map { line ->
                LyricShareCard.Entry(
                    text = line.text,
                    romaji = state.romaji[line.timeMs],
                )
            }
        if (ordered.isEmpty()) return@handleShare
        LyricShareCard.shareLines(
            context = context,
            entries = ordered,
            title = track.title,
            artist = track.artist,
            accentArgb = (state.accentColor ?: AccentDefault).toArgb(),
        )
        selectedTimes = emptySet()
    }

    Box(modifier = modifier.fillMaxSize()) {
        // ── AmbientBg ──
        AmbientBg(
            bitmap = state.albumBitmap,
            highContrast = state.highContrast,
        )

        // ── Header scrim ──
        // Solid-ish gradient di belakang header supaya teks (terutama mono
        // eyebrow album) tetap legible di atas album art terang + halftone
        // noise. Tinggi ~180dp menutup status bar + header + hairline rule.
        // bg0 strong di top, fade transparan ke bawah supaya lyrics tidak
        // ketutupan.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to sv.bg0.copy(alpha = 0.92f),
                            0.6f to sv.bg0.copy(alpha = 0.78f),
                            1.0f to sv.bg0.copy(alpha = 0f),
                        ),
                    )
                ),
        )

        // ── Control scrim (mirror header) ──
        // Bottom-anchored gradient supaya transport/banner legible di atas
        // album art terang. Transparan di atas, bg0 strong di bawah.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .align(Alignment.BottomCenter)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to sv.bg0.copy(alpha = 0f),
                            0.4f to sv.bg0.copy(alpha = 0.78f),
                            1.0f to sv.bg0.copy(alpha = 0.92f),
                        ),
                    )
                ),
        )

        // ── Content stack ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = if (isLandscape) SvSpace.s4 else SvSpace.s8),
        ) {
            // Header: switches to selection mode header when selecting
            if (isSelecting) {
                SelectionHeader(
                    count = selectedTimes.size,
                    max = maxSelect,
                    onCancel = { selectedTimes = emptySet() },
                )
            } else {
                NPHeader(
                    albumBitmap = state.albumBitmap,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    showRomajiIcon = state.canRomanize,
                    romajiEnabled = state.romanizationEnabled,
                    overlayEnabled = state.overlayEnabled,
                    onRomajiClick = onToggleRomanization,
                    onOverlayClick = onToggleOverlay,
                    onMoreClick = onOpenSettings,
                )
            }

            com.tglabs.spotivibe.ui.component.HairlineRule()

            // Lyrics list (mask via padding handles edge fade)
            Box(modifier = Modifier.weight(1f, fill = true).fillMaxWidth()) {
                LyricsList(
                    lyrics = state.lyrics,
                    progressMs = effectiveProgressMs,
                    romaji = state.romaji,
                    fontSize = state.lyricsFontSize,
                    lineSpacing = (state.lineSpacing + 14).coerceAtLeast(10), // base 7 → ~21dp gap target
                    highContrast = state.highContrast,
                    smoothScroll = state.smoothScroll,
                    hapticEnabled = state.hapticEnabled,
                    onSeekToLine = handleLineTap,
                    onLongPressShare = handleLineLongPress,
                    selectedTimes = selectedTimes,
                    isSelecting = isSelecting,
                )
            }

            // ── Bottom: selection bar / premium banner / transport ──
            when {
                isSelecting -> {
                    SelectionActionBar(
                        count = selectedTimes.size,
                        onShare = handleShareSelected,
                        onCancel = { selectedTimes = emptySet() },
                    )
                }
                !state.isPremium -> {
                    com.tglabs.spotivibe.ui.component.HairlineRule()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = SvSpace.s4),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "SPOTIFY PREMIUM REQUIRED TO CONTROL PLAYBACK",
                            style = SvType.MonoUp,
                            color = sv.ink3,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                else -> {
                    Transport(
                        progressMs = rawProgressMs,
                        durationMs = track.durationMs,
                        isPaused = track.isPaused,
                        onDrag = { draggingValue = it },
                        onSeek = { positionMs ->
                            draggingValue = -1L
                            onSeek(positionMs)
                        },
                        onTogglePlayPause = onTogglePlayPause,
                        onNext = onNext,
                        onPrevious = onPrevious,
                    )
                }
            }
        }
    }
}
