package com.tglabs.spotivibe.data

import android.util.Log
import com.atilika.kuromoji.ipadic.Tokenizer

/**
 * Varian `full`: Jepang didukung lewat kuromoji IPADIC.
 *
 * Ini SATU-SATUNYA berkas di seluruh project yang menyebut
 * `com.atilika.kuromoji`. Kalau ada yang kedua, varian `lite` gagal compile,
 * dan itu memang yang diinginkan: kegagalannya jelas dan langsung, bukan APK
 * `lite` yang diam-diam tetap membawa kamus 12,71 MB.
 */
const val SUPPORTS_JAPANESE = true

fun createJapaneseTokenizer(): JapaneseTokenizer? = KuromojiTokenizer()

private class KuromojiTokenizer : JapaneseTokenizer {

    /**
     * Dimuat saat pertama dipakai, bukan saat dibuat. Memuat IPADIC makan 1-2
     * detik, dan lagu berbahasa Inggris tidak boleh membayarnya.
     */
    private val tokenizer: Tokenizer by lazy {
        Log.d(TAG, "Memuat kuromoji IPADIC (sekali, ~1-2 detik)…")
        Tokenizer.Builder().build().also { Log.d(TAG, "kuromoji siap") }
    }

    override fun tokenize(text: String): List<JapaneseTokenizer.Token> =
        tokenizer.tokenize(text).map { tok ->
            JapaneseTokenizer.Token(
                surface = tok.surface,
                // Kuromoji memakai "*" untuk menandai bacaan yang tidak ada.
                // Meneruskannya apa adanya akan memunculkan tanda bintang di
                // tengah romaji.
                readingKatakana = tok.reading?.takeIf { it.isNotBlank() && it != "*" },
            )
        }

    private companion object {
        const val TAG = "KuromojiTokenizer"
    }
}

/** Semua script didukung di varian ini. */
fun supportedScripts(): Set<com.tglabs.spotivibe.domain.Script> =
    com.tglabs.spotivibe.domain.ROMANIZABLE_ALL
