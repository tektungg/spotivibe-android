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
