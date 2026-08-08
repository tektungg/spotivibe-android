package com.tglabs.spotivibe.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.pow

/**
 * Test untuk konversi OkLab di AccentLock.
 *
 * 18 konstanta matriks tanpa satu pun test sebelumnya. Salah satu digit salah
 * ketik akan menggeser warna aksen secara halus di SEMUA lagu, dan tidak ada
 * yang akan menangkapnya sampai ada yang membandingkan dengan referensi.
 */
class AccentLockTest {

    /** Warna uji yang mencakup rentang hue yang lazim di sampul album. */
    private val warnaUji = listOf(
        "merah K-pop" to Color(0xFFE53935),
        "hijau anime" to Color(0xFF43A047),
        "biru city-pop" to Color(0xFF1E88E5),
        "kuning" to Color(0xFFFDD835),
        "ungu" to Color(0xFF8E24AA),
        "cyan" to Color(0xFF00ACC1),
        "oranye" to Color(0xFFFB8C00),
        "magenta" to Color(0xFFD81B60),
        "coral bawaan" to AccentCoralDark,
    )

    // ── Salinan matematika OkLab untuk MEMERIKSA keluaran ────────
    //
    // Sengaja ditulis ulang di sini alih-alih memanggil fungsi privat yang
    // diuji. Kalau test memakai konversi yang sama dengan yang diuji, salah
    // ketik pada konstanta akan lolos karena kedua sisi salah dengan cara yang
    // sama.

    private fun srgbToLinear(c: Double): Double =
        if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private data class OkLch(val l: Double, val c: Double, val h: Double)

    private fun toOkLch(color: Color): OkLch {
        val r = srgbToLinear(color.red.toDouble())
        val g = srgbToLinear(color.green.toDouble())
        val b = srgbToLinear(color.blue.toDouble())

        val l_ = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m_ = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s_ = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)

        val lightness = 0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_
        val a = 1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_
        val bb = 0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_

