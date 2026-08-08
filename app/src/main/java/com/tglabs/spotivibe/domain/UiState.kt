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
        /**
         * Membawa alasan kenapa lirik kosong, bukan cuma kosongnya. UI
         * membedakan "lagu ini memang tidak berlirik" dari "gagal memuat".
         */
        val lyricsState: LyricsState = LyricsState.Loading,
        /**
         * Baris aktif, dihitung sekali di PlaybackController dan dipakai
         * bersama oleh layar, notification, dan overlay. UI TIDAK boleh
         * menghitung ulang dari progress: itu yang dulu bikin tiga permukaan
         * bisa menampilkan baris berbeda.
         */
        val currentLineIndex: Int = -1,
        val canRomanize: Boolean = false,
        val romanizationEnabled: Boolean = false,
        /** Key = [SyncedLine.id], bukan timeMs. Lihat [SyncedLine] soal kenapa. */
        val romaji: Map<Int, String?> = emptyMap(),
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
    ) : UiState {
        /** Konten lirik saja, untuk kode yang tidak peduli kenapa kosong. */
        val lyrics: LyricsResult? get() = (lyricsState as? LyricsState.Ready)?.result
    }

    data class Error(val message: String) : UiState
}
