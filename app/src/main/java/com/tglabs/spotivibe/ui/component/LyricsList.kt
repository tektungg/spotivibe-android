package com.tglabs.spotivibe.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import com.tglabs.spotivibe.domain.LyricsResult
import com.tglabs.spotivibe.domain.SyncedLine
import com.tglabs.spotivibe.domain.SyncedWord
import com.tglabs.spotivibe.util.Haptics

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LyricsList(
    lyrics: LyricsResult?,
    progressMs: Long,
    accent: Color,
    /** Map keyed by SyncedLine.timeMs → romaji string (atau null kalau no need) */
    romaji: Map<Long, String?> = emptyMap(),
    /** Font size lyrics dalam sp. Default 17, range 12-56. */
    fontSize: Int = 17,
    /** Extra vertical spacing antar baris (dp). Default 7. */
    lineSpacing: Int = 7,
    /** Bold weights + no dim fade — outdoor / accessibility. */
    highContrast: Boolean = false,
    /** Auto-scroll animation: true = animateScrollToItem, false = scrollToItem snap. */
    smoothScroll: Boolean = true,
    /** Trigger Vibrator tick saat active line berubah. */
    hapticEnabled: Boolean = true,
    /** Tap-to-seek: short tap baris → jump ke timestamp. */
    onSeekToLine: ((Long) -> Unit)? = null,
    /** Long-press baris → bagikan sebagai image card. */
    onLongPressShare: ((SyncedLine) -> Unit)? = null,
    /** Set timeMs baris yang sedang dipilih untuk multi-line share. */
    selectedTimes: Set<Long> = emptySet(),
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            lyrics == null -> StatusText("Mencari lirik…")
            lyrics.synced != null && lyrics.synced.isNotEmpty() ->
                SyncedLyricsView(
                    lines = lyrics.synced,
                    progressMs = progressMs,
                    accent = accent,
                    romaji = romaji,
                    fontSize = fontSize,
                    lineSpacing = lineSpacing,
                    highContrast = highContrast,
                    smoothScroll = smoothScroll,
                    hapticEnabled = hapticEnabled,
                    onSeekToLine = onSeekToLine,
                    onLongPressShare = onLongPressShare,
                    selectedTimes = selectedTimes,
                )
            !lyrics.plain.isNullOrBlank() -> PlainLyricsView(lyrics.plain, fontSize)
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
    lineSpacing: Int,
    highContrast: Boolean,
    smoothScroll: Boolean,
    hapticEnabled: Boolean,
    onSeekToLine: ((Long) -> Unit)?,
    onLongPressShare: ((SyncedLine) -> Unit)?,
    selectedTimes: Set<Long>,
) {
    val listState = rememberLazyListState()
    val activeIndex = findActiveIndex(lines, progressMs)
    val context = LocalContext.current

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            if (smoothScroll) {
                listState.animateScrollToItem(index = activeIndex, scrollOffset = -200)
            } else {
                listState.scrollToItem(index = activeIndex, scrollOffset = -200)
            }
        }
    }

    // Haptic on line change — skip initial composition (activeIndex starts at -1 or first)
    if (hapticEnabled) {
        LaunchedEffect(activeIndex) {
            if (activeIndex >= 0) Haptics.tick(context)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(lineSpacing.coerceIn(0, 20).dp),
    ) {
        item { Spacer(modifier = Modifier.height(80.dp)) }

        itemsIndexed(lines, key = { _, line -> line.timeMs }) { idx, line ->
            LyricLineItem(
                line = line,
                romaji = romaji[line.timeMs],
                isActive = idx == activeIndex,
                isSelected = selectedTimes.contains(line.timeMs),
                progressMs = progressMs,
                accent = accent,
                fontSize = fontSize,
                highContrast = highContrast,
                onTap = onSeekToLine?.let { { it(line.timeMs) } },
                onLongPress = onLongPressShare?.let { { it(line) } },
            )
        }

        item { Spacer(modifier = Modifier.height(120.dp)) }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun LyricLineItem(
    line: SyncedLine,
    romaji: String?,
    isActive: Boolean,
    isSelected: Boolean,
    progressMs: Long,
    accent: Color,
    fontSize: Int,
    highContrast: Boolean,
    onTap: (() -> Unit)?,
    onLongPress: (() -> Unit)?,
) {
    // High contrast: kurang dim, lebih opaque pada inactive lines (75% vs 40%)
    val inactiveAlpha = if (highContrast) 0.75f else 0.4f
    val dimColor = MaterialTheme.colorScheme.onBackground.copy(alpha = inactiveAlpha)
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

    val hasWordTiming = line.words.isNotEmpty()
    // High contrast: semua line pakai SemiBold; active jadi Bold
    val inactiveWeight = if (highContrast) FontWeight.Medium else FontWeight.Normal
    val activeWeight = if (highContrast) FontWeight.Bold else FontWeight.SemiBold

    // Selection visual — accent border + subtle bg tint. Animate alpha biar
    // smooth saat masuk/keluar selection mode.
    val selectionAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "selectionAlpha",
    )

    val baseModifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 6.dp, horizontal = 4.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(accent.copy(alpha = 0.10f * selectionAlpha))
        .border(
            width = (1.5f * selectionAlpha).dp,
            color = accent.copy(alpha = selectionAlpha),
            shape = RoundedCornerShape(10.dp),
        )
        .padding(vertical = 4.dp, horizontal = 8.dp)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }

    val interactiveModifier = if (onTap != null || onLongPress != null) {
        baseModifier.combinedClickable(
            onClick = { onTap?.invoke() },
            onLongClick = onLongPress,
        )
    } else baseModifier

    Column(
        modifier = interactiveModifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (isActive && hasWordTiming) {
            // Per-word karaoke: render AnnotatedString dengan highlight per-kata.
            WordHighlightText(
                words = line.words,
                progressMs = progressMs,
                activeColor = accent,
                pendingColor = dimColor,
                fontSize = fontSize,
                activeWeight = activeWeight,
                pendingWeight = inactiveWeight,
            )
        } else {
            Text(
                text = line.text.ifBlank { "♪" },
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize + 7).sp,
                ),
                fontWeight = if (isActive) activeWeight else inactiveWeight,
                color = color,
                textAlign = TextAlign.Center,
            )
        }
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

/**
 * Render baris lirik dengan highlight per-kata. Kata yang `timeMs <= progressMs`
 * di-render dengan activeColor (sudah dinyanyikan), kata sisanya pendingColor.
 */
@Composable
private fun WordHighlightText(
    words: List<SyncedWord>,
    progressMs: Long,
    activeColor: Color,
    pendingColor: Color,
    fontSize: Int,
    activeWeight: FontWeight,
    pendingWeight: FontWeight,
) {
    val annotated = buildAnnotatedString {
        words.forEach { word ->
            val sung = word.timeMs <= progressMs
            withStyle(
                SpanStyle(
                    color = if (sung) activeColor else pendingColor,
                    fontWeight = if (sung) activeWeight else pendingWeight,
                )
            ) {
                append(word.text)
            }
        }
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = fontSize.sp,
            lineHeight = (fontSize + 7).sp,
        ),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun PlainLyricsView(plain: String, fontSize: Int) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        item {
            Text(
                text = plain,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize + 7).sp,
                ),
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
