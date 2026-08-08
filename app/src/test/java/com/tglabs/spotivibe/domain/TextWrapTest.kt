package com.tglabs.spotivibe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextWrapTest {

    /**
     * Pengukur palsu yang meniru sifat penting font sungguhan: karakter CJK
     * kira-kira dua kali lebar karakter Latin. Angka bulat supaya assert-nya
     * bisa persis.
     */
    private val measure: (String) -> Float = { s ->
        s.sumOf { if (it.isWide()) 2 else 1 }.toFloat()
    }

    private fun Char.isWide(): Boolean =
        this in '぀'..'ヿ' || this in '一'..'鿿' || this in '가'..'힯'

    private fun wrap(text: String, maxWidth: Float) = wrapText(text, maxWidth, measure)

    // ── Latin ────────────────────────────────────────────────────

    @Test
    fun `teks pendek tidak dipecah`() {
        assertEquals(listOf("halo"), wrap("halo", 100f))
    }

    @Test
    fun `latin dipecah di spasi`() {
        assertEquals(listOf("halo", "dunia"), wrap("halo dunia", 6f))
    }

    @Test
    fun `baris baru tidak diawali spasi`() {
        wrap("aaa bbb ccc", 4f).forEach {
            assertTrue("baris diawali spasi: '$it'", !it.startsWith(" "))
        }
    }

    @Test
    fun `tidak ada baris yang melebihi lebar`() {
        val hasil = wrap("satu dua tiga empat lima enam tujuh", 10f)
        hasil.forEach {
            assertTrue("baris '$it' lebarnya ${measure(it)} > 10", measure(it) <= 10f)
        }
    }

    // ── CJK ──────────────────────────────────────────────────────

    /**
     * REGRESI: inti perbaikan ini.
     *
     * Versi lama memecah dengan `split(' ')`. Jepang dan Mandarin tidak memakai
     * spasi, jadi seluruh baris jadi SATU atom yang tidak pernah dipotong lalu
     * digambar keluar kanvas. Dua dari tiga bahasa yang jadi alasan app ini ada
     * menghasilkan share card terpotong.
     */
    @Test
    fun `lirik Jepang tanpa spasi tetap dipecah`() {
        val teks = "君の名前を呼ぶ夜明け前の空"
        val hasil = wrap(teks, 10f)

        assertTrue("harus jadi lebih dari satu baris, dapat $hasil", hasil.size > 1)
        hasil.forEach {
            assertTrue("baris '$it' lebarnya ${measure(it)} > 10", measure(it) <= 10f)
        }
        assertEquals("tidak boleh ada karakter hilang", teks, hasil.joinToString(""))
    }

    @Test
    fun `lirik Mandarin tanpa spasi tetap dipecah`() {
        val teks = "月亮代表我的心永远不变"
        val hasil = wrap(teks, 8f)
        assertTrue(hasil.size > 1)
        assertEquals(teks, hasil.joinToString(""))
    }

    @Test
    fun `dengan split spasi lama teks CJK akan jadi satu baris kelebaran`() {
        // Mendokumentasikan bug lamanya secara eksplisit.
        val teks = "君の名前を呼ぶ夜明け前の空"
        val caraLama = teks.split(' ')
        assertEquals("split spasi tidak memecah apa pun", 1, caraLama.size)
        assertTrue("dan hasilnya jauh melebihi kanvas", measure(caraLama[0]) > 10f)
    }

    @Test
    fun `campuran Latin dan Jepang dipecah di keduanya`() {
        val teks = "Oh baby 君を想う yeah"
        val hasil = wrap(teks, 8f)
        hasil.forEach { assertTrue(measure(it) <= 8f) }
        assertEquals(teks.replace(" ", ""), hasil.joinToString("").replace(" ", ""))
    }

    @Test
    fun `Korea memakai spasi jadi tetap dipecah seperti Latin`() {
        val teks = "너의 이름을 부르는 밤"
        val hasil = wrap(teks, 8f)
        hasil.forEach { assertTrue(measure(it) <= 8f) }
    }

    // ── Kinsoku dasar ────────────────────────────────────────────

    /**
     * Tanpa ini, baris baru bisa dimulai dengan koma atau kurung tutup, yang di
     * teks Jepang terlihat jelas salah.
     */
    @Test
    fun `baris tidak dimulai dengan tanda baca penutup`() {
        val teks = "君の名前、夜明けの空。"
        wrap(teks, 6f).forEach { baris ->
            assertTrue(
                "baris dimulai dengan tanda baca penutup: '$baris'",
                baris.firstOrNull() !in listOf('、', '。', '）', '」'),
            )
        }
    }

    @Test
    fun `baris tidak dimulai dengan chouonpu atau kana kecil`() {
        val teks = "コーヒーショップでちょっと"
        wrap(teks, 6f).forEach { baris ->
            assertTrue(
                "baris dimulai dengan karakter lanjutan: '$baris'",
                baris.firstOrNull() !in listOf('ー', 'ョ', 'ッ', 'ゃ', 'ゅ', 'ょ', 'っ'),
            )
        }
    }

    // ── Kasus tepi ───────────────────────────────────────────────

    @Test
    fun `satu kata Latin lebih lebar dari kanvas dipotong paksa`() {
        val teks = "supercalifragilisticexpialidocious"
        val hasil = wrap(teks, 10f)
        assertTrue("harus dipotong, dapat $hasil", hasil.size > 1)
        hasil.forEach { assertTrue("baris '$it' kelebaran", measure(it) <= 10f) }
        assertEquals(teks, hasil.joinToString(""))
    }

    @Test
    fun `teks kosong menghasilkan satu baris kosong`() {
        assertEquals(listOf(""), wrap("", 100f))
    }

    @Test
    fun `teks hanya spasi tidak menghasilkan sampah`() {
        assertEquals(listOf(""), wrap("   ", 100f))
    }

    @Test
    fun `lebar nol tidak bikin loop tak berujung`() {
        val hasil = wrapText("halo dunia", 0f, measure)
        assertEquals(listOf("halo dunia"), hasil)
    }

    @Test
    fun `tidak ada karakter non-spasi yang hilang`() {
        val kasus = listOf(
            "halo dunia yang panjang sekali",
            "君の名前を呼ぶ",
            "月亮代表我的心",
            "Mixed テキスト and 汉字 together",
            "너의 이름",
        )
        kasus.forEach { teks ->
            val gabung = wrap(teks, 7f).joinToString("").replace(" ", "")
            assertEquals("teks='$teks'", teks.replace(" ", ""), gabung)
        }
    }

    // ── textAtoms ────────────────────────────────────────────────

    @Test
    fun `atom menyambung kembali jadi teks asli`() {
        val kasus = listOf("halo dunia", "君の名前", "Mixed テキスト ok", "  spasi  ganda  ")
        kasus.forEach { assertEquals(it, textAtoms(it).joinToString("")) }
    }

    @Test
    fun `tiap karakter CJK jadi atom sendiri`() {
        assertEquals(listOf("君", "の", "名"), textAtoms("君の名"))
    }

    @Test
    fun `kata Latin tetap utuh sebagai satu atom`() {
        assertEquals(listOf("halo", " ", "dunia"), textAtoms("halo dunia"))
    }

    // ── truncateText ─────────────────────────────────────────────

    @Test
    fun `teks yang muat tidak dipotong`() {
        assertEquals("halo", truncateText("halo", 100f, measure))
    }

    @Test
    fun `teks kepanjangan dipotong dengan ellipsis`() {
        val hasil = truncateText("halo dunia yang panjang", 10f, measure)
        assertTrue("harus diakhiri ellipsis, dapat '$hasil'", hasil.endsWith("…"))
        assertTrue("harus muat, lebar ${measure(hasil)}", measure(hasil) <= 10f)
    }

    @Test
    fun `judul CJK kepanjangan juga dipotong`() {
        val hasil = truncateText("月亮代表我的心永远不变", 10f, measure)
        assertTrue(hasil.endsWith("…"))
        assertTrue(measure(hasil) <= 10f)
    }
}
