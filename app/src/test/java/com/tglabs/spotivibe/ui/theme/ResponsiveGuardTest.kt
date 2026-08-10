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
}
