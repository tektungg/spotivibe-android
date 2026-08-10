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
 */
fun lyricsPadding(viewportHeightDp: Float): LyricsPadding {
    val tinggi = viewportHeightDp.coerceAtLeast(0f)

    // Setengah sisa ruang setelah baris itu sendiri. Ini yang membuat PUSAT
    // baris mendarat di 50% pada tinggi viewport mana pun, bukan cuma pada satu
    // yang kebetulan dipakai untuk menyetel angkanya.
    val jangkar = ((tinggi - LYRICS_TYPICAL_LINE_DP) / 2f).coerceIn(0f, tinggi * 0.5f)

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
