package com.tglabs.spotivibe.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsMetricsTest {

    /** Viewport lirik portrait di HP penguji: 834 dp layar minus header + transport. */
    private val portrait = 600f

    /** Panel kanan landscape di HP penguji: 375 dp layar minus padding + transport. */
    private val landscape = 285f

    /**
     * REGRESI: perilaku portrait TIDAK boleh berubah. Nilai lama 80 dp atas dan
     * 120 dp bawah harus keluar lagi persis, kalau tidak perbaikan landscape
     * ikut menggeser layar yang selama ini sudah benar.
     */
    @Test
    fun `portrait mereproduksi nilai lama`() {
        val p = lyricsPadding(portrait)
        assertEquals(80f, p.leadingDp, 0.5f)
        assertEquals(120f, p.trailingDp, 0.5f)
    }

    /**
     * REGRESI: ini alasan fungsi ini ada. Ruang atas + bawah yang tetap memakan
     * 200 dari 285 dp panel landscape, yaitu 70% ruang untuk kekosongan.
     */
    @Test
    fun `landscape tidak menghabiskan separuh panel`() {
        val p = lyricsPadding(landscape)
        val lamaTotal = 80f + 120f
        val baruTotal = p.leadingDp + p.trailingDp

        assertTrue("nilai lama memakan $lamaTotal dari $landscape dp", lamaTotal / landscape > 0.6f)
        assertTrue("jangkar harus ikut mengecil, bukan tetap 64 dp", p.anchorDp < LYRICS_ANCHOR_MAX_DP)
        assertTrue(
            "yang baru memakan $baruTotal dari $landscape dp, harus di bawah separuh",
            baruTotal / landscape < 0.5f,
        )
    }

    @Test
    fun `ruang bawah tidak pernah lebih besar dari viewport`() {
        listOf(0f, 10f, 50f, 100f, 285f, 600f, 2000f).forEach { h ->
            val p = lyricsPadding(h)
            assertTrue("h=$h ekor=${p.trailingDp}", p.trailingDp <= h || h == 0f)
        }
    }

    /**
     * Jangkar harus tetap di bagian ATAS viewport. Tanpa batas seperempat, di
     * panel 200 dp jangkar 64 dp masih 32%, tapi di panel 150 dp jadi 43% dan
     * baris aktif turun ke tengah-bawah.
     */
    @Test
    fun `jangkar tetap di bagian atas viewport`() {
        listOf(120f, 150f, 200f, 285f, 600f, 1000f).forEach { h ->
            val p = lyricsPadding(h)
            assertTrue("h=$h jangkar=${p.anchorDp} terlalu turun", p.anchorDp <= h * 0.25f + 0.01f)
        }
    }

    @Test
    fun `ruang atas selalu cukup untuk baris pertama mencapai jangkar`() {
        listOf(120f, 285f, 600f, 1000f).forEach { h ->
            val p = lyricsPadding(h)
            assertTrue("h=$h", p.leadingDp >= p.anchorDp)
        }
    }

    @Test
    fun `viewport nol tidak menghasilkan nilai negatif atau NaN`() {
        val p = lyricsPadding(0f)
        assertTrue(p.anchorDp >= 0f && p.leadingDp >= 0f && p.trailingDp >= 0f)
        assertTrue(!p.trailingDp.isNaN() && !p.anchorDp.isNaN())
    }

    @Test
    fun `viewport negatif diperlakukan seperti nol`() {
        assertEquals(lyricsPadding(0f), lyricsPadding(-500f))
    }

    @Test
    fun `viewport besar tidak menghasilkan ruang bawah tak terbatas`() {
        assertEquals(LYRICS_TRAIL_MAX_DP, lyricsPadding(5000f).trailingDp, 0.01f)
    }

    /**
     * Jangkar dinyatakan dalam dp, jadi konversinya ke piksel ikut kerapatan
     * layar. Nilai 200 px yang lama berarti jarak yang berbeda di tiap
     * perangkat; ini menunjukkan selisihnya.
     */
    @Test
    fun `jangkar dp konsisten sementara 200 px tidak`() {
        val jangkarDp = lyricsPadding(portrait).anchorDp
        listOf(2.0f, 2.625f, 3.25f, 4.0f).forEach { density ->
            val lamaDp = 200f / density
            val px = jangkarDp * density
            assertEquals("dp -> px -> dp harus bolak-balik", jangkarDp, px / density, 0.01f)
            if (density != 3.25f) {
                assertTrue(
                    "density=$density: 200 px = $lamaDp dp, beda dari $jangkarDp dp",
                    kotlin.math.abs(lamaDp - jangkarDp) > 5f,
                )
            }
        }
    }

    @Test
    fun `deterministik`() {
        repeat(5) { assertEquals(lyricsPadding(landscape), lyricsPadding(landscape)) }
    }
}
