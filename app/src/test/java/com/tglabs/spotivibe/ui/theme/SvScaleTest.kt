package com.tglabs.spotivibe.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SvScaleTest {

    @Test
    fun `skala satu tidak mengubah nilai`() {
        assertEquals(44f, scaleDp(44.dp, 1f).value, 0.001f)
    }

    @Test
    fun `skala mengalikan nilai`() {
        assertEquals(37.4f, scaleDp(44.dp, 0.85f).value, 0.01f)
        assertEquals(55f, scaleDp(44.dp, 1.25f).value, 0.01f)
    }

    /**
     * REGRESI: `Dp.Unspecified` adalah `Float.NaN`. Mengalikannya menghasilkan
     * NaN yang lolos diam-diam sampai jadi crash pengukuran di layar, bukan
     * error compile.
     */
    @Test
    fun `Unspecified dilewatkan apa adanya`() {
        val hasil = scaleDp(Dp.Unspecified, 1.25f)
        assertTrue("harus tetap Unspecified, bukan NaN hasil kali", hasil == Dp.Unspecified)
    }

    @Test
    fun `Infinity dilewatkan apa adanya`() {
        assertEquals(Dp.Infinity, scaleDp(Dp.Infinity, 0.85f))
    }

    @Test
    fun `nol tetap nol`() {
        assertEquals(0f, scaleDp(0.dp, 1.25f).value, 0.001f)
    }

    /**
     * Menghubungkan kedua lapis: faktor yang dipakai `svDp` selalu datang dari
     * [svScale], jadi jepitannya ikut berlaku. Tanpa ini, `svDp` bisa saja
     * dipakai dengan faktor mentah dan bug pembengkakan kembali.
     */
    @Test
    fun `skala perangkat nyata tidak pernah membengkakkan album`() {
        val albumDesain = 92f
        listOf(
            375f to 812f,    // HP portrait
            320f to 568f,    // HP kecil
            834f to 375f,    // HP landscape
            1280f to 800f,   // tablet landscape
        ).forEach { (w, h) ->
            val hasil = scaleDp(albumDesain.dp, svScale(w, h)).value
            assertTrue(
                "w=$w h=$h album=$hasil tidak boleh melebihi tinggi layar",
                hasil < h,
            )
            assertTrue("w=$w h=$h album=$hasil membengkak", hasil <= albumDesain * SCALE_MAX)
        }
    }
}
