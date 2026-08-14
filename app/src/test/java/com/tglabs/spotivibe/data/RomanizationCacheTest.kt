package com.tglabs.spotivibe.data

import com.tglabs.spotivibe.domain.Script
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Test cache romanisasi.
 *
 * Sengaja memakai Korea dan Mandarin untuk sebagian besar kasus: keduanya murah
 * (tabel jamo murni Kotlin, dan pinyin4j lookup per karakter). Jepang cuma
 * dipakai di satu test, karena init kuromoji memuat IPADIC dan makan 1 sampai 2
 * detik sekali per proses.
 */
class RomanizationCacheTest {

    private lateinit var svc: RomanizationService

    @Before
    fun setUp() {
        svc = RomanizationService()
        svc.clearCache()
    }

    // ── Perilaku dasar cache ─────────────────────────────────────

    @Test
    fun `panggilan pertama miss, panggilan kedua hit`() = runTest {
        val teks = "사랑해"

        val pertama = svc.romanize(teks, Script.KO)
        assertEquals(1, svc.stats().misses)
        assertEquals(0, svc.stats().hits)

        val kedua = svc.romanize(teks, Script.KO)
        assertEquals("tidak boleh dihitung ulang", 1, svc.stats().misses)
        assertEquals(1, svc.stats().hits)

        assertEquals("hasil dari cache harus identik", pertama, kedua)
    }

    /**
     * REGRESI: key WAJIB memuat script. Teks yang sama menghasilkan romanisasi
     * berbeda tergantung script lagunya, jadi key berbasis teks saja akan
     * mengembalikan pinyin untuk lagu Jepang, atau sebaliknya.
     */
    @Test
    fun `teks sama dengan script berbeda tidak saling menimpa`() = runTest {
        val teks = "運命"

        val zh = svc.romanize(teks, Script.ZH)
        val ja = svc.romanize(teks, Script.JA)

        if (!SUPPORTS_JAPANESE) {
            // Tanpa kamus Jepang tidak ada dua hasil untuk ditukar. Yang tetap
            // harus dijaga: sisi Mandarin-nya utuh dan tidak ikut hilang.
            assertNull(ja)
            assertTrue("Mandarin harus tetap jalan di lite", !zh.isNullOrBlank())
            assertEquals("hanya entri ZH yang masuk cache", 1, svc.stats().size)
            assertEquals(zh, svc.romanize(teks, Script.ZH))
            assertEquals(1, svc.stats().hits)
            return@runTest
        }

        assertNotEquals("hasilnya memang harus berbeda", zh, ja)
        assertEquals("keduanya harus jadi entry terpisah", 2, svc.stats().size)
        assertEquals(2, svc.stats().misses)

        // Ambil ulang keduanya: harus kembali nilai masing-masing, bukan tertukar.
        assertEquals(zh, svc.romanize(teks, Script.ZH))
        assertEquals(ja, svc.romanize(teks, Script.JA))
        assertEquals(2, svc.stats().hits)
    }

    // ── Yang tidak boleh masuk cache ─────────────────────────────

    @Test
    fun `baris Latin tidak masuk cache`() = runTest {
        assertNull(svc.romanize("I love you", Script.KO))
        assertNull(svc.romanize("I love you", Script.KO))
        assertEquals("baris Latin murah dideteksi, tak perlu disimpan", 0, svc.stats().size)
    }

    @Test
    fun `baris kosong tidak masuk cache`() = runTest {
        assertNull(svc.romanize("", Script.KO))
        assertNull(svc.romanize("   ", Script.KO))
        assertEquals(0, svc.stats().size)
    }

    // ── Batas ukuran ─────────────────────────────────────────────

    @Test
    fun `cache dibatasi dan membuang yang paling lama tak dipakai`() = runTest {
        // Bangkitkan lebih banyak baris unik daripada batasnya.
        repeat(2_100) { i ->
            svc.romanize("사랑$i", Script.KO)
        }
        val size = svc.stats().size
        assertTrue("cache tidak boleh tumbuh tanpa batas, size=$size", size <= 2_000)
    }

    // ── Dedup baris kembar dalam satu lagu ───────────────────────

