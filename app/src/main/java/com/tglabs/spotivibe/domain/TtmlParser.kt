package com.tglabs.spotivibe.domain

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

/**
 * Parser TTML (Timed Text Markup Language), format lirik Apple Music yang juga
 * dipakai AMLL dan lrc.red.
 *
 * Kenapa TTML dan bukan cukup LRC: TTML membawa tiga hal yang tidak bisa
 * ditulis di LRC, dan ketiganya terlihat di layar:
 *
 * - **Suku kata bertimer** dengan waktu mulai DAN akhir (`<span begin end>`),
 *   jadi karaoke bisa mengikuti "e-nough" per suku kata.
 * - **Vokal latar** (`ttm:role="x-bg"`), yang dinyanyikan di belakang baris
 *   utama dan selama ini ikut tercampur ke baris itu atau hilang sama sekali.
 * - **Duet** lewat `ttm:agent`: baris penyanyi kedua digambar di sisi kanan.
 *
 * Memakai `javax.xml.parsers` (DOM), bukan `XmlPullParser`. Keduanya ada di
 * Android, tapi `XmlPullParser` dari `android.util.Xml` hanya stub di unit test
 * JVM, jadi parser ini tidak akan bisa diuji. Dokumen dengan DOCTYPE ditolak
 * sebelum diparse: lirik tidak butuh DTD, dan menolaknya menutup XXE dan
 * entity expansion di semua implementasi parser sekaligus.
 */

/** Hasil parse satu dokumen TTML. */
data class ParsedTtml(
    /** Baris terurut, id posisional, jeda panjang sudah ditandai baris kosong. */
    val lines: List<SyncedLine>,
    /** false untuk `timing="None"`: teksnya ada, waktunya tidak bisa dipercaya. */
    val isSynced: Boolean,
    /** Durasi dari `<body dur>`, untuk dibandingkan dengan durasi lagu. */
    val durationMs: Long?,
)

/** Durasi lirik dalam detik, bentuk yang dipakai pemilihan kandidat. */
val ParsedTtml.durationSec: Double? get() = durationMs?.let { it / MS_PER_SECOND }

/** Teks per baris, untuk lirik yang tidak bertimer. */
fun ParsedTtml.plainText(): String? =
    lines.filterNot { it.isInterlude }.joinToString("\n") { it.text }.takeIf { it.isNotBlank() }

/**
 * Parse [xml]. Null kalau bukan TTML, rusak, memuat DOCTYPE, atau tidak punya
 * satu baris pun yang berisi.
 */
fun parseTtml(xml: String): ParsedTtml? {
    if (DOCTYPE.containsMatchIn(xml)) return null
    val root = parseXmlRoot(xml) ?: return null
    if (root.localName != "tt") return null

    val timing = root.attrByLocalName("timing")?.lowercase()
    val agents = AgentRoles.from(root)
    val paragraphs = root.descendants("p")
    val lines = paragraphs.mapNotNull { parseParagraph(it, agents) }
        .sortedBy { it.timeMs }
    if (lines.isEmpty()) return null

    val hasTiming = timing != "none" && paragraphs.any { it.hasAttribute("begin") }
    return ParsedTtml(
        lines = if (hasTiming) insertInterludeMarkers(lines) else lines.withPositionalIds(),
        isSynced = hasTiming,
        durationMs = root.descendants("body").firstOrNull()?.getAttribute("dur")?.let(::parseTtmlTime),
    )
}

private fun parseXmlRoot(xml: String): Element? = runCatching {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isExpandEntityReferences = false
        isIgnoringComments = true
    }
    factory.newDocumentBuilder().parse(InputSource(StringReader(xml))).documentElement
}.getOrNull()

/**
 * Siapa penyanyi utama dan siapa yang berduet dengannya.
 *
 * Agent utama = agent yang PERTAMA dipakai sebuah baris, bukan yang pertama
 * dideklarasikan: sebagian file mendeklarasikan grup (`v1000`) lebih dulu.
 * Agent bertipe `group` ("semua penyanyi") tidak pernah dianggap pihak kedua,
 * karena paduan suara bukan lawan duet.
 */
private class AgentRoles(private val groupIds: Set<String>, private val primary: String?) {

    fun isOpposite(agent: String?): Boolean =
        agent != null && primary != null && agent != primary && agent !in groupIds

    companion object {
        fun from(root: Element): AgentRoles {
            val groups = root.descendants("agent")
                .filter { it.getAttribute("type") == "group" }
                .mapNotNull { it.attrByLocalName("id") }
                .toSet()
            val primary = root.descendants("p")
                .mapNotNull { it.attrByLocalName("agent") }
                .firstOrNull { it !in groups }
            return AgentRoles(groups, primary)
        }
    }
}

/** Suku kata satu baris, utama dan latar, dikumpulkan sambil berjalan. */
private class SyllableCollector {
    val lead = mutableListOf<SyncedWord>()
    val background = mutableListOf<SyncedWord>()
    val looseText = StringBuilder()
}

