package com.tglabs.spotivibe.domain

import androidx.compose.runtime.Immutable

/** Satu kata dalam enhanced LRC — punya timestamp sendiri */
@Immutable
data class SyncedWord(
    val timeMs: Long,
    val text: String,
)

/**
 * Satu baris synced lyric.
 *
 * [id] adalah identitas baris di dalam satu lagu: posisinya setelah diurutkan.
 * Ini ADA karena `timeMs` bukan identitas. File LRC boleh, dan sering, punya
 * beberapa baris dengan timestamp identik (reff yang ditandai berulang, baris
 * kosong penanda jeda). Dulu `timeMs` dipakai sebagai key di `LazyColumn`, dan
 * Compose melempar `IllegalArgumentException: Key was already used` begitu
 * ketemu lagu seperti itu. `timeMs` juga dipakai sebagai key map romaji dan set
 * seleksi, jadi dua baris berbeda saling menimpa romaji satu sama lain, dan
 * memilih satu baris ikut memilih kembarannya.
 *
 * [words] terisi kalau LRCLIB punya format enhanced
 * (`[mm:ss.xx]<mm:ss.xx>kata<mm:ss.xx>kata`). Kalau kosong, highlight jatuh
 * balik ke per-baris.
 */
@Immutable
data class SyncedLine(
    val id: Int,
    val timeMs: Long,
    val text: String,
    val words: List<SyncedWord> = emptyList(),
)

/** Hasil fetch lyrics dari LRCLIB */
@Immutable
data class LyricsResult(
    val trackId: String,
    val synced: List<SyncedLine>?,
    val plain: String?,
) {
    val hasContent: Boolean get() = !synced.isNullOrEmpty() || !plain.isNullOrBlank()
}

/** Script detection result */
enum class Script { JA, KO, ZH, LATIN }

/** Hiragana U+3040..U+309F atau katakana U+30A0..U+30FF. Eksklusif milik Jepang. */
private fun String.hasKana(): Boolean = any { it in '぀'..'ゟ' || it in '゠'..'ヿ' }

/**
 * Suku kata Hangul U+AC00..U+D7A3. Eksklusif milik Korea.
 *
 * Blok jamo terpisah (U+1100.., U+3130..) SENGAJA tidak dihitung, karena
 * [romanizeKorean] cuma bisa memecah suku kata prakomposisi. Mendeteksi jamo di
 * sini akan melabeli lagu sebagai KO padahal romanisasinya tidak bisa
 * mengerjakannya.
 */
private fun String.hasHangul(): Boolean = any { it in '가'..'힯' }

/** Ideograf CJK U+4E00..U+9FFF. AMBIGU: dipakai Jepang (kanji) maupun Mandarin. */
private fun String.hasHan(): Boolean = any { it in '一'..'鿿' }

/**
 * Deteksi script satu BARIS.
 *
 * Hati-hati memakai ini untuk memilih mesin romanisasi. Baris yang isinya kanji
 * saja tanpa kana akan terbaca [Script.ZH], padahal di lagu Jepang itu tetap
 * kanji. Untuk memilih mesin, pakai [detectDocumentScript] yang melihat seluruh
 * lagu. Fungsi ini cocoknya cuma untuk pertanyaan yang tidak ambigu, misalnya
 * "apakah baris ini murni Latin".
 */
fun detectScript(text: String): Script = when {
    text.hasKana() -> Script.JA
    text.hasHangul() -> Script.KO
    text.hasHan() -> Script.ZH
    else -> Script.LATIN
}

/**
 * Deteksi script untuk SATU LAGU, dari seluruh barisnya.
 *
 * Ini ada karena keputusan per baris salah untuk lagu Jepang. Kana itu eksklusif
 * milik Jepang dan hangul eksklusif milik Korea, tapi kanji dipakai bersama
 * Jepang dan Mandarin. Jadi baris Jepang yang kebetulan isinya kanji saja akan
 * dinilai Mandarin dan keluar sebagai pinyin, di tengah lagu yang baris-baris
 * lainnya keluar sebagai romaji. Satu lagu bisa bercampur dua sistem.
 *
 * Keputusannya: kehadiran kana atau hangul DI MANA PUN dalam lagu itu
 * menentukan, karena keduanya tidak mungkin muncul di bahasa lain. Kanji baru
 * berarti Mandarin kalau di seluruh lagu tidak ada kana maupun hangul.
 *
 * Kalau kana dan hangul sama-sama muncul (lagu campuran, sangat jarang), yang
 * barisnya lebih banyak menang. Seri dimenangkan kana. Sewenang-wenang, tapi
 * deterministik.
 */
fun detectDocumentScript(lines: List<String>): Script {
    var kanaLines = 0
    var hangulLines = 0
    var hanLines = 0
    for (line in lines) {
        if (line.hasKana()) kanaLines++
        if (line.hasHangul()) hangulLines++
        if (line.hasHan()) hanLines++
    }
    return when {
        kanaLines == 0 && hangulLines == 0 && hanLines == 0 -> Script.LATIN
        kanaLines > 0 && kanaLines >= hangulLines -> Script.JA
        hangulLines > 0 -> Script.KO
        else -> Script.ZH
    }
}

/** Cek apakah ada minimal 1 baris yang non-Latin → bisa di-romanize */
fun hasRomanizableText(lines: List<String>): Boolean =
    lines.any { detectScript(it) != Script.LATIN }

