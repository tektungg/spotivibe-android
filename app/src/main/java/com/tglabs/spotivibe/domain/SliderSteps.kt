package com.tglabs.spotivibe.domain

/**
 * Pemetaan nilai setelan ke posisi garis pada slider.
 *
 * ## Kenapa ini ada
 *
 * Slider di Settings menggambar 21 garis tapi melaporkan pecahan KONTINU, lalu
 * pemanggilnya memetakannya ke rentangnya masing-masing. Jumlah nilai yang bisa
 * dipilih karena itu tidak ada hubungannya dengan jumlah garis yang terlihat:
 *
 * ```
 * font size     45 nilai   21 garis
 * sync offset 4001 nilai   21 garis
 * line spacing  21 nilai   21 garis   <- kebetulan cocok
 * ```
 *
 * Akibatnya gagang berhenti di antara garis, dan nilai seperti `+513 ms` muncul
 * di layar padahal tidak ada garis yang mewakilinya. Line spacing terlihat benar
 * hanya karena rentangnya kebetulan 0..20, yaitu 21 nilai, persis sebanyak
 * garisnya.
 *
 * Sekarang slider bekerja dengan INDEKS garis, bukan pecahan. Nilai yang tidak
 * jatuh di garis mana pun tidak bisa lagi terbentuk.
 */

/** Indeks garis terdekat untuk sebuah nilai. */
fun tickIndexOf(value: Int, min: Int, max: Int, tickCount: Int): Int {
    if (tickCount <= 1 || max <= min) return 0
    val rentang = (max - min).toFloat()
    val pecahan = ((value - min) / rentang).coerceIn(0f, 1f)
    return Math.round(pecahan * (tickCount - 1)).coerceIn(0, tickCount - 1)
}

/**
 * Nilai untuk sebuah indeks garis.
 *
 * Dibulatkan, bukan dipotong. Memotong membuat garis terakhir tidak pernah
 * menghasilkan nilai maksimum saat rentangnya tidak habis dibagi.
 */
fun tickValueOf(index: Int, min: Int, max: Int, tickCount: Int): Int {
    if (tickCount <= 1 || max <= min) return min
    val i = index.coerceIn(0, tickCount - 1)
    val rentang = (max - min).toFloat()
    return (min + Math.round(i / (tickCount - 1).toFloat() * rentang)).coerceIn(min, max)
}

/**
 * Indeks garis untuk sebuah posisi sentuh mendatar.
 *
 * @param fraction 0..1 relatif terhadap lebar slider.
 */
fun tickIndexAt(fraction: Float, tickCount: Int): Int {
    if (tickCount <= 1) return 0
    if (fraction.isNaN()) return 0
    return Math.round(fraction.coerceIn(0f, 1f) * (tickCount - 1)).coerceIn(0, tickCount - 1)
}

/**
 * Jumlah garis untuk sebuah rentang dan langkah.
 *
 * Dipakai supaya jumlah garis DITURUNKAN dari rentangnya, bukan diketik sebagai
 * angka lepas yang kebetulan cocok di satu slider dan meleset di dua lainnya.
 */
fun tickCountFor(min: Int, max: Int, step: Int): Int {
    if (step <= 0 || max <= min) return 1
    return (max - min) / step + 1
}

// ── Langkah tiap setelan ─────────────────────────────────────────
//
// Dipilih supaya setiap garis jatuh di bilangan bulat DAN jumlah garisnya tetap
// terbaca. Rentang dibagi habis oleh langkahnya; kalau tidak, garis terakhir
// tidak akan mewakili nilai maksimum.

/** 12..56 sp, langkah 2 -> 23 garis. */
const val FONT_SIZE_STEP = 2

/** -2000..2000 ms, langkah 200 -> 21 garis. */
const val LYRICS_OFFSET_STEP_MS = 200

/** 0..20 dp, langkah 1 -> 21 garis. Sudah benar sejak awal. */
const val LINE_SPACING_STEP = 1
