package com.tglabs.spotivibe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class WordMotionTest {

    // ── Spline ───────────────────────────────────────────────────

    @Test
    fun `spline melewati setiap titik kunci`() {
        val xs = floatArrayOf(0f, 0.15f, 0.6f, 1f)
        val ys = floatArrayOf(0f, 1f, 1f, 0f)
        val s = NaturalCubicSpline(xs, ys)
        xs.indices.forEach { assertEquals(ys[it], s.at(xs[it]), 1e-5f) }
    }

    @Test
    fun `spline dua titik adalah garis lurus`() {
        val s = NaturalCubicSpline(floatArrayOf(0f, 1f), floatArrayOf(2f, 4f))
        assertEquals(3f, s.at(0.5f), 1e-5f)
    }

    @Test
    fun `spline dijepit di luar rentang`() {
        val s = NaturalCubicSpline(floatArrayOf(0f, 0.7f, 1f), floatArrayOf(0.95f, 1.0505f, 1f))
        assertEquals(0.95f, s.at(-3f), 1e-6f)
        assertEquals(1f, s.at(9f), 1e-6f)
    }

    @Test
    fun `spline mulus tanpa lompatan`() {
        val s = NaturalCubicSpline(floatArrayOf(0f, 0.7f, 1f), floatArrayOf(0.95f, 1.0505f, 1f))
        var prev = s.at(0f)
        for (i in 1..100) {
            val cur = s.at(i / 100f)
            assertTrue("lompatan di ${i / 100f}: $prev -> $cur", kotlin.math.abs(cur - prev) < 0.01f)
            prev = cur
        }
    }

    // ── Gerak kata ───────────────────────────────────────────────

    @Test
    fun `gerak di titik kunci`() {
        assertEquals(0.95f, wordMotion(0f).scale, 1e-4f)
        assertEquals(1.0505f, wordMotion(0.7f).scale, 1e-4f)
        assertEquals(1f, wordMotion(1f).scale, 1e-4f)
        assertEquals(0f, wordMotion(0f).glow, 1e-4f)
        assertEquals(1f, wordMotion(0.3f).glow, 0.1f)
        assertEquals(0f, wordMotion(1f).glow, 1e-4f)
        assertEquals(0f, wordMotion(1f).liftEm, 1e-4f)
    }

    @Test
    fun `cahaya selalu di antara nol dan satu`() {
        for (i in -10..110) assertTrue(wordMotion(i / 100f).glow in 0f..1f)
    }

    @Test
    fun `tepi sapuan dari kiri luar sampai kanan`() {
        assertEquals(-WIPE_EDGE, wipePosition(0f), 1e-6f)
        assertEquals(1f, wipePosition(1f), 1e-6f)
        assertEquals(1f, wipePosition(5f), 1e-6f)
    }

    // ── Progress dan potongan ────────────────────────────────────

    @Test
    fun `progress potongan terjepit`() {
        val g = KaraokeGlyph("a", 1_000, 2_000, 0)
        assertEquals(0f, glyphProgress(g, 500), 0f)
        assertEquals(0.5f, glyphProgress(g, 1_500), 1e-6f)
        assertEquals(1f, glyphProgress(g, 9_000), 0f)
        assertEquals(1f, glyphProgress(KaraokeGlyph("a", 1_000, 1_000, 0), 1_001), 0f)
    }

    @Test
    fun `akhir kata dari endMs lalu kata berikutnya lalu akhir baris`() {
        val words = listOf(SyncedWord(0, "a ", endMs = 300), SyncedWord(400, "b "), SyncedWord(900, "c"))
        assertEquals(300L, wordEndMs(words, 0, null))
        assertEquals(900L, wordEndMs(words, 1, null))
        assertEquals(1_500L, wordEndMs(words, 2, lineEndMs = 1_500))
        assertEquals(1_400L, wordEndMs(words, 2, lineEndMs = null))
    }

    @Test
    fun `suku kata tanpa spasi satu kata dan spasi menutup kata`() {
        val words = listOf(SyncedWord(0, "I've ", 200), SyncedWord(200, "e", 400), SyncedWord(400, "nough", 800))
        val glyphs = karaokeGlyphs(words, null)
        assertEquals(listOf(0, 1, 1), glyphs.map { it.wordIndex })
        assertEquals("I've enough", glyphs.joinToString("") { it.text })
    }

    @Test
    fun `kata panjang dipecah per huruf dengan waktu rata`() {
        val glyphs = karaokeGlyphs(listOf(SyncedWord(1_000, "baby ", 2_200)), null)
        assertEquals(listOf("b", "a", "b", "y "), glyphs.map { it.text })
        assertEquals(listOf(1_000L, 1_300L, 1_600L, 1_900L), glyphs.map { it.startMs })
        assertEquals(2_200L, glyphs.last().endMs)
        assertTrue(glyphs.all { it.wordIndex == 0 })
    }

    @Test
    fun `kata pendek dan satu huruf tidak dipecah`() {
        assertEquals(1, karaokeGlyphs(listOf(SyncedWord(0, "hey", 999)), null).size)
        assertEquals(1, karaokeGlyphs(listOf(SyncedWord(0, "愛", 3_000)), null).size)
    }

    @Test
    fun `emoji tidak terbelah saat dipecah`() {
        val glyphs = karaokeGlyphs(listOf(SyncedWord(0, "a😀", 2_000)), null)
        assertEquals(listOf("a", "😀"), glyphs.map { it.text })
    }

    // ── Pegas ────────────────────────────────────────────────────

    @Test
    fun `pegas frekuensi jadi kekakuan dua pi f kuadrat`() {
        val p = springFromFrequency(1f, 0.5f)
        assertEquals((2 * PI * 2 * PI).toFloat(), p.stiffness, 1e-3f)
        assertEquals(0.5f, p.dampingRatio, 0f)
    }
}
