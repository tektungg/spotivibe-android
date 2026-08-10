package com.tglabs.spotivibe.ui.component

/**
 * Ukuran adaptif untuk daftar lirik tersinkron.
 *
 * Ada dua masalah yang diselesaikan di sini, dan keduanya tidak akan pernah
 * gagal compile:
 *
 * **1. Jangkar baris aktif dulunya dalam PIKSEL.** `animateScrollToItem` dipanggil
 * dengan `scrollOffset = -200`, dan satuannya piksel mentah, bukan dp. Artinya
 * posisi baris aktif berpindah-pindah tergantung kerapatan layar:
 *
 * ```
 * density 2,0 (mdpi tinggi)  -> 200 px = 100 dp dari atas
 * density 3,25 (HP penguji)  -> 200 px =  62 dp dari atas
 * density 4,0 (xxxhdpi)      -> 200 px =  50 dp dari atas
 * ```
 *
 * App yang sama, layar yang sama besarnya, tapi baris aktif duduk di tempat yang
 * berbeda. Dinyatakan dalam dp supaya sama di semua perangkat.
 *
 * **2. Ruang bawah dulunya tetap 120 dp.** Nilai itu masuk akal di viewport
 * portrait yang tingginya ~600 dp, tapi di panel landscape yang tingginya ~285 dp
 * ia memakan 42% ruang yang tersedia. Digabung dengan ruang atas, lebih dari
 * separuh panel lirik habis untuk kekosongan.
 *
 * Fungsi murni dengan satuan dp sebagai [Float] supaya bisa diuji tanpa Compose.
 */
data class LyricsPadding(
    /** Jarak baris aktif dari atas viewport. */
    val anchorDp: Float,
    /** Ruang di atas baris pertama, supaya ia bisa naik sampai jangkar. */
    val leadingDp: Float,
    /** Ruang di bawah baris terakhir, supaya ia tidak mentok di dasar. */
    val trailingDp: Float,
)

/**
 * Jangkar sebagai bagian dari tinggi viewport, dijepit.
 *
 * Batas atas 64 dp dipilih karena mereproduksi perilaku lama di HP penguji
 * (200 px pada density 3,25 = 62 dp), jadi portrait tidak melihat perubahan
 * sama sekali. Di viewport pendek pecahannya yang berlaku, sehingga jangkar
 * ikut naik dan baris aktif tidak terdorong ke tengah panel.
 */
const val LYRICS_ANCHOR_FRACTION = 0.11f
const val LYRICS_ANCHOR_MIN_DP = 36f
const val LYRICS_ANCHOR_MAX_DP = 64f

/** Napas di atas baris pertama, di luar jangkar. */
const val LYRICS_LEAD_EXTRA_DP = 16f

/** Ruang bawah sebagai bagian dari tinggi viewport. */
const val LYRICS_TRAIL_FRACTION = 0.20f
const val LYRICS_TRAIL_MIN_DP = 64f
const val LYRICS_TRAIL_MAX_DP = 200f

/**
 * @param viewportHeightDp tinggi area lirik yang terlihat.
 */
fun lyricsPadding(viewportHeightDp: Float): LyricsPadding {
    val tinggi = viewportHeightDp.coerceAtLeast(0f)

    // Jepitan bawah 36 dp masih bisa melebihi seperempat viewport di panel yang
    // sangat pendek, dan di situ baris aktif terdorong ke tengah. Seperempat
    // tinggi jadi batas terakhir.
    val jangkar = (tinggi * LYRICS_ANCHOR_FRACTION)
        .coerceIn(LYRICS_ANCHOR_MIN_DP, LYRICS_ANCHOR_MAX_DP)
        .coerceAtMost(tinggi * 0.25f)

    val ekor = (tinggi * LYRICS_TRAIL_FRACTION)
        .coerceIn(LYRICS_TRAIL_MIN_DP, LYRICS_TRAIL_MAX_DP)
        // Ruang bawah tidak boleh melebihi viewport itu sendiri, kalau tidak
        // daftar lirik pendek jadi tidak bisa di-scroll ke mana-mana.
        .coerceAtMost(tinggi)

    return LyricsPadding(
        anchorDp = jangkar,
        leadingDp = jangkar + LYRICS_LEAD_EXTRA_DP,
        trailingDp = ekor,
    )
}
