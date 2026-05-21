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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.tglabs.spotivibe.ui.theme.AccentDefault
import com.tglabs.spotivibe.ui.theme.BorderHairline
import com.tglabs.spotivibe.ui.theme.TextDim
import com.tglabs.spotivibe.ui.theme.TextPrimary
import com.tglabs.spotivibe.ui.theme.TextSecondary
import kotlinx.coroutines.flow.StateFlow

/**
 * Background hampir solid biar tetap legible di atas app apapun, tapi tetap
 * sedikit transparan untuk hint glass effect. Nilai dipilih 95% opaque dari
 * BackgroundDeep (#0A0E1A).
 */
private val OverlayBackground = Color(0xF20A0E1A)

/**
 * Root Composable overlay. Mengobservasi flows dari PlaybackController dan
 * mendelegasikan drag/tap ke caller (OverlayManager). Punya dua state UI:
 * mini bar (pill) atau expanded card.
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
 * Modifier yang membedakan drag vs tap dalam satu gesture.
 * - Kalau total displacement kurang dari [tapThresholdPx] saat finger lifted → onTap()
 * - Selama drag berlangsung → emit onDrag(dx, dy) per frame
 *
 * Kenapa custom (bukan combine clickable + draggable)? Karena clickable steal
 * pointer setelah long-press window, dan kombinasinya sering bikin drag terasa
 * "lengket" di pertama frame. Implementasi manual ini terasa lebih responsif.
 */
private fun Modifier.dragOrTap(
    tapThresholdPx: Float = 16f,
    onDrag: (dx: Int, dy: Int) -> Unit,
    onTap: () -> Unit,
    onDragEnd: () -> Unit = {},
): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            // Tunggu first down
            val down = awaitPointerEvent()
            if (down.changes.none { it.changedToDown() }) continue
            var totalDrag = 0f
            // Loop sampai semua finger lifted
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
            if (totalDrag < tapThresholdPx) {
                onTap()
            } else {
                // User drag dan release — caller bisa snap ke edge atau settle
                onDragEnd()
            }
        }
    }
}

private fun PointerInputChange.changedToDown(): Boolean =
    pressed && !previousPressed

/** Mini bar: 280dp × 48dp pill horizontal. Cover + active lyric + play/pause. */
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
    val activeLine = lyrics?.synced?.getOrNull(idx)
    val activeText = activeLine?.text
        ?: lyrics?.plain?.lineSequence()?.firstOrNull { it.isNotBlank() }
        ?: track?.title.orEmpty()
    val activeRomaji = activeLine?.let { romaji[it.timeMs] }?.takeIf { it.isNotBlank() }

    // Auto-grow height saat ada romaji — 48dp default, 64dp dengan romaji
    val barHeight = if (activeRomaji != null) 64.dp else 48.dp

    // Full-width dengan padding 16dp dari edge layar (outer wrapper Box).
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(RoundedCornerShape(24.dp))
                .background(OverlayBackground)
                .border(1.dp, BorderHairline, RoundedCornerShape(24.dp))
                .dragOrTap(onDrag = onDrag, onTap = onExpand, onDragEnd = onDragEnd)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
        CoverThumb(bitmap = bitmap, size = if (activeRomaji != null) 40 else 32)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = activeText.ifBlank { "♪ Spotivibe" },
                color = if (activeText.isBlank()) TextDim else TextPrimary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (activeRomaji != null) {
                Text(
                    text = activeRomaji,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Mini play/pause — stopPropagation supaya tap di tombol tidak expand
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitPointerEvent()
                            if (down.changes.any { it.changedToDown() }) {
                                down.changes.forEach { it.consume() }
                                // Tunggu sampai release tanpa drag meaningful
                                var moved = 0f
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    moved += ev.changes.firstOrNull()?.positionChange()?.getDistance() ?: 0f
                                    if (ev.changes.none { it.pressed }) break
                                }
                                if (moved < 16f) onPlayPause()
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (track?.isPaused != false) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                contentDescription = if (track?.isPaused != false) "Play" else "Pause",
                tint = AccentDefault,
                modifier = Modifier.size(20.dp),
            )
        }

        // Close (X) — disable overlay sepenuhnya
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .consumeTap(onTap = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close overlay",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
        } // close Row
    } // close outer Box (fillMaxWidth + padding)
}

/** Expanded card: full-width dengan padding 16dp. Cover, metadata, 3 baris lirik + romaji, controls. */
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

    // Outer wrapper: full-width dengan padding 16dp dari edge layar
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(OverlayBackground)
                .border(1.dp, BorderHairline, RoundedCornerShape(20.dp))
                // Drag area: seluruh card draggable; tombol di dalam consume event sendiri
                .dragOrTap(onDrag = onDrag, onTap = onCollapse, onDragEnd = onDragEnd)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
        // Header row: cover + title/artist + close
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CoverThumb(bitmap = bitmap, size = 60)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track?.title.orEmpty().ifBlank { "Spotivibe" },
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track?.artist.orEmpty().ifBlank { "—" },
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ControlButton(
                onClick = onClose,
                size = 32,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close overlay",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // Lyrics — 3 lines (prev dim, current accent, next dim) dengan romaji per-baris
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            LyricLineGroup(
                main = prevLine.ifBlank { " " },
                romaji = prevRomaji,
                mainColor = TextDim,
                romajiColor = TextDim,
                mainFontSize = 12,
                romajiFontSize = 10,
                bold = false,
            )
            LyricLineGroup(
                main = curLine.ifBlank { "♪" },
                romaji = curRomaji,
                mainColor = AccentDefault,
                romajiColor = AccentDefault.copy(alpha = 0.75f),
                mainFontSize = 14,
                romajiFontSize = 11,
                bold = true,
            )
            LyricLineGroup(
                main = nextLine.ifBlank { " " },
                romaji = nextRomaji,
                mainColor = TextDim,
                romajiColor = TextDim,
                mainFontSize = 12,
                romajiFontSize = 10,
                bold = false,
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Controls row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            ControlButton(onClick = onPrevious, size = 40) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "Previous",
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(AccentDefault)
                    .consumeTap(onTap = onPlayPause),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (track?.isPaused != false) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = if (track?.isPaused != false) "Play" else "Pause",
                    tint = Color.Black,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            ControlButton(onClick = onNext, size = 40) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        } // close Column (card)
    } // close outer Box (fillMaxWidth + padding)
}

@Composable
private fun CoverThumb(bitmap: Bitmap?, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 6).dp))
            .background(Color(0x14FFFFFF)),
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
            Text(text = "♪", color = AccentDefault, fontSize = (size / 2).sp)
        }
    }
}

/**
 * Render baris lirik original + romaji (kalau ada) sebagai sebuah group.
 * Aktif vs non-aktif diatur dari luar via warna + ukuran font.
 */
@Composable
private fun LyricLineGroup(
    main: String,
    romaji: String?,
    mainColor: Color,
    romajiColor: Color,
    mainFontSize: Int,
    romajiFontSize: Int,
    bold: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = main,
            color = mainColor,
            fontSize = mainFontSize.sp,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!romaji.isNullOrBlank()) {
            Text(
                text = romaji,
                color = romajiColor,
                fontSize = romajiFontSize.sp,
                fontStyle = FontStyle.Italic,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Tombol kontrol yang consume gesture sendiri supaya gak ikut nge-drag parent.
 * IconButton biasa di pakai di dalam draggable area sering ditelan drag detector.
 */
@Composable
private fun ControlButton(
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

/**
 * Modifier yang consume semua pointer event dan call onTap kalau total drag kecil.
 * Mencegah gesture bocor ke drag detector di parent.
 */
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
