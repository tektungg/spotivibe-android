package com.tglabs.spotivibe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackDecisionsTest {

    // ── Balapan fetch lirik ──────────────────────────────────────

    /**
     * REGRESI: fetch berjalan asinkron. Kalau user mengganti lagu sebelum
     * jaringan menjawab, jawaban untuk lagu LAMA tetap datang. Tanpa
     * pemeriksaan ini lirik lagu sebelumnya menempel di lagu yang sedang
     * diputar, dan bagi user itu terlihat seperti "liriknya salah", bukan
     * seperti bug balapan.
     */
    @Test
    fun `hasil fetch untuk lagu lain dibuang`() {
        assertFalse(shouldApplyFetchedLyrics("lagu_lama", "lagu_baru"))
    }

    @Test
    fun `hasil fetch untuk lagu yang sama dipakai`() {
        assertTrue(shouldApplyFetchedLyrics("lagu_a", "lagu_a"))
    }

    /**
     * Lagu berhenti sepenuhnya di tengah fetch. Menerapkan hasilnya berarti
     * memasang lirik ke layar yang sudah tidak menampilkan lagu apa pun.
     */
    @Test
    fun `hasil fetch dibuang kalau tidak ada lagu yang diputar`() {
        assertFalse(shouldApplyFetchedLyrics("lagu_a", null))
        assertFalse(shouldApplyFetchedLyrics("lagu_a", ""))
    }

    @Test
    fun `id kosong tidak pernah dianggap cocok`() {
        assertFalse("dua-duanya kosong bukan berarti cocok", shouldApplyFetchedLyrics(null, null))
        assertFalse(shouldApplyFetchedLyrics("", ""))
        assertFalse(shouldApplyFetchedLyrics("   ", "   "))
    }

    // ── Penerbitan index baris ───────────────────────────────────

    /**
     * Ticker berjalan terus selama lagu main. Menerbitkan nilai yang sama tiap
     * tick membangunkan seluruh konsumen sekaligus: layar, notification, dan
     * overlay.
     */
    @Test
    fun `index diterbitkan hanya kalau berubah`() {
        assertFalse(shouldPublishLineIndex(5, 5))
        assertTrue(shouldPublishLineIndex(6, 5))
    }

    @Test
    fun `perpindahan ke dan dari tanpa baris aktif tetap diterbitkan`() {
        assertTrue(shouldPublishLineIndex(-1, 0))
        assertTrue(shouldPublishLineIndex(0, -1))
        assertFalse(shouldPublishLineIndex(-1, -1))
    }

    // ── Pengambilan baris ────────────────────────────────────────

    /**
     * Index datang dari engine sync sementara daftar barisnya datang dari
     * fetch, dan keduanya berubah di waktu yang berbeda. Di sela itu index bisa
     * menunjuk ke luar daftar yang baru.
     */
    @Test
    fun `index di luar rentang menghasilkan null bukan crash`() {
        val baris = listOf("a", "b", "c")
        assertNull(baris.let { lineAt(it, 3) })
        assertNull(lineAt(baris, 99))
        assertNull("index -1 berarti belum ada baris aktif", lineAt(baris, -1))
    }

    @Test
    fun `index yang sah mengembalikan barisnya`() {
        val baris = listOf("a", "b", "c")
        assertEquals("a", lineAt(baris, 0))
        assertEquals("c", lineAt(baris, 2))
    }

    @Test
    fun `daftar null atau kosong menghasilkan null`() {
        assertNull(lineAt<String>(null, 0))
        assertNull(lineAt(emptyList<String>(), 0))
    }

    // ── Preload antrian ──────────────────────────────────────────

    /**
     * Antrian Web API bisa memuat entri tanpa id, misalnya iklan atau episode
     * lokal. Memintanya ke LRCLIB hanya membuang kuota.
     */
    @Test
    fun `entri tanpa id tidak di-preload`() {
        assertFalse(shouldPreload(null))
        assertFalse(shouldPreload(""))
        assertFalse(shouldPreload("   "))
    }

    @Test
    fun `entri dengan id di-preload`() {
        assertTrue(shouldPreload("spotify:track:abc"))
    }

    // ── Pengecekan product ───────────────────────────────────────

    /**
     * Jawabannya tidak berubah selama sesi. Mengulanginya tiap ganti lagu
     * memboroskan panggilan dan mendekatkan ke batas rate limit.
     */
    @Test
    fun `product hanya dicek sekali per sesi`() {
        assertTrue(shouldCheckProduct(verifiedThisSession = false, online = true))
        assertFalse(shouldCheckProduct(verifiedThisSession = true, online = true))
    }

    /**
     * REGRESI: dulu syaratnya "product masih null". Sejak product disimpan ke
     * disk, nilai hasil restore bukan null tapi juga belum tentu segar. Kalau
     * syarat lama dipakai, user yang baru upgrade ke Premium tidak pernah dicek
     * ulang dan terus melihat banner "Premium required".
     */
    @Test
    fun `nilai hasil restore tetap dicek ulang sekali saat online`() {
        assertTrue(shouldCheckProduct(verifiedThisSession = false, online = true))
    }

    /**
     * Offline: panggilannya pasti gagal, dan menunggunya berarti menahan
     * timeout koneksi setiap ganti lagu.
     */
    @Test
    fun `product tidak dicek saat offline`() {
        assertFalse(shouldCheckProduct(verifiedThisSession = false, online = false))
        assertFalse(shouldCheckProduct(verifiedThisSession = true, online = false))
    }

    // ── Accent dari album art ────────────────────────────────────

    /**
     * Dibandingkan lewat IDENTITAS, bukan isi. Membandingkan isi bitmap berarti
     * memindai jutaan piksel hanya untuk memutuskan apakah perlu memindainya.
     */
    @Test
    fun `accent tidak dihitung ulang untuk objek bitmap yang sama`() {
        val bitmap = Any()
        assertFalse(shouldRecomputeAccent(bitmap, bitmap))
    }

    @Test
    fun `dua objek berbeda dengan isi sama tetap dihitung ulang`() {
        // Sengaja: identitas, bukan kesamaan. Dua objek yang equals tapi beda
        // instance tetap memicu perhitungan.
        val a = listOf(1, 2, 3)
        val b = listOf(1, 2, 3)
        assertTrue("equals tapi beda instance", a == b)
        assertTrue(shouldRecomputeAccent(a, b))
    }

    @Test
    fun `bitmap hilang memicu perhitungan ulang`() {
        val bitmap = Any()
        assertTrue(shouldRecomputeAccent(null, bitmap))
        assertTrue(shouldRecomputeAccent(bitmap, null))
        assertFalse(shouldRecomputeAccent(null, null))
    }

    /**
     * Ekstraksi berjalan di dispatcher lain. Kalau album sudah berganti saat
     * hasilnya selesai, warnanya milik lagu yang salah.
     */
    @Test
    fun `accent yang selesai untuk album lama dibuang`() {
        val lama = Any()
        val baru = Any()
        assertFalse(shouldApplyAccent(lama, baru))
        assertTrue(shouldApplyAccent(baru, baru))
    }

    // ── Pemilihan warna Palette ──────────────────────────────────

    @Test
    fun `vibrant dipakai kalau ada`() {
        assertEquals(0xFF112233.toInt(), pickAccentArgb(0xFF112233.toInt(), 0xFF445566.toInt(), 0xFF778899.toInt(), 1))
    }

    /**
     * Palette mengembalikan nilai fallback yang kita berikan saat swatch tidak
     * ada, dan untuk `0` itu berarti "tidak ada".
     */
    @Test
    fun `turun ke lightVibrant saat vibrant tidak ada`() {
        assertEquals(0xFF445566.toInt(), pickAccentArgb(0, 0xFF445566.toInt(), 0xFF778899.toInt(), 1))
    }

    @Test
    fun `turun ke dominant saat dua-duanya tidak ada`() {
        assertEquals(0xFF778899.toInt(), pickAccentArgb(0, 0, 0xFF778899.toInt(), 1))
    }

    /**
     * REGRESI: album art monokrom sering tidak punya swatch vibrant sama
     * sekali. Tanpa rantai lengkap sampai fallback, accent-nya jadi hitam pekat
     * yang tidak terbaca terhadap teks.
     */
    @Test
    fun `sampul monokrom jatuh ke warna cadangan bukan hitam`() {
        val coral = 0xFFEC6A5C.toInt()
        assertEquals(coral, pickAccentArgb(0, 0, 0, coral))
    }

    @Test
    fun `urutan prioritasnya tetap`() {
        // Kalau semuanya ada, yang paling awal menang, bukan yang terakhir.
        assertEquals(1, pickAccentArgb(1, 2, 3, 4))
        assertEquals(2, pickAccentArgb(0, 2, 3, 4))
        assertEquals(3, pickAccentArgb(0, 0, 3, 4))
        assertEquals(4, pickAccentArgb(0, 0, 0, 4))
    }

    @Test
    fun `deterministik`() {
        repeat(5) { assertEquals(pickAccentArgb(0, 0, 5, 9), pickAccentArgb(0, 0, 5, 9)) }
    }
}
