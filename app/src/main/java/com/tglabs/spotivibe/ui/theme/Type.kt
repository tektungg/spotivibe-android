package com.tglabs.spotivibe.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.tglabs.spotivibe.R

/**
 * Editorial Magazine design tokens — type layer.
 *
 * Three faces:
 * - InstrumentSerif — display + big editorial headlines
 * - Newsreader     — body lyrics (the hero) + italic light romanization
 * - JetBrainsMono  — uppercase eyebrows + tabular metadata
 *
 * All three loaded via Google Fonts downloadable provider — no TTF
 * bundled in APK. Provider certs in res/values/font_certs.xml.
 *
 * Fallback: kalau provider request gagal (offline, expired cert), Font
 * loaders auto-fallback ke FontFamily.Serif / Monospace system fonts.
 */

private val googleFontsProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private fun gfont(name: String) = GoogleFont(name)

val InstrumentSerif = FontFamily(
    Font(gfont("Instrument Serif"), googleFontsProvider, FontWeight.Normal),
    Font(gfont("Instrument Serif"), googleFontsProvider, FontWeight.Normal, FontStyle.Italic),
)

val Newsreader = FontFamily(
    Font(gfont("Newsreader"), googleFontsProvider, FontWeight.Light),
    Font(gfont("Newsreader"), googleFontsProvider, FontWeight.Light, FontStyle.Italic),
    Font(gfont("Newsreader"), googleFontsProvider, FontWeight.Normal),
    Font(gfont("Newsreader"), googleFontsProvider, FontWeight.Medium),
    Font(gfont("Newsreader"), googleFontsProvider, FontWeight.SemiBold),
)

val JetBrainsMono = FontFamily(
    Font(gfont("JetBrains Mono"), googleFontsProvider, FontWeight.Normal),
    Font(gfont("JetBrains Mono"), googleFontsProvider, FontWeight.Medium),
    Font(gfont("JetBrains Mono"), googleFontsProvider, FontWeight.SemiBold),
)

/**
 * Canonical Spotivibe text styles. Reference via SvType.* di seluruh UI.
 * Material3 Typography (di bawah) di-derive dari sini hanya untuk Material
 * primitives yang masih kepakai (mostly Material Switch/Slider — yang akan
 * diganti dengan custom primitives di Phase 5, jadi Material Typography
 * makin lama makin tidak terpakai).
 */
object SvType {
    // ── Display + Latin lyrics (Instrument Serif) ─────────
    val Display = TextStyle(
        fontFamily = InstrumentSerif,
        fontSize = 72.sp, lineHeight = 70.sp, letterSpacing = (-1.4).sp,
        fontWeight = FontWeight.Normal,
    )
    val H1 = TextStyle(
        fontFamily = InstrumentSerif,
        fontSize = 44.sp, lineHeight = 44.sp, letterSpacing = (-0.6).sp,
        fontWeight = FontWeight.Normal,
    )
    val H2 = TextStyle(
        fontFamily = InstrumentSerif,
        fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp,
        fontWeight = FontWeight.Normal,
    )
    val H3 = TextStyle(
        fontFamily = InstrumentSerif,
        fontSize = 20.sp, lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
    )

    // ── Body lyrics (Newsreader — the hero) ───────────────
    val LyricActive = TextStyle(
        fontFamily = Newsreader,
        fontSize = 30.sp, lineHeight = 36.sp,
        fontWeight = FontWeight.Medium, letterSpacing = (-0.15).sp,
    )
    val LyricPassive = TextStyle(
        fontFamily = Newsreader,
        fontSize = 22.sp, lineHeight = 28.sp,
        fontWeight = FontWeight.Normal,
    )
    val LyricDistant = TextStyle(
        fontFamily = Newsreader,
        fontSize = 18.sp, lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
    )

    // ── Romanization (italic, smaller, accent-dim color) ──
    val Romanization = TextStyle(
        fontFamily = Newsreader,
        fontSize = 14.sp,
        fontStyle = FontStyle.Italic, fontWeight = FontWeight.Light,
    )
    val RomanizationActive = TextStyle(
        fontFamily = Newsreader,
        fontSize = 16.sp,
        fontStyle = FontStyle.Italic, fontWeight = FontWeight.Light,
    )

    // ── Body UI text (Newsreader) ─────────────────────────
    val Body = TextStyle(
        fontFamily = Newsreader,
        fontSize = 16.sp, lineHeight = 22.sp,
    )
    val BodyItalic = TextStyle(
        fontFamily = Newsreader,
        fontSize = 14.sp, lineHeight = 20.sp,
        fontStyle = FontStyle.Italic, fontWeight = FontWeight.Light,
    )

    // ── Mono labels & metadata (JetBrains Mono) ───────────
    val Mono = TextStyle(
        fontFamily = JetBrainsMono,
        fontSize = 12.sp, fontWeight = FontWeight.Medium,
    )
    /** Uppercase eyebrow: 11sp, 0.12em tracking ≈ 1.3sp absolute. */
    val MonoUp = TextStyle(
        fontFamily = JetBrainsMono,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.3.sp,
    )
    /** Tabular numerals — for time, version, counts. */
    val MonoTabular = TextStyle(
        fontFamily = JetBrainsMono,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        fontFeatureSettings = "tnum",
    )
}

/**
 * Material3 Typography bridge — maps M3 slots ke SvType supaya komponen
 * Material yang belum di-replace (Switch, Slider sementara) tetap konsisten.
 * Setelah Phase 5 custom primitives selesai, mostly Material slots tidak
 * dipakai langsung lagi.
 */
val Typography = Typography(
    displayLarge   = SvType.Display,
    displayMedium  = SvType.H1,
    displaySmall   = SvType.H2,
    headlineLarge  = SvType.H2,
    headlineMedium = SvType.H3,
    headlineSmall  = SvType.H3,
    titleLarge     = SvType.H3,
    titleMedium    = SvType.Body,
    titleSmall     = SvType.Mono,
    bodyLarge      = SvType.Body,
    bodyMedium     = SvType.Body,
    bodySmall      = SvType.MonoTabular,
    labelLarge     = SvType.Mono,
    labelMedium    = SvType.MonoUp,
    labelSmall     = SvType.MonoUp,
)
