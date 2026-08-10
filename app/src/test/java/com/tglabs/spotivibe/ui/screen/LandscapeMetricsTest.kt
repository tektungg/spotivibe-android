package com.tglabs.spotivibe.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LandscapeMetricsTest {

    /** HP penguji: 1220x2712 px @520dpi, jadi landscape 834x375 dp. */
    private val hpLebar = 347f   // kolom kiri = weight(1f) dari weight 1 : 1,4
    private val hpTinggi = 375f

    /** Tablet 10 inci landscape, kira-kira 1280x800 dp. */
    private val tabletLebar = 533f
    private val tabletTinggi = 800f

    /**
     * Tinggi semua yang WAJIB terlihat di bawah album. Kalau album ditambah
     * angka ini melebihi ruang isi, transport terpotong.
     */
    private fun wajibDp(m: LandscapePaneMetrics): Float =
        m.gapDp +
            m.titleSp * TITLE_LINE_FACTOR * TITLE_MAX_LINES +
            TITLE_ARTIST_GAP_DP +
            m.artistSp * ARTIST_LINE_FACTOR +
            TITLE_TRANSPORT_GAP_DP +
            (if (m.compactTransport) TRANSPORT_COMPACT_DP else TRANSPORT_FULL_DP)

    // ── Invarian: transport tidak boleh terpotong ────────────────

    /**
     * REGRESI: model lama memesan 90 dp untuk transport padahal Transport.kt
     * tingginya 119 dp, dan sama sekali tidak menghitung eyebrow serta meta
     * strip. Panel kiri butuh 385,6 dp di ruang 295 dp, jadi baris tombol
     * play/next terpotong habis dan slider terpotong separuh. Tidak terlihat
     * karena yang hilang ada di bawah lipatan layar.
     */
    @Test
    fun `album plus yang wajib terlihat tidak pernah melebihi panel`() {
        listOf(
            hpLebar to hpTinggi,
            tabletLebar to tabletTinggi,
            300f to 320f,
            420f to 400f,
            533f to 800f,
            700f to 1000f,
        ).forEach { (w, h) ->
            val m = landscapePaneMetrics(w, h)
            val isiTinggi = h - m.padDp * 2
            val terpakai = m.albumDp + wajibDp(m)
            assertTrue(
                "panel ${w}x$h: terpakai $terpakai melebihi isi $isiTinggi",
                terpakai <= isiTinggi + 0.01f,
            )
        }
    }

    @Test
    fun `perilaku lama akan gagal invarian yang sama`() {
        // Model lama: album = (tinggi - 80 padding - 90 transport) * 0,45,
        // plus eyebrow 37 dan meta 60 yang tidak pernah dihitung.
        val albumLama = (hpTinggi - 80f - 90f) * 0.45f
        val terpakaiLama = 37f + albumLama + 16f + 26.4f + 4f + 18f + 60f + 13f + TRANSPORT_FULL_DP
        assertTrue(
            "seharusnya melebihi ${hpTinggi - 80f}, dapat $terpakaiLama",
            terpakaiLama > hpTinggi - 80f,
        )
    }

    // ── Album membesar setelah eyebrow dan meta dibuang ──────────

    /**
     * Permintaan langsung: besarkan album di landscape. Ruangnya datang dari
     * eyebrow dan meta strip yang dibuang, plus padding yang dirapatkan, BUKAN
     * dari mengurangi jatah transport.
     */
    @Test
    fun `album HP landscape lebih besar dari nilai lama`() {
        val albumLama = (hpTinggi - 80f - 90f) * 0.45f   // 92,25
        val m = landscapePaneMetrics(hpLebar, hpTinggi)
        assertTrue(
            "album ${m.albumDp} harus melebihi $albumLama yang lama",
            m.albumDp > albumLama,
        )
    }

    @Test
    fun `panel pendek merapatkan padding dan transport`() {
        val m = landscapePaneMetrics(hpLebar, hpTinggi)
        assertEquals(PAD_TIGHT_DP, m.padDp, 0.01f)
        assertTrue("transport harus dirapatkan di panel pendek", m.compactTransport)
    }

    @Test
    fun `panel lega tetap memakai padding dan transport penuh`() {
        val m = landscapePaneMetrics(tabletLebar, tabletTinggi)
        assertEquals(PAD_ROOMY_DP, m.padDp, 0.01f)
        assertTrue("tablet tidak boleh ikut dirapatkan", !m.compactTransport)
    }

    // ── Batas ────────────────────────────────────────────────────

    @Test
    fun `album tidak pernah melebihi lebar panel`() {
        listOf(80f, 150f, 267f, 400f, 900f).forEach { w ->
            val m = landscapePaneMetrics(w, 1000f)
            assertTrue("w=$w album=${m.albumDp}", m.albumDp <= w)
        }
    }

    @Test
    fun `album tidak pernah melebihi batas desain`() {
        val m = landscapePaneMetrics(2000f, 2000f)
        assertTrue("album ${m.albumDp}", m.albumDp <= ALBUM_MAX_DP)
    }

    @Test
    fun `panel lega memakai ukuran tablet penuh`() {
        val m = landscapePaneMetrics(tabletLebar, tabletTinggi)
        assertEquals(ALBUM_MAX_DP, m.albumDp, 0.01f)
        assertEquals(42f, m.titleSp, 0.01f)
    }

    @Test
    fun `panel sempit mengecilkan tipografi juga`() {
        val m = landscapePaneMetrics(hpLebar, hpTinggi)
        assertTrue("judul ${m.titleSp} harus lebih kecil dari ukuran tablet", m.titleSp < 42f)
        assertTrue("artis ${m.artistSp} harus lebih kecil dari ukuran tablet", m.artistSp < 18f)
    }

    // ── Ketahanan ────────────────────────────────────────────────

    @Test
    fun `panel nol tidak menghasilkan ukuran negatif`() {
        val m = landscapePaneMetrics(0f, 0f)
        assertTrue(m.albumDp >= 0f)
        assertTrue(m.titleSp > 0f)
        assertTrue(m.artistSp > 0f)
    }

    @Test
    fun `panel negatif diperlakukan seperti nol`() {
        assertEquals(landscapePaneMetrics(0f, 0f), landscapePaneMetrics(-500f, -500f))
    }

    /**
     * Album mengecil sampai nol lebih baik daripada tombol play yang hilang.
     * Lantai minimum apa pun akan melanggar invarian dan mengembalikan bug
     * transport terpotong.
     */
    @Test
    fun `panel lebih pendek dari yang wajib memberi album nol bukan nilai negatif`() {
        val m = landscapePaneMetrics(300f, 120f)
        assertEquals(0f, m.albumDp, 0.01f)
    }

    @Test
    fun `album membesar seiring panel membesar`() {
        var sebelumnya = -1f
        listOf(200f, 300f, 375f, 500f, 700f, 900f).forEach { h ->
            val a = landscapePaneMetrics(600f, h).albumDp
            assertTrue("mundur di h=$h", a >= sebelumnya)
            sebelumnya = a
        }
    }

    @Test
    fun `deterministik`() {
        repeat(5) {
            assertEquals(
                landscapePaneMetrics(hpLebar, hpTinggi),
                landscapePaneMetrics(hpLebar, hpTinggi),
            )
        }
    }
}
