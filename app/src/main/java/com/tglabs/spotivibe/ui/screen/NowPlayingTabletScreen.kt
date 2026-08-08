package com.tglabs.spotivibe.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tglabs.spotivibe.domain.UiState
import com.tglabs.spotivibe.ui.component.AmbientBg
import com.tglabs.spotivibe.ui.component.HairlineIconButton
import com.tglabs.spotivibe.ui.component.HairlineRule
import com.tglabs.spotivibe.ui.component.LyricsList
import com.tglabs.spotivibe.ui.component.MonoEyebrow
import com.tglabs.spotivibe.ui.component.SelectionActionBar
import com.tglabs.spotivibe.ui.component.Transport
import com.tglabs.spotivibe.ui.theme.LocalSvColors
import com.tglabs.spotivibe.ui.theme.SvIcons
import com.tglabs.spotivibe.ui.theme.SvRadius
import com.tglabs.spotivibe.ui.theme.SvSpace
import com.tglabs.spotivibe.ui.theme.SvType
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Tablet landscape NowPlaying — split layout.
 *
 *  ┌─────────────────────┬───────────────────────────┐
 *  │ NOW PLAYING · LANG  │           [icons]          │
 *  │ (eyebrow)           │ LYRICS                     │
 *  │                     │                            │
 *  │ [Album 360dp cover] │                            │
 *  │  (halftone)         │  scrolling lyrics column   │
 *  │                     │  active line very large    │
 *  │ Title (serif 42sp)  │  cascading down            │
 *  │ titleRoman italic   │                            │
 *  │                     │                            │
 *  │ ARTIST | BPM | KEY  │                            │
 *  │                     │                            │
 *  ├─────────────────────│                            │
 *  │ Transport (large)   │                            │
 *  │ progress + buttons  │                            │
 *  └─────────────────────┴───────────────────────────┘
 *
 * Split ratio 1:1.4 (kiri lebih sempit) per design canvas.
 */
