package com.tglabs.spotivibe.ui.screen

/**
 * Ukuran adaptif untuk panel kiri di layout dua kolom.
 *
 * ## Kenapa album dihitung sebagai SISA, bukan sebagai pecahan
 *
 * Versi sebelumnya memberi album `content * 0.45` dan memesan 90 dp untuk
 * transport. Dua-duanya tebakan, dan dua-duanya meleset. Dihitung dari
 * sumbernya, panel kiri di HP landscape butuh 385,6 dp padahal cuma punya
 * 295 dp:
 *
 * ```
 * eyebrow + jarak              37,0
 * album                        92,2
 * jarak + judul + artis        64,4
 * meta ALBUM | DURATION        60,0
 * hairline + jarak             13,0
 * transport                   119,0   <- model memesan 90
 *                            ------
 *                             385,6   tersedia 295, TERPOTONG 90,6
 * ```
 *
 * Akibatnya baris tombol play/next terpotong habis dan slider terpotong
 * separuh. Tidak ada yang melaporkannya karena yang hilang ada di bawah lipatan
 * layar, dan tidak ada test yang bisa menangkapnya selama angka-angka itu cuma
 * hidup sebagai literal yang tersebar di composable.
 *
 * Sekarang setiap elemen yang WAJIB terlihat dijumlahkan eksplisit, dan album
 * mendapat sisanya. Dengan begitu "transport terpotong" bukan lagi sesuatu yang
 * bisa terjadi diam-diam: kalau ruangnya tidak cukup, yang mengecil album.
 *
 * Fungsi murni dengan satuan dp sebagai [Float] supaya bisa diuji tanpa Compose.
 * Perhitungan tata letak yang salah tidak pernah gagal compile.
 */
data class LandscapePaneMetrics(
    val albumDp: Float,
    val titleSp: Float,
    val artistSp: Float,
    /** Jarak antara album dan blok judul. */
    val gapDp: Float,
    /** Padding panel. Dirapatkan di panel pendek supaya album dapat ruang. */
    val padDp: Float,
    /** Rapatkan jarak vertikal transport. Ukuran tombol tidak ikut mengecil. */
    val compactTransport: Boolean,
)

/** Batas atas album, sama dengan nilai desain tablet aslinya. */
const val ALBUM_MAX_DP = 360f

/** Di atas tinggi ini panel dianggap lega dan memakai ukuran tablet penuh. */
const val ROOMY_PANE_DP = 600f

const val PAD_ROOMY_DP = 40f
const val PAD_TIGHT_DP = 24f

/**
 * Tinggi transport, dijumlahkan dari Transport.kt, bukan dikira-kira.
 *
 * Penuh: hairline 1 + padding 16 + slider 14 + jarak 16 + tombol 52 + padding 20.
 * Rapat: padding 16/16/20 dipangkas jadi 8/8/8. Ukuran tombol TIDAK berubah;
 * 44 dp sudah di bawah ambang target sentuh Material 48 dp.
 */
const val TRANSPORT_FULL_DP = 119f
const val TRANSPORT_COMPACT_DP = 91f

/**
 * Jarak antara blok judul dan transport.
 *
 * Menggantikan HairlineRule + jarak yang dulu ada di sini. Garis itu dibuang
 * karena Transport sudah menggambar garisnya SENDIRI di baris pertama, jadi
 * keduanya menghasilkan dua garis bertumpuk tepat di atas seeker.
 */
const val TITLE_TRANSPORT_GAP_DP = 12f

/** Judul boleh membungkus jadi dua baris, jadi ruangnya dipesan untuk dua. */
const val TITLE_MAX_LINES = 2f
const val TITLE_LINE_FACTOR = 1.1f
const val ARTIST_LINE_FACTOR = 1.3f

/** Jarak antara judul dan artis. */
const val TITLE_ARTIST_GAP_DP = 4f

/**
 * @param paneWidthDp lebar panel SEBELUM padding.
 * @param paneHeightDp tinggi panel SEBELUM padding. Padding ditentukan di sini,
 *   bukan oleh pemanggil, supaya tidak ada dua tempat yang harus sepakat soal
 *   berapa ruang yang tersisa.
 */
fun landscapePaneMetrics(
    paneWidthDp: Float,
    paneHeightDp: Float,
): LandscapePaneMetrics {
    val lebar = paneWidthDp.coerceAtLeast(0f)
    val tinggi = paneHeightDp.coerceAtLeast(0f)
    val lega = tinggi >= ROOMY_PANE_DP

    val pad = if (lega) PAD_ROOMY_DP else PAD_TIGHT_DP
    val isiLebar = (lebar - pad * 2).coerceAtLeast(0f)
    val isiTinggi = (tinggi - pad * 2).coerceAtLeast(0f)

    val titleSp = if (lega) 42f else 24f
    val artistSp = if (lega) 18f else 14f
    val gapDp = if (lega) 32f else 16f
    val rapat = !lega
    val transport = if (rapat) TRANSPORT_COMPACT_DP else TRANSPORT_FULL_DP

    // Semua yang harus tetap terlihat. Album TIDAK ada di sini; ia dapat sisanya.
    val blokTeks = titleSp * TITLE_LINE_FACTOR * TITLE_MAX_LINES +
        TITLE_ARTIST_GAP_DP +
        artistSp * ARTIST_LINE_FACTOR
    val wajib = gapDp + blokTeks + TITLE_TRANSPORT_GAP_DP + transport

    val sisa = (isiTinggi - wajib).coerceAtLeast(0f)
    // Tanpa lantai minimum. Lantai apa pun akan melanggar invarian di atas dan
    // mengembalikan transport ke keadaan terpotong, yaitu bug yang justru
    // sedang diperbaiki. Album yang mengecil sampai nol lebih baik daripada
    // tombol play yang hilang.
    val album = minOf(isiLebar, ALBUM_MAX_DP, sisa)

    return LandscapePaneMetrics(
        albumDp = album,
        titleSp = titleSp,
        artistSp = artistSp,
        gapDp = gapDp,
        padDp = pad,
        compactTransport = rapat,
    )
}
