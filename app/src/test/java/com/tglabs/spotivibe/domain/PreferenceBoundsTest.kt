package com.tglabs.spotivibe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferenceBoundsTest {

    // ── Ukuran font ──────────────────────────────────────────────

    @Test
    fun `nilai di dalam rentang dibiarkan`() {
        assertEquals(24, clampFontSize(24))
        assertEquals(FONT_SIZE_MIN, clampFontSize(FONT_SIZE_MIN))
        assertEquals(FONT_SIZE_MAX, clampFontSize(FONT_SIZE_MAX))
    }

    @Test
    fun `nilai di luar rentang dijepit`() {
        assertEquals(FONT_SIZE_MIN, clampFontSize(1))
        assertEquals(FONT_SIZE_MIN, clampFontSize(-999))
        assertEquals(FONT_SIZE_MAX, clampFontSize(999))
    }

    @Test
    fun `belum pernah diset memakai nilai bawaan`() {
        assertEquals(FONT_SIZE_DEFAULT, clampFontSize(null))
        assertTrue(
            "bawaan harus di dalam rentangnya sendiri",
            FONT_SIZE_DEFAULT in FONT_SIZE_MIN..FONT_SIZE_MAX,
        )
    }

    // ── Offset lirik ─────────────────────────────────────────────

    @Test
    fun `offset negatif dan positif sama-sama sah`() {
        assertEquals(-500, clampLyricsOffsetMs(-500))
        assertEquals(500, clampLyricsOffsetMs(500))
    }

    @Test
    fun `offset di luar rentang dijepit di kedua arah`() {
        assertEquals(LYRICS_OFFSET_MIN_MS, clampLyricsOffsetMs(-99999))
        assertEquals(LYRICS_OFFSET_MAX_MS, clampLyricsOffsetMs(99999))
    }

    @Test
    fun `offset bawaan itu nol`() {
        assertEquals(0, clampLyricsOffsetMs(null))
        assertEquals(0, LYRICS_OFFSET_DEFAULT_MS)
    }

    // ── Jarak baris ──────────────────────────────────────────────

    @Test
    fun `jarak baris nol sah`() {
        assertEquals(0, clampLineSpacing(0))
    }

    @Test
    fun `jarak baris dijepit`() {
        assertEquals(LINE_SPACING_MIN, clampLineSpacing(-10))
        assertEquals(LINE_SPACING_MAX, clampLineSpacing(500))
    }

    @Test
    fun `jarak baris bawaan di dalam rentang`() {
        assertEquals(LINE_SPACING_DEFAULT, clampLineSpacing(null))
        assertTrue(LINE_SPACING_DEFAULT in LINE_SPACING_MIN..LINE_SPACING_MAX)
    }

    // ── Sisi baca dan sisi tulis harus sepakat ───────────────────

    /**
     * REGRESI STRUKTURAL: angka batas yang sama dulu ditulis ulang di delapan
     * tempat, sekali di sisi baca dan sekali di sisi tulis untuk tiap
     * preferensi. Sisi yang tidak sepakat menghasilkan kegagalan paling licin
     * dari semuanya: nilai di luar batas bisa TERSIMPAN, lalu akibatnya baru
     * terasa jauh kemudian di layar yang tidak ada hubungannya dengan tempat
     * ia diset.
     *
     * Menjepit dua kali harus sama hasilnya dengan menjepit sekali. Itu yang
     * membuktikan kedua sisi memakai fungsi yang sama.
     */
    @Test
    fun `menjepit ulang tidak mengubah apa pun`() {
        listOf(-9999, -1, 0, 5, 17, 30, 56, 99, 9999).forEach { n ->
            assertEquals("font $n", clampFontSize(n), clampFontSize(clampFontSize(n)))
            assertEquals("offset $n", clampLyricsOffsetMs(n), clampLyricsOffsetMs(clampLyricsOffsetMs(n)))
            assertEquals("jarak $n", clampLineSpacing(n), clampLineSpacing(clampLineSpacing(n)))
        }
    }

    @Test
    fun `hasil selalu di dalam rentang untuk masukan apa pun`() {
        listOf(null, Int.MIN_VALUE, -1, 0, 100, Int.MAX_VALUE).forEach { n ->
            assertTrue("font $n", clampFontSize(n) in FONT_SIZE_MIN..FONT_SIZE_MAX)
            assertTrue("offset $n", clampLyricsOffsetMs(n) in LYRICS_OFFSET_MIN_MS..LYRICS_OFFSET_MAX_MS)
            assertTrue("jarak $n", clampLineSpacing(n) in LINE_SPACING_MIN..LINE_SPACING_MAX)
        }
    }

    // ── Penambah ukuran font ─────────────────────────────────────

    @Test
    fun `menaikkan dan menurunkan bekerja dari nilai tersimpan`() {
        assertEquals(22, bumpFontSize(20, 2))
        assertEquals(18, bumpFontSize(20, -2))
    }

    @Test
    fun `menaikkan berhenti di batas atas`() {
        assertEquals(FONT_SIZE_MAX, bumpFontSize(FONT_SIZE_MAX, 10))
        assertEquals(FONT_SIZE_MAX, bumpFontSize(50, 100))
    }

    @Test
    fun `menurunkan berhenti di batas bawah`() {
        assertEquals(FONT_SIZE_MIN, bumpFontSize(FONT_SIZE_MIN, -10))
        assertEquals(FONT_SIZE_MIN, bumpFontSize(14, -100))
    }

    @Test
    fun `menaikkan dari belum pernah diset mulai dari bawaan`() {
        assertEquals(FONT_SIZE_DEFAULT + 2, bumpFontSize(null, 2))
    }

    /**
     * Nilai rusak dari versi lama atau dari penyuntingan manual harus ditarik
     * kembali ke rentang yang sah lebih dulu. Tanpa itu ia bergeser dari titik
     * yang salah dan tidak pernah kembali.
     */
    @Test
    fun `nilai tersimpan yang rusak ditarik ke rentang lebih dulu`() {
        assertEquals(FONT_SIZE_MAX, bumpFontSize(9999, 1))
        assertEquals(FONT_SIZE_MIN + 1, bumpFontSize(-9999, 1))
    }

    @Test
    fun `delta nol menormalkan tanpa mengubah`() {
        assertEquals(24, bumpFontSize(24, 0))
        assertEquals(FONT_SIZE_MAX, bumpFontSize(9999, 0))
    }

    @Test
    fun `deterministik`() {
        repeat(5) { assertEquals(bumpFontSize(20, 2), bumpFontSize(20, 2)) }
    }
}
