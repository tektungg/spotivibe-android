package com.tglabs.spotivibe.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Editorial Magazine design tokens — color layer.
 *
 * Source of truth: claude-design-handoff/handoff/tokens.kt.md +
 * claude-design-handoff/tokens.css.
 *
 * Two neutral ramps (dark primary, light "paper-white" for outdoor),
 * plus three accent presets used only as fallback / preview. The runtime
 * accent comes from album-art extraction with locked chroma+lightness —
 * see AccentLock.kt.
 */

// ── Neutrals · Dark (primary mode) ────────────────────────
val BgDark0       = Color(0xFF14151B)   // deepest
val BgDark1       = Color(0xFF1A1B22)   // base
val BgDark2       = Color(0xFF22232A)   // raised
val BgDark3       = Color(0xFF2A2B33)   // card
val InkDark1      = Color(0xFFF4F1EA)   // paper-warm white
val InkDark2      = Color(0xFFC2BFB6)
val InkDark3      = Color(0xFF8A877F)
val InkDark4      = Color(0xFF5F5D56)
val RuleDark      = Color(0xFF3C3D44)
val RuleSoftDark  = Color(0xFF2E2F35)

// ── Neutrals · Light (warm paper-white) ────────────────────
val BgLight0      = Color(0xFFF7F4EE)
val BgLight1      = Color(0xFFF1EEE8)
val BgLight2      = Color(0xFFE6E3DC)
val BgLight3      = Color(0xFFD9D6CF)
val InkLight1     = Color(0xFF1B1C22)
val InkLight2     = Color(0xFF494A52)
val InkLight3     = Color(0xFF73747C)
val InkLight4     = Color(0xFF9D9EA5)
val RuleLight     = Color(0xFFBCBDC2)
val RuleSoftLight = Color(0xFFD5D6DA)

// ── Accent presets (fallback / preview only) ──────────────
// Runtime accent: dynamic from album art via AccentLock.lockedAccent().
// Chroma + lightness LOCKED at L≈0.74 C≈0.17 (dark) so ANY hue stays
// readable against InkDark1.
val AccentCoralDark  = Color(0xFFEC6A5C)   // oklch(0.74 0.18  30)
val AccentVioletDark = Color(0xFFA17BFF)   // oklch(0.70 0.18 295)
val AccentJadeDark   = Color(0xFF3CC9A1)   // oklch(0.76 0.16 165)

// AccentDim = same hue, L≈0.50 — for romanization line under each lyric.
val AccentCoralDim   = Color(0xFF92402D)
val AccentVioletDim  = Color(0xFF553F8C)
val AccentJadeDim    = Color(0xFF1F6F5B)

// AccentInk — foreground used WHEN the background IS accent (e.g. play
// circle button, AccentCtaButton label). Near-black tinted with accent
// hue so contrast tetap WCAG AA tanpa feel "white-on-color" generic.
val AccentInkDark    = Color(0xFF0E0C12)
val AccentInkLight   = Color(0xFFFAF7F1)

// Default accent fallback saat album art belum loaded
val AccentDefault    = AccentCoralDark
val AccentDefaultDim = AccentCoralDim

// ── Legacy aliases (kept for non-redesigned code while refactor lands) ──
// Akan dihapus setelah semua screen rewritten ke LocalSvColors.current.
val BackgroundDeep = BgDark0
val BackgroundLift = BgDark1
val BackgroundDeepLight = BgLight0
val BackgroundLiftLight = BgLight1
val TextPrimary = InkDark1
val TextSecondary = InkDark3
val TextDim = InkDark4
val SurfaceFrost = BgDark2
val SurfaceFrostHi = BgDark3
val BorderHairline = RuleDark
val GlowSoft = AccentCoralDark.copy(alpha = 0.18f)
val AccentSpotifyGreen = Color(0xFF1DB954)
