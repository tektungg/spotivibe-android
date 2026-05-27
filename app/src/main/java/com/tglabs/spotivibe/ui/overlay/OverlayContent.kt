package com.tglabs.spotivibe.ui.overlay

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tglabs.spotivibe.domain.LyricsResult
import com.tglabs.spotivibe.domain.NowPlaying
import com.tglabs.spotivibe.ui.theme.LocalSvColors
import com.tglabs.spotivibe.ui.theme.SvIcons
import com.tglabs.spotivibe.ui.theme.SvRadius
import com.tglabs.spotivibe.ui.theme.SvSpace
import com.tglabs.spotivibe.ui.theme.SvType
import kotlinx.coroutines.flow.StateFlow

/**
 * Editorial overlay — hairline 1px border, square corners (r2 = 6dp).
 * Drag handle row dengan mono "SPOTIVIBE · NOW" eyebrow.
 * Two states: mini bar (collapsed) atau expanded card.
 */
@Composable
fun OverlayContent(
    trackFlow: StateFlow<NowPlaying?>,
    bitmapFlow: StateFlow<Bitmap?>,
    lyricsFlow: StateFlow<LyricsResult?>,
    currentLineIndexFlow: StateFlow<Int>,
    romajiFlow: StateFlow<Map<Long, String?>>,
    onDrag: (dx: Int, dy: Int) -> Unit,
    onDragEnd: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
) {
    val track by trackFlow.collectAsState()
    val bitmap by bitmapFlow.collectAsState()
    val lyrics by lyricsFlow.collectAsState()
    val idx by currentLineIndexFlow.collectAsState()
    val romaji by romajiFlow.collectAsState()

    var expanded by remember { mutableStateOf(false) }

    if (expanded) {
        ExpandedCard(
            track = track,
            bitmap = bitmap,
            lyrics = lyrics,
            idx = idx,
            romaji = romaji,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
            onCollapse = { expanded = false },
            onClose = onClose,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onPrevious = onPrevious,
        )
    } else {
        MiniBar(
            track = track,
            bitmap = bitmap,
            lyrics = lyrics,
            idx = idx,
            romaji = romaji,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
            onExpand = { expanded = true },
            onClose = onClose,
            onPlayPause = onPlayPause,
        )
    }
}

/**
 * Drag-or-tap gesture differentiator (preserved dari versi sebelumnya).
 * Total drag < threshold = tap; else = drag.
 */
private fun Modifier.dragOrTap(
    tapThresholdPx: Float = 16f,
    onDrag: (dx: Int, dy: Int) -> Unit,
    onTap: () -> Unit,
    onDragEnd: () -> Unit = {},
): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val down = awaitPointerEvent()
            if (down.changes.none { it.changedToDown() }) continue
            var totalDrag = 0f
            while (true) {
                val event = awaitPointerEvent()
                val change: PointerInputChange = event.changes.firstOrNull() ?: break
                val delta = change.positionChange()
                if (delta != Offset.Zero) {
                    totalDrag += delta.getDistance()
                    onDrag(delta.x.toInt(), delta.y.toInt())
                    change.consume()
                }
                if (event.changes.none { it.pressed }) break
            }
            if (totalDrag < tapThresholdPx) onTap() else onDragEnd()
        }
    }
}

private fun PointerInputChange.changedToDown(): Boolean = pressed && !previousPressed

