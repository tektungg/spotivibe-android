package com.tglabs.spotivibe.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.tglabs.spotivibe.domain.KaraokeGlyph
import com.tglabs.spotivibe.domain.SpringParams
import com.tglabs.spotivibe.domain.SyncedWord
import com.tglabs.spotivibe.domain.WIPE_EDGE
import com.tglabs.spotivibe.domain.glyphProgress
import com.tglabs.spotivibe.domain.karaokeGlyphs
import com.tglabs.spotivibe.domain.springFromFrequency
import com.tglabs.spotivibe.domain.wipePosition
import com.tglabs.spotivibe.domain.wordMotion

/** Jam lirik yang diteruskan ke baris: posisi terakhir dan apakah lagu sedang jalan. */
@Immutable
internal data class LyricsClock(val progressMs: Long, val isPlaying: Boolean)

/** Kata bertimer satu baris (utama atau latar) plus akhir barisnya. */
@Immutable
internal data class KaraokeWords(val words: List<SyncedWord>, val lineEndMs: Long?)

/** Warna yang sudah dinyanyikan dan yang belum. Cahaya memakai warna [sung]. */
@Immutable
internal data class KaraokeColors(val sung: Color, val pending: Color)

/**
 * Pegas gaya spr.lua (frekuensi, redaman). Kekakuannya rendah, jadi kata
 * sedikit memantul setelah membesar, bukan berhenti kaku.
 */
private val SCALE_SPRING: SpringParams = springFromFrequency(0.88f, 0.64f)
private val LIFT_SPRING: SpringParams = springFromFrequency(1.45f, 0.4f)
private val GLOW_SPRING: SpringParams = springFromFrequency(1.18f, 0.56f)

/**
 * Batas ekstrapolasi jam per frame di atas posisi terakhir. Posisi dari layar
 * diperbarui tiap 200 ms; lewat dari ini berarti pembaruannya macet (seek,
 * buffering), dan lebih jujur berhenti daripada menyapu kata yang belum
 * dinyanyikan.
 */
private const val MAX_EXTRAPOLATION_MS = 400L

/** Cahaya penuh: alpha dan blur bayangan teks. */
private const val GLOW_MAX_ALPHA = 0.35f
private const val GLOW_BASE_BLUR_DP = 4f
private const val GLOW_EXTRA_BLUR_DP = 2f

/** Di bawah ini cahaya tidak terlihat, jadi bayangannya tidak digambar sama sekali. */
private const val GLOW_VISIBLE = 0.02f

/**
 * Karaoke penuh untuk baris aktif: tiap suku kata disapu warna dari kiri,
 * membesar dan terangkat sedikit, dan bercahaya saat dinyanyikan. Suku kata
 * satu kata tidak pernah terpisah ke baris berbeda.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KaraokeText(words: KaraokeWords, clock: LyricsClock, colors: KaraokeColors, style: TextStyle) {
    val groups = remember(words) {
        karaokeGlyphs(words.words, words.lineEndMs).groupBy { it.wordIndex }.values.toList()
    }
    val now = rememberFrameClock(clock)
    FlowRow(
        horizontalArrangement = if (style.textAlign == TextAlign.End) Arrangement.End else Arrangement.Start,
    ) {
        groups.forEach { group ->
            Row { group.forEach { glyph -> GlyphText(glyph, now, colors, style) } }
        }
    }
}

/**
 * Posisi lirik per frame. Posisi dari atas hanya datang tiap 200 ms; di
 * antaranya posisi diekstrapolasi dari jam frame selama lagu jalan, supaya
 * sapuan bergerak mulus, bukan melompat lima kali sedetik.
 */
@Composable
private fun rememberFrameClock(clock: LyricsClock): State<Long> =
    produceState(clock.progressMs, clock.progressMs, clock.isPlaying) {
        value = clock.progressMs
        if (!clock.isPlaying) return@produceState
        val base = withFrameMillis { it }
        while (true) {
            val frame = withFrameMillis { it }
            value = clock.progressMs + (frame - base).coerceAtMost(MAX_EXTRAPOLATION_MS)
        }
    }

@Composable
private fun GlyphText(glyph: KaraokeGlyph, now: State<Long>, colors: KaraokeColors, style: TextStyle) {
    val p = glyphProgress(glyph, now.value)
    val target = wordMotion(p)
    val scale by animateFloatAsState(target.scale, SCALE_SPRING.toSpec(), label = "glyphScale")
    val lift by animateFloatAsState(target.liftEm, LIFT_SPRING.toSpec(), label = "glyphLift")
    val glow by animateFloatAsState(target.glow, GLOW_SPRING.toSpec(), label = "glyphGlow")
    val density = LocalDensity.current
    val fontPx = with(density) { style.fontSize.toPx() }
    Text(
        text = glyph.text,
        style = style.copy(brush = wipeBrush(p, colors), shadow = glowShadow(glow, colors.sung, density.density)),
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            translationY = lift * fontPx
            // Membesar dari garis dasar, bukan dari tengah, supaya baris tidak
            // terlihat bergoyang naik turun.
            transformOrigin = TransformOrigin(0.5f, 1f)
        },
    )
}

private fun SpringParams.toSpec() = spring<Float>(dampingRatio = dampingRatio, stiffness = stiffness)

/**
 * Gradien sapuan: warna "sudah" sampai tepi, transisi selebar [WIPE_EDGE],
 * lalu warna "belum". Di kedua ujung progress cukup warna polos.
 */
private fun wipeBrush(p: Float, colors: KaraokeColors): Brush {
    if (p <= 0f) return SolidColor(colors.pending)
    if (p >= 1f) return SolidColor(colors.sung)
    val edge = wipePosition(p)
    return Brush.horizontalGradient(
        0f to colors.sung,
        edge.coerceIn(0f, 1f) to colors.sung,
        (edge + WIPE_EDGE).coerceIn(0f, 1f) to colors.pending,
        1f to colors.pending,
    )
}

/** Blur dalam dp lalu dikali [densityScale]: radius piksel mentah terlalu kecil di layar rapat. */
private fun glowShadow(glow: Float, color: Color, densityScale: Float): Shadow {
    if (glow < GLOW_VISIBLE) return Shadow.None
    return Shadow(
        color = color.copy(alpha = color.alpha * GLOW_MAX_ALPHA * glow),
        blurRadius = (GLOW_BASE_BLUR_DP + GLOW_EXTRA_BLUR_DP * glow) * densityScale,
    )
}
