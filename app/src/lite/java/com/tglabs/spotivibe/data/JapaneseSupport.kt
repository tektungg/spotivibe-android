package com.tglabs.spotivibe.data

/**
 * Varian `lite`: tanpa dukungan Jepang, dan tanpa kamus 12,71 MB yang
 * dibutuhkannya.
 *
 * Tidak ada import kuromoji di sini, dan itu bukan kelalaian melainkan seluruh
 * inti variannya. APK-nya turun dari 15,75 MB jadi sekitar 3 MB.
 *
 * Yang HILANG cuma Jepang. Korea dan Mandarin tetap jalan penuh: Hangul dibaca
 * per suku kata tanpa kamus, dan tabel pinyin4j cuma 0,21 MB jadi tidak layak
 * dibuang.
 *
 * `createJapaneseTokenizer` mengembalikan null, bukan implementasi yang
 * mengembalikan teks apa adanya. Bedanya penting: null membuat lapisan di
 * atasnya tahu Jepang tidak didukung, sehingga tombol romanisasi tidak
 * ditampilkan untuk lagu berbahasa Jepang. Implementasi kosong akan membuat
 * tombolnya ada tapi tidak melakukan apa pun saat ditekan.
 */
const val SUPPORTS_JAPANESE = false

fun createJapaneseTokenizer(): JapaneseTokenizer? = null

/**
 * Jepang dikeluarkan. Diturunkan dari [ROMANIZABLE_ALL] alih-alih diketik
 * ulang, supaya script yang ditambahkan nanti otomatis ikut didukung di sini
 * tanpa ada yang perlu ingat memperbarui dua daftar.
 */
fun supportedScripts(): Set<com.tglabs.spotivibe.domain.Script> =
    com.tglabs.spotivibe.domain.ROMANIZABLE_ALL - com.tglabs.spotivibe.domain.Script.JA
