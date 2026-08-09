package com.tglabs.spotivibe.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.tglabs.spotivibe.domain.LyricsState
import com.tglabs.spotivibe.domain.SyncedLine
import com.tglabs.spotivibe.domain.SyncedWord
import com.tglabs.spotivibe.ui.theme.LocalSvColors
import com.tglabs.spotivibe.ui.theme.SvIcons
import com.tglabs.spotivibe.ui.theme.SvMotion
import com.tglabs.spotivibe.ui.theme.SvSpace
import com.tglabs.spotivibe.ui.theme.SvType
import com.tglabs.spotivibe.util.Haptics

/**
 * Editorial lyrics list. Newsreader serif typography:
 * - Active line: SvType.LyricActive (Medium 30sp), color = accent
 * - Near lines (idx ±1): LyricPassive 22sp, color = ink2 + 85% alpha
 * - Far lines (idx ±2): smaller 18sp, color = ink3 + 55% alpha
 * - Distant lines: smaller still, color = ink4 + 35% alpha
 *
 * Romanization below each in Newsreader Italic Light, color = AccentDim.
 * Selection mode shows accent-ghost bg + left-gutter checkmark.
 * Per-word karaoke on active line when LRC+ words present.
 *
 * `fontSize` user override multiplies LyricActive base (30sp). 17 default
 * maintains roughly current behavior; settings slider 12-56 maps directly.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LyricsList(
    /**
     * Bukan sekadar konten. Kosong karena LRCLIB bilang lagu ini tidak
     * berlirik itu beda dengan kosong karena jaringan mati, dan user berhak
     * tahu bedanya: yang satu permanen, yang satu tinggal ditunggu.
     */
    lyricsState: LyricsState,
    /**
     * Baris aktif, sudah dihitung di PlaybackController dengan offset user
     * ikut diperhitungkan. Komponen ini SENGAJA tidak menghitungnya sendiri:
     * salinan kedua dari logika itu dulu bikin layar dan notification bisa
     * menunjuk baris berbeda, dan cuma salinan milik UI yang menerapkan offset.
     */
    activeIndex: Int,
    /**
     * Posisi lirik (sudah termasuk offset), hanya untuk highlight per-kata di
     * LRC enhanced. Bukan untuk menentukan baris aktif.
     */
    progressMs: Long,
    /** Key = [SyncedLine.id]. Bukan timeMs: timestamp bisa kembar. */
    romaji: Map<Int, String?> = emptyMap(),
    fontSize: Int = 30,
    lineSpacing: Int = 22,
    highContrast: Boolean = false,
    smoothScroll: Boolean = true,
    hapticEnabled: Boolean = true,
    onSeekToLine: ((SyncedLine) -> Unit)? = null,
    onLongPressShare: ((SyncedLine) -> Unit)? = null,
    selectedIds: Set<Int> = emptySet(),
    isSelecting: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val sv = LocalSvColors.current
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val synced = (lyricsState as? LyricsState.Ready)?.result?.synced
        val plain = (lyricsState as? LyricsState.Ready)?.result?.plain
        when {
            lyricsState is LyricsState.Loading -> StatusText("Finding lyrics…")

            !synced.isNullOrEmpty() ->
                SyncedView(
                    lines = synced,
                    activeIndex = activeIndex,
                    progressMs = progressMs,
                    romaji = romaji,
                    fontSize = fontSize,
                    lineSpacing = lineSpacing,
                    highContrast = highContrast,
                    smoothScroll = smoothScroll,
                    hapticEnabled = hapticEnabled,
                    onSeekToLine = onSeekToLine,
                    onLongPressShare = onLongPressShare,
                    selectedIds = selectedIds,
                    isSelecting = isSelecting,
                )

            !plain.isNullOrBlank() -> PlainView(plain, fontSize, sv.ink2)

            lyricsState is LyricsState.Unavailable ->
                StatusText("Couldn't load lyrics. Check your connection, then replay the song.")

            else -> StatusText("No lyrics found for this track")
        }
    }
}

