package com.tglabs.spotivibe.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Glassmorphism Tokyo color scheme — dark-only (no light variant)
private val SpotivibeDarkScheme = darkColorScheme(
    primary = AccentDefault,
    onPrimary = Color(0xFF1A0E2E),
    secondary = AccentSpotifyGreen,
    onSecondary = Color(0xFF002106),
    background = BackgroundDeep,
    onBackground = TextPrimary,
    surface = BackgroundLift,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceFrost,
    onSurfaceVariant = TextSecondary,
    outline = BorderHairline,
    outlineVariant = BorderHairline,
)

@Composable
fun SpotivibeTheme(
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val insets = WindowCompat.getInsetsController(window, view)
            insets.isAppearanceLightStatusBars = false
            insets.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = SpotivibeDarkScheme,
        typography = Typography,
        content = content,
    )
}
