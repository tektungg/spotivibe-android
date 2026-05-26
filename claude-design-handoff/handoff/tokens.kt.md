# Spotivibe — Design Tokens for Jetpack Compose

> Source of truth from the redesign canvas. Paste these into your
> Compose `ui/theme/` and reference everywhere instead of hardcoding.

---

## 1. Color (Compose Color tokens)

```kotlin
// ui/theme/Color.kt
package com.tektungg.spotivibe.ui.theme

import androidx.compose.ui.graphics.Color

// ── Neutrals · Dark mode (primary) ──────────────────────────────
val BgDark0    = Color(0xFF0F1014)   // deepest
val BgDark1    = Color(0xFF14151A)   // base
val BgDark2    = Color(0xFF1B1C22)   // raised
val BgDark3    = Color(0xFF22232A)   // card
val InkDark1   = Color(0xFFF4F1EA)   // paper-warm white
val InkDark2   = Color(0xFFC2BFB6)
val InkDark3   = Color(0xFF8A877F)
val InkDark4   = Color(0xFF5F5D56)
val RuleDark   = Color(0xFF3C3D44)
val RuleSoftDark = Color(0xFF2E2F35)

// ── Neutrals · Light mode (paper-white) ─────────────────────────
val BgLight0   = Color(0xFFF7F4EE)
val BgLight1   = Color(0xFFF1EEE8)
val BgLight2   = Color(0xFFE6E3DC)
val BgLight3   = Color(0xFFD9D6CF)
val InkLight1  = Color(0xFF1B1C22)
val InkLight2  = Color(0xFF494A52)
val InkLight3  = Color(0xFF73747C)
val InkLight4  = Color(0xFF9D9EA5)
val RuleLight  = Color(0xFFBCBDC2)
val RuleSoftLight = Color(0xFFD5D6DA)

// ── Accent presets (demo / fallback) ────────────────────────────
// Real accent comes from album-art extraction. The constants below
// are reference values; chroma + lightness are LOCKED at L≈0.74 C≈0.17
// (dark) / L≈0.55 C≈0.13 (light) so only HUE varies per track.
val AccentCoralDark  = Color(0xFFEC6A5C)   // ~oklch(0.74 0.18  30)
val AccentVioletDark = Color(0xFFA17BFF)   // ~oklch(0.70 0.18 295)
val AccentJadeDark   = Color(0xFF3CC9A1)   // ~oklch(0.76 0.16 165)

// Dim accent = same hue, lightness ~0.50 — used for romanization line.
val AccentCoralDim  = Color(0xFF92402D)
val AccentVioletDim = Color(0xFF553F8C)
val AccentJadeDim   = Color(0xFF1F6F5B)
```

**Accent extraction rule (port from your existing palette generator):**
```
Given AlbumArt → palette → pick most chromatic swatch as `hue`.
Lock L = 0.74, C = 0.17 (dark) / L = 0.55, C = 0.13 (light).
Compute Accent = OkLCh(L, C, hue).
Compute AccentDim = OkLCh(L * 0.68, C * 0.66, hue).
```

This guarantees contrast against ink-1 for ANY song.

---

## 2. Typography (Compose Type tokens)

