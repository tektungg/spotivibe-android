package com.tglabs.spotivibe.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tglabs.spotivibe.domain.SyncedLine

/**
 * Slim 3dp bar di antara header + lyrics list, menampilkan progress
 * WITHIN current lyric line — fill = (progressMs - line.timeMs) / (nextLine.timeMs - line.timeMs).
 *
 * Useful untuk visual cue "lagu masih di line ini sampai detik berapa", terutama
 * pas line panjang yang nyanyiannya slow.
 */
@Composable
fun LyricsScrubberBar(
    lines: List<SyncedLine>?,
    progressMs: Long,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val ratio = computeLineRatio(lines, progressMs)
    val animated by animateFloatAsState(
        targetValue = ratio,
        animationSpec = tween(durationMillis = 200),
        label = "scrubberRatio",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(accent.copy(alpha = 0.8f)),
        )
    }
}

private fun computeLineRatio(lines: List<SyncedLine>?, progressMs: Long): Float {
    if (lines.isNullOrEmpty()) return 0f
    // Find active line via binary search
    var lo = 0
    var hi = lines.size - 1
    var activeIdx = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (lines[mid].timeMs <= progressMs) {
            activeIdx = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    if (activeIdx < 0) return 0f
    val start = lines[activeIdx].timeMs
    val end = if (activeIdx + 1 < lines.size) lines[activeIdx + 1].timeMs else start + 4000L
    val span = (end - start).coerceAtLeast(1L)
    return ((progressMs - start).toFloat() / span).coerceIn(0f, 1f)
}
