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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SpotivibeViewModel(
    private val controller: PlaybackController,
    private val connection: SpotifyConnection,
    private val preferencesRepository: PreferencesRepository,
    private val lyricsRepository: com.tglabs.spotivibe.data.LyricsRepository,
    private val overrideRepository: com.tglabs.spotivibe.data.LyricsOverrideRepository,
) : ViewModel() {

    // ── Pencarian lirik manual ───────────────────────────────────

    private val _searchState =
        MutableStateFlow<com.tglabs.spotivibe.domain.LyricsSearchState>(
            com.tglabs.spotivibe.domain.LyricsSearchState.Idle
        )
    val searchState: StateFlow<com.tglabs.spotivibe.domain.LyricsSearchState> =
        _searchState.asStateFlow()

    /**
     * Apakah lagu yang sedang diputar punya lirik tersimpan permanen.
     *
     * Ikut `revision` karena override disimpan di ConcurrentHashMap yang tidak
     * bisa diobservasi sendiri; tanpa itu menu tidak akan pernah menyadari
     * pilihan baru sampai lagunya berganti.
     */
    val hasRememberedOverride: StateFlow<Boolean> = combine(
        controller.track,
        overrideRepository.revision,
    ) { track, _ ->
        track?.id?.let { overrideRepository.isRemembered(it) } ?: false
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), false)

    fun searchLyrics(title: String, artist: String) {
        viewModelScope.launch {
            _searchState.value = com.tglabs.spotivibe.domain.LyricsSearchState.Loading
            _searchState.value = lyricsRepository.searchLyrics(title, artist)
        }
    }

    fun resetSearch() {
        _searchState.value = com.tglabs.spotivibe.domain.LyricsSearchState.Idle
    }

    /**
     * Terapkan lirik pilihan user ke track yang sedang diputar.
     *
     * Hasil pencarian dipasang ke track SPOTIFY yang sedang diputar, bukan ke
     * track milik entry LRCLIB. Itu seluruh gunanya: menghubungkan lagu yang
     * metadatanya tidak cocok dengan lirik yang benar.
     */
    fun applySearchResult(
        result: com.tglabs.spotivibe.domain.LyricsSearchResult,
        remember: Boolean,
    ) {
        val trackId = controller.track.value?.id ?: return
        viewModelScope.launch {
            overrideRepository.apply(
                trackId = trackId,
                result = result.result.copy(trackId = trackId),
                lrclibId = result.lrclibId,
                remember = remember,
            )
            controller.reloadLyrics()
        }
    }

    fun forgetOverride() {
        val trackId = controller.track.value?.id ?: return
        viewModelScope.launch {
            overrideRepository.forget(trackId)
            controller.reloadLyrics()
        }
    }

    /**
     * Combine 8 flows → UiState. Accent dihitung di PlaybackController (off main)
     * jadi ViewModel cuma forward — tidak ada heavy compute di Main thread.
     */
    val uiState: StateFlow<UiState> = combine(
        listOf(
            controller.connectionState,
            controller.track,
            controller.albumBitmap,
            controller.lyricsState,
            controller.currentLineIndex,
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
        val lyricsState = values[3] as com.tglabs.spotivibe.domain.LyricsState
        val currentLineIndex = values[4] as Int
        val romaji = values[5] as Map<Int, String?>
        val accent = values[6] as Color?
        val capability = values[7] as com.tglabs.spotivibe.domain.PlaybackCapability
        val romaEnabled = values[8] as Boolean
        val overlayEnabled = values[9] as Boolean
        val darkMode = values[10] as Boolean
        val fontSize = values[11] as Int
        val offsetMs = values[12] as Int
        val lineSpacing = values[13] as Int
        val highContrast = values[14] as Boolean
        val smoothScroll = values[15] as Boolean
        val haptic = values[16] as Boolean

        when (connState) {
            ConnectionState.Disconnected -> UiState.Disconnected
            ConnectionState.Connecting -> UiState.Connecting
            ConnectionState.Connected -> track?.let { t ->
                val synced = (lyricsState as? com.tglabs.spotivibe.domain.LyricsState.Ready)
                    ?.result?.synced
                val canRoma = synced?.let { hasRomanizableText(it.map { l -> l.text }) } ?: false
                UiState.Playing(
                    track = t,
                    albumBitmap = bitmap,
                    accentColor = accent,
                    lyricsState = lyricsState,
                    currentLineIndex = currentLineIndex,
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

    /** Statistik lirik untuk ditampilkan di Settings. */
    val lyricsStats: StateFlow<com.tglabs.spotivibe.domain.LyricsStats> =
        preferencesRepository.lyricsStats.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = com.tglabs.spotivibe.domain.LyricsStats(),
        )

    fun resetLyricsStats() {
        viewModelScope.launch { preferencesRepository.resetLyricsStats() }
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
        private val lyricsRepository: com.tglabs.spotivibe.data.LyricsRepository,
        private val overrideRepository: com.tglabs.spotivibe.data.LyricsOverrideRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SpotivibeViewModel(
                controller,
                connection,
                preferencesRepository,
                lyricsRepository,
                overrideRepository,
            ) as T
        }
    }
}
