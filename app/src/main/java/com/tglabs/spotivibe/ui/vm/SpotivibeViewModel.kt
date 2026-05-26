package com.tglabs.spotivibe.ui.vm

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tglabs.spotivibe.data.PlaybackController
import com.tglabs.spotivibe.data.PreferencesRepository
import com.tglabs.spotivibe.data.SpotifyConnection
import com.tglabs.spotivibe.data.SpotifyConnection.ConnectionState
import com.tglabs.spotivibe.domain.LyricsResult
import com.tglabs.spotivibe.domain.UiState
import com.tglabs.spotivibe.domain.hasRomanizableText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SpotivibeViewModel(
    private val controller: PlaybackController,
    private val connection: SpotifyConnection,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    /**
     * Combine 8 flows → UiState. Accent dihitung di PlaybackController (off main)
     * jadi ViewModel cuma forward — tidak ada heavy compute di Main thread.
     */
    val uiState: StateFlow<UiState> = combine(
        listOf(
            controller.connectionState,
            controller.track,
            controller.albumBitmap,
            controller.lyrics,
            controller.romaji,
            controller.accent,
            controller.isPremium,
            preferencesRepository.romanizationEnabled,
            preferencesRepository.overlayEnabled,
            preferencesRepository.darkMode,
            preferencesRepository.lyricsFontSize,
            preferencesRepository.lyricsOffsetMs,
            preferencesRepository.lineSpacing,
            preferencesRepository.highContrast,
            preferencesRepository.smoothScroll,
            preferencesRepository.hapticEnabled,
        )
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val connState = values[0] as ConnectionState
        val track = values[1] as com.tglabs.spotivibe.domain.NowPlaying?
        val bitmap = values[2] as Bitmap?
        val lyrics = values[3] as LyricsResult?
        val romaji = values[4] as Map<Long, String?>
        val accent = values[5] as Color?
        val isPremium = values[6] as Boolean
        val romaEnabled = values[7] as Boolean
        val overlayEnabled = values[8] as Boolean
        val darkMode = values[9] as Boolean
        val fontSize = values[10] as Int
        val offsetMs = values[11] as Int
        val lineSpacing = values[12] as Int
        val highContrast = values[13] as Boolean
        val smoothScroll = values[14] as Boolean
        val haptic = values[15] as Boolean

        when (connState) {
            ConnectionState.Disconnected -> UiState.Disconnected
            ConnectionState.Connecting -> UiState.Connecting
            ConnectionState.Connected -> track?.let { t ->
                val canRoma = lyrics?.synced?.let { hasRomanizableText(it.map { l -> l.text }) } ?: false
                UiState.Playing(
                    track = t,
                    albumBitmap = bitmap,
                    accentColor = accent,
                    lyrics = lyrics,
                    canRomanize = canRoma,
                    romanizationEnabled = romaEnabled,
                    romaji = romaji,
                    overlayEnabled = overlayEnabled,
                    darkMode = darkMode,
                    lyricsFontSize = fontSize,
                    isPremium = isPremium,
                    lyricsOffsetMs = offsetMs,
                    lineSpacing = lineSpacing,
                    highContrast = highContrast,
                    smoothScroll = smoothScroll,
                    hapticEnabled = haptic,
                )
            } ?: UiState.Idle
            is ConnectionState.Error -> UiState.Error(connState.message)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = UiState.Disconnected,
    )

    fun startAuth(activity: Activity, launcher: ActivityResultLauncher<Intent>) {
        connection.startAuth(activity, launcher)
    }

    fun handleAuthResult(context: Context, resultCode: Int, data: Intent?) {
        connection.handleAuthResult(context, resultCode, data)
    }

    fun disconnect() = connection.disconnect()

    /**
     * Local logout — clear authorized flag + access token + disconnect AppRemote.
     * NOTE: Tidak revoke authorization di Spotify side. Spotify masih simpan
     * grant untuk Client ID + signing key — next Connect = silent re-auth.
     * Untuk benar-benar revoke: spotify.com → Account → Apps → Remove Access.
     */
    fun logout() {
        viewModelScope.launch {
            preferencesRepository.setSpotifyAuthorized(false)
            preferencesRepository.clearSpotifyAccessToken()
            preferencesRepository.setOverlayEnabled(false)
            connection.disconnect()
        }
    }

    fun togglePlayPause() {
        val state = uiState.value
        if (state is UiState.Playing) {
            if (state.track.isPaused) connection.play() else connection.pause()
        }
    }
    fun next() = connection.skipNext()
    fun previous() = connection.skipPrevious()
    fun seekTo(positionMs: Long) = connection.seekTo(positionMs)

    fun toggleRomanization() {
        viewModelScope.launch {
            val current = (uiState.value as? UiState.Playing)?.romanizationEnabled ?: false
            preferencesRepository.setRomanizationEnabled(!current)
        }
    }

    fun setOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setOverlayEnabled(enabled)
        }
    }

    fun toggleDarkMode() {
        viewModelScope.launch {
            val current = (uiState.value as? UiState.Playing)?.darkMode ?: true
            preferencesRepository.setDarkMode(!current)
        }
    }

    fun bumpFontSize(delta: Int) {
        viewModelScope.launch {
            preferencesRepository.bumpLyricsFontSize(delta)
        }
    }

    /** Direct set font size — dipakai oleh slider di SettingsScreen */
    fun setFontSize(size: Int) {
        viewModelScope.launch {
            preferencesRepository.setLyricsFontSize(size)
        }
    }

    // ── Lyrics tuning + UX preferences (Improvement set) ──
    fun setLyricsOffsetMs(ms: Int) {
        viewModelScope.launch { preferencesRepository.setLyricsOffsetMs(ms) }
    }

    fun setLineSpacing(dp: Int) {
        viewModelScope.launch { preferencesRepository.setLineSpacing(dp) }
    }

    fun toggleHighContrast() {
        viewModelScope.launch {
            val current = (uiState.value as? UiState.Playing)?.highContrast ?: false
            preferencesRepository.setHighContrast(!current)
        }
    }

    fun toggleSmoothScroll() {
        viewModelScope.launch {
            val current = (uiState.value as? UiState.Playing)?.smoothScroll ?: true
            preferencesRepository.setSmoothScroll(!current)
        }
    }

    fun toggleHaptic() {
        viewModelScope.launch {
            val current = (uiState.value as? UiState.Playing)?.hapticEnabled ?: true
            preferencesRepository.setHapticEnabled(!current)
        }
    }

    class Factory(
        private val controller: PlaybackController,
        private val connection: SpotifyConnection,
        private val preferencesRepository: PreferencesRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SpotivibeViewModel(
                controller,
                connection,
                preferencesRepository,
            ) as T
        }
    }
}
