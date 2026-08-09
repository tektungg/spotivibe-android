package com.tglabs.spotivibe.ui.screen

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.tglabs.spotivibe.domain.UiState
import com.tglabs.spotivibe.ui.theme.LocalSvColors

/**
 * State-based router. NowPlayingScreen handles its own ambient bg.
 * Other states (Disconnected / Connecting / Idle / Error) use solid
 * sv.bg0 — no gradient, no glow, editorial flat surface.
 */
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
    onOpenSettings: () -> Unit,
    onSearchLyrics: () -> Unit = {},
    hasRememberedOverride: Boolean = false,
    onForgetOverride: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val sv = LocalSvColors.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(sv.bg0),
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
            is UiState.Idle -> EmptyState(kind = EmptyKind.Idle)
            is UiState.Playing -> {
                // Route ke tablet/landscape variant kalau orientation landscape.
                val isLandscape = LocalConfiguration.current.orientation ==
                    Configuration.ORIENTATION_LANDSCAPE
                if (isLandscape) {
                    NowPlayingTabletScreen(
                        state = state,
                        onTogglePlayPause = onTogglePlayPause,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onSeek = onSeek,
                        onToggleRomanization = onToggleRomanization,
                        onToggleOverlay = onToggleOverlay,
                        onOpenSettings = onOpenSettings,
                        onSearchLyrics = onSearchLyrics,
                        hasRememberedOverride = hasRememberedOverride,
                        onForgetOverride = onForgetOverride,
                    )
                } else {
                    NowPlayingScreen(
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
                        onOpenSettings = onOpenSettings,
                        onSearchLyrics = onSearchLyrics,
                        hasRememberedOverride = hasRememberedOverride,
                        onForgetOverride = onForgetOverride,
                    )
                }
            }
        }
    }
}
