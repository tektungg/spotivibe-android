package com.tglabs.spotivibe.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.unit.dp

/**
 * Editorial Magazine design tokens — spacing, radius, motion.
 *
 * Spacing scale = 4dp base, magazine grid.
 * Radius default = 1-2dp (square corners are the editorial signal —
 *   pill / fully-round corners are FORBIDDEN on primary surfaces).
 * Motion = cubic-bezier(0.16, 1, 0.3, 1) "snappy never bouncy".
 */

object SvSpace {
    val s1  = 4.dp
    val s2  = 8.dp
    val s3  = 12.dp
    val s4  = 16.dp
    val s5  = 20.dp
    val s6  = 24.dp
    val s8  = 32.dp
    val s10 = 40.dp
    val s12 = 48.dp
    val s16 = 64.dp
}

object SvRadius {
    val r0 = 0.dp    // rules, dividers
    val r1 = 2.dp    // DEFAULT — cards, buttons (mostly square)
    val r2 = 6.dp    // small chips, status dots
    val r3 = 12.dp   // sheets, share cards
    // NO pill / fully-round corners on primary surfaces.
}

object SvMotion {
    /** Snappy, never bouncy — cubic-bezier(0.16, 1, 0.3, 1). */
    val EaseOut   = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    /** Symmetric in/out — for paired transitions. */
    val EaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

    const val DurFast  = 140    // toggles, micro-interactions
    const val DurBase  = 240    // color crossfades, transport
    const val DurSlow  = 480    // lyric scroll
    const val DurTrack = 540    // track-change crossfade (full sequence)
    const val DurLine  = 280    // active-line transition

    // Lyric scroll uses spring(stiffness = Medium, dampingRatio = NoBouncy).
    // Snap mode skips the spring → animateScrollToItem replaced with scrollToItem.
}
