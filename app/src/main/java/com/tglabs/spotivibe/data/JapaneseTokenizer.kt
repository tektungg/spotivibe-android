package com.tglabs.spotivibe.data

/**
 * Pembaca token Jepang, disediakan berbeda per varian build.
 *
 * ## Kenapa harus di belakang antarmuka
 *
 * Kamus IPADIC milik kuromoji mengisi **12,71 dari 15,75 MB** APK, yaitu 81%
 * ukurannya untuk satu bahasa. Varian `lite` membuangnya, dan itu hanya bisa
 * dilakukan kalau tidak ada satu pun kode di `main` yang menyebut
 * `com.atilika.kuromoji`. Satu import saja sudah cukup membuat varian tanpa
 * dependensinya gagal compile.
 *
 * Kanji tidak bisa diromanisasi tanpa kamus morfologis. Tidak ada versi ringan
 * dari kemampuan itu, jadi pilihannya memang memuat kamusnya atau tidak
 * mendukung Jepang sama sekali. Korea dan Mandarin tetap ada di KEDUA varian:
 * Hangul dibaca per suku kata tanpa kamus, dan tabel pinyin4j cuma 0,21 MB.
 *
 * Implementasi beserta [createJapaneseTokenizer] dan [SUPPORTS_JAPANESE] ada di
 * `src/full/` dan `src/lite/`, bukan di sini. Keduanya wajib menyediakan nama
 * yang sama; kalau salah satu lupa, variannya gagal compile alih-alih diam-diam
 * kehilangan kemampuan.
 */
interface JapaneseTokenizer {

    /** Satu token beserta bacaannya dalam katakana, kalau kamusnya mengenalinya. */
    data class Token(
        val surface: String,
        /**
         * Bacaan katakana, atau null untuk token yang tidak dikenal seperti
         * simbol dan huruf Latin. Pemanggil memakai [surface] apa adanya di
         * kasus itu.
         */
        val readingKatakana: String?,
    )

    fun tokenize(text: String): List<Token>
}
