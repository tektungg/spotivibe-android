package com.tglabs.spotivibe.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Penjaga sistem responsif.
 *
 * Sistemnya sendiri tidak bisa memaksa siapa pun memakainya: menulis
 * `LocalConfiguration.current.orientation` di layar baru tetap compile dan tetap
 * jalan, dan salahnya baru terlihat saat dipakai di perangkat yang bentuknya
 * beda. Persis itu yang terjadi sebelumnya, di empat file sekaligus, dan
 * berakhir jadi album yang memakan 267 dari 295 dp di HP landscape.
 *
 * Test ini memindai source dan gagal kalau cek orientasi mentah muncul lagi di
 * luar [Theme.kt], satu-satunya tempat yang boleh membacanya.
 */
class ResponsiveGuardTest {

    private val bolehBacaKonfigurasi = setOf("Theme.kt")

    private fun sumberUi(): List<File> {
        val kandidat = listOf(
            File("src/main/java/com/tglabs/spotivibe/ui"),
            File("app/src/main/java/com/tglabs/spotivibe/ui"),
        )
        val akar = kandidat.firstOrNull { it.isDirectory }
            ?: fail("direktori sumber UI tidak ketemu dari ${File(".").absolutePath}")
                .let { return emptyList() }
        return akar.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    @Test
    fun `sumber UI ketemu supaya penjaga ini tidak lolos kosong`() {
        val berkas = sumberUi()
        assertTrue("harus memindai sesuatu, dapat ${berkas.size} berkas", berkas.size > 5)
    }

    @Test
    fun `tidak ada cek orientasi mentah di luar Theme`() {
        val pelanggar = sumberUi()
            .filter { it.name !in bolehBacaKonfigurasi }
            .flatMap { berkas ->
                berkas.readLines().withIndex()
                    .filter { (_, baris) ->
                        val kode = baris.substringBefore("//")
                        "ORIENTATION_LANDSCAPE" in kode ||
                            "ORIENTATION_PORTRAIT" in kode ||
                            Regex("""LocalConfiguration\.current\.orientation""").containsMatchIn(kode)
                    }
                    .map { (i, baris) -> "${berkas.name}:${i + 1}  ${baris.trim()}" }
            }

        assertTrue(
            "Cek orientasi mentah. Orientasi tidak bisa membedakan HP landscape " +
                "dari tablet landscape. Pakai LocalSvWindow.current.isWide / .isShort:\n" +
                pelanggar.joinToString("\n"),
            pelanggar.isEmpty(),
        )
    }

    /**
     * Skala berbasis lebar saja adalah model flutter_screenutil, dan itu yang
     * membengkakkan elemen saat layar dirotasi. Kalau ada yang menulisnya
     * langsung, test ini yang menangkap.
     */
    @Test
    fun `tidak ada skala berbasis lebar buatan sendiri`() {
        val pelanggar = sumberUi()
            .filter { it.name !in setOf("SvWindow.kt", "SvScale.kt") }
            .flatMap { berkas ->
                berkas.readLines().withIndex()
                    .filter { (_, baris) ->
                        val kode = baris.substringBefore("//")
                        Regex("""screenWidthDp\s*/""").containsMatchIn(kode) ||
                            Regex("""/\s*DESIGN_WIDTH_DP""").containsMatchIn(kode)
                    }
                    .map { (i, baris) -> "${berkas.name}:${i + 1}  ${baris.trim()}" }
            }

        assertTrue(
            "Skala berbasis lebar membengkak 2,22x di HP landscape. Pakai svScale():\n" +
                pelanggar.joinToString("\n"),
            pelanggar.isEmpty(),
        )
    }

    // ── Safe area ────────────────────────────────────────────────

    /** Baris kode tanpa komentar baris dan tanpa isi KDoc. */
    private fun kodeDari(baris: String): String {
        val t = baris.trim()
        if (t.startsWith("*") || t.startsWith("/*")) return ""
        return baris.substringBefore("//")
    }

    /**
     * REGRESI: dulu tidak ada satu layar pun yang memakai insets, dan header
     * tertimpa navigation bar di head unit. Setiap layar penuh di ui/screen
     * wajib lewat svSafeContent. MainScreen dikecualikan karena ia hanya
     * router: insets di sana akan ikut memotong latar full-bleed layar anak.
     */
    @Test
    fun `setiap layar memakai safe area`() {
        val layar = sumberUi()
            .filter { it.parentFile?.name == "screen" && it.name != "MainScreen.kt" }
            .filter { berkas -> berkas.readLines().any { "@Composable" in kodeDari(it) } }
        assertTrue("harus menemukan layar, dapat ${layar.size}", layar.size >= 6)

        val tanpaInsets = layar
            .filterNot { berkas -> berkas.readLines().any { "svSafeContent(" in kodeDari(it) } }
            .map { it.name }

        assertTrue(
            "Layar tanpa safe area. Pasang .background(...).svSafeContent() di " +
                "container kontennya (lihat SvInsets.kt):\n" + tanpaInsets.joinToString("\n"),
            tanpaInsets.isEmpty(),
        )
    }

    /**
     * Semua insets lewat svSafeInsets() supaya test bisa menyuntikkan insets
     * palsu lewat LocalSvSafeInsets. Pemanggilan langsung melewati seam itu,
     * dan UI test safe area jadi diam-diam tidak menguji apa pun.
     */
    @Test
    fun `insets sistem hanya dibaca di SvInsets`() {
        val pelanggar = sumberUi()
            .filter { it.name != "SvInsets.kt" }
            .flatMap { berkas ->
                berkas.readLines().withIndex()
                    .filter { (_, baris) ->
                        val kode = kodeDari(baris)
                        Regex("""WindowInsets\.(safeDrawing|safeContent|systemBars|statusBars|navigationBars)\b""")
                            .containsMatchIn(kode)
                    }
                    .map { (i, baris) -> "${berkas.name}:${i + 1}  ${baris.trim()}" }
            }

        assertTrue(
            "Baca insets lewat svSafeInsets() / svSafeContent():\n" + pelanggar.joinToString("\n"),
            pelanggar.isEmpty(),
        )
    }

    /**
     * Pola lama: jarak atas tetap 16/32 dp sebagai pengganti clearance status
     * bar. Cukup untuk HP, tidak untuk head unit. Clearance harus dari insets.
     */
    @Test
    fun `tidak ada clearance status bar palsu`() {
        val pelanggar = sumberUi()
            .flatMap { berkas ->
                berkas.readLines().withIndex()
                    .filter { (_, baris) ->
                        Regex("""padding\(top\s*=\s*if\s*\(isLandscape\)""").containsMatchIn(kodeDari(baris))
                    }
                    .map { (i, baris) -> "${berkas.name}:${i + 1}  ${baris.trim()}" }
            }

        assertTrue(
            "Jarak atas berbasis orientasi untuk menghindari status bar. Pakai svSafeContent():\n" +
                pelanggar.joinToString("\n"),
            pelanggar.isEmpty(),
        )
    }
}
