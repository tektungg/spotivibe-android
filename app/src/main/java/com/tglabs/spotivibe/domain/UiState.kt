package com.tglabs.spotivibe.domain

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

sealed interface UiState {
    data object Disconnected : UiState
    data object Connecting : UiState
    data object Idle : UiState

    /**
     * @Immutable promise ke Compose Compiler: kalau instance ini sama (equals=true),
     * children Composable bisa skip recomposition. Walaupun Bitmap dan Map<Long, ...>
     * teknisnya bukan @Stable (Compose default), kita garansi: tidak pernah di-mutate
     * setelah construction — selalu replace whole instance.
     */
    @Immutable
    data class Playing(
        val track: NowPlaying,
        val albumBitmap: Bitmap? = null,
        val accentColor: Color? = null,
        val lyrics: LyricsResult? = null,
        val canRomanize: Boolean = false,
        val romanizationEnabled: Boolean = false,
        val romaji: Map<Long, String?> = emptyMap(),
        val overlayEnabled: Boolean = false,
        val darkMode: Boolean = true,
        val lyricsFontSize: Int = 17,
        /**
         * Kontrol transport ditampilkan optimistis selama masih
         * [PlaybackCapability.Unknown]. Lihat [PlaybackCapability] untuk alasan
         * kenapa ini bukan boolean lagi.
         */
        val capability: PlaybackCapability = PlaybackCapability.Unknown,
        // Improvement set: lyrics tuning + UX prefs
        val lyricsOffsetMs: Int = 0,
        val lineSpacing: Int = 7,
        val highContrast: Boolean = false,
        val smoothScroll: Boolean = true,
        val hapticEnabled: Boolean = true,
    ) : UiState

    data class Error(val message: String) : UiState
}
