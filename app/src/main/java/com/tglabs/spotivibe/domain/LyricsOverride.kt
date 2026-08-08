package com.tglabs.spotivibe.domain

/**
 * Lirik yang dipilih user sendiri untuk sebuah track, menggantikan hasil
 * auto-match.
 *
 * Override MENANG atas mem cache, disk cache, dan jaringan. Konsekuensi yang
 * disengaja: lagu yang sudah di-override berhenti menanyakan LRCLIB selamanya.
 * Itu memang tujuannya, dan efek sampingnya mengurangi beban ke API komunitas
 * gratis.
 */
data class LyricsOverride(
    val trackId: String,
    val result: LyricsResult,
    /**
     * Id entry LRCLIB yang dipilih. Tidak dipakai untuk resolusi apa pun
     * sekarang; disimpan supaya fitur "segarkan override" bisa ditambahkan
     * nanti tanpa mengubah format penyimpanan.
     */
    val lrclibId: Long,
    val savedAtMs: Long,
)

/**
 * Sumber override untuk [LyricsResult]. Diabstraksi supaya `LyricsRepository`
 * bisa diuji tanpa menyeret penyimpanan, sama seperti [LyricsStatsRecorder].
 */
fun interface LyricsOverrideSource {
    fun overrideFor(trackId: String): LyricsResult?

    companion object {
        /** Tidak pernah punya override. Default untuk test. */
        val None = LyricsOverrideSource { null }
    }
}

/**
 * Penyimpanan permanen override.
 *
 * Implementasinya menulis ke `filesDir`, BUKAN `cacheDir`. Override adalah
 * keputusan user, bukan cache: `cacheDir` boleh dihapus sistem kapan saja saat
 * penyimpanan sesak, dan kehilangan keputusan user karena itu tidak bisa
 * diterima. Ini juga membuatnya kebal dari pembatasan LRU cache lirik.
 */
interface LyricsOverrideStore {
    suspend fun loadAll(): List<LyricsOverride>
    suspend fun save(override: LyricsOverride)
    suspend fun delete(trackId: String)
}
