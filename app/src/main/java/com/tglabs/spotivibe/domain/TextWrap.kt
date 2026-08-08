package com.tglabs.spotivibe.domain

/**
 * Word-wrap yang sadar CJK.
 *
 * Versi lama memecah teks dengan `text.split(' ')`. Bahasa Jepang dan Mandarin
 * tidak memakai spasi antar kata, jadi satu baris lirik jadi SATU "kata"
 * raksasa yang tidak pernah dipotong, lalu digambar keluar dari kanvas. Dua
 * dari tiga bahasa yang jadi alasan app ini ada menghasilkan share card yang
 * terpotong. Korea kebetulan aman karena memakai spasi.
 *
 * Pengukuran di-inject lewat [measure] supaya seluruh logika ini bisa diuji di
 * JVM tanpa `android.graphics.Paint`.
 */

/** Rentang yang boleh dipotong di antara tiap karakternya. */
private fun Char.isCjk(): Boolean =
    this in '぀'..'ヿ' ||   // kana
    this in '㐀'..'䶿' ||   // ideograf ekstensi A
    this in '一'..'鿿' ||   // ideograf CJK
    this in '가'..'힯' ||   // suku kata hangul
    this in '＀'..'｠' ||   // fullwidth
    this in '　'..'〿'      // tanda baca CJK

/**
 * Karakter yang tidak boleh berada di AWAL baris.
 *
 * Ini kinsoku shori dasar. Tanpa ini, baris baru bisa dimulai dengan koma atau
 * kurung tutup, yang di teks Jepang terlihat jelas salah. Chouonpu dan kana
 * kecil ikut masuk karena keduanya melanjutkan bunyi karakter sebelumnya.
 */
private const val NO_START = "、。，．！？：；）」』】〉》〕｝’”・…ー" +
    "ゃゅょっゎァィゥェォッャュョヮ,.!?:;)]}"

/**
 * Pecah [text] jadi atom-atom yang tidak boleh dibelah. Menyambung semua atom
 * kembali menghasilkan [text] persis.
 *
 * - Karakter CJK: satu atom per karakter, karena batas barisnya memang boleh di
 *   antara hampir semua pasangan.
 * - Kata Latin: satu atom utuh.
 * - Deretan spasi: satu atom tersendiri, supaya baris baru tidak diawali spasi.
 */
internal fun textAtoms(text: String): List<String> {
    val atoms = mutableListOf<String>()
    val buf = StringBuilder()

    fun flush() {
        if (buf.isNotEmpty()) {
            atoms.add(buf.toString())
            buf.clear()
        }
    }

    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c == ' ' -> {
                flush()
                val start = i
                while (i < text.length && text[i] == ' ') i++
                atoms.add(text.substring(start, i))
                continue
            }
            c.isCjk() -> {
                flush()
                atoms.add(c.toString())
            }
            else -> buf.append(c)
        }
        i++
    }
    flush()
    return atoms
}

/**
 * Bungkus [text] jadi beberapa baris yang masing-masing muat dalam [maxWidth].
 *
 * @param measure lebar render sebuah string. Di produksi ini `Paint.measureText`,
 *   yang mengukur advance width. JANGAN pakai `Paint.getTextBounds`: itu
 *   mengembalikan kotak ketat di sekitar goresan glyph dan mengabaikan side
 *   bearing serta spasi tepi, jadi keputusan layoutnya meleset.
 *
 * Atom tunggal yang lebih lebar dari [maxWidth] dipaksa dipotong per karakter,
 * supaya kata Latin yang sangat panjang pun tidak pernah meluber.
 */
fun wrapText(text: String, maxWidth: Float, measure: (String) -> Float): List<String> {
    if (text.isEmpty()) return listOf("")
    if (maxWidth <= 0f) return listOf(text)

    val lines = mutableListOf<String>()
    var current = StringBuilder()

    fun commit() {
        val s = current.toString().trimEnd()
        if (s.isNotEmpty()) lines.add(s)
        current = StringBuilder()
    }

    for (atom in textAtoms(text)) {
        // Baris baru tidak pernah diawali spasi.
        if (current.isEmpty() && atom.isBlank()) continue

        if (measure(current.toString() + atom) <= maxWidth) {
            current.append(atom)
            continue
        }

        // Tidak muat. Kalau atom ini dilarang mengawali baris, biarkan tetap di
        // baris sekarang walau sedikit meluber; itu lebih baik daripada koma
        // atau kurung tutup yang menggantung di awal baris berikutnya.
        if (current.isNotEmpty() && atom.length == 1 && atom[0] in NO_START) {
            current.append(atom)
            continue
        }

        commit()

        if (atom.isBlank()) continue

        if (measure(atom) <= maxWidth) {
            current.append(atom)
        } else {
            // Atom tunggal kelebaran: potong paksa per karakter.
            for (ch in atom) {
                if (current.isNotEmpty() && measure(current.toString() + ch) > maxWidth) {
                    commit()
                }
                current.append(ch)
            }
        }
    }
    commit()

    return if (lines.isEmpty()) listOf("") else lines
}

/**
 * Potong [text] supaya muat dalam [maxWidth], tambahkan ellipsis kalau perlu.
 * Memakai [measure] yang sama dengan [wrapText].
 */
fun truncateText(text: String, maxWidth: Float, measure: (String) -> Float): String {
    if (measure(text) <= maxWidth) return text
    var cut = text.length
    while (cut > 0) {
        cut--
        val attempt = text.substring(0, cut).trimEnd() + "…"
        if (measure(attempt) <= maxWidth) return attempt
    }
    return "…"
}
