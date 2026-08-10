package com.tglabs.spotivibe.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
    onSearchLyrics: () -> Unit = {},
    hasRememberedOverride: Boolean = false,
    onForgetOverride: () -> Unit = {},
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
    var selectedIds by remember(track.id) { mutableStateOf<Set<Int>>(emptySet()) }
    val isSelecting = selectedIds.isNotEmpty()
    BackHandler(enabled = isSelecting) { selectedIds = emptySet() }
    val toggleSelect: (Int) -> Unit = { id ->
        selectedIds = if (selectedIds.contains(id)) selectedIds - id
            else if (selectedIds.size < 5) selectedIds + id else selectedIds
    }
    val handleShareSelected: () -> Unit = handleShare@{
        val synced = state.lyrics?.synced ?: return@handleShare
        val ordered = synced
            .filter { it.id in selectedIds }
            .sortedBy { it.id }
            .map { line ->
                com.tglabs.spotivibe.util.LyricShareCard.Entry(
                    text = line.text,
                    romaji = state.romaji[line.id],
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
        selectedIds = emptySet()
    }

    Box(modifier = modifier.fillMaxSize()) {
        AmbientBg(bitmap = state.albumBitmap, highContrast = state.highContrast)

        Row(
            modifier = Modifier.fillMaxSize(),
        ) {
            // ── LEFT half — album + meta + transport ──
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .background(sv.bg0.copy(alpha = if (sv.isDark) 0.25f else 0.35f)),
            ) {
                // Album HARUS dibatasi tinggi, bukan cuma lebar. Versi lama cuma
                // memakai widthIn + aspectRatio, jadi di HP landscape album
                // memakan 267 dari 295 dp yang tersedia dan mendorong judul,
                // artis, meta, serta transport keluar layar sepenuhnya.
                val m = landscapePaneMetrics(
                    paneWidthDp = maxWidth.value,
                    paneHeightDp = maxHeight.value,
                )
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(m.padDp.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(m.albumDp.dp)
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

                // Ruang lentur di ATAS blok judul, bukan di bawahnya. Dengan
                // weight di bawah artis, judul menempel ke album dan seluruh
                // sisa ruang menumpuk jadi satu lubang tepat di atas divider.
                Spacer(modifier = Modifier.height(m.gapDp.dp))
                Spacer(modifier = Modifier.weight(1f))

                // Track meta — serif title + italic subtitle
                Text(
                    text = track.title.ifBlank { "Untitled" },
                    style = SvType.H1.copy(
                        fontSize = m.titleSp.sp,
                        lineHeight = (m.titleSp * 1.1f).sp,
                        letterSpacing = (-0.6).sp,
                    ),
                    color = sv.ink1,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(SvSpace.s1))
                Text(
                    text = track.artist.ifBlank { "Unknown artist" },
                    style = SvType.BodyItalic.copy(fontSize = m.artistSp.sp),
                    color = sv.ink3,
                )

                Spacer(modifier = Modifier.height(TITLE_TRANSPORT_GAP_DP.dp))

                // Transport / Premium banner / Selection action bar
                when {
                    isSelecting -> SelectionActionBar(
                        count = selectedIds.size,
                        onShare = handleShareSelected,
                        onCancel = { selectedIds = emptySet() },
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
                        // TANPA HairlineRule di sini. Transport sudah menggambar
                        // garisnya sendiri di baris pertama, jadi menambahkan
                        // satu lagi menghasilkan dua garis bertumpuk di atas
                        // seeker.
                        Transport(
                            compact = m.compactTransport,
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

            }

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
                        // Komponen yang sama dengan portrait. Saat fitur
                        // pencarian pertama dipasang, layar ini tertinggal dan
                        // pintunya tidak ada sama sekali karena menunya ditulis
                        // langsung di NPHeader yang tidak dipakai di sini.
                        com.tglabs.spotivibe.ui.component.HeaderOverflowMenu(
                            onSearchLyrics = onSearchLyrics,
                            onOpenSettings = onOpenSettings,
                            showForgetOverride = hasRememberedOverride,
                            onForgetOverride = onForgetOverride,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(SvSpace.s3))

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    LyricsList(
                        lyricsState = state.lyricsState,
                        activeIndex = state.currentLineIndex,
                        progressMs = effectiveProgressMs,
                        romaji = state.romaji,
                        fontSize = (state.lyricsFontSize + 8).coerceAtMost(56),
                        lineSpacing = (state.lineSpacing + 18).coerceAtLeast(12),
                        highContrast = state.highContrast,
                        smoothScroll = state.smoothScroll,
                        hapticEnabled = state.hapticEnabled,
                        onSeekToLine = { line ->
                            if (isSelecting) toggleSelect(line.id)
                            else onSeek(
                                com.tglabs.spotivibe.domain.seekTargetMs(
                                    line.timeMs, state.lyricsOffsetMs,
                                )
                            )
                        },
                        onLongPressShare = { line ->
                            if (isSelecting) toggleSelect(line.id)
                            else selectedIds = setOf(line.id)
                        },
                        selectedIds = selectedIds,
                        isSelecting = isSelecting,
                    )
                }
            }
        }
    }
}
