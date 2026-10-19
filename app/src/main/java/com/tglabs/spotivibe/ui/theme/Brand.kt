package com.tglabs.spotivibe.ui.theme

/**
 * Tagline Spotivibe, satu sumber untuk semua tempat yang menampilkannya:
 * colophon AccessScreen, footer About di Settings, dan pojok share card.
 *
 * Tagline sebelumnya peninggalan versi extension VS Code; app ini menjual
 * lirik tersinkron untuk player apa pun, jadi taglinenya ikut berganti.
 * Video promo (`promo/src/data/brand.ts`) memakai string yang sama, dan
 * `promo/test/brand.test.mjs` gagal bila keduanya berbeda.
 */
object Brand {
    const val TAGLINE = "Every player. Every line."

    /** Bentuk eyebrow mono: "EVERY PLAYER · EVERY LINE". */
    val TAGLINE_EYEBROW: String = eyebrowOf(TAGLINE)

    /** Huruf besar, kalimat dipisah " · ", titik terakhir dibuang. */
    fun eyebrowOf(tagline: String): String =
        tagline.trim().removeSuffix(".").uppercase().replace(". ", " · ")
}