@Composable
private fun StatusText(text: String) {
    val sv = LocalSvColors.current
    Text(
        text = text,
        style = SvType.BodyItalic,
        color = sv.ink3,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = SvSpace.s6),
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SyncedView(
    lines: List<SyncedLine>,
    activeIndex: Int,
    progressMs: Long,
    romaji: Map<Int, String?>,
    fontSize: Int,
    lineSpacing: Int,
    highContrast: Boolean,
    smoothScroll: Boolean,
    hapticEnabled: Boolean,
    onSeekToLine: ((SyncedLine) -> Unit)?,
    onLongPressShare: ((SyncedLine) -> Unit)?,
    selectedIds: Set<Int>,
    isSelecting: Boolean,
) {
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // Reset scroll ke atas saat KONTEN lirik berubah (ganti lagu). Tanpa ini,
    // kalau lirik lagu baru preloaded/cached, transisi null→loaded di controller
    // terjadi dalam 1 frame sehingga SyncedView tidak keluar-masuk composition
    // dan LazyListState lama (ter-scroll di bawah dari lagu sebelumnya) kebawa.
    // Hanya reset kalau belum ada active line (intro) — kalau sudah ada,
    // biarkan efek activeIndex di bawah yang handle supaya tidak double-jump.
    LaunchedEffect(lines) {
        if (activeIndex < 0) {
            listState.scrollToItem(0)
        }
    }

    // Smooth scroll uses spring. Snap mode uses scrollToItem (no anim).
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            if (smoothScroll) {
                listState.animateScrollToItem(index = activeIndex, scrollOffset = -200)
            } else {
                listState.scrollToItem(index = activeIndex, scrollOffset = -200)
            }
        }
    }

    if (hapticEnabled) {
        LaunchedEffect(activeIndex) {
            if (activeIndex >= 0) Haptics.tick(context)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = SvSpace.s3),
        verticalArrangement = Arrangement.spacedBy(lineSpacing.coerceIn(0, 40).dp),
    ) {
        item { Spacer(modifier = Modifier.height(80.dp)) }

        // key = line.id, BUKAN line.timeMs. File LRC boleh punya beberapa baris
        // dengan timestamp identik, dan key duplikat membuat Compose melempar
        // IllegalArgumentException: Key was already used.
        itemsIndexed(lines, key = { _, line -> line.id }) { idx, line ->
            LyricLineItem(
                line = line,
                romaji = romaji[line.id],
                state = lyricState(idx, activeIndex),
                progressMs = progressMs,
                fontSize = fontSize,
                highContrast = highContrast,
                isSelected = selectedIds.contains(line.id),
                isSelecting = isSelecting,
                onTap = onSeekToLine?.let { { it(line) } },
                onLongPress = onLongPressShare?.let { { it(line) } },
            )
        }

        item { Spacer(modifier = Modifier.height(120.dp)) }
    }
}

private enum class LyricLineState { Active, Near, Far, Distant }

