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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
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
    /** Karaoke penuh (sapuan, skala, cahaya). Mati = highlight per kata sederhana. */
    val lyricEffects: Boolean = true,
    /**
     * Blur menurut jarak berlaku SAAT INI. Turunan, bukan setelan: dihitung
     * ulang di daftar dari [lyricEffects], high contrast, dan apakah user sedang
     * menggulir. Saat menggulir user sedang membaca baris lain, dan baris yang
     * kabur justru menghalanginya.
     */
    val distanceBlur: Boolean = false,
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
    /** Radius blur menurut jarak dari baris aktif, 0 = tajam. */
    val blurDp: Float = 0f,
)

/** Duet: jarak tambahan di kiri baris penyanyi kedua. */
private val DUET_INSET = 48.dp

/** Vokal latar relatif terhadap baris utamanya. */
private const val BACKGROUND_SIZE_RATIO = 0.72f
private const val BACKGROUND_ALPHA = 0.7f
private const val BACKGROUND_PENDING_ALPHA = 0.4f

/** Kata yang belum dinyanyikan di baris aktif. */
private const val PENDING_ALPHA = 0.55f

private fun alignFor(line: SyncedLine): TextAlign =
    if (line.oppositeAligned) TextAlign.End else TextAlign.Start

/**
 * Cara mewarnai satu baris. [color] beranimasi tanpa alpha keadaan, dipakai
 * karaoke; [tint] sudah termasuk redupnya keadaan baris.
 */
private data class LinePaint(val color: Color, val tint: Color, val effects: Boolean)

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
    clock: LyricsClock,
) {
    val sv = LocalSvColors.current
    val target = lineTint(model.state, prefs.highContrast, sv)
    val color by animateColorAsState(
        targetValue = target.color,
        animationSpec = tween(SvMotion.DurLine, easing = SvMotion.EaseOut),
        label = "lineColor",
    )
    val paint = LinePaint(color = color, tint = color.copy(alpha = color.alpha * target.alpha), effects = prefs.lyricEffects)
    // Perataan ikut di style, jadi semua teks baris ini (utama, latar,
    // romanisasi) sepakat tanpa parameter tambahan.
    val textStyle = lineTextStyle(model.state, prefs.fontSize).copy(textAlign = alignFor(model.line))
    val isSelected = interaction.selectedIds.contains(model.line.id)

    Row(
        modifier = Modifier.lyricLineRow(model.line, interaction, if (isSelected) sv.accentGhost else Color.Transparent)
            // Android < 12 tidak punya RenderEffect, dan blur() di sana tidak
            // berefek: head unit Android 9/10 tampil tajam seperti sebelumnya.
            .then(if (model.blurDp > 0f) Modifier.blur(model.blurDp.dp) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (interaction.isSelecting) SelectionGutter(isSelected)
        val edge = if (interaction.isSelecting) SvSpace.s4 else SvSpace.s5
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = SvSpace.s1)
                // Baris penyanyi kedua digeser ke kanan dengan jarak tambahan
                // di kiri, supaya dua suara terbaca sebagai dua sisi.
                .padding(start = if (model.line.oppositeAligned) edge + DUET_INSET else edge, end = edge),
            horizontalAlignment = if (model.line.oppositeAligned) Alignment.End else Alignment.Start,
        ) {
            LyricLineBody(model, textStyle, paint, clock)
            BackgroundVocalText(model, textStyle, paint, clock)
            RomanizationText(model, textStyle, paint.tint)
        }
    }
}

private fun Modifier.lyricLineRow(line: SyncedLine, interaction: LineInteraction, background: Color): Modifier {
    val base = this
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

/** Isi baris: titik jeda, karaoke per kata (baris aktif), atau teks biasa. */
@Composable
private fun LyricLineBody(model: LyricLineModel, textStyle: TextStyle, paint: LinePaint, clock: LyricsClock) {
    val sv = LocalSvColors.current
    val line = model.line
    when {
        // Penanda jeda: titik-titik, bukan "♪" yang diam. Hanya baris aktif
        // yang beranimasi; sisanya titik redup.
        line.isInterlude -> InterludeDots(
            elapsedMs = if (model.state == LyricLineState.Active) clock.progressMs - line.timeMs else null,
            gapMs = model.interludeGapMs,
            color = paint.tint,
            dotSize = interludeDotSize(textStyle.fontSize.value),
            modifier = Modifier.padding(vertical = SvSpace.s2),
        )

        model.state == LyricLineState.Active && line.words.isNotEmpty() -> ActiveWords(
            words = KaraokeWords(line.words, line.endMs),
            clock = clock,
            colors = KaraokeColors(sung = paint.color, pending = sv.ink3.copy(alpha = PENDING_ALPHA)),
            style = textStyle,
            effects = paint.effects,
        )

        else -> Text(text = line.text, style = textStyle, color = paint.tint)
    }
}

/**
 * Vokal latar di bawah baris utama: lebih kecil, miring, dan lebih redup,
 * karena itu suara di belakang, bukan yang dinyanyikan bersama. Ikut karaoke
 * per kata saat barisnya aktif.
 */
@Composable
private fun BackgroundVocalText(model: LyricLineModel, textStyle: TextStyle, paint: LinePaint, clock: LyricsClock) {
    val bg = model.line.background ?: return
    if (model.state == LyricLineState.Distant) return
    val sv = LocalSvColors.current
    val size = textStyle.fontSize.value * BACKGROUND_SIZE_RATIO
    val style = textStyle.copy(
        fontSize = size.sp,
        lineHeight = (size + 4f).sp,
        fontStyle = FontStyle.Italic,
    )
    val modifier = Modifier.padding(top = 2.dp)
    if (model.state == LyricLineState.Active && bg.words.isNotEmpty()) {
        Box(modifier) {
            ActiveWords(
                words = KaraokeWords(bg.words, model.line.endMs),
                clock = clock,
                colors = KaraokeColors(
                    sung = paint.color.copy(alpha = paint.color.alpha * BACKGROUND_ALPHA),
                    pending = sv.ink3.copy(alpha = BACKGROUND_PENDING_ALPHA),
                ),
                style = style,
                effects = paint.effects,
            )
        }
    } else {
        Text(
            text = bg.text,
            style = style,
            color = paint.tint.copy(alpha = paint.tint.alpha * BACKGROUND_ALPHA),
            modifier = modifier,
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
            textAlign = textStyle.textAlign,
        ),
        // Warna PERSIS sama dengan baris liriknya, termasuk alpha. Sebelumnya
        // accentDim dengan alpha 0,85, jadi romanisasi selalu lebih redup dari
        // liriknya walau baris itu aktif.
        color = tint,
        modifier = Modifier.padding(top = 2.dp),
    )
}

/** Kata bertimer baris aktif: karaoke penuh, atau highlight sederhana kalau efek dimatikan. */
@Composable
private fun ActiveWords(
    words: KaraokeWords,
    clock: LyricsClock,
    colors: KaraokeColors,
    style: TextStyle,
    effects: Boolean,
) {
    if (effects) {
        KaraokeText(words, clock, colors, style)
    } else {
        WordHighlightText(words.words, clock.progressMs, colors.sung, colors.pending, style)
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
    Text(text = annotated, style = style)
}
