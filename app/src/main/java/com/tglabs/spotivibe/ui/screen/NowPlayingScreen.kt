package com.tglabs.spotivibe.ui.screen

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tglabs.spotivibe.domain.UiState
import com.tglabs.spotivibe.domain.lyricsProgressMs
import com.tglabs.spotivibe.domain.seekTargetMs
import com.tglabs.spotivibe.ui.component.AmbientBg
import com.tglabs.spotivibe.ui.component.LyricsList
import com.tglabs.spotivibe.ui.component.NPHeader
import com.tglabs.spotivibe.ui.component.SelectionActionBar
import com.tglabs.spotivibe.ui.component.SelectionHeader
import com.tglabs.spotivibe.ui.component.Transport
import com.tglabs.spotivibe.ui.theme.AccentDefault
import com.tglabs.spotivibe.ui.theme.LocalSvColors
import com.tglabs.spotivibe.ui.theme.LocalSvWindow
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
    onSearchLyrics: () -> Unit = {},
    hasRememberedOverride: Boolean = false,
    onForgetOverride: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Theme wrapping dipindahkan ke MainActivity — di sini cukup baca SvColors
    val sv = LocalSvColors.current
    val track = state.track
    val context = LocalContext.current
    // Padding atas dikurangi saat tingginya sempit, bukan saat landscape:
    // tablet landscape tingginya lega dan tidak perlu dirapatkan.
    val isLandscape = LocalSvWindow.current.isShort

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
    // rawProgressMs = posisi playback sebenarnya, untuk slider transport.
    // effectiveProgressMs = posisi setelah koreksi offset, untuk highlight
    // per-kata. Baris aktif TIDAK dihitung di sini lagi; datang dari
    // state.currentLineIndex supaya sama persis dengan notification + overlay.
    val rawProgressMs = if (draggingValue >= 0L) draggingValue else displayProgressMs
    val effectiveProgressMs = lyricsProgressMs(rawProgressMs, state.lyricsOffsetMs)

    // ── Karaoke fullscreen state ──
    var karaokeMode by remember { mutableStateOf(false) }
    if (karaokeMode) {
        KaraokeView(
            lyricsState = state.lyricsState,
            activeIndex = state.currentLineIndex,
            progressMs = effectiveProgressMs,
            accent = state.accentColor ?: sv.accent,
            romaji = state.romaji,
            fontSize = state.lyricsFontSize,
            onExit = { karaokeMode = false },
            durationMs = track.durationMs,
            artist = track.artist,
        )
        return
    }

    // ── Selection mode state ──
    // Diidentifikasi lewat SyncedLine.id, bukan timeMs. Dengan timeMs, memilih
    // satu baris ikut memilih kembarannya yang timestamp-nya sama.
    var selectedIds by remember(track.id) { mutableStateOf<Set<Int>>(emptySet()) }
    val isSelecting = selectedIds.isNotEmpty()
    val maxSelect = 5
    BackHandler(enabled = isSelecting) { selectedIds = emptySet() }

    val toggleSelect: (Int) -> Unit = { id ->
        selectedIds = when {
            selectedIds.contains(id)     -> selectedIds - id
            selectedIds.size < maxSelect -> selectedIds + id
            else                         -> selectedIds
        }
    }
    val handleLineTap: (com.tglabs.spotivibe.domain.SyncedLine) -> Unit = { line ->
        // Seek dikompensasi offset. Tanpa ini, dengan offset non-nol tap baris
        // mendarat di posisi yang justru membuat baris BERIKUTNYA yang aktif,
        // karena progress lirik = posisi + offset.
        if (isSelecting) toggleSelect(line.id)
        else onSeek(seekTargetMs(line.timeMs, state.lyricsOffsetMs))
    }
    val handleLineLongPress: (com.tglabs.spotivibe.domain.SyncedLine) -> Unit = { line ->
        if (isSelecting) toggleSelect(line.id)
        else selectedIds = setOf(line.id)
    }

    val handleShareSelected: () -> Unit = handleShare@{
        val synced = state.lyrics?.synced ?: return@handleShare
        val ordered = synced
            .filter { it.id in selectedIds }
            .sortedBy { it.id }
            .map { line ->
                LyricShareCard.Entry(
                    text = line.text,
                    romaji = state.romaji[line.id],
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
        selectedIds = emptySet()
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
                    count = selectedIds.size,
                    max = maxSelect,
                    onCancel = { selectedIds = emptySet() },
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
                    onEnterKaraoke = { karaokeMode = true },
                    onMoreClick = onOpenSettings,
                    onSearchLyrics = onSearchLyrics,
                    showForgetOverride = hasRememberedOverride,
                    onForgetOverride = onForgetOverride,
                )
            }

            com.tglabs.spotivibe.ui.component.HairlineRule()

            // Lyrics list (mask via padding handles edge fade)
            Box(modifier = Modifier.weight(1f, fill = true).fillMaxWidth()) {
                LyricsList(
                    lyricsState = state.lyricsState,
                    activeIndex = state.currentLineIndex,
                    progressMs = effectiveProgressMs,
                    romaji = state.romaji,
                    fontSize = state.lyricsFontSize,
                    lineSpacing = (state.lineSpacing + 14).coerceAtLeast(10), // base 7 → ~21dp gap target
                    highContrast = state.highContrast,
                    smoothScroll = state.smoothScroll,
                    hapticEnabled = state.hapticEnabled,
                    onSeekToLine = handleLineTap,
                    onLongPressShare = handleLineLongPress,
                    selectedIds = selectedIds,
                    isSelecting = isSelecting,
                )
            }

            // ── Bottom: selection bar / premium banner / transport ──
            when {
                isSelecting -> {
                    SelectionActionBar(
                        count = selectedIds.size,
                        onShare = handleShareSelected,
                        onCancel = { selectedIds = emptySet() },
                    )
                }
                // Banner hanya muncul kalau sudah TERBUKTI kontrol ditolak:
                // /me bilang non-premium, atau App Remote menolak perintah.
                // Selama masih Unknown, transport tetap tampil.
                state.capability.showsUpgradeNotice -> {
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
