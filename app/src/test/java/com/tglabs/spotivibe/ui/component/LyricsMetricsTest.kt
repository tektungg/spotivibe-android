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

    /** Baris lirik dua baris teks pada font acuan, tanpa romanisasi. */
    private val tinggiBaris = LYRICS_TYPICAL_LINE_DP

    /** Pusat baris sebagai pecahan viewport, untuk tinggi baris tertentu. */
    private fun pusatUntukBaris(viewport: Float, tinggi: Float): Float =
        (lyricsPadding(viewport, lineHeightDp = tinggi).anchorDp + tinggi / 2f) / viewport

    /** Posisi PUSAT baris aktif sebagai pecahan tinggi viewport. */
    private fun pusatRelatif(viewport: Float): Float =
        (lyricsPadding(viewport).anchorDp + tinggiBaris / 2f) / viewport

    /**
     * Posisi PUSAT baris aktif sebagai pecahan tinggi LAYAR. Ini yang dinilai
     * mata, dan ini yang tidak sama dengan tengah viewport begitu chrome atas
     * dan bawah tidak seimbang.
     */
    private fun pusatDiLayar(viewport: Float, atas: Float, bawah: Float): Float {
        val layar = viewport + atas + bawah
        val jangkar = lyricsPadding(viewport, atas, bawah).anchorDp
        return (atas + jangkar + tinggiBaris / 2f) / layar
    }

    /** Panel kanan landscape: padding atas 32 + baris header 36, bawah 16. */
    private val chromeLandscapeAtas = 68f
    private val chromeLandscapeBawah = 16f

    /** Portrait: NPHeader di atas, transport di bawah. */
    private val chromePortraitAtas = 90f
    private val chromePortraitBawah = 119f

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

    // ── Tengah LAYAR, bukan tengah viewport ──────────────────────

    /**
     * REGRESI: dilaporkan dari perangkat, "lirik di landscape masih belum tepat
     * di tengah" padahal portrait sudah.
     *
     * Baris aktif memang SUDAH di tengah viewport. Yang tidak di tengah adalah
     * viewport-nya terhadap layar: panel kanan punya 68 dp chrome di atas
     * (padding 32 + baris header berisi ikon 36) tapi cuma 16 dp di bawah, jadi
     * baris aktif mendarat 26 dp di bawah tengah layar, persis (68 - 16) / 2.
     */
    @Test
    fun `baris aktif di tengah LAYAR walau chrome timpang`() {
        val pusat = pusatDiLayar(291f, chromeLandscapeAtas, chromeLandscapeBawah)
        assertEquals("harus tengah layar", 0.5f, pusat, 0.01f)
    }

    @Test
    fun `mengabaikan chrome membuatnya meleset ke bawah`() {
        val viewport = 291f
        val layar = viewport + chromeLandscapeAtas + chromeLandscapeBawah
        val tanpaKoreksi = lyricsPadding(viewport).anchorDp
        val pusatSalah = (chromeLandscapeAtas + tanpaKoreksi + tinggiBaris / 2f) / layar
        assertTrue("tanpa koreksi harusnya meleset ke bawah, dapat $pusatSalah", pusatSalah > 0.55f)
    }

    /**
     * Portrait chrome-nya nyaris seimbang, jadi bug yang sama cuma meleset
     * 14,5 dp ke ATAS dan tidak pernah dikeluhkan. Koreksinya tetap harus
     * berlaku, dan tidak boleh merusak yang sudah terasa benar.
     */
    @Test
    fun `portrait juga tepat di tengah layar`() {
        val pusat = pusatDiLayar(625f, chromePortraitAtas, chromePortraitBawah)
        assertEquals(0.5f, pusat, 0.01f)
    }

    @Test
    fun `chrome seimbang tidak menggeser apa pun`() {
        val tanpa = lyricsPadding(400f).anchorDp
        val seimbang = lyricsPadding(400f, 50f, 50f).anchorDp
        assertEquals("chrome simetris harus netral", tanpa, seimbang, 0.001f)
    }

    @Test
    fun `chrome ekstrem tidak menghasilkan jangkar negatif atau melewati tengah`() {
        listOf(0f to 500f, 500f to 0f, 1000f to 1f, 1f to 1000f).forEach { (a, b) ->
            val p = lyricsPadding(300f, a, b)
            assertTrue("atas=$a bawah=$b jangkar=${p.anchorDp}", p.anchorDp >= 0f)
            assertTrue("atas=$a bawah=$b jangkar=${p.anchorDp}", p.anchorDp <= 150f + 0.01f)
        }
    }

    @Test
    fun `ruang atas tetap cukup walau chrome timpang`() {
        listOf(0f to 0f, 68f to 16f, 90f to 119f, 200f to 10f).forEach { (a, b) ->
            val p = lyricsPadding(291f, a, b)
            assertTrue("atas=$a bawah=$b", p.leadingDp >= p.anchorDp)
        }
    }

    // ── Tinggi baris ikut font dan romanisasi ────────────────────

    /**
     * REGRESI: dilaporkan dari perangkat. Dengan romanisasi menyala, baris
     * aktif turun sekitar 24 dp dari tengah.
     *
     * Sebabnya setiap baris membawa teks KEDUA di bawahnya, jadi barisnya jauh
     * lebih tinggi sementara jangkar tetap dihitung untuk baris polos.
     */
    @Test
    fun `romanisasi membuat baris jauh lebih tinggi`() {
        val polos = lyricsTypicalLineDp(LYRICS_REF_FONT_SP, hasRomanization = false)
        val beromanisasi = lyricsTypicalLineDp(LYRICS_REF_FONT_SP, hasRomanization = true)
        assertTrue("harus lebih tinggi, $polos -> $beromanisasi", beromanisasi > polos)
        assertEquals("tambahannya 3/4 tinggi baris plus jaraknya", 47f, beromanisasi - polos, 0.01f)
    }

    @Test
    fun `baris tetap di tengah walau romanisasi menyala`() {
        val tinggi = lyricsTypicalLineDp(LYRICS_REF_FONT_SP, hasRomanization = true)
        assertEquals(0.5f, pusatUntukBaris(portrait, tinggi), 0.001f)
        assertEquals(0.5f, pusatUntukBaris(landscape, tinggi), 0.001f)
    }

    @Test
    fun `mengabaikan romanisasi membuatnya turun dari tengah`() {
        val tinggiAsli = lyricsTypicalLineDp(LYRICS_REF_FONT_SP, hasRomanization = true)
        // Model lama: jangkar dihitung untuk baris polos, tapi barisnya beromanisasi.
        val jangkarSalah = lyricsPadding(portrait, lineHeightDp = LYRICS_TYPICAL_LINE_DP).anchorDp
        val pusatSalah = (jangkarSalah + tinggiAsli / 2f) / portrait
        assertTrue("harusnya turun jelas dari tengah, dapat $pusatSalah", pusatSalah > 0.53f)
    }

    /**
     * Cacat yang sama untuk ukuran font, dan belum sempat dilaporkan: nilai
     * acuan diukur pada 30 sp, jadi menaikkannya ke 56 sp menggeser pusat
     * dengan cara yang persis sama.
     */
    @Test
    fun `tinggi baris ikut ukuran font`() {
        val kecil = lyricsTypicalLineDp(15, hasRomanization = false)
        val acuan = lyricsTypicalLineDp(LYRICS_REF_FONT_SP, hasRomanization = false)
        val besar = lyricsTypicalLineDp(60, hasRomanization = false)
        assertEquals("setengah font acuan", acuan / 2f, kecil, 0.01f)
        assertEquals("dua kali font acuan", acuan * 2f, besar, 0.01f)
    }

    @Test
    fun `baris tetap di tengah pada ukuran font apa pun`() {
        listOf(12, 17, 30, 44, 56).forEach { font ->
            listOf(false, true).forEach { roma ->
                val tinggi = lyricsTypicalLineDp(font, roma)
                assertEquals(
                    "font=$font romanisasi=$roma",
                    0.5f,
                    pusatUntukBaris(portrait, tinggi),
                    0.001f,
                )
            }
        }
    }

    @Test
    fun `font nol atau negatif tidak menghasilkan tinggi negatif`() {
        listOf(0, -5, Int.MIN_VALUE).forEach { font ->
            assertTrue("font=$font", lyricsTypicalLineDp(font, false) > 0f)
            assertTrue("font=$font", lyricsTypicalLineDp(font, true) > 0f)
        }
    }

    @Test
    fun `tinggi baris negatif tidak menjatuhkan perhitungan`() {
        val p = lyricsPadding(600f, lineHeightDp = -100f)
        assertTrue(p.anchorDp >= 0f && p.leadingDp >= 0f && p.trailingDp >= 0f)
    }

    @Test
    fun `nilai bawaan tetap sama seperti sebelumnya`() {
        assertEquals(
            lyricsPadding(portrait, lineHeightDp = LYRICS_TYPICAL_LINE_DP),
            lyricsPadding(portrait),
        )
    }
}
