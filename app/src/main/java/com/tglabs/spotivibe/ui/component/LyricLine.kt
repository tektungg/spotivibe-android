package com.tglabs.spotivibe.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tglabs.spotivibe.domain.SyncedLine
import com.tglabs.spotivibe.domain.SyncedWord
import com.tglabs.spotivibe.domain.isInterlude
import com.tglabs.spotivibe.ui.theme.LocalSvColors
import com.tglabs.spotivibe.ui.theme.SvColors
import com.tglabs.spotivibe.ui.theme.SvIcons
import com.tglabs.spotivibe.ui.theme.SvMotion
import com.tglabs.spotivibe.ui.theme.SvSpace
import com.tglabs.spotivibe.ui.theme.SvType

/** Jarak sebuah baris dari baris aktif, menentukan ukuran dan redupnya. */
internal enum class LyricLineState { Active, Near, Far, Distant }

internal fun lyricState(idx: Int, active: Int): LyricLineState {
    if (active < 0) return LyricLineState.Distant
    val d = kotlin.math.abs(idx - active)
    return when {
        d == 0 -> LyricLineState.Active
        d == 1 -> LyricLineState.Near
        d == 2 -> LyricLineState.Far
        else -> LyricLineState.Distant
    }
}

/** Setelan tampilan dari Settings, sama untuk semua baris. */
@Immutable
internal data class LyricsDisplayPrefs(
    val fontSize: Int,
    val lineSpacing: Int,
    val highContrast: Boolean,
    val smoothScroll: Boolean,
    val hapticEnabled: Boolean,
)

/** Interaksi baris: tap untuk seek, tekan lama untuk memilih. */
@Immutable
internal data class LineInteraction(
    val onSeekToLine: ((SyncedLine) -> Unit)?,
    val onLongPressShare: ((SyncedLine) -> Unit)?,
    val selectedIds: Set<Int>,
    val isSelecting: Boolean,
)

/** Semua yang khas untuk SATU baris. */
@Immutable
internal data class LyricLineModel(
    val line: SyncedLine,
    val romaji: String?,
    val state: LyricLineState,
    /** Panjang jeda kalau baris ini penanda jeda yang layak dianimasikan. */
    val interludeGapMs: Long?,
)

/** Warna target dan alpha tambahan untuk satu keadaan baris. */
private data class LineTint(val color: Color, val alpha: Float)

/** High contrast: kurang redup, lebih opaque. */
private fun lineTint(state: LyricLineState, highContrast: Boolean, sv: SvColors): LineTint {
    val inactive = if (highContrast) 0.85f else 1f
    return when (state) {
        LyricLineState.Active -> LineTint(sv.accent, 1f)
        LyricLineState.Near -> LineTint(sv.ink2, 0.85f * inactive)
        LyricLineState.Far -> LineTint(sv.ink3, 0.55f * inactive)
        LyricLineState.Distant -> LineTint(sv.ink4, 0.35f * inactive)
    }
}

/**
 * Ukuran font per keadaan. [fontSize] adalah setelan user untuk baris aktif;
 * baris lain mengecil bertingkat dari situ.
 */
private fun lineTextStyle(state: LyricLineState, fontSize: Int): TextStyle {
    val active = fontSize.sp
    val near = (fontSize - 8).coerceAtLeast(14).sp
    val far = (fontSize - 12).coerceAtLeast(12).sp
    val distant = (fontSize - 14).coerceAtLeast(10).sp
    return when (state) {
        LyricLineState.Active -> SvType.LyricActive.copy(fontSize = active, lineHeight = (active.value + 6).sp)
        LyricLineState.Near -> SvType.LyricPassive.copy(fontSize = near, lineHeight = (near.value + 6).sp)
        LyricLineState.Far -> SvType.LyricDistant.copy(fontSize = far, lineHeight = (far.value + 4).sp)
        LyricLineState.Distant -> SvType.LyricDistant.copy(fontSize = distant, lineHeight = (distant.value + 4).sp)
    }
}

/**
 * Satu baris lirik: gutter seleksi, teks (atau titik jeda, atau karaoke per
 * kata), dan romanisasi di bawahnya.
 */
