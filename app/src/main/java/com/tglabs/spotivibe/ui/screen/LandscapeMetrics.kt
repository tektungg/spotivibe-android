package com.tglabs.spotivibe.ui.screen

/**
 * Ukuran adaptif untuk panel kiri di orientasi landscape.
 *
 * Ada karena layout landscape dirancang untuk tablet tapi dipakai untuk SEMUA
 * landscape, termasuk HP. Album cover-nya cuma dibatasi lebar
 * (`widthIn(max = 360.dp)` plus `aspectRatio(1f)`), tidak pernah dibatasi
 * tinggi. Di HP landscape hasilnya:
 *
 * ```
 * kolom kiri  347 dp lebar, 375 dp tinggi
 * padding     40 dp atas + bawah  -> 295 dp tersedia
 * album       267 dp (mengikuti lebar)
 * ```
 *
 * Album sendirian memakan 267 dari 295 dp, jadi judul, artis, meta, dan
 * transport semuanya terdorong keluar layar. User melaporkannya sebagai
 * "judul lagu tidak tampil di landscape".
 *
 * Dipisah jadi fungsi murni dengan satuan dp sebagai [Float] supaya bisa diuji
 * tanpa Compose. Perhitungan layout yang salah tidak akan pernah gagal compile,
 * jadi satu-satunya cara menjaganya adalah test.
 */
data class LandscapePaneMetrics(
    val albumDp: Float,
    val titleSp: Float,
    val artistSp: Float,
    /** Jarak antara album dan blok judul. */
    val gapDp: Float,
)

/** Batas atas album, sama dengan nilai desain tablet aslinya. */
const val ALBUM_MAX_DP = 360f

/** Di bawah ini album tidak berguna lagi sebagai penanda visual. */
const val ALBUM_MIN_DP = 72f

/** Panel selega ini muat memakai ukuran tablet penuh. */
const val ROOMY_CONTENT_DP = 480f

/**
 * Hitung ukuran panel kiri.
 *
 * @param paneWidthDp lebar panel SETELAH dikurangi padding.
 * @param paneHeightDp tinggi panel SETELAH dikurangi padding.
 * @param reservedDp ruang yang dipesan untuk transport yang dipatok di bawah.
 *   Tidak boleh ikut jadi jatah album, karena transport harus selalu terlihat
 *   tanpa perlu di-scroll.
 */
fun landscapePaneMetrics(
    paneWidthDp: Float,
    paneHeightDp: Float,
    reservedDp: Float = 90f,
): LandscapePaneMetrics {
    val content = (paneHeightDp - reservedDp).coerceAtLeast(0f)
    val roomy = content >= ROOMY_CONTENT_DP

    // Di panel sempit album dibatasi TINGGI, bukan cuma lebar. 0.45 menyisakan
    // cukup ruang supaya judul dan artis ikut terlihat tanpa scroll.
    val capTinggi = if (roomy) ALBUM_MAX_DP else content * 0.45f
    val album = minOf(paneWidthDp, ALBUM_MAX_DP, capTinggi)
        .coerceIn(0f, ALBUM_MAX_DP)
        .let { if (paneWidthDp <= 0f || content <= 0f) 0f else maxOf(it, minOf(ALBUM_MIN_DP, paneWidthDp)) }

    return LandscapePaneMetrics(
        albumDp = album,
        // Tipografi ikut mengecil. Judul 42sp di panel pendek memakan tiga baris
        // dan mendorong artis keluar layar lagi, jadi mengecilkan album saja
        // tidak cukup.
        titleSp = if (roomy) 42f else 24f,
        artistSp = if (roomy) 18f else 14f,
        gapDp = if (roomy) 32f else 16f,
    )
}
