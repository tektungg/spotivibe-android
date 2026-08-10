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
 *
 * Ukuran album TIDAK dikembalikan. Sebelumnya iya, dihitung dari perkiraan judul
 * dua baris, dan judul satu baris menyisakan 26 dp yang tidak dipakai siapa pun
 * lalu muncul sebagai lubang antara album dan judul. Sekarang album memakai
 * `weight(1f)` sehingga menerima persis apa pun yang tersisa setelah judul,
 * artis, dan transport DIUKUR. Perkiraan yang tidak bisa tepat lebih baik
 * dihapus daripada disetel ulang.
 */
fun landscapePaneMetrics(
    paneWidthDp: Float,
    paneHeightDp: Float,
): LandscapePaneMetrics {
    val tinggi = paneHeightDp.coerceAtLeast(0f)
    val lega = tinggi >= ROOMY_PANE_DP

    return LandscapePaneMetrics(
        titleSp = if (lega) 42f else 24f,
        artistSp = if (lega) 18f else 14f,
        gapDp = if (lega) 32f else 16f,
        padDp = if (lega) PAD_ROOMY_DP else PAD_TIGHT_DP,
        compactTransport = !lega,
    )
}

/**
 * Tinggi semua yang berada DI BAWAH album dan wajib terlihat.
 *
 * Album mendapat sisanya. Kalau angka ini melebihi tinggi isi panel, `weight(1f)`
 * memberi album nol dan yang terpotong justru transport, yaitu bug yang sudah
 * pernah terjadi: baris tombol play/next hilang seluruhnya di HP landscape
 * karena model memesan 90 dp untuk transport yang sebenarnya 119 dp.
 */
fun landscapeFixedBelowAlbumDp(m: LandscapePaneMetrics): Float =
    m.gapDp +
        m.titleSp * TITLE_LINE_FACTOR * TITLE_MAX_LINES +
        TITLE_ARTIST_GAP_DP +
        m.artistSp * ARTIST_LINE_FACTOR +
        TITLE_TRANSPORT_GAP_DP +
        (if (m.compactTransport) TRANSPORT_COMPACT_DP else TRANSPORT_FULL_DP)

/** Tinggi isi panel setelah padding. */
fun landscapeContentHeightDp(paneHeightDp: Float, m: LandscapePaneMetrics): Float =
    (paneHeightDp.coerceAtLeast(0f) - m.padDp * 2).coerceAtLeast(0f)
