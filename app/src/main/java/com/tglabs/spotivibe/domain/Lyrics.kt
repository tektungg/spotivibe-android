package com.tglabs.spotivibe.domain

import androidx.compose.runtime.Immutable

/** Satu kata dalam enhanced LRC — punya timestamp sendiri */
@Immutable
data class SyncedWord(
    val timeMs: Long,
    val text: String,
)

/**
 * Satu baris synced lyric — timeMs adalah millisecond dari start lagu.
 * Kalau LRCLIB punya enhanced format (`[mm:ss.xx]<mm:ss.xx>word<mm:ss.xx>word`),
 * field `words` populated dengan timing per-kata. Else empty list → fallback
 * highlight per-baris seperti biasa.
 */
@Immutable
data class SyncedLine(
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

/**
 * Parse LRC format `[mm:ss.xx]text` → list of SyncedLine.
 *
 * Support 2 format:
 * - Standard: `[01:23.45]Hello world`
 * - Enhanced (per-word): `[01:23.45]<01:23.45>Hello <01:23.70>world`
 *   → parsing kata-kata dengan timestamp masing-masing untuk karaoke per-kata
 *
 * Returns sorted by timeMs ascending. Lines tanpa timestamp di-skip.
 */
fun parseLrc(raw: String): List<SyncedLine> {
    val linePattern = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?]\s*(.*)""")
    val wordPattern = Regex("""<(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?>([^<]*)""")
    val lines = mutableListOf<SyncedLine>()

    raw.lineSequence().forEach { line ->
        val m = linePattern.find(line) ?: return@forEach
        val lineMs = parseTimestamp(m.groupValues[1], m.groupValues[2], m.groupValues[3])
        val rawText = m.groupValues[4]

        // Cek apakah ada word-level timestamps
        val wordMatches = wordPattern.findAll(rawText).toList()
        if (wordMatches.isEmpty()) {
            // Standard LRC, no per-word timing
            lines.add(SyncedLine(timeMs = lineMs, text = rawText.trim()))
            return@forEach
        }

        // Enhanced LRC — extract words
        val words = wordMatches.mapNotNull { w ->
            val wTime = parseTimestamp(w.groupValues[1], w.groupValues[2], w.groupValues[3])
            val wText = w.groupValues[4]
            if (wText.isBlank()) null else SyncedWord(timeMs = wTime, text = wText)
        }
        // Reconstruct line text dari join words (trim whitespace)
        val joinedText = words.joinToString("") { it.text }.trim()
        lines.add(
            SyncedLine(
                timeMs = lineMs,
                text = joinedText,
                words = words,
            )
        )
    }
    return lines.sortedBy { it.timeMs }
}

private fun parseTimestamp(minStr: String, secStr: String, fracStr: String): Long {
    val min = minStr.toIntOrNull() ?: 0
    val sec = secStr.toIntOrNull() ?: 0
    val frac = fracStr.ifBlank { "0" }.padEnd(3, '0').take(3).toIntOrNull() ?: 0
    return (min * 60L + sec) * 1000L + frac
}
