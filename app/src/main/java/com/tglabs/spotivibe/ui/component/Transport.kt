package com.tglabs.spotivibe.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tglabs.spotivibe.ui.theme.LocalSvColors
import com.tglabs.spotivibe.ui.theme.SvIcons
import com.tglabs.spotivibe.ui.theme.SvSpace
import com.tglabs.spotivibe.ui.theme.SvType

/**
 * Editorial transport. Minimal chrome:
 * - Hairline rule above
 * - Seek bar: mono tabular time labels left/right, 2px hairline track,
 *   accent-filled progress, 2×14dp vertical thumb (no circle)
 * - Buttons row: only prev / play / next centered. Play = 52dp accent
 *   circle dengan accent-ink icon. Prev/next = monoline icons in ink2.
 *
 * Tap-to-drag seek + onSeek dipanggil saat drag release (sama dengan
 * existing pattern di NowPlayingScreen lama).
 */
@Composable
fun Transport(
    progressMs: Long,
    durationMs: Long,
    isPaused: Boolean,
    onDrag: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sv = LocalSvColors.current
    val safeDuration = durationMs.coerceAtLeast(1L)
    val ratio = (progressMs.toFloat() / safeDuration).coerceIn(0f, 1f)

    Column(modifier = modifier.fillMaxWidth()) {
        HairlineRule(modifier = Modifier.padding(bottom = SvSpace.s4))

        // ── Seek row ─────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = SvSpace.s5),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SvSpace.s3),
        ) {
            Text(
                text = formatMs(progressMs),
                style = SvType.MonoTabular.copy(fontSize = androidx.compose.ui.unit.TextUnit(11f, androidx.compose.ui.unit.TextUnitType.Sp)),
                color = sv.ink2,
                modifier = Modifier.width(34.dp),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(14.dp)
                    .pointerInput(safeDuration) {
                        detectHorizontalDragGestures(
                            onDragEnd = { /* onSeek handled by caller via onDrag(-1) convention */ },
                            onHorizontalDrag = { change, _ ->
                                val rel = (change.position.x / size.width).coerceIn(0f, 1f)
                                onDrag((rel * safeDuration).toLong())
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                // Hairline base track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(sv.rule),
                )
                // Accent-filled progress + thumb at end
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(ratio)
                            .height(2.dp)
                            .background(sv.accent)
                            .align(Alignment.CenterVertically),
                    )
                    // Thumb — vertical bar 2×14dp di posisi ratio (between filled and unfilled)
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(14.dp)
                            .background(sv.accent),
                    )
                }
            }
            Text(
                text = formatMs(durationMs),
                style = SvType.MonoTabular.copy(fontSize = androidx.compose.ui.unit.TextUnit(11f, androidx.compose.ui.unit.TextUnitType.Sp)),
                color = sv.ink3,
                modifier = Modifier.width(34.dp),
                textAlign = TextAlign.End,
            )
        }

        Spacer(modifier = Modifier.height(SvSpace.s4))

        // ── Buttons row ──────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = SvSpace.s5),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clickable(onClick = onPrevious),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = SvIcons.Prev,
                    contentDescription = "Previous",
                    tint = sv.ink2,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(SvSpace.s8))
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(sv.accent)
                    .clickable(onClick = onTogglePlayPause),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPaused) SvIcons.Play else SvIcons.Pause,
                    contentDescription = if (isPaused) "Play" else "Pause",
                    tint = sv.accentInk,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(SvSpace.s8))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clickable(onClick = onNext),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = SvIcons.Next,
                    contentDescription = "Next",
                    tint = sv.ink2,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