        return OkLch(
            l = lightness,
            c = kotlin.math.sqrt(a * a + bb * bb),
            h = atan2(bb, a),
        )
    }

    /** Selisih hue melingkar dalam radian, 0..PI. */
    private fun bedaHue(a: Double, b: Double): Double {
        var d = abs(a - b) % (2 * Math.PI)
        if (d > Math.PI) d = 2 * Math.PI - d
        return d
    }

    // ── Lightness dan chroma benar-benar dikunci ─────────────────

    /**
     * Inti dari kelas ini: apa pun warna sampulnya, aksen keluar dengan
     * lightness dan chroma yang SAMA, supaya kontras terhadap ink-1 bisa
     * diprediksi untuk lagu apa pun.
     */
    @Test
    fun `lightness terkunci untuk semua hue di mode gelap`() {
        warnaUji.forEach { (nama, warna) ->
            val hasil = toOkLch(AccentLock.lockedAccent(warna, dark = true))
            assertEquals("$nama: L meleset", 0.78, hasil.l, 0.02)
        }
    }

    @Test
    fun `lightness terkunci untuk semua hue di mode terang`() {
        warnaUji.forEach { (nama, warna) ->
            val hasil = toOkLch(AccentLock.lockedAccent(warna, dark = false))
            assertEquals("$nama: L meleset", 0.55, hasil.l, 0.02)
        }
    }

    @Test
    fun `mode gelap lebih terang daripada mode terang`() {
        warnaUji.forEach { (nama, warna) ->
            val gelap = toOkLch(AccentLock.lockedAccent(warna, dark = true)).l
            val terang = toOkLch(AccentLock.lockedAccent(warna, dark = false)).l
            assertTrue("$nama: $gelap harus > $terang", gelap > terang)
        }
    }

    // ── Hue dipertahankan ────────────────────────────────────────

    /**
     * Hue adalah SATU-SATUNYA hal yang boleh lolos dari sampul album. Kalau ikut
     * bergeser, aksennya berhenti terasa berasal dari sampulnya.
     *
     * Toleransinya longgar karena warna di luar gamut sRGB dipotong saat
     * konversi balik, dan pemotongan itu memang sedikit menggeser hue.
     */
    @Test
    fun `hue dipertahankan dari warna sumber`() {
        warnaUji.forEach { (nama, warna) ->
            val asal = toOkLch(warna).h
            val hasil = toOkLch(AccentLock.lockedAccent(warna, dark = true)).h
            assertTrue(
                "$nama: hue bergeser ${bedaHue(asal, hasil)} rad",
                bedaHue(asal, hasil) < 0.25,
            )
        }
    }

    @Test
    fun `hue yang berbeda menghasilkan warna yang berbeda`() {
        val merah = AccentLock.lockedAccent(Color(0xFFE53935))
        val biru = AccentLock.lockedAccent(Color(0xFF1E88E5))
        assertNotEquals(merah, biru)
    }

    // ── Keluaran selalu warna yang sah ───────────────────────────

    @Test
    fun `komponen selalu di dalam rentang yang sah`() {
        val ekstrem = warnaUji.map { it.second } + listOf(
            Color.Black, Color.White,
            Color(0xFF000001), Color(0xFFFFFFFE),
            Color(0xFFFF0000), Color(0xFF00FF00), Color(0xFF0000FF),
        )
        ekstrem.forEach { warna ->
            listOf(true, false).forEach { dark ->
                val h = AccentLock.lockedAccent(warna, dark)
                assertTrue("merah ${h.red}", h.red in 0f..1f)
                assertTrue("hijau ${h.green}", h.green in 0f..1f)
                assertTrue("biru ${h.blue}", h.blue in 0f..1f)
                assertEquals("harus buram", 1f, h.alpha, 0.0001f)
            }
        }
    }

    /**
     * Hitam dan putih tidak punya hue. Yang penting keluarannya tetap warna yang
     * sah dan tidak NaN, bukan hue tertentu.
     */
    @Test
    fun `warna netral tidak menghasilkan NaN`() {
        listOf(Color.Black, Color.White, Color(0xFF808080)).forEach { warna ->
            val h = AccentLock.lockedAccent(warna)
            assertTrue("merah NaN", !h.red.isNaN())
            assertTrue("hijau NaN", !h.green.isNaN())
            assertTrue("biru NaN", !h.blue.isNaN())
        }
    }

    @Test
    fun `deterministik`() {
        val warna = Color(0xFFE53935)
        val pertama = AccentLock.lockedAccent(warna)
        repeat(5) { assertEquals(pertama, AccentLock.lockedAccent(warna)) }
    }

    // ── dimOf ────────────────────────────────────────────────────

    /**
     * Baris romanisasi harus lebih redup dari baris aktif, tapi tetap terbaca.
     * Komentar di AccentLock mencatat versi sebelumnya terlalu gelap sampai
     * teks italic kecil susah dibaca.
     */
    @Test
    fun `dim lebih redup daripada aksen di mode gelap`() {
        warnaUji.forEach { (nama, warna) ->
            val aksen = AccentLock.lockedAccent(warna, dark = true)
            val dim = AccentLock.dimOf(aksen, dark = true)
            val lAksen = toOkLch(aksen).l
            val lDim = toOkLch(dim).l
            assertTrue("$nama: dim $lDim harus < aksen $lAksen", lDim < lAksen)
            assertTrue("$nama: dim $lDim terlalu gelap untuk dibaca", lDim > 0.55)
        }
    }

    @Test
    fun `dim mempertahankan hue aksen`() {
        warnaUji.forEach { (nama, warna) ->
            val aksen = AccentLock.lockedAccent(warna, dark = true)
            val dim = AccentLock.dimOf(aksen, dark = true)
            assertTrue(
                "$nama: hue dim bergeser",
                bedaHue(toOkLch(aksen).h, toOkLch(dim).h) < 0.25,
            )
        }
    }

    @Test
    fun `dim juga menghasilkan warna yang sah`() {
        warnaUji.forEach { (_, warna) ->
            listOf(true, false).forEach { dark ->
                val d = AccentLock.dimOf(AccentLock.lockedAccent(warna, dark), dark)
                assertTrue(d.red in 0f..1f && d.green in 0f..1f && d.blue in 0f..1f)
            }
        }
    }
}
