package com.tglabs.spotivibe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheEvictionTest {

    private val hariMs = 24L * 60L * 60L * 1000L
    private val now = 1_700_000_000_000L
    private val maxAge = 30 * hariMs

    private fun f(nama: String, umurHari: Long, kb: Long = 4) = CacheFileInfo(
        name = nama,
        sizeBytes = kb * 1024,
        lastAccessMs = now - umurHari * hariMs,
    )

    private fun evict(
        files: List<CacheFileInfo>,
        maxEntries: Int = 500,
        maxBytes: Long = 8L * 1024 * 1024,
    ) = filesToEvict(files, now, maxAge, maxEntries, maxBytes)

    // ── Tahap 1: usia ────────────────────────────────────────────

    /**
     * REGRESI: inti perbaikan ini. Sebelumnya file hanya terhapus kalau
     * KEBETULAN dibaca lagi setelah kedaluwarsa. Lagu yang diputar sekali lalu
     * tidak pernah disentuh menetap selamanya.
     */
    @Test
    fun `file yang terlalu tua dibuang walau tidak pernah dibaca lagi`() {
        val hasil = evict(listOf(f("tua", 40), f("baru", 1)))
        assertEquals(listOf("tua"), hasil)
    }

    @Test
    fun `tepat di batas usia belum dibuang`() {
        assertTrue(evict(listOf(f("pas", 30))).isEmpty())
    }

    @Test
    fun `cache kosong tidak menghasilkan apa-apa`() {
        assertTrue(evict(emptyList()).isEmpty())
    }

    @Test
    fun `semua masih segar dan muat berarti tidak ada yang dibuang`() {
        val files = (1..10).map { f("lagu$it", umurHari = it.toLong()) }
        assertTrue(evict(files).isEmpty())
    }

    // ── Tahap 2: jumlah entry ────────────────────────────────────

    @Test
    fun `kelebihan jumlah membuang yang paling lama tidak dipakai`() {
        val files = listOf(
            f("paling-lama", 10),
            f("sedang", 5),
            f("paling-baru", 1),
        )
        assertEquals(listOf("paling-lama"), evict(files, maxEntries = 2))
    }

    @Test
    fun `membuang secukupnya saja sampai muat`() {
        val files = (1..10).map { f("lagu$it", umurHari = it.toLong()) }
        val hasil = evict(files, maxEntries = 6)
        assertEquals("harus buang 4, bukan lebih", 4, hasil.size)
        // Yang paling lama tidak dipakai adalah umur terbesar.
        assertTrue(hasil.containsAll(listOf("lagu10", "lagu9", "lagu8", "lagu7")))
    }

    @Test
    fun `yang tersisa selalu di bawah batas jumlah`() {
        val files = (1..100).map { f("lagu$it", umurHari = (it % 20).toLong()) }
        val buang = evict(files, maxEntries = 30).toSet()
        val sisa = files.filter { it.name !in buang }
        assertTrue("sisa ${sisa.size} masih di atas 30", sisa.size <= 30)
    }

    // ── Tahap 2: batas byte ──────────────────────────────────────

    @Test
    fun `kelebihan byte membuang walau jumlahnya sedikit`() {
        val files = listOf(
            f("besar-lama", 10, kb = 3000),
            f("besar-baru", 1, kb = 3000),
        )
        val hasil = evict(files, maxEntries = 100, maxBytes = 4L * 1024 * 1024)
        assertEquals(listOf("besar-lama"), hasil)
    }

    @Test
    fun `satu entry patologis tidak lolos hanya karena jumlahnya satu`() {
        val files = listOf(f("raksasa", 1, kb = 50_000))
        val hasil = evict(files, maxEntries = 100, maxBytes = 8L * 1024 * 1024)
        assertEquals(listOf("raksasa"), hasil)
    }

    @Test
    fun `sisa selalu di bawah kedua batas sekaligus`() {
        val files = (1..60).map { f("lagu$it", umurHari = (it % 15).toLong(), kb = 200) }
        val maxBytes = 4L * 1024 * 1024
        val buang = evict(files, maxEntries = 50, maxBytes = maxBytes).toSet()
        val sisa = files.filter { it.name !in buang }
        assertTrue("jumlah ${sisa.size}", sisa.size <= 50)
        assertTrue("byte ${sisa.sumOf { it.sizeBytes }}", sisa.sumOf { it.sizeBytes } <= maxBytes)
    }

    // ── Interaksi kedua tahap ────────────────────────────────────

    @Test
    fun `file kedaluwarsa tidak dihitung dua kali`() {
        val files = listOf(f("tua", 40), f("a", 3), f("b", 2), f("c", 1))
        val hasil = evict(files, maxEntries = 2)
        assertEquals("tidak boleh ada nama berulang", hasil.size, hasil.distinct().size)
        assertTrue(hasil.contains("tua"))
    }

    @Test
    fun `membuang yang kedaluwarsa bisa membuat sisanya sudah muat`() {
        val files = listOf(f("tua1", 40), f("tua2", 35), f("a", 2), f("b", 1))
        val hasil = evict(files, maxEntries = 2)
        assertEquals(
            "cukup buang yang kedaluwarsa saja",
            setOf("tua1", "tua2"),
            hasil.toSet(),
        )
    }

    // ── Determinisme ─────────────────────────────────────────────

    /**
     * Tanpa pemecah seri, dua file dengan stempel waktu identik dibuang menurut
     * urutan listing direktori, yang tidak dijamin dan bikin hasilnya tidak bisa
     * diuji maupun dijelaskan.
     */
    @Test
    fun `stempel waktu kembar dibuang dengan urutan yang tetap`() {
        val files = listOf(f("z", 5), f("a", 5), f("m", 5), f("baru", 1))
        val pertama = evict(files, maxEntries = 2)
        repeat(5) { assertEquals(pertama, evict(files, maxEntries = 2)) }
        assertEquals(listOf("a", "m"), pertama)
    }

    @Test
    fun `urutan masukan tidak mengubah hasil`() {
        val files = listOf(f("a", 9), f("b", 5), f("c", 1))
        assertEquals(
            evict(files, maxEntries = 1).toSet(),
            evict(files.reversed(), maxEntries = 1).toSet(),
        )
    }

    // ── Penjagaan argumen ────────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `maxEntries negatif ditolak`() {
        evict(listOf(f("a", 1)), maxEntries = -1)
    }

    @Test
    fun `batas nol membuang semuanya`() {
        val files = listOf(f("a", 1), f("b", 2))
        assertEquals(2, evict(files, maxEntries = 0, maxBytes = 0).size)
    }

    // ── Retensi offline ──────────────────────────────────────────

    /**
     * Dengan batas usia produksi, lirik yang sudah lewat TTL 30 hari tapi
     * belum lewat retensi tetap ada di disk sebagai cadangan offline.
     */
    @Test
    fun `dengan retensi produksi berkas basi bertahan sampai 180 hari`() {
        val files = listOf(f("basi", 179), f("terlalu_tua", 181), f("segar", 2))
        val buang = filesToEvict(files, now, STALE_RETENTION_MS, 500, 8L * 1024 * 1024)
        assertEquals(listOf("terlalu_tua"), buang)
    }
}