/** Mini bar — full-width, ~52dp tall (64dp if romaji), hairline border. */
@Composable
private fun MiniBar(
    track: NowPlaying?,
    bitmap: Bitmap?,
    lyrics: LyricsResult?,
    idx: Int,
    romaji: Map<Long, String?>,
    onDrag: (Int, Int) -> Unit,
    onDragEnd: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
) {
    val sv = LocalSvColors.current
    val activeLine = lyrics?.synced?.getOrNull(idx)
    val activeText = activeLine?.text
        ?: lyrics?.plain?.lineSequence()?.firstOrNull { it.isNotBlank() }
        ?: track?.title.orEmpty()
    val activeRomaji = activeLine?.let { romaji[it.timeMs] }?.takeIf { it.isNotBlank() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SvSpace.s4),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(SvRadius.r2))
                .background(sv.bg0.copy(alpha = 0.95f))
                .border(1.dp, sv.rule, RoundedCornerShape(SvRadius.r2))
                .dragOrTap(onDrag = onDrag, onTap = onExpand, onDragEnd = onDragEnd)
                .padding(horizontal = SvSpace.s3, vertical = SvSpace.s2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SvSpace.s3),
        ) {
            CoverThumb(bitmap = bitmap, size = if (activeRomaji != null) 40 else 32)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activeText.ifBlank { "♪ Spotivibe" },
                    style = SvType.LyricPassive.copy(
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = if (activeText.isBlank()) sv.ink4 else sv.ink1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (activeRomaji != null) {
                    Text(
                        text = activeRomaji,
                        style = SvType.Romanization.copy(fontSize = 11.sp),
                        color = sv.accentDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Mini play/pause — accent square (consume tap)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(sv.accent.copy(alpha = 0.15f))
                    .consumeTap(onTap = onPlayPause),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (track?.isPaused != false) SvIcons.Play else SvIcons.Pause,
                    contentDescription = if (track?.isPaused != false) "Play" else "Pause",
                    tint = sv.accent,
                    modifier = Modifier.size(16.dp),
                )
            }

            // Close
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .consumeTap(onTap = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = SvIcons.Close,
                    contentDescription = "Close overlay",
                    tint = sv.ink3,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** Expanded card — magazine-style with mono eyebrow, drag handle, transport. */
@Composable
private fun ExpandedCard(
    track: NowPlaying?,
    bitmap: Bitmap?,
    lyrics: LyricsResult?,
    idx: Int,
    romaji: Map<Long, String?>,
    onDrag: (Int, Int) -> Unit,
    onDragEnd: () -> Unit,
    onCollapse: () -> Unit,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val sv = LocalSvColors.current
    val synced = lyrics?.synced.orEmpty()
    val prevEntry = synced.getOrNull(idx - 1)
    val curEntry = synced.getOrNull(idx)
    val nextEntry = synced.getOrNull(idx + 1)

    val prevLine = prevEntry?.text.orEmpty()
    val curLine = curEntry?.text
        ?: lyrics?.plain?.lineSequence()?.firstOrNull { it.isNotBlank() }.orEmpty()
    val nextLine = nextEntry?.text.orEmpty()

    val prevRomaji = prevEntry?.let { romaji[it.timeMs] }?.takeIf { it.isNotBlank() }
    val curRomaji = curEntry?.let { romaji[it.timeMs] }?.takeIf { it.isNotBlank() }
    val nextRomaji = nextEntry?.let { romaji[it.timeMs] }?.takeIf { it.isNotBlank() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SvSpace.s4),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(SvRadius.r2))
                .background(sv.bg0.copy(alpha = 0.96f))
                .border(1.dp, sv.rule, RoundedCornerShape(SvRadius.r2))
                .dragOrTap(onDrag = onDrag, onTap = onCollapse, onDragEnd = onDragEnd),
        ) {
            // ── Drag handle row: dot dots + mono eyebrow + close ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SvSpace.s3, vertical = SvSpace.s2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Drag visual indicator (3 dots horizontal)
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .clip(CircleShape)
                                .background(sv.ink4),
                        )
                    }
                }
                Text(
                    text = "SPOTIVIBE · NOW",
                    style = SvType.MonoUp.copy(fontSize = 10.sp),
                    color = sv.ink3,
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .consumeTap(onTap = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = SvIcons.Close,
                        contentDescription = "Close overlay",
                        tint = sv.ink3,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            // Hairline rule under handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(sv.ruleSoft),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SvSpace.s3),
                verticalArrangement = Arrangement.spacedBy(SvSpace.s2),
            ) {
                // ── Header row: cover + title/artist ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SvSpace.s3),
                ) {
                    CoverThumb(bitmap = bitmap, size = 52)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track?.title.orEmpty().ifBlank { "Spotivibe" },
                            style = SvType.H3.copy(fontSize = 15.sp, lineHeight = 18.sp),
                            color = sv.ink1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = track?.artist.orEmpty().ifBlank { "—" },
                            style = SvType.BodyItalic.copy(fontSize = 12.sp),
                            color = sv.ink3,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // ── Lyrics — 3 lines magazine style ──
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(SvSpace.s1),
                ) {
                    OverlayLyricLine(
                        text = prevLine.ifBlank { " " },
                        romaji = prevRomaji,
                        state = OverlayLineState.Inactive,
                    )
                    OverlayLyricLine(
                        text = curLine.ifBlank { "♪" },
                        romaji = curRomaji,
                        state = OverlayLineState.Active,
                    )
                    OverlayLyricLine(
                        text = nextLine.ifBlank { " " },
                        romaji = nextRomaji,
                        state = OverlayLineState.Inactive,
                    )
                }

                Spacer(modifier = Modifier.height(SvSpace.s1))

                // ── Transport ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    OverlayControlButton(onClick = onPrevious, size = 36) {
                        Icon(
                            imageVector = SvIcons.Prev,
                            contentDescription = "Previous",
                            tint = sv.ink2,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(SvSpace.s4))
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(sv.accent)
                            .consumeTap(onTap = onPlayPause),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (track?.isPaused != false) SvIcons.Play else SvIcons.Pause,
                            contentDescription = if (track?.isPaused != false) "Play" else "Pause",
                            tint = sv.accentInk,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(SvSpace.s4))
                    OverlayControlButton(onClick = onNext, size = 36) {
                        Icon(
                            imageVector = SvIcons.Next,
                            contentDescription = "Next",
                            tint = sv.ink2,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverThumb(bitmap: Bitmap?, size: Int) {
    val sv = LocalSvColors.current
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(SvRadius.r1))
            .background(sv.bg2),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = "♪",
                style = SvType.H3.copy(fontSize = (size / 2).sp),
                color = sv.accent,
            )
        }
    }
}