```kotlin
// ui/theme/Type.kt
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val InstrumentSerif = FontFamily(
  Font(R.font.instrument_serif_regular,  FontWeight.Normal),
  Font(R.font.instrument_serif_italic,   FontWeight.Normal, FontStyle.Italic),
)
val Newsreader = FontFamily(
  Font(R.font.newsreader_regular, FontWeight.Normal),
  Font(R.font.newsreader_medium,  FontWeight.Medium),
  Font(R.font.newsreader_italic,  FontWeight.Light, FontStyle.Italic),
)
val JetBrainsMono = FontFamily(
  Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
  Font(R.font.jetbrains_mono_medium,  FontWeight.Medium),
  Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
)

object SvType {
  // Display + Latin lyrics
  val Display = TextStyle(fontFamily = InstrumentSerif, fontSize = 72.sp, lineHeight = 70.sp, letterSpacing = (-1.4).sp)
  val H1      = TextStyle(fontFamily = InstrumentSerif, fontSize = 44.sp, lineHeight = 44.sp, letterSpacing = (-0.6).sp)
  val H2      = TextStyle(fontFamily = InstrumentSerif, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp)
  val H3      = TextStyle(fontFamily = InstrumentSerif, fontSize = 20.sp, lineHeight = 24.sp)

  // Body lyrics (the hero) — Newsreader
  val LyricActive   = TextStyle(fontFamily = Newsreader, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.15).sp)
  val LyricPassive  = TextStyle(fontFamily = Newsreader, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Normal)
  val LyricDistant  = TextStyle(fontFamily = Newsreader, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal)

  // Romanization (italic, smaller, accent-dim color)
  val Romanization  = TextStyle(fontFamily = Newsreader, fontSize = 14.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Light)
  val RomanizationActive = TextStyle(fontFamily = Newsreader, fontSize = 16.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Light)

  // Body UI text
  val Body          = TextStyle(fontFamily = Newsreader, fontSize = 16.sp, lineHeight = 22.sp)

  // Mono micro-labels (UPPERCASE, 0.12em tracking)
  val Mono          = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp, fontWeight = FontWeight.Medium)
  val MonoUp        = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.3.sp /* ≈0.12em */)
  val MonoTabular   = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp, fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum")
}
```

User-controlled lyric size (Settings → Lyrics font size 12–56sp) multiplies
LyricActive / LyricPassive / Romanization in lock-step; default = 30sp/22sp/14sp.

---

## 3. Spacing, radius, motion

```kotlin
// ui/theme/Tokens.kt
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.unit.dp

object SvSpace {
  val s1 = 4.dp;   val s2 = 8.dp;   val s3 = 12.dp;  val s4 = 16.dp
  val s5 = 20.dp;  val s6 = 24.dp;  val s8 = 32.dp;  val s10 = 40.dp
  val s12 = 48.dp; val s16 = 64.dp
}

object SvRadius {
  val r0 = 0.dp     // rules
  val r1 = 2.dp     // DEFAULT for cards / buttons (mostly square!)
  val r2 = 6.dp     // small chips, status dots
  val r3 = 12.dp    // sheets, share cards
  // No pill / fully-round corners for primary surfaces.
}

object SvMotion {
  val EaseOut    = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)   // snappy, never bouncy
  val EaseInOut  = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

  const val DurFast  = 140    // toggles, micro-interactions
  const val DurBase  = 240    // color, transport, accent crossfades
  const val DurSlow  = 480    // lyric scroll
  const val DurTrack = 540    // track-change crossfade

  // Lyric scroll: spring(stiffness = Medium, dampingRatio = NoBouncy)
}
```

---

## 4. Iconography

All hairline / 1px stroke / monoline. Sizes: 12 / 14 / 16 / 18 / 24 / 32 dp.

Catalog: play · pause · prev · next · romanize (text “Rm”) · pip · kebab ·
close · back · fullscreen · gear · sun · moon · logout · external · check ·
share · spotify · expand · pin · drag · wifi · signal.

Replace any filled Material icon with the hairline equivalent. Source SVGs
are in the canvas under section `04 — Icons`.

---

## 5. Background treatment (Now Playing only)

```kotlin
// Pseudocode for NowPlayingScreen background
Box {
  // Layer 1 — blurred album art
  Image(albumArt, modifier = Modifier.fillMaxSize().blur(60.dp).scale(1.15f).saturate(1.3f))
  // Layer 2 — halftone dot screen (multiply blend)
  Canvas { drawHalftone(dotSize = 1.dp, spacing = 4.dp, color = Color.Black.copy(alpha = 0.55f)) }
  // Layer 3 — bottom vignette gradient
  Box(Modifier.background(Brush.verticalGradient(0f to Black35, 0.5f to Black55, 1f to Black75)))
  content()
}
```

For light mode: blur 80dp, saturate 0.5, brightness 1.6, then white veil
gradient instead of dark. See `tokens.css` `.theme-light` rules.

---

## 6. Persistent contracts (must not change)

- Dynamic accent extracted from album art every track change.
- Dark mode = primary; Light mode is an outdoor-readable variant.
- Lyric size persists; sync offset persists; haptic toggle persists.
- LRCLIB lookup unchanged; per-word LRC+ when available.
