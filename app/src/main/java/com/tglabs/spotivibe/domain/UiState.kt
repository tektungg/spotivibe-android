package com.tglabs.spotivibe.domain

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

sealed interface UiState {
    /**
     * Notification access belum diberikan, jadi sesi media app lain tidak bisa
     * dibaca. Ini satu-satunya syarat app; tidak ada akun yang perlu login.
     */
    data object NeedsAccess : UiState

    /** Izin ada, tapi tidak ada player yang sedang menerbitkan lagu. */
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
        /** Tombol yang diizinkan player yang sedang diikuti. */
        val controls: PlayerControls = PlayerControls.Optimistic,
        /** Nama app player ("Spotify", "YouTube Music"), null kalau belum terbaca. */
        val playerLabel: String? = null,
        /** Jawaban tiap sumber lirik untuk lagu ini, null kalau dijawab cache. */
        val sourceReport: SourceReport? = null,
        /**
         * Ketiga lapis offset sync. [SyncOffsets.totalMs] adalah yang dipakai
         * untuk progress per kata dan kompensasi seek, sama dengan engine.
         */
        val syncOffsets: SyncOffsets = SyncOffsets(),
        val lineSpacing: Int = 7,
        val highContrast: Boolean = false,
        val smoothScroll: Boolean = true,
        val hapticEnabled: Boolean = true,
        /** Karaoke penuh per suku kata. Lihat `PreferencesRepository.lyricEffects`. */
        val lyricEffects: Boolean = true,
        /** false = perangkat offline; header menampilkan penanda "OFFLINE". */
        val isOnline: Boolean = true,
    ) : UiState {
        /** Pesan pengganti lirik yang menyebut sumber gagal, null = pesan bawaan. */
        val lyricsNotice: String? get() = lyricsNotice(lyricsState, sourceReport)

        /** Konten lirik saja, untuk kode yang tidak peduli kenapa kosong. */
        val lyrics: LyricsResult? get() = (lyricsState as? LyricsState.Ready)?.result
    }

}
