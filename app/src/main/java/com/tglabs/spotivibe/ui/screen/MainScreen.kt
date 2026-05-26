package com.tglabs.spotivibe.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.tglabs.spotivibe.domain.UiState
import com.tglabs.spotivibe.ui.theme.BackgroundDeepLight

@Composable
fun MainScreen(
    state: UiState,
    onConnect: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleRomanization: () -> Unit,
    onToggleOverlay: () -> Unit,
    onToggleDarkMode: () -> Unit,
    onBumpFontSize: (Int) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Gradient endpoints dari Material colorScheme — auto switch dark/light
    val bgDeep = MaterialTheme.colorScheme.background
    val bgLift = MaterialTheme.colorScheme.surface
    val isLight = bgDeep == BackgroundDeepLight

    val playingAccent = (state as? UiState.Playing)?.accentColor
    // Light mode: lower alpha biar tidak terlalu wash-out warna
    val tintAlpha = if (isLight) 0.10f else 0.18f
    val targetTint = playingAccent?.copy(alpha = tintAlpha) ?: Color.Transparent
    val animatedTint by animateColorAsState(
        targetValue = targetTint,
        animationSpec = tween(durationMillis = 800),
        label = "bgTint",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to bgDeep,
                        0.55f to bgLift,
                        1.0f to animatedTint.compositeOver(bgLift),
                    ),
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            is UiState.Disconnected -> ConnectScreen(
                isConnecting = false,
                errorMessage = null,
                onConnect = onConnect,
            )
            is UiState.Connecting -> ConnectScreen(
                isConnecting = true,
                onConnect = onConnect,
            )
            is UiState.Error -> ConnectScreen(
                isConnecting = false,
                errorMessage = state.message,
                onConnect = onConnect,
            )
            is UiState.Idle -> Text(
                text = "Connected. Putar lagu di Spotify…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is UiState.Playing -> NowPlayingScreen(
                state = state,
                onTogglePlayPause = onTogglePlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onSeek = onSeek,
                onToggleRomanization = onToggleRomanization,
                onToggleOverlay = onToggleOverlay,
                onToggleDarkMode = onToggleDarkMode,
                onBumpFontSize = onBumpFontSize,
                onLogout = onLogout,
            )
        }
    }
}
