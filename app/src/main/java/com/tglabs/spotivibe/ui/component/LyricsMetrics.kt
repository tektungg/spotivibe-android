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
 * Tinggi perkiraan satu baris lirik, untuk lagu berbahasa Inggris pada ukuran
 * font default: dua baris teks.
 *
 * Dibutuhkan karena `scrollToItem` menempatkan ATAS baris di jangkar, sementara
 * yang harus berada di tengah adalah PUSAT baris. Tanpa memperhitungkan tinggi
 * baris, satu pecahan tetap tidak bisa menengahkan viewport portrait dan
 * landscape sekaligus: 0,44 memberi 49% di portrait tapi 55% di landscape,
 * karena tinggi baris yang sama memakan porsi dua kali lebih besar di panel yang
 * tingginya separuh.
 *
 * Bait yang jauh lebih panjang atau ukuran font yang dinaikkan pengguna akan
 * menggeser pusatnya turun sebesar separuh selisihnya. Tinggi baris sungguhan
 * tidak tersedia di lapis ini, dan menariknya ke sini berarti mengukur teks
 * sebelum tata letak. Perkiraan ini meleset jauh lebih kecil daripada pecahan
 * tetap.
 */
const val LYRICS_TYPICAL_LINE_DP = 60f

/** Ukuran font lirik saat nilai acuan di atas diukur. */
const val LYRICS_REF_FONT_SP = 30

/** Jarak antara baris lirik dan romanisasi di bawahnya. */
const val LYRICS_ROMAJI_GAP_DP = 2f

/**
 * Tinggi perkiraan satu baris untuk ukuran font dan keadaan romanisasi tertentu.
 *
 * Nilai tetap 60 dp tidak cukup, dan itu dilaporkan dari perangkat: dengan
 * romanisasi menyala, baris aktif turun sekitar 24 dp dari tengah. Sebabnya
 * setiap baris membawa teks KEDUA di bawahnya, jadi barisnya lebih tinggi
 * sementara jangkar tetap dihitung untuk baris tanpa romanisasi.
 *
 * Ukuran font punya cacat yang sama dan belum sempat dilaporkan: 60 dp diukur
 * pada font 30 sp, jadi menaikkannya ke 56 sp menggeser pusat dengan cara yang
 * persis sama. Keduanya diperbaiki sekaligus di sini.
 *
 * Yang TIDAK dimodelkan: berapa baris teks hasil pembungkusan. Itu butuh
 * mengukur teks sebelum tata letak, dan tidak tersedia di lapis ini. Bait yang
 * jauh lebih panjang dari biasanya masih akan menggeser pusatnya sebesar
 * separuh selisihnya.
 */
fun lyricsTypicalLineDp(fontSizeSp: Int, hasRomanization: Boolean): Float {
    val skala = fontSizeSp.coerceAtLeast(1).toFloat() / LYRICS_REF_FONT_SP
    val lirik = LYRICS_TYPICAL_LINE_DP * skala
    if (!hasRomanization) return lirik
    return lirik + lirik * ROMANIZATION_RATIO + LYRICS_ROMAJI_GAP_DP
}

/**
 * Banyaknya item LazyColumn SEBELUM baris lirik pertama.
 *
 * Spacer atas adalah `item { }` tersendiri, jadi ia memakai indeks 0 dan baris
 * lirik baru mulai dari indeks 1.
 */
const val LYRICS_LEADING_ITEMS = 1

/**
 * Ubah indeks ke dalam `lines` menjadi indeks item LazyColumn.
 *
 * REGRESI: `scrollToItem` memakai indeks LazyColumn, sementara `activeIndex`
 * datang sebagai indeks ke dalam `lines`. Memasukkannya mentah-mentah membuat
 * baris SEBELUMNYA yang duduk di jangkar, dan baris aktif terdorong satu baris
 * ke bawah. Di portrait selisih itu nyaris tidak terlihat; di landscape yang
 * tingginya separuh, satu baris lirik dua baris teks mendorongnya sampai ke
 * bawah tengah layar.
 *
 * Tidak akan pernah gagal compile: keduanya `Int`.
 */
