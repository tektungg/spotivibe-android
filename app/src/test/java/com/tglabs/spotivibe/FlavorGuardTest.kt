package com.tglabs.spotivibe

import com.tglabs.spotivibe.data.SUPPORTS_JAPANESE
import com.tglabs.spotivibe.data.createJapaneseTokenizer
import com.tglabs.spotivibe.data.supportedScripts
import com.tglabs.spotivibe.domain.ROMANIZABLE_ALL
import com.tglabs.spotivibe.domain.Script
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Penjaga pemisahan varian.
 *
 * Test ini berjalan untuk KEDUA varian dan mengharapkan hasil yang berbeda di
 * masing-masing. Yang dijaga bukan nilainya, melainkan bahwa tiga deklarasi
 * per-varian tetap SEPAKAT satu sama lain, dan bahwa `main` tidak pernah
 * menyentuh kuromoji.
 */
class FlavorGuardTest {

    private fun sumberMain(): List<File> {
        val akar = listOf(File("src/main/java"), File("app/src/main/java"))
            .firstOrNull { it.isDirectory }
        assertNotNull("direktori src/main/java tidak ketemu dari ${File(".").absolutePath}", akar)
        return akar!!.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    @Test
    fun `sumber main terbaca supaya penjaga ini tidak lolos kosong`() {
        assertTrue("harus memindai sesuatu", sumberMain().size > 20)
    }

    /**
     * REGRESI STRUKTURAL: seluruh gunanya varian `lite` bergantung pada satu
     * syarat, yaitu tidak ada kode di `main` yang menyebut kuromoji. SATU import
     * saja membuat varian tanpa dependensinya gagal compile.
     *
     * Kegagalan itu memang keras dan langsung, tapi test ini menangkapnya lebih
     * awal dan menjelaskan sebabnya, ketimbang meninggalkan pesan "unresolved
     * reference" di varian yang tidak sedang dikerjakan.
     */
    @Test
    fun `main tidak pernah menyentuh kuromoji`() {
        val pelanggar = sumberMain().flatMap { berkas ->
            berkas.readLines().withIndex()
                .filter { (_, baris) ->
                    val potong = baris.trim()
                    // Baris KDoc dan komentar dilewati. Menyebut nama pustakanya
                    // dalam penjelasan justru diperlukan supaya alasan
                    // pemisahannya tidak hilang; yang dilarang adalah import dan
                    // pemakaian nyata.
                    val komentar = potong.startsWith("*") ||
                        potong.startsWith("//") ||
                        potong.startsWith("/*")
                    !komentar && "com.atilika.kuromoji" in baris.substringBefore("//")
                }
                .map { (i, baris) -> "${berkas.name}:${i + 1}  ${baris.trim()}" }
        }
        assertTrue(
            "kuromoji cuma boleh disebut di src/full/. Kamusnya 12,71 MB dan " +
                "referensi di main membuat varian lite gagal compile:\n" +
                pelanggar.joinToString("\n"),
            pelanggar.isEmpty(),
        )
    }

    /**
     * Tiga deklarasi per-varian harus sepakat: bendera dukungan, ada tidaknya
     * pembaca token, dan daftar script yang ditawarkan.
     *
     * Kalau salah satu ketinggalan diperbarui, gejalanya jauh dari sebabnya.
     * Bendera true tanpa pembaca token berarti tombol romanisasi muncul lalu
     * tidak melakukan apa pun; daftar script yang masih memuat JA di varian lite
     * berarti hal yang sama.
     */
    @Test
    fun `bendera dukungan, pembaca token, dan daftar script saling sepakat`() {
        val adaPembaca = createJapaneseTokenizer() != null
        val menawarkanJa = Script.JA in supportedScripts()

        assertEquals(
            "SUPPORTS_JAPANESE=$SUPPORTS_JAPANESE tapi pembaca token ada=$adaPembaca",
            SUPPORTS_JAPANESE,
            adaPembaca,
        )
        assertEquals(
            "SUPPORTS_JAPANESE=$SUPPORTS_JAPANESE tapi JA ditawarkan=$menawarkanJa",
            SUPPORTS_JAPANESE,
            menawarkanJa,
        )
    }

    /**
     * Korea dan Mandarin harus tetap ada di KEDUA varian. Hangul dibaca per suku
     * kata tanpa kamus, dan tabel pinyin4j cuma 0,21 MB, jadi tidak ada alasan
     * ukuran untuk membuangnya.
     */
    @Test
    fun `Korea dan Mandarin selalu didukung di varian mana pun`() {
        assertTrue("Korea harus selalu ada", Script.KO in supportedScripts())
        assertTrue("Mandarin harus selalu ada", Script.ZH in supportedScripts())
    }

    @Test
    fun `script Latin tidak pernah ditawarkan untuk diromanisasi`() {
        assertFalse(Script.LATIN in supportedScripts())
        assertFalse(Script.LATIN in ROMANIZABLE_ALL)
    }

    /**
     * Daftar per varian harus TURUNAN dari daftar lengkap, bukan diketik ulang.
     * Script yang ditambahkan nanti harus otomatis ikut, tanpa ada yang perlu
     * ingat memperbarui dua tempat.
     */
    @Test
    fun `daftar varian adalah himpunan bagian dari daftar lengkap`() {
        assertTrue(
            "${supportedScripts()} bukan bagian dari $ROMANIZABLE_ALL",
            ROMANIZABLE_ALL.containsAll(supportedScripts()),
        )
    }

    @Test
    fun `varian penuh mendukung semuanya, varian ringan kurang tepat satu`() {
        if (SUPPORTS_JAPANESE) {
            assertEquals(ROMANIZABLE_ALL, supportedScripts())
            assertNotNull(createJapaneseTokenizer())
        } else {
            assertEquals(ROMANIZABLE_ALL - Script.JA, supportedScripts())
            assertNull(createJapaneseTokenizer())
            assertEquals(
                "hanya Jepang yang boleh hilang",
                1,
                (ROMANIZABLE_ALL - supportedScripts()).size,
            )
        }
    }
}