private fun lyricState(idx: Int, active: Int): LyricLineState {
    if (active < 0) return LyricLineState.Distant
    val d = kotlin.math.abs(idx - active)
    return when {
        d == 0 -> LyricLineState.Active
        d == 1 -> LyricLineState.Near
        d == 2 -> LyricLineState.Far
        else   -> LyricLineState.Distant
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun LyricLineItem(
    line: SyncedLine,
    romaji: String?,
    state: LyricLineState,
    progressMs: Long,
    fontSize: Int,
    highContrast: Boolean,
    isSelected: Boolean,
    isSelecting: Boolean,
    onTap: (() -> Unit)?,
    onLongPress: (() -> Unit)?,
) {
    val sv = LocalSvColors.current

    // Color + opacity per state. High contrast: kurang dim, lebih opaque.
    val baseInactiveAlpha = if (highContrast) 0.85f else 1f
    val (targetColor, targetAlpha) = when (state) {
        LyricLineState.Active  -> sv.accent to 1f
        LyricLineState.Near    -> sv.ink2   to (0.85f * baseInactiveAlpha)
        LyricLineState.Far     -> sv.ink3   to (0.55f * baseInactiveAlpha)
        LyricLineState.Distant -> sv.ink4   to (0.35f * baseInactiveAlpha)
    }
    val color by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(SvMotion.DurLine, easing = SvMotion.EaseOut),
        label = "lineColor",
    )

    // Font size per state. fontSize = user override base (default 30sp =
    // LyricActive). Skew per state.
    val activeSize  = fontSize.sp
    val nearSize    = (fontSize - 8).coerceAtLeast(14).sp
    val farSize     = (fontSize - 12).coerceAtLeast(12).sp
    val distantSize = (fontSize - 14).coerceAtLeast(10).sp

    val textStyle = when (state) {
        LyricLineState.Active  -> SvType.LyricActive.copy(fontSize = activeSize, lineHeight = (activeSize.value + 6).sp)
        LyricLineState.Near    -> SvType.LyricPassive.copy(fontSize = nearSize, lineHeight = (nearSize.value + 6).sp)
        LyricLineState.Far     -> SvType.LyricDistant.copy(fontSize = farSize, lineHeight = (farSize.value + 4).sp)
        LyricLineState.Distant -> SvType.LyricDistant.copy(fontSize = distantSize, lineHeight = (distantSize.value + 4).sp)
    }

    // Selection visual — accent-ghost bg
    val selectionBg = if (isSelected) sv.accentGhost else Color.Transparent

    val baseModifier = Modifier
        .fillMaxWidth()
        .background(selectionBg)
        .padding(vertical = SvSpace.s1)

    val interactiveModifier = if (onTap != null || onLongPress != null) {
        baseModifier.combinedClickable(
            onClick = { onTap?.invoke() },
            onLongClick = onLongPress,
        )
    } else baseModifier

    Row(
        modifier = interactiveModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ── Left gutter — selection checkmark / hollow square ──
        if (isSelecting) {
            Box(
                modifier = Modifier
                    .padding(start = SvSpace.s3)
                    .size(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = SvIcons.Check,
                        contentDescription = null,
                        tint = sv.accent,
                        modifier = Modifier.size(14.dp),
                    )
                } else {
                    // Hollow square — pakai border modifier supaya minimalis
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .border(1.dp, sv.rule),
                    )
                }
            }
        }

        // ── Lyric content ──────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(
                    horizontal = if (isSelecting) SvSpace.s4 else SvSpace.s5,
                    vertical = SvSpace.s1,
                ),
            horizontalAlignment = Alignment.Start,
        ) {
            val hasWordTiming = state == LyricLineState.Active && line.words.isNotEmpty()

            if (hasWordTiming) {
                WordHighlightText(
                    words = line.words,
                    progressMs = progressMs,
                    activeColor = color,
                    pendingColor = sv.ink3.copy(alpha = 0.55f),
                    style = textStyle,
                )
            } else {
                Text(
                    text = line.text.ifBlank { "♪" },
                    style = textStyle,
                    color = color.copy(alpha = color.alpha * targetAlpha),
                    textAlign = TextAlign.Start,
                )
            }

            if (!romaji.isNullOrBlank() && state != LyricLineState.Distant) {
                val romaSize = when (state) {
                    LyricLineState.Active -> SvType.RomanizationActive
                    else -> SvType.Romanization
                }
                Text(
                    text = romaji,
                    style = romaSize,
                    color = sv.accentDim.copy(alpha = 0.85f * targetAlpha),
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/**
 * Per-word karaoke render. Words dengan `timeMs <= progressMs` di-render
 * dengan activeColor + Medium, sisanya pendingColor + Normal.
 */
@Composable
private fun WordHighlightText(
    words: List<SyncedWord>,
    progressMs: Long,
    activeColor: Color,
    pendingColor: Color,
    style: androidx.compose.ui.text.TextStyle,
) {
    val annotated = buildAnnotatedString {
        words.forEach { word ->
            val sung = word.timeMs <= progressMs
            withStyle(
                SpanStyle(
                    color = if (sung) activeColor else pendingColor,
                    fontWeight = if (sung) FontWeight.Medium else FontWeight.Normal,
                )
            ) {
                append(word.text)
            }
        }
    }
    Text(
        text = annotated,
        style = style,
        textAlign = TextAlign.Start,
    )
}

@Composable
private fun PlainView(plain: String, fontSize: Int, color: Color) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = SvSpace.s5, vertical = SvSpace.s3),
    ) {
        item {
            Text(
                text = plain,
                style = SvType.LyricPassive.copy(fontSize = fontSize.sp, lineHeight = (fontSize + 6).sp),
                color = color,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// findActiveIndex dulu ada di sini sebagai salinan kedua dari yang di
// PlaybackController. Sekarang satu-satunya implementasi ada di
// domain/LyricsSync.kt dan hasilnya masuk lewat parameter activeIndex.