@Composable
fun NowPlayingTabletScreen(
    state: UiState.Playing,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleRomanization: () -> Unit,
    onToggleOverlay: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sv = LocalSvColors.current
    val track = state.track

    val displayProgressMs by produceState(initialValue = track.extrapolatedProgressMs(), track) {
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
    // Sama dengan portrait: raw untuk slider, effective untuk highlight
    // per-kata. Baris aktif datang dari state.currentLineIndex.
    val effectiveProgressMs = com.tglabs.spotivibe.domain.lyricsProgressMs(
        rawProgressMs, state.lyricsOffsetMs,
    )

    // Selection state (sama dengan portrait)
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedTimes by remember(track.id) { mutableStateOf<Set<Long>>(emptySet()) }
    val isSelecting = selectedTimes.isNotEmpty()
    BackHandler(enabled = isSelecting) { selectedTimes = emptySet() }
    val toggleSelect: (Long) -> Unit = { ms ->
        selectedTimes = if (selectedTimes.contains(ms)) selectedTimes - ms
            else if (selectedTimes.size < 5) selectedTimes + ms else selectedTimes
    }
    val handleShareSelected: () -> Unit = handleShare@{
        val synced = state.lyrics?.synced ?: return@handleShare
        val ordered = synced
            .filter { it.timeMs in selectedTimes }
            .sortedBy { it.timeMs }
            .map { line ->
                com.tglabs.spotivibe.util.LyricShareCard.Entry(
                    text = line.text,
                    romaji = state.romaji[line.timeMs],
                )
            }
        if (ordered.isEmpty()) return@handleShare
        com.tglabs.spotivibe.util.LyricShareCard.shareLines(
            context = context,
            entries = ordered,
            title = track.title,
            artist = track.artist,
            accentArgb = (state.accentColor ?: com.tglabs.spotivibe.ui.theme.AccentDefault).toArgb(),
        )
        selectedTimes = emptySet()
    }

    Box(modifier = modifier.fillMaxSize()) {
        AmbientBg(bitmap = state.albumBitmap, highContrast = state.highContrast)

        Row(
            modifier = Modifier.fillMaxSize(),
        ) {
            // ── LEFT half — album + meta + transport ──
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .background(sv.bg0.copy(alpha = if (sv.isDark) 0.25f else 0.35f))
                    .padding(SvSpace.s10),
            ) {
                MonoEyebrow(text = "● NOW PLAYING", color = sv.accent)
                Spacer(modifier = Modifier.height(SvSpace.s5))

                // Album cover 1:1, widthIn cap supaya tidak terlalu besar di tablet
                Box(
                    modifier = Modifier
                        .widthIn(max = 360.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(SvRadius.r1))
                        .background(sv.bg2),
                ) {
                    if (state.albumBitmap != null) {
                        Image(
                            bitmap = state.albumBitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.High,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = "♪", style = SvType.H1, color = sv.accent)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(SvSpace.s8))

                // Track meta — serif title + italic subtitle
                Text(
                    text = track.title.ifBlank { "Untitled" },
                    style = SvType.H1.copy(fontSize = 42.sp, lineHeight = 44.sp, letterSpacing = (-0.6).sp),
                    color = sv.ink1,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(SvSpace.s1))
                Text(
                    text = track.artist.ifBlank { "Unknown artist" },
                    style = SvType.BodyItalic.copy(fontSize = 18.sp),
                    color = sv.ink3,
                )

                Spacer(modifier = Modifier.height(SvSpace.s5))

                // Meta strip — ARTIST | ALBUM | YEAR (hairline dividers)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SvSpace.s4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MetricCol(label = "ALBUM", value = track.album.ifBlank { "—" })
                    Box(modifier = Modifier.width(1.dp).height(28.dp).background(sv.rule))
                    MetricCol(
                        label = "DURATION",
                        value = formatDuration(track.durationMs),
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Transport / Premium banner / Selection action bar
                when {
                    isSelecting -> SelectionActionBar(
                        count = selectedTimes.size,
                        onShare = handleShareSelected,
                        onCancel = { selectedTimes = emptySet() },
                    )
                    state.capability.showsUpgradeNotice -> {
                        HairlineRule(soft = true)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = SvSpace.s4),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "SPOTIFY PREMIUM REQUIRED",
                                style = SvType.MonoUp,
                                color = sv.ink3,
                            )
                        }
                    }
                    else -> {
                        HairlineRule(soft = true)
                        Spacer(modifier = Modifier.height(SvSpace.s3))
                        Transport(
                            progressMs = rawProgressMs,
                            durationMs = track.durationMs,
                            isPaused = track.isPaused,
                            onDrag = { draggingValue = it },
                            onSeek = { ms ->
                                draggingValue = -1L
                                onSeek(ms)
                            },
                            onTogglePlayPause = onTogglePlayPause,
                            onNext = onNext,
                            onPrevious = onPrevious,
                        )
                    }
                }
            }

            // ── Vertical hairline divider ──
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(sv.rule),
            )

            // ── RIGHT half — lyrics + header icons ──
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1.4f)
                    .padding(top = SvSpace.s8, bottom = SvSpace.s4),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SvSpace.s6),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MonoEyebrow(text = "LYRICS")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(SvSpace.s2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.canRomanize) {
                            // "Rm" text toggle (match portrait header)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable(onClick = onToggleRomanization),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Rm",
                                    style = SvType.Mono.copy(fontSize = 13.sp),
                                    color = if (state.romanizationEnabled) sv.accent else sv.ink2,
                                )
                            }
                        }
                        HairlineIconButton(
                            onClick = onToggleOverlay,
                            icon = SvIcons.Pip,
                            contentDescription = "Toggle overlay",
                            tint = if (state.overlayEnabled) sv.accent else sv.ink2,
                        )
                        HairlineIconButton(
                            onClick = onOpenSettings,
                            icon = SvIcons.Kebab,
                            contentDescription = "More",
                        )
                    }
                }

                Spacer(modifier = Modifier.height(SvSpace.s3))

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    LyricsList(
                        lyrics = state.lyrics,
                        activeIndex = state.currentLineIndex,
                        progressMs = effectiveProgressMs,
                        romaji = state.romaji,
                        fontSize = (state.lyricsFontSize + 8).coerceAtMost(56),
                        lineSpacing = (state.lineSpacing + 18).coerceAtLeast(12),
                        highContrast = state.highContrast,
                        smoothScroll = state.smoothScroll,
                        hapticEnabled = state.hapticEnabled,
                        onSeekToLine = { ms ->
                            if (isSelecting) toggleSelect(ms)
                            else onSeek(
                                com.tglabs.spotivibe.domain.seekTargetMs(ms, state.lyricsOffsetMs)
                            )
                        },
                        onLongPressShare = { line ->
                            if (isSelecting) toggleSelect(line.timeMs)
                            else selectedTimes = setOf(line.timeMs)
                        },
                        selectedTimes = selectedTimes,
                        isSelecting = isSelecting,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCol(label: String, value: String) {
    val sv = LocalSvColors.current
    Column {
        MonoEyebrow(text = label)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = SvType.H3.copy(fontSize = 18.sp),
            color = sv.ink1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
