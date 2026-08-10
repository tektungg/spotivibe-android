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

    /** Panel landscape terpendek yang realistis di Android. */
    private val panelTerpendek = 320f

    /** Sisa untuk album setelah semua yang wajib terlihat diambil. */
    private fun sisaAlbum(lebar: Float, tinggi: Float): Float {
        val m = landscapePaneMetrics(lebar, tinggi)
        val isi = landscapeContentHeightDp(tinggi, m)
        return minOf(lebar - m.padDp * 2, isi - landscapeFixedBelowAlbumDp(m), ALBUM_MAX_DP)
    }

    // ── Invarian: transport tidak boleh terpotong ────────────────

    /**
     * REGRESI: model lama memesan 90 dp untuk transport padahal Transport.kt
     * tingginya 119 dp, dan sama sekali tidak menghitung eyebrow serta meta
     * strip. Panel kiri butuh 385,6 dp di ruang 295 dp, jadi baris tombol
     * play/next terpotong habis dan slider terpotong separuh. Tidak terlihat
     * karena yang hilang ada di bawah lipatan layar.
     *
     * Album memakai weight(1f), jadi ia yang mengalah lebih dulu. Yang harus
     * dijaga adalah sisanya tetap muat, karena begitu tidak muat yang terpotong
     * langsung transport.
     */
    @Test
    fun `yang wajib terlihat selalu muat di panel yang realistis`() {
        listOf(
            hpLebar to hpTinggi,
            tabletLebar to tabletTinggi,
            300f to panelTerpendek,
            420f to 400f,
            700f to 1000f,
        ).forEach { (w, h) ->
            val m = landscapePaneMetrics(w, h)
            val isi = landscapeContentHeightDp(h, m)
            val wajib = landscapeFixedBelowAlbumDp(m)
            assertTrue("panel ${w}x$h: wajib $wajib melebihi isi $isi", wajib <= isi)
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

    // ── Album mengambil sisa ruang ───────────────────────────────

    /**
     * REGRESI: dilaporkan dari perangkat, album menempel di atas dan
     * menyisakan lubang sebelum judul.
     *
     * Sebabnya model memesan ruang untuk judul DUA baris. Judul satu baris
     * seperti "OMG" menyisakan 26 dp yang tidak dipakai siapa pun. Sekarang
     * album memakai weight(1f) dan menerima apa pun yang tersisa, jadi
     * perbedaan panjang judul jatuh ke album, bukan jadi lubang.
     */
    @Test
    fun `model tidak lagi memutuskan ukuran album`() {
        val bidang = LandscapePaneMetrics::class.java.declaredFields.map { it.name }
        assertTrue(
            "albumDp harus hilang dari model; ukurannya diukur di layar. Bidang: $bidang",
            bidang.none { it.contains("album", ignoreCase = true) },
        )
    }

    @Test
    fun `album HP landscape lebih besar dari nilai lama`() {
        val albumLama = (hpTinggi - 80f - 90f) * 0.45f   // 92,25
        assertTrue(
            "sisa ${sisaAlbum(hpLebar, hpTinggi)} harus melebihi $albumLama yang lama",
            sisaAlbum(hpLebar, hpTinggi) > albumLama,
        )
    }

    @Test
    fun `sisa album tidak pernah negatif di panel realistis`() {
        listOf(hpLebar to hpTinggi, 300f to panelTerpendek, tabletLebar to tabletTinggi)
            .forEach { (w, h) ->
                assertTrue("panel ${w}x$h", sisaAlbum(w, h) >= 0f)
            }
    }

    @Test
    fun `album tidak melebihi lebar panel maupun batas desain`() {
        listOf(150f to 400f, 267f to 500f, 900f to 1000f, 2000f to 2000f).forEach { (w, h) ->
            val m = landscapePaneMetrics(w, h)
            val a = sisaAlbum(w, h)
            assertTrue("w=$w album=$a melebihi lebar isi", a <= w - m.padDp * 2)
            assertTrue("w=$w album=$a melebihi batas desain", a <= ALBUM_MAX_DP)
        }
    }

    @Test
    fun `album membesar seiring panel membesar`() {
        var sebelumnya = -1f
        listOf(320f, 375f, 500f, 700f, 900f).forEach { h ->
            val a = sisaAlbum(600f, h)
            assertTrue("mundur di h=$h", a >= sebelumnya)
            sebelumnya = a
        }
    }

    // ── Tipografi dan kerapatan ──────────────────────────────────

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

    @Test
    fun `panel lega memakai tipografi tablet penuh`() {
        val m = landscapePaneMetrics(tabletLebar, tabletTinggi)
        assertEquals(42f, m.titleSp, 0.01f)
        assertEquals(18f, m.artistSp, 0.01f)
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
        assertTrue(m.titleSp > 0f)
        assertTrue(m.artistSp > 0f)
        assertEquals(0f, landscapeContentHeightDp(0f, m), 0.01f)
    }

    @Test
    fun `panel negatif diperlakukan seperti nol`() {
        assertEquals(landscapePaneMetrics(0f, 0f), landscapePaneMetrics(-500f, -500f))
        assertEquals(
            landscapeContentHeightDp(0f, landscapePaneMetrics(0f, 0f)),
            landscapeContentHeightDp(-500f, landscapePaneMetrics(0f, 0f)),
            0.01f,
        )
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
