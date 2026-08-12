package com.tglabs.spotivibe.domain

/**
 * Batas dan nilai bawaan preferensi, satu tempat.
 *
 * Sebelumnya angka yang sama ditulis ulang di DELAPAN tempat di
 * `PreferencesRepository`: sekali di sisi baca, sekali di sisi tulis, dan dua
 * kali lagi di penambah ukuran font. Sisi baca dan sisi tulis yang tidak sepakat
 * menghasilkan kegagalan paling licin dari semuanya, karena nilai di luar batas
 * bisa TERSIMPAN dan baru terasa akibatnya jauh kemudian, di layar yang tidak
 * ada hubungannya dengan tempat ia diset.
 *
 * Nilainya diikat ke kegunaan nyata, bukan dikarang:
 * - font 12..56 sp, di bawah itu tidak terbaca, di atas itu satu baris lirik
 *   sudah memenuhi layar sendirian.
 * - offset -2000..2000 ms, latensi Bluetooth terburuk yang wajar sekitar 300 ms
 *   dan kualitas file LRC jarang meleset lebih dari dua detik.
 * - jarak baris 0..20 dp, di atas itu jumlah baris yang terlihat tinggal dua.
 */

const val FONT_SIZE_MIN = 12
const val FONT_SIZE_MAX = 56
const val FONT_SIZE_DEFAULT = 17

const val LYRICS_OFFSET_MIN_MS = -2000
const val LYRICS_OFFSET_MAX_MS = 2000
const val LYRICS_OFFSET_DEFAULT_MS = 0

const val LINE_SPACING_MIN = 0
const val LINE_SPACING_MAX = 20
const val LINE_SPACING_DEFAULT = 7

fun clampFontSize(nilai: Int?): Int =
    (nilai ?: FONT_SIZE_DEFAULT).coerceIn(FONT_SIZE_MIN, FONT_SIZE_MAX)

fun clampLyricsOffsetMs(nilai: Int?): Int =
    (nilai ?: LYRICS_OFFSET_DEFAULT_MS).coerceIn(LYRICS_OFFSET_MIN_MS, LYRICS_OFFSET_MAX_MS)

fun clampLineSpacing(nilai: Int?): Int =
    (nilai ?: LINE_SPACING_DEFAULT).coerceIn(LINE_SPACING_MIN, LINE_SPACING_MAX)

/**
 * Ukuran font setelah dinaikkan atau diturunkan.
 *
 * Nilai tersimpan ikut dijepit lebih dulu. Tanpa itu, nilai rusak dari versi
 * lama atau dari penyuntingan manual akan bergeser dari titik yang salah dan
 * tidak pernah kembali ke rentang yang sah.
 */
fun bumpFontSize(sekarang: Int?, delta: Int): Int = clampFontSize(clampFontSize(sekarang) + delta)
