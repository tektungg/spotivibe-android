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
import com.tglabs.spotivibe.ui.theme.BackgroundDeep
import com.tglabs.spotivibe.ui.theme.BackgroundLift
import com.tglabs.spotivibe.ui.theme.TextSecondary

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
    modifier: Modifier = Modifier,
) {
    // Accent-aware gradient — saat Playing, sedikit campur accent album cover
    // di middle-bottom. Smooth 800ms transition saat track ganti.
    val playingAccent = (state as? UiState.Playing)?.accentColor
    val targetTint = playingAccent?.copy(alpha = 0.18f) ?: Color.Transparent
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
                        0.0f to BackgroundDeep,
                        0.55f to BackgroundLift,
                        1.0f to animatedTint.compositeOver(BackgroundLift),
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
                color = TextSecondary,
            )
            is UiState.Playing -> NowPlayingScreen(
                state = state,
                onTogglePlayPause = onTogglePlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onSeek = onSeek,
                onToggleRomanization = onToggleRomanization,
                onToggleOverlay = onToggleOverlay,
            )
        }
    }
}