private fun parseParagraph(p: Element, agents: AgentRoles): SyncedLine? {
    val collector = SyllableCollector()
    collectSyllables(p, collector.lead, collector)
    val begin = p.getAttribute("begin").takeIf { it.isNotEmpty() }?.let(::parseTtmlTime)
        ?: collector.lead.firstOrNull()?.timeMs ?: 0L
    val end = p.getAttribute("end").takeIf { it.isNotEmpty() }?.let(::parseTtmlTime)

    var lead = collector.lead.toList()
    var background = collector.background.toList().takeIf { it.isNotEmpty() }
    // Baris yang isinya cuma vokal latar: latarnya naik jadi baris utama.
    // Kalau tidak, barisnya kosong dan salah terbaca sebagai penanda jeda.
    if (lead.isEmpty() && background != null) {
        lead = background
        background = null
    }
    val text = lead.joinToString("") { it.text }.trim().ifEmpty { collector.looseText.toString().replace(WHITESPACE_RUN, " ").trim() }
    if (text.isEmpty()) return null
    return SyncedLine(
        id = 0,
        timeMs = begin,
        text = text,
        words = lead.trimLastSpace(),
        endMs = end ?: lead.lastOrNull()?.endMs,
        oppositeAligned = agents.isOpposite(p.attrByLocalName("agent")),
        background = background?.let { BackgroundVocal(it.joinToString("") { w -> w.text }.trim(), it.trimLastSpace()) },
    )
}

/**
 * Jalan menelusuri anak [parent]. Span bertimer jadi suku kata di [target];
 * whitespace di antara span adalah batas kata, jadi spasinya ditempel ke suku
 * kata sebelumnya. Span `x-bg` diturunkan ke daftar latar; terjemahan dan
 * romanisasi bawaan dilewati karena app punya romanisasinya sendiri.
 */
private fun collectSyllables(parent: Element, target: MutableList<SyncedWord>, collector: SyllableCollector) {
    for (node in parent.childNodes.asList()) {
        when {
            node.nodeType == Node.TEXT_NODE -> onText(node.textContent, target, collector)
            node !is Element || node.localName != "span" -> Unit
            else -> onSpan(node, target, collector)
        }
    }
}

private fun onText(raw: String, target: MutableList<SyncedWord>, collector: SyllableCollector) {
    when {
        raw.isNotBlank() -> collector.looseText.append(raw)
        target.isNotEmpty() -> markWordBoundary(target)
        // Lirik bertimer per baris: spasi di antara span tanpa waktu tetap spasi.
        else -> collector.looseText.append(' ')
    }
}

private fun onSpan(span: Element, target: MutableList<SyncedWord>, collector: SyllableCollector) {
    when (span.attrByLocalName("role")) {
        null -> Unit
        ROLE_BACKGROUND -> return collectSyllables(span, collector.background, collector)
        else -> return
    }
    val begin = span.getAttribute("begin").takeIf { it.isNotEmpty() }?.let(::parseTtmlTime)
    if (begin == null) {
        // Span tanpa waktu (lirik bertimer per baris): isinya teks biasa.
        collectSyllables(span, target, collector)
        return
    }
    val text = span.textContent
    if (text.isEmpty()) return
    val end = span.getAttribute("end").takeIf { it.isNotEmpty() }?.let(::parseTtmlTime)
    target += SyncedWord(timeMs = begin, text = text, endMs = end)
}

private fun markWordBoundary(target: MutableList<SyncedWord>) {
    val last = target.lastOrNull() ?: return
    if (!last.text.endsWith(" ")) target[target.lastIndex] = last.copy(text = last.text + " ")
}

/** Suku kata terakhir baris tidak butuh spasi penutup. */
private fun List<SyncedWord>.trimLastSpace(): List<SyncedWord> {
    val last = lastOrNull() ?: return this
    return dropLast(1) + last.copy(text = last.text.trimEnd())
}

/**
 * Waktu TTML jadi milidetik. Bentuk yang dikenal: `27.173`, `3:14.571`,
 * `1:02:03.5`, `12.3s`, `450ms`. Null kalau tidak terbaca.
 */
fun parseTtmlTime(raw: String): Long? {
    val s = raw.trim()
    if (s.isEmpty()) return null
    if (s.endsWith("ms")) return s.dropLast(2).toDoubleOrNull()?.toLong()
    if (s.endsWith("s")) return s.dropLast(1).toDoubleOrNull()?.let { (it * MS_PER_SECOND).toLong() }
    val parts = s.split(':')
    if (parts.size > MAX_CLOCK_PARTS) return null
    var seconds = 0.0
    for (part in parts) {
        val value = part.toDoubleOrNull() ?: return null
        seconds = seconds * SECONDS_PER_MINUTE + value
    }
    return (seconds * MS_PER_SECOND).toLong()
}

private fun Element.attrByLocalName(name: String): String? {
    val attrs = attributes
    for (i in 0 until attrs.length) {
        val attr = attrs.item(i)
        val local = attr.localName ?: attr.nodeName.substringAfter(':')
        if (local == name) return attr.nodeValue
    }
    return null
}

private fun Element.descendants(localName: String): List<Element> =
    getElementsByTagNameNS("*", localName).asList().filterIsInstance<Element>()

private fun org.w3c.dom.NodeList.asList(): List<Node> = (0 until length).map { item(it) }

private val DOCTYPE = Regex("<!DOCTYPE", RegexOption.IGNORE_CASE)
private val WHITESPACE_RUN = Regex("""\s+""")
private const val ROLE_BACKGROUND = "x-bg"
private const val MS_PER_SECOND = 1000.0
private const val SECONDS_PER_MINUTE = 60.0

/** `jam:menit:detik`. Lebih dari itu bukan waktu, melainkan data rusak. */
private const val MAX_CLOCK_PARTS = 3
