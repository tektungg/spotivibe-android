package com.tglabs.spotivibe.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LandscapeMetricsTest {

    /** HP penguji: 1220x2712 px @520dpi, jadi landscape 834x375 dp. */
    private val hpLebar = 347f - 80f   // kolom kiri dikurangi padding 40 dp kiri-kanan
    private val hpTinggi = 375f - 80f  // dikurangi padding 40 dp atas-bawah

    /** Tablet 10 inci landscape, kira-kira 1280x800 dp. */
    private val tabletLebar = 533f - 80f
    private val tabletTinggi = 800f - 80f

    // ── Regresi: judul hilang di HP landscape ────────────────────

    /**
     * REGRESI: album cuma dibatasi lebar, tidak pernah tinggi. Di HP landscape
     * album jadi 267 dp dari 295 dp yang tersedia, jadi judul, artis, meta, dan
     * transport semuanya terdorong keluar layar.
     */
    @Test
    fun `album menyisakan ruang untuk judul dan artis di HP landscape`() {
        val m = landscapePaneMetrics(hpLebar, hpTinggi)

        val tinggiJudul = m.titleSp * 1.2f * 2   // dua baris
        val tinggiArtis = m.artistSp * 1.3f
        val terpakai = m.albumDp + m.gapDp + tinggiJudul + tinggiArtis
        val tersedia = hpTinggi - 90f            // transport dipatok di bawah

        assertTrue(
            "album ${m.albumDp} + judul + artis = $terpakai melebihi $tersedia",
            terpakai <= tersedia,
        )
    }

    @Test
    fun `perilaku lama akan gagal syarat yang sama`() {
        // Mendokumentasikan bug-nya: dibatasi lebar saja.
        val albumLama = minOf(hpLebar, ALBUM_MAX_DP)
        assertTrue("album lama $albumLama seharusnya sudah melebihi", albumLama > hpTinggi - 90f)
    }

    // ── Batas ────────────────────────────────────────────────────

    @Test
    fun `album tidak pernah melebihi lebar panel`() {
        listOf(80f, 150f, 267f, 400f, 900f).forEach { w ->
            listOf(100f, 295f, 500f, 720f).forEach { h ->
                val m = landscapePaneMetrics(w, h)
                assertTrue("w=$w h=$h album=${m.albumDp}", m.albumDp <= w + 0.01f)
            }
        }
    }

    @Test
    fun `album tidak pernah melebihi batas desain`() {
        val m = landscapePaneMetrics(2000f, 2000f)
        assertTrue(m.albumDp <= ALBUM_MAX_DP)
    }

    @Test
    fun `panel lega memakai ukuran tablet penuh`() {
        val m = landscapePaneMetrics(tabletLebar, tabletTinggi)
        assertEquals(ALBUM_MAX_DP, m.albumDp, 0.01f)
        assertEquals(42f, m.titleSp, 0.01f)
        assertEquals(18f, m.artistSp, 0.01f)
    }

    @Test
    fun `panel sempit mengecilkan tipografi juga`() {
        val m = landscapePaneMetrics(hpLebar, hpTinggi)
        assertTrue("judul ${m.titleSp} harus lebih kecil dari 42", m.titleSp < 42f)
        assertTrue("artis ${m.artistSp} harus lebih kecil dari 18", m.artistSp < 18f)
    }

    /**
     * Mengecilkan album saja tidak cukup. Judul 42sp di panel pendek memakan
     * tiga baris dan mendorong artis keluar layar lagi.
     */
    @Test
    fun `mengecilkan album tanpa mengecilkan judul tetap tidak muat`() {
        val m = landscapePaneMetrics(hpLebar, hpTinggi)
        val kalauJudulTetapBesar = m.albumDp + m.gapDp + (42f * 1.2f * 2) + (18f * 1.3f)
        assertTrue(
            "dengan judul 42sp hasilnya $kalauJudulTetapBesar, jadi tipografi memang harus ikut turun",
            kalauJudulTetapBesar > hpTinggi - 90f,
        )
    }

    // ── Kasus tepi ───────────────────────────────────────────────

    @Test
    fun `panel nol tidak menghasilkan ukuran negatif`() {
        listOf(
            landscapePaneMetrics(0f, 0f),
            landscapePaneMetrics(0f, 400f),
            landscapePaneMetrics(400f, 0f),
            landscapePaneMetrics(100f, 50f),
        ).forEach { m ->
            assertTrue("album ${m.albumDp}", m.albumDp >= 0f)
            assertTrue("judul ${m.titleSp}", m.titleSp > 0f)
            assertTrue("artis ${m.artistSp}", m.artistSp > 0f)
        }
    }

    @Test
    fun `tinggi lebih kecil dari ruang transport tidak meledak`() {
        val m = landscapePaneMetrics(300f, 60f)
        assertTrue(m.albumDp >= 0f)
        assertTrue(m.albumDp <= 300f)
    }

    @Test
    fun `album membesar seiring panel membesar`() {
        var sebelumnya = -1f
        listOf(120f, 200f, 295f, 400f, 600f, 800f).forEach { h ->
            val a = landscapePaneMetrics(500f, h).albumDp
            assertTrue("mundur di h=$h ($a < $sebelumnya)", a >= sebelumnya)
            sebelumnya = a
        }
    }

    @Test
    fun `panel sangat sempit tetap menghasilkan album yang terlihat`() {
        val m = landscapePaneMetrics(90f, 400f)
        assertTrue("album ${m.albumDp} terlalu kecil untuk dikenali", m.albumDp >= 72f)
        assertTrue("tapi tidak boleh melebihi lebar panel", m.albumDp <= 90f)
    }

    @Test
    fun `deterministik`() {
        val a = landscapePaneMetrics(hpLebar, hpTinggi)
        repeat(5) { assertEquals(a, landscapePaneMetrics(hpLebar, hpTinggi)) }
    }
}
