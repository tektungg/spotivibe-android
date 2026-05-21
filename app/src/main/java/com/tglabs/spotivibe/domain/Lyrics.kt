package com.tglabs.spotivibe.domain

import androidx.compose.runtime.Immutable

/** Satu baris synced lyric — timeMs adalah millisecond dari start lagu */
@Immutable
data class SyncedLine(
    val timeMs: Long,
    val text: String,
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
 * Returns sorted by timeMs ascending. Lines tanpa timestamp di-skip.
 */
fun parseLrc(raw: String): List<SyncedLine> {
    val pattern = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?]\s*(.*)""")
    val lines = mutableListOf<SyncedLine>()
    raw.lineSequence().forEach { line ->
        val m = pattern.find(line) ?: return@forEach
        val min = m.groupValues[1].toInt()
        val sec = m.groupValues[2].toInt()
        val frac = m.groupValues[3].ifBlank { "0" }.padEnd(3, '0').take(3).toInt()
        val text = m.groupValues[4].trim()
        lines.add(SyncedLine(timeMs = (min * 60L + sec) * 1000L + frac, text = text))
    }
    return lines.sortedBy { it.timeMs }
}
