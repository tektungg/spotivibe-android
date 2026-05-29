package com.tglabs.spotivibe.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Custom color set yang exposed via LocalSvColors. Material3 colorScheme
 * tetap di-set (untuk Material primitives yang masih kepakai), tapi
 * mayoritas UI baca dari LocalSvColors.current. Ini pattern yang
 * dipakai banyak design system (Compose-friendly, no Material chrome
 * leakage).
 */
@Immutable
data class SvColors(
    val bg0: Color,
    val bg1: Color,
    val bg2: Color,
    val bg3: Color,
    val ink1: Color,
    val ink2: Color,
    val ink3: Color,
    val ink4: Color,
    val rule: Color,
    val ruleSoft: Color,
    val accent: Color,
    val accentDim: Color,
    val accentGhost: Color,
    val accentSoft: Color,
    val accentInk: Color,
    val isDark: Boolean,
)

private val DarkSvColors = SvColors(
    bg0       = BgDark0,
    bg1       = BgDark1,
    bg2       = BgDark2,
    bg3       = BgDark3,
    ink1      = InkDark1,
    ink2      = InkDark2,
    ink3      = InkDark3,
    ink4      = InkDark4,
    rule      = RuleDark,
    ruleSoft  = RuleSoftDark,
    accent      = AccentDefault,
    accentDim   = AccentDefaultDim,
    accentGhost = AccentDefault.copy(alpha = 0.08f),
    accentSoft  = AccentDefault.copy(alpha = 0.18f),
    accentInk   = AccentInkDark,
    isDark      = true,
)

private val LightSvColors = SvColors(
    bg0       = BgLight0,
    bg1       = BgLight1,
    bg2       = BgLight2,
    bg3       = BgLight3,
    ink1      = InkLight1,
    ink2      = InkLight2,
    ink3      = InkLight3,
    ink4      = InkLight4,
    rule      = RuleLight,
    ruleSoft  = RuleSoftLight,
    accent      = AccentDefault,
    accentDim   = AccentDefaultDim,
    accentGhost = AccentDefault.copy(alpha = 0.07f),
    accentSoft  = AccentDefault.copy(alpha = 0.16f),
    accentInk   = AccentInkLight,
    isDark      = false,
)

val LocalSvColors = staticCompositionLocalOf<SvColors> {
    error("SvColors not provided — wrap your UI with SpotivibeTheme")
}

/**
 * Map SvColors → Material3 ColorScheme untuk M3 primitives yang masih
 * kepakai (Switch, Slider) selama transisi. Setelah custom primitives
 * lengkap, scheme ini perannya minor.
 */
private fun SvColors.toM3DarkScheme() = darkColorScheme(
    primary = accent, onPrimary = accentInk,
    secondary = accent, onSecondary = accentInk,
    background = bg0, onBackground = ink1,
    surface = bg1, onSurface = ink1,
    surfaceVariant = bg2, onSurfaceVariant = ink2,
    outline = rule, outlineVariant = ruleSoft,
    error = Color(0xFFD4685A), onError = ink1,
    errorContainer = Color(0xFF3A1F1A), onErrorContainer = Color(0xFFF1C6BD),
)

private fun SvColors.toM3LightScheme() = lightColorScheme(
    primary = accent, onPrimary = accentInk,
    secondary = accent, onSecondary = accentInk,
    background = bg0, onBackground = ink1,
    surface = bg1, onSurface = ink1,
    surfaceVariant = bg2, onSurfaceVariant = ink2,
    outline = rule, outlineVariant = ruleSoft,
    error = Color(0xFF8E3520), onError = bg0,
    errorContainer = Color(0xFFF1D7CE), onErrorContainer = Color(0xFF5C1F0F),
)

/**
 * @param accent dinamis dari album-art extraction. Kalau null, pakai
 *   AccentDefault (Coral). Caller bertanggung jawab memanggil lockedAccent()
 *   sebelum pass — agar chroma + lightness terkunci.
 * @param accentDim sama hue dengan accent, L*0.68 C*0.66. Kalau null,
 *   derived dari accent via AccentLock.dimOf().
 */
@Composable
fun SpotivibeTheme(
    darkTheme: Boolean = true,
    accent: Color? = null,
    accentDim: Color? = null,
    content: @Composable () -> Unit,
) {
    val baseColors = if (darkTheme) DarkSvColors else LightSvColors
    // Re-lock accent ke nilai yang COCOK untuk mode aktif. accent yang masuk
    // dari controller di-lock dark (carrier hue) — di sini di-relock supaya
    // dark mode dapat accent terang/vivid, light mode dapat accent gelap/
    // kontras di paper. accentDim DITURUNKAN dari accent (bukan fallback
    // coral) — warna romanization ikut hue track + arah dim sesuai mode.
    val activeAccent = accent?.let { AccentLock.lockedAccent(it, dark = darkTheme) }
        ?: baseColors.accent
    val activeAccentDim = accentDim
        ?: accent?.let { AccentLock.dimOf(it, dark = darkTheme) }
        ?: baseColors.accentDim
    val activeAccentInk = if (darkTheme) AccentInkDark else AccentInkLight

    val svColors = baseColors.copy(
        accent      = activeAccent,
        accentDim   = activeAccentDim,
        accentGhost = activeAccent.copy(alpha = if (darkTheme) 0.08f else 0.07f),
        accentSoft  = activeAccent.copy(alpha = if (darkTheme) 0.18f else 0.16f),
        accentInk   = activeAccentInk,
    )

    val m3Scheme = if (darkTheme) svColors.toM3DarkScheme() else svColors.toM3LightScheme()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val insets = WindowCompat.getInsetsController(window, view)
            insets.isAppearanceLightStatusBars = !darkTheme
            insets.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalSvColors provides svColors) {
        MaterialTheme(
            colorScheme = m3Scheme,
            typography = Typography,
            content = content,
        )
    }
}
