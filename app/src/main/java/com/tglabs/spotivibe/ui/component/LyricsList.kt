package com.tglabs.spotivibe.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tglabs.spotivibe.domain.LyricsResult
import com.tglabs.spotivibe.domain.SyncedLine
import com.tglabs.spotivibe.ui.theme.TextDim
import com.tglabs.spotivibe.ui.theme.TextSecondary

@Composable
fun LyricsList(
    lyrics: LyricsResult?,
    progressMs: Long,
    accent: Color,
    /** Map keyed by SyncedLine.timeMs → romaji string (atau null kalau no need) */
    romaji: Map<Long, String?> = emptyMap(),
    /** Font size lyrics dalam sp. Default 17, range 12-24. */
    fontSize: Int = 17,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            lyrics == null -> StatusText("Mencari lirik…")
            lyrics.synced != null && lyrics.synced.isNotEmpty() ->
                SyncedLyricsView(lyrics.synced, progressMs, accent, romaji, fontSize)
            !lyrics.plain.isNullOrBlank() -> PlainLyricsView(lyrics.plain)
            else -> StatusText("Lirik tidak ditemukan untuk track ini")
        }
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
}

@Composable
private fun SyncedLyricsView(
    lines: List<SyncedLine>,
    progressMs: Long,
    accent: Color,
    romaji: Map<Long, String?>,
    fontSize: Int,
) {
    val listState = rememberLazyListState()
    val activeIndex = findActiveIndex(lines, progressMs)

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            listState.animateScrollToItem(index = activeIndex, scrollOffset = -200)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item { Spacer(modifier = Modifier.height(80.dp)) }

        itemsIndexed(lines, key = { _, line -> line.timeMs }) { idx, line ->
            LyricLineItem(
                text = line.text,
                romaji = romaji[line.timeMs],
                isActive = idx == activeIndex,
                accent = accent,
                fontSize = fontSize,
            )
        }

        item { Spacer(modifier = Modifier.height(120.dp)) }
    }
}

@Composable
private fun LyricLineItem(
    text: String,
    romaji: String?,
    isActive: Boolean,
    accent: Color,
    fontSize: Int,
) {
    // Theme-aware dim color — pakai onBackground dengan alpha rendah,
    // works untuk dark (light text dim) dan light (dark text dim).
    val dimColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
    val color by animateColorAsState(
        targetValue = if (isActive) accent else dimColor,
        animationSpec = tween(durationMillis = 300),
        label = "lineColor",
    )
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.04f else 1f,
        animationSpec = tween(durationMillis = 300),
        label = "lineScale",
    )
    val romajiColor by animateColorAsState(
        targetValue = if (isActive) accent.copy(alpha = 0.85f) else dimColor.copy(alpha = 0.7f),
        animationSpec = tween(durationMillis = 300),
        label = "romajiColor",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text.ifBlank { "♪" },
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = fontSize.sp,
                lineHeight = (fontSize + 7).sp,
            ),
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            color = color,
            textAlign = TextAlign.Center,
        )
        if (!romaji.isNullOrBlank()) {
            val romajiSize = (fontSize - 4).coerceAtLeast(10)
            Text(
                text = romaji,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = romajiSize.sp,
                    lineHeight = (romajiSize + 5).sp,
                ),
                fontStyle = FontStyle.Italic,
                color = romajiColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PlainLyricsView(plain: String) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        item {
            Text(
                text = plain,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
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
