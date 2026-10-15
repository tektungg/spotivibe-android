package com.tglabs.spotivibe.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tglabs.spotivibe.domain.INTERLUDE_DOT_COUNT
import com.tglabs.spotivibe.domain.InterludeFrame
import com.tglabs.spotivibe.domain.interludeFrame
import com.tglabs.spotivibe.ui.theme.SvSpace

/**
 * Progress lirik di UI cuma diperbarui tiap 200 ms. Tanpa penghalusan, nyala
 * titik naik bertangga empat sampai lima kali per titik.
 */
private const val SMOOTHING_MS = 200

/** Titik yang belum menyala tetap terlihat samar, supaya jumlahnya terbaca. */
private const val DOT_DIM_ALPHA = 0.25f

/** Jarak antar titik sebagai kelipatan ukuran titik. */
private const val DOT_GAP_RATIO = 0.8f

/**
 * Tiga titik untuk jeda instrumental.
 *
 * [elapsedMs] null berarti titik diam: baris jeda yang sedang tidak aktif, atau
 * jedanya terlalu pendek untuk dianimasikan.
 */
@Composable
fun InterludeDots(
    elapsedMs: Long?,
    gapMs: Long?,
    color: Color,
    dotSize: Dp,
    modifier: Modifier = Modifier,
) {
    val frame = if (elapsedMs != null && gapMs != null) interludeFrame(elapsedMs, gapMs) else InterludeFrame.Idle
    val scale by animateFloatAsState(
        targetValue = frame.scale,
        animationSpec = tween(SMOOTHING_MS, easing = LinearEasing),
        label = "interludeScale",
    )
    Row(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        horizontalArrangement = Arrangement.spacedBy(dotSize * DOT_GAP_RATIO),
    ) {
        repeat(INTERLUDE_DOT_COUNT) { i -> Dot(level = frame.dotLevels[i], color = color, size = dotSize) }
    }
}

@Composable
private fun Dot(level: Float, color: Color, size: Dp) {
    val smooth by animateFloatAsState(
        targetValue = level,
        animationSpec = tween(SMOOTHING_MS, easing = LinearEasing),
        label = "interludeDot",
    )
    val alpha = DOT_DIM_ALPHA + (1f - DOT_DIM_ALPHA) * smooth
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = color.alpha * alpha)),
    )
}

/** Titik sebesar kira-kira tinggi huruf kecil dari baris di sekitarnya. */
private const val DOT_TO_FONT_RATIO = 0.4f

fun interludeDotSize(fontSp: Float): Dp = (fontSp * DOT_TO_FONT_RATIO).dp

/**
 * Ruang kosong di atas baris pertama, berisi titik-titik selama intro.
 *
 * Titik ditempel di dasar ruang itu supaya muncul tepat di atas baris pertama,
 * di tempat mata user sudah menunggu lirik.
 */
@Composable
fun IntroSpacer(
    heightDp: Float,
    introGapMs: Long?,
    /** Posisi lirik selama intro masih berjalan; null menyembunyikan titik. */
    elapsedMs: Long?,
    color: Color,
    fontSp: Float,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        if (elapsedMs != null && introGapMs != null && elapsedMs < introGapMs) {
            InterludeDots(
                elapsedMs = elapsedMs,
                gapMs = introGapMs,
                color = color,
                dotSize = interludeDotSize(fontSp),
                modifier = Modifier.padding(start = SvSpace.s5, bottom = SvSpace.s2),
            )
        }
    }
}
