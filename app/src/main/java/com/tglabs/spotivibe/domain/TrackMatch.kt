package com.tglabs.spotivibe.domain

import java.text.Normalizer

/**
 * Pencocokan judul dan artis untuk sumber yang dicari lewat teks.
 *
 * Pencarian lrc.red untuk "Blinding Lights" mengembalikan versi asli, versi
 * remix, versi "(Mixed)" dari kompilasi, dan cover oleh artis lain, semuanya
 * berdurasi mirip. Durasi saja tidak cukup membedakannya; judul dan artis harus
 * cocok juga.
 */

/**
 * Judul tanpa tanda kurung, akhiran " - ...", aksen, dan tanda baca, huruf
 * kecil. "Blinding Lights (Mixed)" dan "Blinding Lights - Remastered 2020"
 * sama-sama jadi "blinding lights".
 */
fun normalizeTitle(raw: String): String =
    foldText(raw.replace(BRACKETED, " ").substringBefore(" - "))

/** Artis dalam bentuk yang sama, tanpa membuang apa pun selain tanda baca. */
fun normalizeArtist(raw: String): String = foldText(raw)

private fun foldText(raw: String): String =
    Normalizer.normalize(raw, Normalizer.Form.NFKD)
        .replace(COMBINING_MARKS, "")
        .lowercase()
        .replace(NON_WORD, " ")
        .trim()
        .replace(SPACES, " ")

/**
 * Judul kandidat menunjuk lagu yang sama.
 *
 * Judul yang sama setelah dinormalisasi cocok. Judul kandidat yang memuat kata
 * versi lain ("remix", "live", "acoustic", ...) yang tidak ada di judul lagunya
 * ditolak walau sisanya sama: itu rekaman berbeda dengan timing berbeda.
 */
fun titlesMatch(trackTitle: String, candidateTitle: String): Boolean {
    if (normalizeTitle(trackTitle) != normalizeTitle(candidateTitle)) return false
    val track = foldText(trackTitle).split(' ').toSet()
    val candidate = foldText(candidateTitle).split(' ').toSet()
    return VERSION_WORDS.none { it in candidate && it !in track }
}

/**
 * Artis kandidat memuat artis utama lagunya, atau sebaliknya. Kolaborasi
 * ditulis berbeda di tiap sumber ("A & B", "A, B", "A feat. B"), jadi yang
 * dituntut cuma artis pertama.
 */
fun artistsMatch(trackArtist: String, candidateArtist: String): Boolean {
    val primary = normalizeArtist(trackArtist.split(ARTIST_SEPARATOR).first())
    val candidate = normalizeArtist(candidateArtist)
    if (primary.isEmpty() || candidate.isEmpty()) return false
    return candidate.contains(primary) || primary.contains(candidate)
}

private val BRACKETED = Regex("""[(\[][^)\]]*[)\]]""")
private val COMBINING_MARKS = Regex("""\p{Mn}+""")
private val NON_WORD = Regex("""[^\p{L}\p{N}]+""")
private val SPACES = Regex(""" +""")
private val ARTIST_SEPARATOR = Regex(""",|&| feat\.? | ft\.? | x """, RegexOption.IGNORE_CASE)
private val VERSION_WORDS = setOf("remix", "live", "acoustic", "instrumental", "karaoke", "cover", "sped", "slowed")
