package com.tglabs.spotivibe.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SvWindowTest {

    // Perangkat nyata, dalam dp.
    private val hpPortrait = 375f to 812f
    private val hpLandscape = 834f to 375f      // HP penguji, 1220x2712 @520dpi
    private val tabletPortrait = 800f to 1280f
    private val tabletLandscape = 1280f to 800f

    private fun kelas(p: Pair<Float, Float>) = classifyWindow(p.first, p.second)

    // ── Klasifikasi ──────────────────────────────────────────────

    @Test
    fun `HP portrait sempit di kedua sisi lebar`() {
        val w = kelas(hpPortrait)
        assertEquals(SvSizeClass.Compact, w.widthClass)
        assertFalse("bukan layar lebar", w.isWide)
    }

    /**
     * REGRESI KONSEPTUAL: inti kenapa sistem ini ada.
     *
     * HP landscape punya lebar Expanded tapi tinggi Compact. Melihat lebar saja
     * membuat layout mengira dirinya ada di tablet, lalu memakai album 360 dp di
     * layar yang tingginya cuma 375 dp. Itu persis bug judul lagu yang hilang.
     */
    @Test
    fun `HP landscape lebar tapi PENDEK`() {
        val w = kelas(hpLandscape)
        assertTrue("lebarnya memang muat dua kolom", w.isWide)
        assertTrue("tapi tingginya sempit dan itu yang menentukan", w.isShort)
    }

    @Test
    fun `tablet landscape lebar DAN lega`() {
        val w = kelas(tabletLandscape)
        assertTrue(w.isWide)
        assertFalse("tablet tidak boleh ikut mengecil", w.isShort)
    }

    /**
     * Menunjukkan kenapa orientasi bukan penanda yang benar: HP landscape dan
     * tablet landscape sama-sama landscape, tapi butuh perlakuan berbeda.
     */
    @Test
    fun `orientasi saja tidak cukup membedakan HP dan tablet landscape`() {
        assertTrue(kelas(hpLandscape).isShort)
        assertFalse(kelas(tabletLandscape).isShort)
        assertEquals(
            "keduanya sama-sama lebar, jadi lebar saja juga tidak cukup",
            kelas(hpLandscape).isWide,
            kelas(tabletLandscape).isWide,
        )
    }

    @Test
    fun `tablet portrait lega di kedua sisi`() {
        val w = kelas(tabletPortrait)
        assertTrue(w.isWide)
        assertFalse(w.isShort)
    }

    @Test
    fun `ambang breakpoint tepat di batas`() {
        assertEquals(SvSizeClass.Compact, classifyWindow(599f, 100f).widthClass)
        assertEquals(SvSizeClass.Medium, classifyWindow(600f, 100f).widthClass)
        assertEquals(SvSizeClass.Medium, classifyWindow(839f, 100f).widthClass)
        assertEquals(SvSizeClass.Expanded, classifyWindow(840f, 100f).widthClass)
    }

    @Test
    fun `ukuran negatif tidak menghasilkan nilai aneh`() {
        val w = classifyWindow(-10f, -10f)
        assertTrue(w.widthDp >= 0f)
        assertTrue(w.heightDp >= 0f)
    }

    // ── Faktor skala ─────────────────────────────────────────────

    @Test
    fun `kanvas desain menghasilkan skala satu`() {
        assertEquals(1f, svScale(DESIGN_WIDTH_DP, DESIGN_HEIGHT_DP), 0.01f)
    }

    /**
     * REGRESI: inilah yang membuat model flutter_screenutil berbahaya di sini.
     *
     * Skala berbasis lebar di HP landscape menghasilkan 834/375 = 2,22x, yang
     * akan MEMBENGKAKKAN elemen di layar yang justru tingginya paling sempit.
     * Mengambil rasio yang lebih langka membuatnya menyusut, bukan membengkak.
     */
    @Test
    fun `HP landscape tidak pernah menghasilkan skala membesar`() {
        val skala = svScale(hpLandscape.first, hpLandscape.second)
        val skalaBerbasisLebar = hpLandscape.first / DESIGN_WIDTH_DP

        assertTrue("skala berbasis lebar $skalaBerbasisLebar akan membengkakkan", skalaBerbasisLebar > 2f)
        assertTrue("skala kita $skala tidak boleh membesar", skala <= 1f)
    }

    @Test
    fun `skala selalu di dalam jepitan`() {
        listOf(
            1f to 1f, 320f to 480f, 375f to 812f, 834f to 375f,
            1280f to 800f, 2000f to 3000f, 5000f to 5000f,
        ).forEach { (w, h) ->
            val s = svScale(w, h)
            assertTrue("w=$w h=$h skala=$s", s in SCALE_MIN..SCALE_MAX)
        }
    }

    @Test
    fun `layar lebih besar tidak pernah menghasilkan skala lebih kecil`() {
        var sebelumnya = 0f
        listOf(320f, 375f, 420f, 600f, 800f, 1200f).forEach { sisi ->
            // Layar persegi supaya kedua dimensi tumbuh bersama.
            val s = svScale(sisi, sisi * (DESIGN_HEIGHT_DP / DESIGN_WIDTH_DP))
            assertTrue("mundur di $sisi", s >= sebelumnya)
            sebelumnya = s
        }
    }

    @Test
    fun `dimensi nol tidak membagi nol`() {
        assertEquals(1f, svScale(0f, 100f), 0.001f)
        assertEquals(1f, svScale(100f, 0f), 0.001f)
        assertEquals(1f, svScale(0f, 0f), 0.001f)
    }

    @Test
    fun `deterministik`() {
        val a = svScale(hpLandscape.first, hpLandscape.second)
        repeat(5) { assertEquals(a, svScale(hpLandscape.first, hpLandscape.second), 0.0f) }
    }
}