@Composable
internal fun LyricLineItem(
    model: LyricLineModel,
    prefs: LyricsDisplayPrefs,
    interaction: LineInteraction,
    progressMs: Long,
) {
    val sv = LocalSvColors.current
    val target = lineTint(model.state, prefs.highContrast, sv)
    val color by animateColorAsState(
        targetValue = target.color,
        animationSpec = tween(SvMotion.DurLine, easing = SvMotion.EaseOut),
        label = "lineColor",
    )
    val tint = color.copy(alpha = color.alpha * target.alpha)
    val textStyle = lineTextStyle(model.state, prefs.fontSize)
    val isSelected = interaction.selectedIds.contains(model.line.id)

    Row(
        modifier = lineRowModifier(model.line, interaction, if (isSelected) sv.accentGhost else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (interaction.isSelecting) SelectionGutter(isSelected)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(
                    horizontal = if (interaction.isSelecting) SvSpace.s4 else SvSpace.s5,
                    vertical = SvSpace.s1,
                ),
            horizontalAlignment = Alignment.Start,
        ) {
            LyricLineBody(model, textStyle, color, tint, progressMs)
            RomanizationText(model, textStyle, tint)
        }
    }
}

private fun lineRowModifier(line: SyncedLine, interaction: LineInteraction, background: Color): Modifier {
    val base = Modifier
        .fillMaxWidth()
        .background(background)
        .padding(vertical = SvSpace.s1)
    val onTap = interaction.onSeekToLine
    val onLongPress = interaction.onLongPressShare
    if (onTap == null && onLongPress == null) return base
    return base.combinedClickable(
        onClick = { onTap?.invoke(line) },
        onLongClick = onLongPress?.let { { it(line) } },
    )
}

/** Gutter kiri saat mode seleksi: centang untuk yang terpilih, kotak kosong untuk sisanya. */
@Composable
private fun SelectionGutter(isSelected: Boolean) {
    val sv = LocalSvColors.current
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

/**
 * Isi baris. [color] adalah warna beranimasi tanpa alpha keadaan, dipakai
 * karaoke per kata; [tint] sudah termasuk redupnya keadaan baris.
 */
@Composable
private fun LyricLineBody(
    model: LyricLineModel,
    textStyle: TextStyle,
    color: Color,
    tint: Color,
    progressMs: Long,
) {
    val sv = LocalSvColors.current
    val line = model.line
    when {
        // Penanda jeda: titik-titik, bukan "♪" yang diam. Hanya baris aktif
        // yang beranimasi; sisanya titik redup.
        line.isInterlude -> InterludeDots(
            elapsedMs = if (model.state == LyricLineState.Active) progressMs - line.timeMs else null,
            gapMs = model.interludeGapMs,
            color = tint,
            dotSize = interludeDotSize(textStyle.fontSize.value),
            modifier = Modifier.padding(vertical = SvSpace.s2),
        )

        model.state == LyricLineState.Active && line.words.isNotEmpty() -> WordHighlightText(
            words = line.words,
            progressMs = progressMs,
            activeColor = color,
            pendingColor = sv.ink3.copy(alpha = 0.55f),
            style = textStyle,
        )

        else -> Text(
            text = line.text,
            style = textStyle,
            color = tint,
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
private fun RomanizationText(model: LyricLineModel, textStyle: TextStyle, tint: Color) {
    val romaji = model.romaji
    if (romaji.isNullOrBlank() || model.state == LyricLineState.Distant) return
    // Ukuran mengikuti baris yang diromanisasi, bukan ukuran tetap. Gaya lama
    // memakai SvType.Romanization yang ukurannya kaku, jadi menaikkan ukuran
    // font lirik di Settings tidak berpengaruh sama sekali dan romanisasinya
    // makin tenggelam.
    val romaSp = romanizationSizeSp(textStyle.fontSize.value)
    Text(
        text = romaji,
        style = SvType.Romanization.copy(
            fontSize = romaSp.sp,
            lineHeight = (romaSp + 4f).sp,
        ),
        // Warna PERSIS sama dengan baris liriknya, termasuk alpha. Sebelumnya
        // accentDim dengan alpha 0,85, jadi romanisasi selalu lebih redup dari
        // liriknya walau baris itu aktif.
        color = tint,
        textAlign = TextAlign.Start,
        modifier = Modifier.padding(top = 2.dp),
    )
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
    style: TextStyle,
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