fun lyricsScrollIndex(lineIndex: Int): Int =
    (lineIndex + LYRICS_LEADING_ITEMS).coerceAtLeast(0)

/**
 * @param viewportHeightDp tinggi area lirik yang terlihat.
 * @param chromeAboveDp tinggi apa pun yang berada DI ATAS area lirik di dalam
 *   jendela: header, padding, tombol.
 * @param chromeBelowDp tinggi apa pun yang berada DI BAWAHNYA.
 *
 * Dua parameter terakhir ada karena "tengah viewport" dan "tengah layar"
 * ternyata bukan hal yang sama, dan yang dinilai mata adalah tengah LAYAR.
 *
 * Di panel kanan landscape, chrome-nya timpang berat: 68 dp di atas (padding
 * 32 + baris header berisi ikon 36 dp) tapi cuma 16 dp di bawah. Baris aktif
 * yang duduk tepat di tengah viewport karena itu mendarat 26 dp di bawah tengah
 * layar, yaitu persis (68 - 16) / 2, dan terlihat jelas melenceng.
 *
 * Portrait tidak pernah mengeluh karena chrome-nya nyaris seimbang: header
 * 90 dp di atas, transport 119 dp di bawah, jadi melesetnya 14,5 dp ke ATAS
 * dan tidak terasa. Bug yang sama, cuma sepertiga besarnya dan arah
 * sebaliknya.
 */
fun lyricsPadding(
    viewportHeightDp: Float,
    chromeAboveDp: Float = 0f,
    chromeBelowDp: Float = 0f,
    lineHeightDp: Float = LYRICS_TYPICAL_LINE_DP,
): LyricsPadding {
    val tinggi = viewportHeightDp.coerceAtLeast(0f)
    val tinggiBaris = lineHeightDp.coerceAtLeast(0f)

    // Setengah sisa ruang setelah baris itu sendiri. Ini yang membuat PUSAT
    // baris mendarat di 50% pada tinggi viewport mana pun, bukan cuma pada satu
    // yang kebetulan dipakai untuk menyetel angkanya.
    val tengahViewport = (tinggi - tinggiBaris) / 2f

    // Geser ke atas sebesar setengah kelebihan chrome atas. Setelah koreksi ini
    // pusat baris mendarat di tengah LAYAR, bukan tengah viewport.
    val koreksi = (chromeAboveDp - chromeBelowDp) / 2f

    val jangkar = (tengahViewport - koreksi).coerceIn(0f, tinggi * 0.5f)

    return LyricsPadding(
        anchorDp = jangkar,
        // Persis sebesar jangkar. Baris PERTAMA cuma bisa naik sampai batas
        // scroll atas, jadi tanpa ruang sebesar ini ia tidak akan pernah sampai
        // ke tengah dan bait pembuka duduk lebih tinggi dari bait lainnya.
        leadingDp = jangkar,
        // Sisa viewport di bawah jangkar. Baris TERAKHIR cuma bisa turun sampai
        // batas scroll bawah; kurang dari ini, bait penutup melorot ke bawah
        // tengah satu per satu menjelang lagu habis. Kelebihan sedikit tidak
        // berbahaya karena scrollToItem berhenti di jangkar.
        trailingDp = (tinggi - jangkar).coerceAtLeast(0f),
    )
}

/**
 * Perbandingan ukuran romanisasi terhadap baris liriknya.
 *
 * Sebelumnya romanisasi memakai gaya berukuran TETAP, jadi ia tidak ikut
 * membesar saat pengguna menaikkan ukuran font lirik di Settings, dan di baris
 * aktif yang 30 sp romanisasinya tenggelam. Diikatkan ke ukuran baris supaya
 * perbandingannya tetap sama di semua ukuran font dan di semua state.
 */
const val ROMANIZATION_RATIO = 0.75f

/** Di bawah ini teks latin tidak terbaca lagi di layar ponsel. */
const val ROMANIZATION_MIN_SP = 10f

fun romanizationSizeSp(lyricSizeSp: Float): Float =
    (lyricSizeSp * ROMANIZATION_RATIO).coerceAtLeast(ROMANIZATION_MIN_SP)
