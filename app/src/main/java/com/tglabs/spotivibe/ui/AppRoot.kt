package com.tglabs.spotivibe.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tglabs.spotivibe.domain.UiState
import com.tglabs.spotivibe.ui.component.SongSyncDialog
import com.tglabs.spotivibe.ui.screen.LyricsSearchScreen
import com.tglabs.spotivibe.ui.screen.MainScreen
import com.tglabs.spotivibe.ui.screen.SettingsScreen
import com.tglabs.spotivibe.ui.theme.SpotivibeTheme
import com.tglabs.spotivibe.ui.vm.SpotivibeViewModel
import kotlinx.coroutines.flow.Flow

/** Aksi yang hanya bisa dikerjakan Activity: membuka layar sistem. */
class ActivityActions(
    val openNotificationAccess: () -> Unit,
    val toggleOverlay: () -> Unit,
)

/** Layar yang sedang tampil. Disimpan sebagai nama supaya lolos rotasi. */
private enum class Route { Main, Settings, Search }

/**
 * Akar UI: tema dan pilihan layar.
 *
 * Dulu seluruh routing ini tinggal di `MainActivity.onCreate`. Dipisah supaya
 * Activity cuma mengurus hal yang memang miliknya (izin, layar sistem), dan
 * tiap layar disusun di fungsinya sendiri.
 */
@Composable
fun AppRoot(viewModel: SpotivibeViewModel, darkModeFlow: Flow<Boolean>, actions: ActivityActions) {
    // Default true sambil DataStore memuat, supaya cold start tidak berkedip terang.
    val darkMode by darkModeFlow.collectAsState(initial = true)
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    // Accent dinamis per lagu dari PlaybackController, diteruskan ke tema supaya
    // LocalSvColors.current.accent selalu mengikuti lagu yang sedang diputar.
    val accent = (state as? UiState.Playing)?.accentColor
    SpotivibeTheme(darkTheme = darkMode, accent = accent) {
        var route by rememberSaveable { mutableStateOf(Route.Main) }
        val playing = state as? UiState.Playing
        when {
            route == Route.Search && playing != null ->
                SearchRoute(viewModel, playing, onClose = { route = Route.Main })
            route == Route.Settings ->
                SettingsRoute(viewModel, state, actions, onClose = { route = Route.Main })
            else -> MainRoute(
                viewModel = viewModel,
                state = state,
                actions = actions,
                onNavigate = { route = it },
            )
        }
    }
}

@Composable
private fun SearchRoute(viewModel: SpotivibeViewModel, playing: UiState.Playing, onClose: () -> Unit) {
    val close = {
        onClose()
        viewModel.resetSearch()
    }
    LyricsSearchScreen(
        initialTitle = playing.track.title,
        initialArtist = playing.track.artist,
        state = viewModel.searchState.collectAsStateWithLifecycle().value,
        hasRememberedOverride = viewModel.hasRememberedOverride.collectAsStateWithLifecycle().value,
        onSearch = { t, a -> viewModel.searchLyrics(t, a) },
        onPick = { hasil, ingat ->
            viewModel.applySearchResult(hasil, ingat)
            close()
        },
        onForget = {
            viewModel.forgetOverride()
            close()
        },
        onBack = close,
    )
}

@Composable
private fun SettingsRoute(
    viewModel: SpotivibeViewModel,
    state: UiState,
    actions: ActivityActions,
    onClose: () -> Unit,
) {
    SettingsScreen(
        state = state,
        onBack = onClose,
        onToggleDarkMode = { viewModel.toggleDarkMode() },
        onSetFontSize = { size -> viewModel.setFontSize(size) },
        onOpenAccess = actions.openNotificationAccess,
        onSetLyricsOffsetMs = { ms -> viewModel.setLyricsOffsetMs(ms) },
        onSetDeviceOffsetMs = { ms -> viewModel.setDeviceOffsetMs(ms) },
        onSetLineSpacing = { dp -> viewModel.setLineSpacing(dp) },
        onToggleHighContrast = { viewModel.toggleHighContrast() },
        onToggleSmoothScroll = { viewModel.toggleSmoothScroll() },
        onToggleHaptic = { viewModel.toggleHaptic() },
        onToggleLyricEffects = { viewModel.toggleLyricEffects() },
        communitySources = viewModel.communitySources.collectAsStateWithLifecycle().value,
        onToggleCommunitySources = { viewModel.toggleCommunitySources() },
        lyricsStats = viewModel.lyricsStats.collectAsStateWithLifecycle().value,
        onResetLyricsStats = { viewModel.resetLyricsStats() },
    )
}

@Composable
private fun MainRoute(
    viewModel: SpotivibeViewModel,
    state: UiState,
    actions: ActivityActions,
    onNavigate: (Route) -> Unit,
) {
    var showSongSync by rememberSaveable { mutableStateOf(false) }
    MainScreen(
        state = state,
        onGrantAccess = actions.openNotificationAccess,
        onTogglePlayPause = { viewModel.togglePlayPause() },
        onNext = { viewModel.next() },
        onPrevious = { viewModel.previous() },
        onSeek = { positionMs -> viewModel.seekTo(positionMs) },
        onToggleRomanization = { viewModel.toggleRomanization() },
        onToggleOverlay = actions.toggleOverlay,
        onToggleDarkMode = { viewModel.toggleDarkMode() },
        onBumpFontSize = { delta -> viewModel.bumpFontSize(delta) },
        onOpenSettings = { onNavigate(Route.Settings) },
        onSearchLyrics = { onNavigate(Route.Search) },
        hasRememberedOverride = viewModel.hasRememberedOverride.collectAsStateWithLifecycle().value,
        onForgetOverride = { viewModel.forgetOverride() },
        onSyncSong = { showSongSync = true },
    )
    val playing = state as? UiState.Playing
    if (showSongSync && playing != null) {
        SongSyncDialog(
            title = playing.track.title,
            songOffsetMs = playing.syncOffsets.songMs,
            onChange = { ms -> viewModel.setSongOffsetMs(ms) },
            onDismiss = { showSongSync = false },
        )
    }
}
