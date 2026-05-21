package com.tglabs.spotivibe.ui.theme

import androidx.compose.ui.graphics.Color

// Glassmorphism Tokyo palette — deep navy base + frosted accents
// Background gradient: BackgroundDeep → BackgroundLift
val BackgroundDeep = Color(0xFF0A0E1A)
val BackgroundLift = Color(0xFF141A2A)
val SurfaceFrost = Color(0x14FFFFFF)      // rgba(255,255,255,0.08) frosted glass
val SurfaceFrostHi = Color(0x1FFFFFFF)    // rgba(255,255,255,0.12) elevated frost
val BorderHairline = Color(0x1FFFFFFF)    // 12% white

val TextPrimary = Color(0xFFE6E9F2)
val TextSecondary = Color(0xFF8D94AB)
val TextDim = Color(0x66E6E9F2)           // 40% opacity for inactive lyrics

// Default accent (overridden by album dominant color at runtime)
val AccentDefault = Color(0xFFB8A4FF)     // soft lavender, neutral starting point
val AccentSpotifyGreen = Color(0xFF1DB954)

// Active state highlights
val GlowSoft = Color(0x33B8A4FF)          // accent at 20% for glow rings
