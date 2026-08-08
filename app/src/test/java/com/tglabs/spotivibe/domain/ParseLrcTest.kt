package com.tglabs.spotivibe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseLrcTest {

    // ── Format standar ───────────────────────────────────────────

    @Test
    fun `baris standar terbaca`() {
        val lines = parseLrc("[01:23.45]Halo dunia")
        assertEquals(1, lines.size)
        assertEquals(83_450L, lines[0].timeMs)
        assertEquals("Halo dunia", lines[0].text)
    }

    @Test
    fun `centisecond dan milisecond sama-sama benar`() {
        assertEquals(1_450L, parseLrc("[00:01.45]a")[0].timeMs)
        assertEquals(1_450L, parseLrc("[00:01.450]a")[0].timeMs)
        assertEquals(1_500L, parseLrc("[00:01.5]a")[0].timeMs)
        assertEquals(1_000L, parseLrc("[00:01]a")[0].timeMs)
    }

    @Test
    fun `pemisah titik dua untuk pecahan juga diterima`() {
        assertEquals(1_450L, parseLrc("[00:01:45]a")[0].timeMs)
    }

    @Test
    fun `hasil terurut naik walau file tidak urut`() {
        val lines = parseLrc(
            """
            [00:30.00]ketiga
            [00:10.00]pertama
            [00:20.00]kedua
            """.trimIndent()
        )
        assertEquals(listOf("pertama", "kedua", "ketiga"), lines.map { it.text })
    }

    @Test
    fun `baris tanpa timestamp dilewati`() {
        val lines = parseLrc("ini bukan lirik\n[00:01.00]ini lirik")
        assertEquals(1, lines.size)
        assertEquals("ini lirik", lines[0].text)
    }

    @Test
    fun `lirik kosong tetap jadi baris untuk menandai jeda`() {
        val lines = parseLrc("[00:10.00]\n[00:20.00]nyanyi")
        assertEquals(2, lines.size)
        assertEquals("", lines[0].text)
    }

    // ── Multi-timestamp ──────────────────────────────────────────

    /**
     * REGRESI. Versi lama memakai `Regex.find` yang hanya mengambil kecocokan
     * PERTAMA, jadi dua hal rusak sekaligus: timestamp kedua hilang, sehingga
     * reff tidak menyala di kemunculan berikutnya, DAN sisa string yang belum
     * ter-konsumsi ikut jadi teks, sehingga user melihat literal `[01:30.00]`
     * di layar.
     */
    @Test
    fun `multi-timestamp menghasilkan satu baris per waktu`() {
        val lines = parseLrc("[00:12.00][01:30.00]Reff")
        assertEquals(2, lines.size)
        assertEquals(listOf(12_000L, 90_000L), lines.map { it.timeMs })
        assertTrue("semua harus punya teks yang sama", lines.all { it.text == "Reff" })
    }

    @Test
    fun `timestamp kedua tidak bocor ke teks yang ditampilkan`() {
        val lines = parseLrc("[00:12.00][01:30.00]Reff")
        lines.forEach { line ->
            assertTrue("timestamp bocor: '${line.text}'", '[' !in line.text)
        }
    }

    @Test
    fun `tiga timestamp sekaligus`() {
        val lines = parseLrc("[00:05.00][00:35.00][01:05.00]Chorus")
        assertEquals(3, lines.size)
        assertEquals(listOf(5_000L, 35_000L, 65_000L), lines.map { it.timeMs })
    }

    @Test
    fun `spasi di antara tag timestamp tetap terbaca`() {
        val lines = parseLrc("[00:12.00] [01:30.00] Reff")
        assertEquals(2, lines.size)
        assertEquals("Reff", lines[0].text)
    }

    @Test
    fun `baris multi-timestamp disisipkan pada urutan waktu yang benar`() {
        val lines = parseLrc(
            """
            [00:10.00][00:30.00]reff
            [00:20.00]bait
            """.trimIndent()
        )
        assertEquals(listOf("reff", "bait", "reff"), lines.map { it.text })
    }

    // ── Tag metadata ─────────────────────────────────────────────

    @Test
    fun `tag metadata tidak jadi lirik`() {
        val lines = parseLrc(
            """
            [ar:Nama Artis]
            [ti:Judul Lagu]
            [al:Album]
            [by:kontributor]
            [length:03:21]
            [00:01.00]lirik asli
            """.trimIndent()
        )
        assertEquals(1, lines.size)
        assertEquals("lirik asli", lines[0].text)
    }

    /**
     * `[offset:...]` sengaja tidak diterapkan, tapi WAJIB dilewati supaya tidak
     * muncul sebagai baris lirik palsu di layar.
     */
    @Test
    fun `tag offset dilewati bukan ditampilkan`() {
        val lines = parseLrc("[offset:+500]\n[00:01.00]lirik")
        assertEquals(1, lines.size)
        assertEquals("lirik", lines[0].text)
    }

    @Test
    fun `tag metadata tidak case sensitive`() {
        assertTrue(parseLrc("[AR:Artis]\n[TI:Judul]").isEmpty())
    }

    // ── Enhanced LRC ─────────────────────────────────────────────

    @Test
    fun `timing per-kata terbaca`() {
        val lines = parseLrc("[00:01.00]<00:01.00>Halo <00:01.70>dunia")
        assertEquals(1, lines.size)
        assertEquals("Halo dunia", lines[0].text)
        assertEquals(2, lines[0].words.size)
        assertEquals(1_000L, lines[0].words[0].timeMs)
        assertEquals(1_700L, lines[0].words[1].timeMs)
    }

    /**
     * Timing per-kata itu absolut, jadi tidak mungkin benar untuk lebih dari
     * satu kemunculan. Lebih baik turun ke highlight per-baris daripada
     * menyalakan kata di waktu yang salah.
     */
    @Test
    fun `timing per-kata dibuang kalau barisnya multi-timestamp`() {
        val lines = parseLrc("[00:01.00][00:31.00]<00:01.00>Halo <00:01.70>dunia")
        assertEquals(2, lines.size)
        assertTrue("kata tidak boleh dibawa ke kemunculan kedua", lines.all { it.words.isEmpty() })
        assertTrue("teks tetap utuh", lines.all { it.text == "Halo dunia" })
    }

    // ── Identitas baris ──────────────────────────────────────────

    @Test
    fun `id berurutan mulai dari nol`() {
        val lines = parseLrc("[00:01.00]a\n[00:02.00]b\n[00:03.00]c")
        assertEquals(listOf(0, 1, 2), lines.map { it.id })
    }

    /**
     * REGRESI. `timeMs` dipakai sebagai key `LazyColumn`, key map romaji, dan
     * set seleksi. Timestamp kembar itu sah di format LRC, dan waktu itu
     * Compose langsung melempar IllegalArgumentException: Key was already used.
     */
    @Test
    fun `timestamp kembar tetap menghasilkan id yang unik`() {
        val lines = parseLrc(
            """
            [00:10.00]baris pertama
            [00:10.00]baris kedua
            [00:10.00]baris ketiga
            """.trimIndent()
        )
        assertEquals(3, lines.size)
        assertEquals("timeMs memang kembar", 1, lines.map { it.timeMs }.distinct().size)
        assertEquals("tapi id wajib unik", 3, lines.map { it.id }.distinct().size)
    }

    @Test
    fun `id unik untuk lirik apapun termasuk multi-timestamp`() {
        val lines = parseLrc(
            """
            [00:10.00][00:40.00]reff
            [00:10.00]bait bersamaan
            [00:20.00]lain
            """.trimIndent()
        )
        assertEquals(lines.size, lines.map { it.id }.distinct().size)
    }

    @Test
    fun `urutan file dipertahankan untuk timestamp kembar`() {
        val lines = parseLrc("[00:10.00]pertama\n[00:10.00]kedua")
        assertEquals(listOf("pertama", "kedua"), lines.map { it.text })
        assertTrue(lines[0].id < lines[1].id)
    }

    @Test
    fun `baris dengan teks sama tetap dibedakan oleh id`() {
        val lines = parseLrc("[00:10.00]sama\n[00:10.00]sama")
        assertNotEquals(lines[0].id, lines[1].id)
        assertEquals(lines[0].timeMs, lines[1].timeMs)
        assertEquals(lines[0].text, lines[1].text)
    }

    // ── withPositionalIds ────────────────────────────────────────

    @Test
    fun `id dipulihkan berdasarkan posisi`() {
        val mentah = listOf(
            SyncedLine(id = 0, timeMs = 100, text = "a"),
            SyncedLine(id = 0, timeMs = 100, text = "b"),
            SyncedLine(id = 0, timeMs = 200, text = "c"),
        )
        assertEquals(listOf(0, 1, 2), mentah.withPositionalIds().map { it.id })
    }

    @Test
    fun `id yang sudah benar tidak diubah`() {
        val lines = parseLrc("[00:01.00]a\n[00:02.00]b")
        assertEquals(lines, lines.withPositionalIds())
    }

    // ── Ketahanan ────────────────────────────────────────────────

    @Test
    fun `input kosong menghasilkan daftar kosong`() {
        assertTrue(parseLrc("").isEmpty())
        assertTrue(parseLrc("\n\n\n").isEmpty())
    }

    @Test
    fun `kurung siku di dalam lirik tidak merusak parsing`() {
        val lines = parseLrc("[00:01.00]lirik [dengan] kurung")
        assertEquals(1, lines.size)
        assertEquals("lirik [dengan] kurung", lines[0].text)
    }

    @Test
    fun `lagu lebih dari satu jam tetap terbaca`() {
        assertEquals(3_723_000L, parseLrc("[62:03.00]a")[0].timeMs)
    }
}