    /**
     * REGRESI: reff yang berulang dulu dikirim ke mesin romanisasi berkali-kali.
     * Karena semua baris dilempar ke async bersamaan, cache pun tidak menolong:
     * semuanya bisa miss sebelum ada yang sempat menulis hasil.
     *
     * Assert-nya sengaja pada lastBatchDistinct, BUKAN pada jumlah miss. Miss
     * bergantung penjadwalan coroutine, jadi tanpa dedup pun angkanya bisa
     * kebetulan benar dan test lolos padahal bug-nya ada.
     */
    @Test
    fun `reff berulang cuma dikirim sekali ke mesin romanisasi`() = runTest {
        val reff = listOf("사랑해", "너를", "영원히")
        val lagu = reff + listOf("다른 줄") + reff + reff

        val hasil = svc.romanizeLines(lagu)
        val s = svc.stats()

        assertEquals("panjang hasil harus sama dengan input", lagu.size, hasil.size)
        assertEquals("10 baris masuk", 10, s.lastBatchTotal)
        assertEquals("tapi cuma 4 yang unik", 4, s.lastBatchDistinct)
        assertEquals("dan cuma 4 entry cache yang terbentuk", 4, s.size)
    }

    @Test
    fun `lagu tanpa baris kembar tidak berubah oleh dedup`() = runTest {
        val lagu = listOf("사랑해", "너를", "영원히", "우리")
        svc.romanizeLines(lagu)
        val s = svc.stats()
        assertEquals(4, s.lastBatchTotal)
        assertEquals(4, s.lastBatchDistinct)
    }

    @Test
    fun `baris kembar mendapat hasil yang sama persis`() = runTest {
        val lagu = listOf("사랑해", "너를", "사랑해")
        val hasil = svc.romanizeLines(lagu)
        assertEquals(hasil[0], hasil[2])
    }

    @Test
    fun `pemetaan hasil tetap sejajar dengan input`() = runTest {
        val lagu = listOf("사랑해", "plain english", "", "너를")
        val hasil = svc.romanizeLines(lagu)

        assertEquals(4, hasil.size)
        assertTrue("baris 0 harus diromanisasi", hasil[0] != null)
        assertNull("baris Latin tetap null", hasil[1])
        assertNull("baris kosong tetap null", hasil[2])
        assertTrue("baris 3 harus diromanisasi", hasil[3] != null)
    }

    // ── Toggle off lalu on ───────────────────────────────────────

    /**
     * Ini pemicu paling sering di pemakaian nyata: user mematikan lalu
     * menyalakan lagi toggle romanisasi pada lagu yang sama.
     */
    @Test
    fun `menjalankan lagu yang sama dua kali tidak menghitung ulang`() = runTest {
        val lagu = listOf("사랑해", "너를", "영원히", "우리")

        svc.romanizeLines(lagu)
        val setelahPertama = svc.stats()

        svc.romanizeLines(lagu)
        val setelahKedua = svc.stats()

        assertEquals(
            "putaran kedua tidak boleh menghitung apa pun",
            setelahPertama.misses,
            setelahKedua.misses,
        )
        assertEquals(
            "semua baris putaran kedua harus dari cache",
            lagu.size.toLong(),
            setelahKedua.hits - setelahPertama.hits,
        )
    }

    @Test
    fun `lagu Latin murni tidak menyentuh cache sama sekali`() = runTest {
        val hasil = svc.romanizeLines(listOf("Hello", "world", "yeah"))
        assertTrue(hasil.all { it == null })
        assertEquals(0, svc.stats().size)
        assertEquals(0, svc.stats().misses)
    }

    // ── Jepang, satu kali saja karena kuromoji berat ─────────────

    @Test
    fun `romanisasi Jepang jalan dan ikut ter-cache`() = runTest {
        val teks = "こんにちは"

        val pertama = svc.romanize(teks, Script.JA)
        if (!SUPPORTS_JAPANESE) {
            // Varian lite tidak membawa kamus Jepang. Hasil null adalah
            // perilaku yang BENAR di sini, dan null sengaja tidak di-cache
            // supaya tidak ada ambiguitas antara "belum dihitung" dan "pernah
            // dihitung, hasilnya null".
            assertNull("lite tidak boleh menghasilkan romaji Jepang", pertama)
            assertEquals(0, svc.stats().hits)
            return@runTest
        }

        assertTrue("harus menghasilkan romaji, dapat: $pertama", !pertama.isNullOrBlank())
        assertEquals(1, svc.stats().misses)

        val kedua = svc.romanize(teks, Script.JA)
        assertEquals(pertama, kedua)
        assertEquals(1, svc.stats().hits)
    }
}
