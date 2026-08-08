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

/**
 * Detect script per baris. Priority: kana (JA), hangul (KO), CJK (ZH), else LATIN.
 * Catatan: kanji standalone tanpa kana terbaca sebagai ZH karena ambiguous.
 * Untuk lagu Jepang biasanya ada hiragana/katakana yang trigger JA dengan benar.
 */
fun detectScript(text: String): Script {
    if (text.any { it in '぀'..'ゟ' || it in '゠'..'ヿ' }) return Script.JA
    if (text.any { it in '가'..'힯' }) return Script.KO
    if (text.any { it in '一'..'鿿' }) return Script.ZH
    return Script.LATIN
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
