package com.tglabs.spotivibe.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsMetricsTest {

    /** Viewport lirik portrait di HP penguji: 834 dp layar minus header + transport. */
    private val portrait = 600f

    /** Panel kanan landscape di HP penguji: 375 dp layar minus padding + transport. */
    private val landscape = 285f

    /** Baris lirik dua baris teks pada font default, kira-kira. */
    private val tinggiBaris = LYRICS_TYPICAL_LINE_DP

    /** Posisi PUSAT baris aktif sebagai pecahan tinggi viewport. */
    private fun pusatRelatif(viewport: Float): Float =
        (lyricsPadding(viewport).anchorDp + tinggiBaris / 2f) / viewport

    // ── Baris aktif di tengah ────────────────────────────────────

    /**
     * Permintaan langsung dari pemakaian di perangkat: baris aktif duduk terlalu
     * ke bawah di landscape dan seharusnya di tengah layar.
     */
    @Test
    fun `baris aktif mendarat dekat tengah di landscape`() {
        val pusat = pusatRelatif(landscape)
        assertTrue("pusat di $pusat, harusnya dekat 0,5", pusat in 0.42f..0.58f)
    }

    @Test
    fun `baris aktif mendarat dekat tengah di portrait`() {
        val pusat = pusatRelatif(portrait)
        assertTrue("pusat di $pusat, harusnya dekat 0,5", pusat in 0.42f..0.58f)
    }

    /**
     * Keputusan yang diambil bersama: tengah berlaku untuk KEDUA orientasi, bukan
     * landscape saja. Tanpa test ini tidak ada yang menahan salah satunya
     * bergeser sendiri nanti.
     */
    @Test
    fun `portrait dan landscape menaruh pusat baris di titik yang sama`() {
        assertEquals(
            "yang harus identik adalah PUSAT baris, bukan pecahan jangkar",
            pusatRelatif(portrait),
            pusatRelatif(landscape),
            0.001f,
        )
    }

    /**
     * Pecahan tetap tidak bisa menengahkan dua tinggi viewport sekaligus: 0,44
     * memberi 49% di portrait tapi 55% di landscape. Menurunkan jangkar dari
     * tinggi baris yang tepat 50% di mana pun.
     */
    @Test
    fun `pusat baris tepat di tengah pada tinggi viewport apa pun`() {
        listOf(100f, 200f, 285f, 400f, 600f, 900f, 1400f).forEach { h ->
            assertEquals("h=$h", 0.5f, pusatRelatif(h), 0.001f)
        }
    }

    /**
     * Jangkar di bawah tengah membuat "baris aktif" berada di paruh bawah layar
     * dan lirik berikutnya nyaris tidak terlihat.
     */
    @Test
    fun `jangkar tidak pernah melewati tengah`() {
        listOf(0f, 50f, 120f, 285f, 600f, 1200f, 5000f).forEach { h ->
            val a = lyricsPadding(h).anchorDp
            assertTrue("h=$h jangkar=$a melewati tengah", a <= h * 0.5f + 0.01f)
        }
    }

    // ── Ruang atas dan bawah ─────────────────────────────────────

    /**
     * Baris PERTAMA cuma bisa naik sampai batas scroll atas. Ruang atas yang
     * kurang membuat bait pembuka duduk lebih tinggi dari bait lainnya.
     */
    @Test
    fun `baris pertama bisa mencapai jangkar`() {
        listOf(120f, 285f, 600f, 1200f).forEach { h ->
            val p = lyricsPadding(h)
            assertTrue(
                "h=$h ruang atas ${p.leadingDp} < jangkar ${p.anchorDp}",
                p.leadingDp >= p.anchorDp,
            )
        }
    }

    /**
     * Baris TERAKHIR cuma bisa turun sampai batas scroll bawah. Ruang bawah yang
     * kurang membuat bait penutup melorot ke bawah tengah satu per satu
     * menjelang lagu habis. Ini yang membuat nilai tetap 120 dp lama tidak bisa
     * dipakai lagi: di viewport 600 dp dibutuhkan lebih dari dua kali lipatnya.
     */
    @Test
    fun `baris terakhir bisa mencapai jangkar`() {
        listOf(120f, 285f, 600f, 1200f).forEach { h ->
            val p = lyricsPadding(h)
            val dibutuhkan = h - p.anchorDp - tinggiBaris
            assertTrue("h=$h ruang bawah ${p.trailingDp} < $dibutuhkan", p.trailingDp >= dibutuhkan)
        }
    }

    @Test
    fun `nilai tetap 120 dp lama tidak akan cukup di portrait`() {
        val p = lyricsPadding(portrait)
        assertTrue(
            "ruang bawah ${p.trailingDp} harus jauh di atas 120 dp yang lama",
            p.trailingDp > 120f * 2,
        )
    }

    // ── Ketahanan ────────────────────────────────────────────────

    @Test
    fun `viewport nol tidak menghasilkan nilai negatif atau NaN`() {
        val p = lyricsPadding(0f)
        listOf(p.anchorDp, p.leadingDp, p.trailingDp).forEach {
            assertTrue("nilai $it", it >= 0f && !it.isNaN())
        }
    }

    @Test
    fun `viewport negatif diperlakukan seperti nol`() {
        assertEquals(lyricsPadding(0f), lyricsPadding(-500f))
    }

    @Test
    fun `viewport lebih besar tidak pernah menghasilkan jangkar lebih kecil`() {
        var sebelumnya = -1f
        listOf(0f, 120f, 285f, 600f, 1200f, 5000f).forEach { h ->
            val a = lyricsPadding(h).anchorDp
            assertTrue("mundur di h=$h", a >= sebelumnya)
            sebelumnya = a
        }
    }

    @Test
    fun `deterministik`() {
        repeat(5) { assertEquals(lyricsPadding(landscape), lyricsPadding(landscape)) }
    }

    // ── Indeks scroll ────────────────────────────────────────────

    /**
     * REGRESI: `scrollToItem` memakai indeks LazyColumn, dan Spacer atas memakai
     * indeks 0. Memasukkan `activeIndex` mentah membuat baris SEBELUMNYA yang
     * duduk di jangkar. Terlihat sebagai "baris aktif terlalu ke bawah" di
     * landscape, dan nyaris tak terlihat di portrait yang tingginya dua kali.
     */
    @Test
    fun `indeks scroll melewati Spacer atas`() {
        assertEquals(1, lyricsScrollIndex(0))
        assertEquals(6, lyricsScrollIndex(5))
        assertNotEquals("indeks mentah adalah bug-nya", 5, lyricsScrollIndex(5))
    }

    @Test
    fun `indeks scroll tidak pernah negatif`() {
        assertTrue(lyricsScrollIndex(-1) >= 0)
        assertTrue(lyricsScrollIndex(-100) >= 0)
    }

    @Test
    fun `indeks scroll naik satu-satu`() {
        (0..20).forEach { i ->
            assertEquals(lyricsScrollIndex(i) + 1, lyricsScrollIndex(i + 1))
        }
    }

    // ── Ukuran romanisasi ────────────────────────────────────────

    /**
     * Permintaan langsung: romanisasi terlalu kecil, buat 3/4 ukuran lirik
     * aslinya.
     */
    @Test
    fun `romanisasi tiga perempat ukuran liriknya`() {
        assertEquals(22.5f, romanizationSizeSp(30f), 0.01f)
        assertEquals(16.5f, romanizationSizeSp(22f), 0.01f)
        assertEquals(30f, romanizationSizeSp(40f), 0.01f)
    }

    /**
     * REGRESI: gaya lama berukuran TETAP, jadi menaikkan ukuran font lirik di
     * Settings tidak berpengaruh sama sekali pada romanisasi dan ia makin
     * tenggelam di baris aktif.
     */
    @Test
    fun `romanisasi ikut membesar saat lirik dibesarkan`() {
        val kecil = romanizationSizeSp(20f)
        val besar = romanizationSizeSp(40f)
        assertTrue("harus ikut membesar, $kecil -> $besar", besar > kecil)
        assertEquals("perbandingannya harus tetap", 2f, besar / kecil, 0.01f)
    }

    @Test
    fun `romanisasi punya batas bawah supaya tetap terbaca`() {
        assertEquals(ROMANIZATION_MIN_SP, romanizationSizeSp(1f), 0.01f)
        assertEquals(ROMANIZATION_MIN_SP, romanizationSizeSp(0f), 0.01f)
        assertTrue(romanizationSizeSp(-10f) >= ROMANIZATION_MIN_SP)
    }

    @Test
    fun `romanisasi tidak pernah lebih besar dari liriknya`() {
        listOf(12f, 20f, 30f, 44f, 60f).forEach { ukuran ->
            assertTrue("ukuran=$ukuran", romanizationSizeSp(ukuran) <= ukuran)
        }
    }
}