private enum class OverlayLineState { Active, Inactive }

@Composable
private fun OverlayLyricLine(
    text: String,
    romaji: String?,
    state: OverlayLineState,
) {
    val sv = LocalSvColors.current
    val mainColor = if (state == OverlayLineState.Active) sv.accent else sv.ink4
    val mainSize = if (state == OverlayLineState.Active) 15 else 13
    val romaSize = if (state == OverlayLineState.Active) 12 else 10

    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = text,
            style = if (state == OverlayLineState.Active) {
                SvType.LyricActive.copy(fontSize = mainSize.sp, lineHeight = (mainSize + 4).sp)
            } else {
                SvType.LyricPassive.copy(fontSize = mainSize.sp, lineHeight = (mainSize + 4).sp)
            },
            color = mainColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!romaji.isNullOrBlank()) {
            Text(
                text = romaji,
                style = SvType.Romanization.copy(fontSize = romaSize.sp, fontStyle = FontStyle.Italic),
                color = if (state == OverlayLineState.Active) sv.accentDim else sv.ink4,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun OverlayControlButton(
    onClick: () -> Unit,
    size: Int,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .consumeTap(onTap = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private fun Modifier.consumeTap(onTap: () -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val down = awaitPointerEvent()
            if (down.changes.any { it.changedToDown() }) {
                down.changes.forEach { it.consume() }
                var moved = 0f
                while (true) {
                    val ev = awaitPointerEvent()
                    ev.changes.forEach { c ->
                        moved += c.positionChange().getDistance()
                        c.consume()
                    }
                    if (ev.changes.none { it.pressed }) break
                }
                if (moved < 16f) onTap()
            }
        }
    }
}
