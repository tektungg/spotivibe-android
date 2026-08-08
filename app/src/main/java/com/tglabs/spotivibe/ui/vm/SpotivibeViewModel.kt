package com.tglabs.spotivibe.ui.vm

import android.content.Context
import android.graphics.Bitmap
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
            controller.capability,
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
        val capability = values[6] as com.tglabs.spotivibe.domain.PlaybackCapability
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
                    capability = capability,
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

    /**
     * Mulai Authorization Code + PKCE. [openUrl] menerima URL `/authorize` yang
     * harus dibuka di Custom Tab; dipanggil di Main thread.
     *
     * Tidak lagi memakai `AuthorizationClient` SDK: jalur native-nya membuang
     * `code_challenge`, jadi PKCE tidak mungkin lewat sana.
     */
    fun beginAuth(openUrl: (String) -> Unit) {
        viewModelScope.launch {
            val url = connection.authorizeUrl() ?: return@launch
            openUrl(url)
        }
    }

    /** Teruskan URI intent yang masuk. URI asing diabaikan tanpa efek samping. */
    fun handleRedirect(context: Context, uri: String?) {
        viewModelScope.launch { connection.handleRedirect(context, uri) }
    }

    /** Sudah ada sesi tersimpan yang layak auto-reconnect? */
    fun hasSession(): Boolean = connection.hasSession()

    fun disconnect() = connection.disconnect()

    /**
     * Logout lokal: buang access + refresh token, putus App Remote.
     * NOTE: Tidak revoke authorization di sisi Spotify. Grant untuk Client ID
     * masih tersimpan, jadi Connect berikutnya biasanya silent re-auth.
     * Untuk benar-benar revoke: spotify.com → Account → Apps → Remove Access.
     */
    fun logout() {
        viewModelScope.launch {
            preferencesRepository.setOverlayEnabled(false)
            connection.signOut()
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