/** Tag metadata LRC yang bukan lirik: `[ar:...]`, `[ti:...]`, dan kawan-kawan. */
private val METADATA_TAG = Regex(
    """^\s*\[(ar|ti|al|au|by|length|re|ve|tool|offset|#)\s*:""",
    RegexOption.IGNORE_CASE,
)

/** `[mm:ss.xx]` atau `[mm:ss:xx]`. Menit boleh lebih dari 99 untuk lagu panjang. */
private val TIME_TAG = Regex("""\[(\d{1,3}):([0-5]?\d)(?:[.:](\d{1,3}))?]""")

/** `<mm:ss.xx>kata` untuk enhanced LRC. */
private val WORD_TAG = Regex("""<(\d{1,3}):([0-5]?\d)(?:[.:](\d{1,3}))?>([^<]*)""")

/**
 * Parse format LRC jadi daftar [SyncedLine], terurut naik dan ber-[SyncedLine.id]
 * unik.
 *
 * Yang didukung:
 *
 * - Standar: `[01:23.45]Halo dunia`
 * - **Multi-timestamp**: `[00:12.00][01:30.00]Reff`. Satu baris teks yang
 *   dipakai di beberapa waktu, dan ini format LRC yang sah. Versi lama memakai
 *   `Regex.find` yang cuma mengambil kecocokan PERTAMA, jadi timestamp kedua
 *   hilang (reff tidak menyala lagi) DAN literal `[01:30.00]` ikut terbawa ke
 *   teks yang ditampilkan ke user.
 * - Enhanced per-kata: `[01:23.45]<01:23.45>Halo <01:23.70>dunia`
 * - Tag metadata (`[ar:]`, `[ti:]`, dan lain-lain) dilewati, tidak jadi lirik.
 *
 * Yang TIDAK didukung: tag `[offset:...]`. Tag itu dikenali dan dilewati supaya
 * tidak bocor ke teks, tapi nilainya tidak diterapkan. Arah tandanya berbeda
 * antar pemutar (sebagian menganggap positif mempercepat, sebagian
 * memperlambat), dan menebak salah membuat sync lebih buruk daripada tidak
 * mendukung sama sekali. Koreksi manual user di Settings sudah menutupi
 * kebutuhan ini dan berlaku di semua permukaan.
 *
 * Baris tanpa timestamp dilewati. Urutan file dipertahankan untuk timestamp
 * yang kembar, karena `sortedBy` di Kotlin stabil.
 */
fun parseLrc(raw: String): List<SyncedLine> {
    val collected = mutableListOf<Pair<Long, SyncedLine>>()

    raw.lineSequence().forEach { rawLine ->
        if (rawLine.isBlank()) return@forEach
        if (METADATA_TAG.containsMatchIn(rawLine)) return@forEach

        // Kumpulkan SEMUA timestamp di awal baris, bukan cuma yang pertama.
        val times = mutableListOf<Long>()
        var cursor = 0
        while (cursor < rawLine.length) {
            while (cursor < rawLine.length && rawLine[cursor].isWhitespace()) cursor++
            val m = TIME_TAG.matchAt(rawLine, cursor) ?: break
            times += parseTimestamp(m.groupValues[1], m.groupValues[2], m.groupValues[3])
            cursor = m.range.last + 1
        }
        if (times.isEmpty()) return@forEach

        val content = rawLine.substring(cursor)
        val wordMatches = WORD_TAG.findAll(content).toList()

        val text: String
        val words: List<SyncedWord>
        if (wordMatches.isEmpty()) {
            text = content.trim()
            words = emptyList()
        } else {
            val parsed = wordMatches.mapNotNull { w ->
                val wText = w.groupValues[4]
                if (wText.isBlank()) null else SyncedWord(
                    timeMs = parseTimestamp(w.groupValues[1], w.groupValues[2], w.groupValues[3]),
                    text = wText,
                )
            }
            text = parsed.joinToString("") { it.text }.trim()
            // Timing per-kata itu absolut, jadi tidak bisa benar untuk lebih
            // dari satu kemunculan. Kalau barisnya multi-timestamp, turunkan ke
            // highlight per-baris daripada menyala di waktu yang salah.
            words = if (times.size == 1) parsed else emptyList()
        }

        times.forEach { t ->
            collected += t to SyncedLine(id = 0, timeMs = t, text = text, words = words)
        }
    }

    // id ditetapkan SETELAH pengurutan, jadi nilainya stabil dan unik walaupun
    // timeMs kembar.
    return collected
        .sortedBy { it.first }
        .mapIndexed { index, (_, line) -> line.copy(id = index) }
}

private fun parseTimestamp(minStr: String, secStr: String, fracStr: String): Long {
    val min = minStr.toIntOrNull() ?: 0
    val sec = secStr.toIntOrNull() ?: 0
    val frac = fracStr.ifBlank { "0" }.padEnd(3, '0').take(3).toIntOrNull() ?: 0
    return (min * 60L + sec) * 1000L + frac
}

/**
 * Tetapkan ulang [SyncedLine.id] berdasarkan posisi. Dipakai saat memuat dari
 * cache disk, yang tidak menyimpan id karena nilainya memang turunan posisi.
 */
fun List<SyncedLine>.withPositionalIds(): List<SyncedLine> =
    mapIndexed { index, line -> if (line.id == index) line else line.copy(id = index) }
