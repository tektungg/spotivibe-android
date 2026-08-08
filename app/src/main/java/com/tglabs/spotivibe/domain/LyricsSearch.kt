package com.tglabs.spotivibe.domain

import androidx.compose.runtime.Immutable

/**
 * Pencarian lirik manual, untuk saat auto-match memuat lirik dari lagu yang
 * sama sekali berbeda.
 *
 * Semua di sini murni: pemetaan hasil mentah LRCLIB jadi baris yang siap
 * ditampilkan, penyaringan, dan format. Yang menyentuh jaringan ada di
 * `LyricsRepository`.
 */

/** Satu hasil pencarian yang siap ditampilkan dan langsung bisa diterapkan. */
@Immutable
data class LyricsSearchResult(
    val lrclibId: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val durationSec: Int,
    val hasSynced: Boolean,
    /** Sudah ter-parse, jadi memilihnya tidak perlu request lagi. */
    val result: LyricsResult,
)

sealed interface LyricsSearchState {
    /** Belum ada pencarian dijalankan. */
    data object Idle : LyricsSearchState

    data object Loading : LyricsSearchState

    @Immutable
    data class Ready(val results: List<LyricsSearchResult>) : LyricsSearchState

    /** Pencarian berhasil, tapi tidak ada yang cocok. */
    data object Empty : LyricsSearchState

    /** Tidak bisa mencari sekarang: jaringan mati, timeout, 5xx. */
    data class Failed(val reason: String) : LyricsSearchState
}

/**
 * Ubah entry LRCLIB jadi baris hasil, sambil membuang yang tidak berguna.
 *
 * Dua jenis disaring:
 *
 * - `instrumental = true`. Lagunya memang tidak berlirik, memilihnya cuma
 *   menghasilkan layar kosong.
 * - Yang tidak punya lirik ter-sync maupun teks polos. LRCLIB kadang
 *   mengembalikan entry metadata tanpa isi.
 *
 * Menyaring di sini, bukan di UI, supaya aturannya bisa diuji dan tidak
 * terduplikasi kalau nanti ada permukaan lain yang memakai pencarian.
 */
fun toSearchResults(dtos: List<LrclibSearchEntry>, trackId: String): List<LyricsSearchResult> =
    dtos.mapNotNull { dto ->
        if (dto.instrumental) return@mapNotNull null

        val synced = dto.syncedLyrics?.takeIf { it.isNotBlank() }?.let { parseLrc(it) }
            ?.takeIf { it.isNotEmpty() }
        val plain = dto.plainLyrics?.takeIf { it.isNotBlank() }
        if (synced == null && plain == null) return@mapNotNull null

        LyricsSearchResult(
            lrclibId = dto.id,
            trackName = dto.trackName.ifBlank { "(tanpa judul)" },
            artistName = dto.artistName.ifBlank { "(tanpa artis)" },
            albumName = dto.albumName,
            durationSec = dto.durationSec,
            hasSynced = synced != null,
            result = LyricsResult(trackId = trackId, synced = synced, plain = plain),
        )
    }

/**
 * Bentuk netral dari satu entry LRCLIB, lepas dari DTO jaringan.
 *
 * Ada supaya [toSearchResults] bisa diuji tanpa menyeret Moshi dan bentuk JSON
 * LRCLIB ke dalam test.
 */
data class LrclibSearchEntry(
    val id: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val durationSec: Int,
    val instrumental: Boolean,
    val syncedLyrics: String?,
    val plainLyrics: String?,
)

/**
 * Durasi jadi `m:ss`, penanda paling cepat untuk mengenali versi live atau
 * extended. Negatif dan nol jadi tanda hubung, bukan `0:00`, supaya "tidak
 * diketahui" tidak tersamar jadi "lagu nol detik".
 */
fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return "—"
    val m = seconds / 60
    val s = seconds % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
