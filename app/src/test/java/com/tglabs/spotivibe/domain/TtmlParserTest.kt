package com.tglabs.spotivibe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixture di `src/test/resources/ttml/` meniru bentuk asli AMLL (`itunes:timing`)
 * dan lrc.red (`lrc:timing`) yang diambil 2026-10-15, dengan teks buatan.
 */
class TtmlParserTest {

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/ttml/$name")) { "fixture $name tidak ada" }.readText()

    private fun parse(name: String): ParsedTtml =
        requireNotNull(parseTtml(fixture(name))) { "$name gagal diparse" }

    // ── Timing per kata ──────────────────────────────────────────

    @Test
    fun `suku kata satu kata tergabung dan spasi jadi batas kata`() {
        val first = parse("word_duet_bg.ttml").lines.first()
        assertEquals("I've been enough", first.text)
        assertEquals(listOf("I've ", "been ", "e", "nough"), first.words.map { it.text })
        assertEquals(listOf(1_000L, 1_400L, 1_800L, 2_200L), first.words.map { it.timeMs })
        assertEquals(3_000L, first.words.last().endMs)
        assertEquals(1_000L, first.timeMs)
        assertEquals(3_000L, first.endMs)
    }

    @Test
    fun `terjemahan bawaan tidak bocor ke teks`() {
        val first = parse("word_duet_bg.ttml").lines.first()
        assertFalse(first.text.contains("我"))
        assertFalse(first.words.any { it.text.contains("我") })
    }

    @Test
    fun `vokal latar dipisah dari baris utama`() {
        val first = parse("word_duet_bg.ttml").lines.first()
        val bg = requireNotNull(first.background) { "vokal latar hilang" }
        assertEquals("(Oh yeah)", bg.text)
        assertEquals(listOf("(Oh ", "yeah)"), bg.words.map { it.text })
        assertFalse(first.text.contains("Oh"))
    }

    @Test
    fun `baris yang isinya cuma vokal latar naik jadi baris utama`() {
        val last = parse("word_duet_bg.ttml").lines.last()
        assertEquals("(Only background)", last.text)
        assertNull(last.background)
        assertFalse(last.isInterlude)
    }

    // ── Duet ─────────────────────────────────────────────────────

    @Test
    fun `agent kedua rata kanan dan grup tidak`() {
        val byText = parse("word_duet_bg.ttml").lines.associateBy { it.text }
        assertFalse(byText.getValue("I've been enough").oppositeAligned)
        assertTrue(byText.getValue("Second voice").oppositeAligned)
        assertFalse(byText.getValue("Together").oppositeAligned)
    }

    // ── Jeda dan dokumen ─────────────────────────────────────────

    @Test
    fun `jeda panjang ditandai baris kosong dan id posisional`() {
        val lines = parse("word_duet_bg.ttml").lines
        assertEquals(listOf(1_000L, 3_200L, 5_000L, 12_000L, 14_500L), lines.map { it.timeMs })
        assertTrue(lines[2].isInterlude)
        assertEquals(lines.indices.toList(), lines.map { it.id })
    }

    @Test
    fun `durasi dibaca dari body`() {
        val doc = parse("word_duet_bg.ttml")
        assertEquals(194_571L, doc.durationMs)
        assertEquals(194.571, doc.durationSec!!, 0.0001)
        assertTrue(doc.isSynced)
    }

    @Test
    fun `timing per baris dari lrc red`() {
        val doc = parse("line_lrcred.ttml")
        assertTrue(doc.isSynced)
        assertEquals(200_500L, doc.durationMs)
        assertEquals(listOf("First line here", "Untimed spans"), doc.lines.map { it.text })
        assertEquals(listOf(10_250L, 12_500L), doc.lines.map { it.timeMs })
        assertTrue(doc.lines.all { it.words.isEmpty() })
    }

    @Test
    fun `timing None jadi teks tanpa waktu`() {
        val doc = parse("untimed.ttml")
        assertFalse(doc.isSynced)
        assertEquals("Plain one\nPlain two", doc.plainText())
    }

    // ── Input buruk ──────────────────────────────────────────────

    @Test
    fun `dokumen dengan DOCTYPE ditolak`() {
        assertNull(parseTtml(fixture("doctype.ttml")))
    }

    @Test
    fun `xml rusak bukan tt atau tanpa baris menghasilkan null`() {
        assertNull(parseTtml("<tt><body><p begin=\"1\">"))
        assertNull(parseTtml("<html><p>halo</p></html>"))
        assertNull(parseTtml("<tt xmlns=\"http://www.w3.org/ns/ttml\"><body/></tt>"))
        assertNull(parseTtml(""))
    }

    // ── Format waktu ─────────────────────────────────────────────

    @Test
    fun `format waktu yang dikenal`() {
        assertEquals(27_173L, parseTtmlTime("27.173"))
        assertEquals(194_571L, parseTtmlTime("3:14.571"))
        assertEquals(3_723_500L, parseTtmlTime("1:02:03.5"))
        assertEquals(12_300L, parseTtmlTime("12.3s"))
        assertEquals(450L, parseTtmlTime("450ms"))
        assertEquals(10_250L, parseTtmlTime(" 00:00:10.250 "))
    }

    @Test
    fun `format waktu rusak null`() {
        assertNull(parseTtmlTime(""))
        assertNull(parseTtmlTime("abc"))
        assertNull(parseTtmlTime("1:2:3:4"))
    }
}
