package com.tglabs.spotivibe.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkScheme = darkColorScheme(
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

private val LightScheme = lightColorScheme(
    primary = AccentDefault,
    onPrimary = Color(0xFF1A0E2E),
    secondary = AccentSpotifyGreen,
    onSecondary = Color.White,
    background = BackgroundDeepLight,
    onBackground = TextPrimaryLight,
    surface = BackgroundLiftLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceFrostLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = BorderHairlineLight,
    outlineVariant = BorderHairlineLight,
)

@Composable
fun SpotivibeTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkScheme else LightScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val insets = WindowCompat.getInsetsController(window, view)
            // Light scheme = dark icons on system bars (good contrast on light bg)
            insets.isAppearanceLightStatusBars = !darkTheme
            insets.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
