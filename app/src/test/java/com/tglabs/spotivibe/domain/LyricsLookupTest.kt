package com.tglabs.spotivibe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsLookupTest {

    private val synced = LyricsResult(
        trackId = "spotify:track:abc",
        synced = listOf(SyncedLine(id = 0, timeMs = 0, text = "halo")),
        plain = null,
    )
    private val plainOnly = LyricsResult(
        trackId = "spotify:track:abc",
        synced = null,
        plain = "halo dunia",
    )

    private fun content(r: LyricsResult = synced) = LrclibProbe.Content(r)
    private fun failed(reason: String = "timeout") = LrclibProbe.Failed(reason)

    // ── combineProbes ────────────────────────────────────────────

    @Test
    fun `get berisi konten dipakai lebih dulu`() {
        val hasil = combineProbes(content(synced), content(plainOnly))
        assertEquals(LyricsLookup.Found(synced), hasil)
    }

    @Test
    fun `search dipakai kalau get tidak punya`() {
        val hasil = combineProbes(LrclibProbe.Absent, content(plainOnly))
        assertEquals(LyricsLookup.Found(plainOnly), hasil)
    }

    @Test
    fun `search dipakai walau get gagal`() {
        val hasil = combineProbes(failed(), content(synced))
        assertEquals(LyricsLookup.Found(synced), hasil)
    }

    /** Dua-duanya berhasil dan sama-sama bilang tidak ada. Ini jawaban asli. */
    @Test
    fun `kedua probe absent berarti NotFound dan boleh di-cache`() {
        assertEquals(
            LyricsLookup.NotFound,
            combineProbes(LrclibProbe.Absent, LrclibProbe.Absent),
        )
    }

    /**
     * REGRESI: inti perbaikan ini.
     *
     * Dulu semua exception ditelan jadi satu LyricsResult kosong yang ditulis
     * ke disk dengan TTL negatif 1 hari. Satu lagu diputar saat sinyal hilang,
     * dan liriknya hilang 24 jam walaupun jaringan balik semenit kemudian.
     */
    @Test
    fun `kedua probe gagal TIDAK boleh jadi NotFound`() {
        val hasil = combineProbes(failed("timeout"), failed("timeout"))
        assertTrue("gagal jaringan bukan jawaban", hasil is LyricsLookup.Unavailable)
    }

    /**
     * Satu probe gagal berarti kita tidak benar-benar tahu: yang gagal itu
     * mungkin punya liriknya. Menyimpannya sebagai "tidak ada" akan
     * menyembunyikan lirik selama masa TTL negatif.
     */
    @Test
    fun `satu absent satu gagal tetap Unavailable`() {
        assertTrue(
            combineProbes(LrclibProbe.Absent, failed()) is LyricsLookup.Unavailable
        )
        assertTrue(
            combineProbes(failed(), LrclibProbe.Absent) is LyricsLookup.Unavailable
        )
    }

    @Test
    fun `alasan kegagalan diteruskan untuk log`() {
        val hasil = combineProbes(failed("http_503"), LrclibProbe.Absent)
        assertEquals("http_503", (hasil as LyricsLookup.Unavailable).reason)
    }

    // ── probeFromHttpFailure ─────────────────────────────────────

    /** 404 dari /get adalah jawaban: kombinasi metadata itu tidak ada. */
    @Test
    fun `404 adalah jawaban sungguhan`() {
        assertEquals(LrclibProbe.Absent, probeFromHttpFailure(404))
    }

    @Test
    fun `rate limit bersifat sementara`() {
        assertTrue(probeFromHttpFailure(429) is LrclibProbe.Failed)
    }

    @Test
    fun `error server bersifat sementara`() {
        assertTrue(probeFromHttpFailure(500) is LrclibProbe.Failed)
        assertTrue(probeFromHttpFailure(502) is LrclibProbe.Failed)
        assertTrue(probeFromHttpFailure(503) is LrclibProbe.Failed)
    }

    @Test
    fun `403 bersifat sementara`() {
        assertTrue(probeFromHttpFailure(403) is LrclibProbe.Failed)
    }

    // ── retryBackoffMs ───────────────────────────────────────────

    @Test
    fun `backoff naik eksponensial`() {
        assertEquals(400L, retryBackoffMs(1))
        assertEquals(800L, retryBackoffMs(2))
        assertEquals(1_600L, retryBackoffMs(3))
    }

    @Test
    fun `backoff dipotong supaya user tidak menunggu lama`() {
        assertEquals(3_000L, retryBackoffMs(4))
        assertEquals(3_000L, retryBackoffMs(20))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `attempt nol ditolak`() {
        retryBackoffMs(0)
    }

    // ── cacheEntryFor: aturan tulis cache ────────────────────────
    //
    // Ini fungsi yang benar-benar dipakai LyricsRepository di satu titik tulis.

    private val trackId = "spotify:track:abc"

    @Test
    fun `lirik yang ketemu ditulis apa adanya`() {
        assertEquals(synced, cacheEntryFor(trackId, LyricsLookup.Found(synced)))
    }

    @Test
    fun `NotFound ditulis sebagai penanda kosong untuk TTL negatif`() {
        val entry = cacheEntryFor(trackId, LyricsLookup.NotFound)
        assertEquals(trackId, entry?.trackId)
        assertEquals(false, entry?.hasContent)
    }

    /**
     * REGRESI, dan inilah baris kode yang dulu salah: repository memanggil
     * `diskCache.put(result)` tanpa syarat, jadi kegagalan jaringan tersimpan
     * sebagai "tidak ada lirik" selama 1 hari.
     */
    @Test
    fun `kegagalan transport tidak menghasilkan apapun untuk ditulis`() {
        assertNull(
            "Unavailable tidak boleh menyentuh cache",
            cacheEntryFor(trackId, LyricsLookup.Unavailable("timeout")),
        )
    }

    @Test
    fun `mode pesawat tidak meracuni cache`() {
        // Kedua endpoint melempar IOException.
        val lookup = combineProbes(
            failed("Unable to resolve host"),
            failed("Unable to resolve host"),
        )
        assertNull(cacheEntryFor(trackId, lookup))
    }

    @Test
    fun `lagu tanpa lirik tetap di-cache negatif supaya tidak spam LRCLIB`() {
        val lookup = combineProbes(LrclibProbe.Absent, LrclibProbe.Absent)
        assertNotNull(
            "jawaban 'memang tidak ada' harus di-cache",
            cacheEntryFor(trackId, lookup),
        )
    }
}
