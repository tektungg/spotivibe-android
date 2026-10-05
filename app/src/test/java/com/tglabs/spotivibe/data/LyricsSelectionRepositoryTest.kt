package com.tglabs.spotivibe.data

import com.tglabs.spotivibe.domain.LyricsLookupEvent
import com.tglabs.spotivibe.domain.LyricsResult
import com.tglabs.spotivibe.domain.LyricsState
import com.tglabs.spotivibe.domain.ProbeSource
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

/**
 * Pemilihan lirik end-to-end lewat [LyricsRepository]: respons LRCLIB palsu
 * yang realistis (beberapa versi lagu yang sama di `/search`) masuk, lirik yang
 * terpilih keluar.
 *
 * Test domain (`LyricsSelectionTest`) membuktikan aturan peringkatnya; yang
 * dibuktikan di sini adalah wiring-nya: durasi dari DTO benar-benar sampai ke
 * aturan itu, `/search` benar-benar ditunggu saat `/get` cuma plain, dan
 * statistik mencatat endpoint yang menang.
 */
class LyricsSelectionRepositoryTest {

    private class FakeLrclib(
        val get: LrclibDto?,
        val search: List<LrclibDto>,
    ) : LrclibApi {
        override suspend fun get(
            trackName: String,
            artistName: String,
            albumName: String,
            duration: Int,
        ): Response<LrclibDto> =
            if (get == null) Response.error(404, "".toResponseBody(null)) else Response.success(get)

        override suspend fun search(trackName: String, artistName: String): Response<List<LrclibDto>> =
            Response.success(search)
    }

    private class NoDisk : LyricsDiskCache {
        override fun get(trackId: String): LyricsResult? = null
        override fun put(result: LyricsResult) {}
    }

    private fun synced(nama: String, durasi: Double?) =
        LrclibDto(trackName = nama, duration = durasi, syncedLyrics = "[00:01.00] $nama")

    private fun plain(nama: String, durasi: Double?) =
        LrclibDto(trackName = nama, duration = durasi, plainLyrics = nama)

    private val dicatat = mutableListOf<LyricsLookupEvent>()

    /** Lagu di Spotify berdurasi 200 detik. */
    private suspend fun ambil(api: FakeLrclib): String? {
        val state = LyricsRepository(
            diskCache = NoDisk(),
            statsRecorder = { dicatat += it },
            lrclibApi = api,
        ).fetchLyrics(
            trackId = "t1",
            title = "judul",
            artist = "artis",
            album = "album",
            durationMs = 200_000,
            allowRetry = false,
        )
        val r = (state as? LyricsState.Ready)?.result ?: return null
        return r.synced?.firstOrNull()?.text ?: r.plain
    }

    /** REGRESI: dulu plain dari /get lewat jalur cepat dan /search diabaikan. */
    @Test
    fun `get plain kalah dari search synced yang durasinya cocok`() = runTest {
        val hasil = ambil(FakeLrclib(get = plain("get plain", 200.0), search = listOf(synced("search synced", 201.0))))
        assertEquals("search synced", hasil)
        assertEquals(ProbeSource.Search, dicatat.single().probe)
    }

    /** REGRESI: dulu yang diambil synced PERTAMA di /search, tanpa melihat durasi. */
    @Test
    fun `dari search dipilih synced yang durasinya paling mirip`() = runTest {
        val hasil = ambil(
            FakeLrclib(
                get = null,
                search = listOf(
                    synced("live version", 245.0),
                    plain("plain pas", 200.0),
                    synced("album version", 200.5),
                    synced("radio edit", 203.5),
                ),
            ),
        )
        assertEquals("album version", hasil)
    }

    @Test
    fun `get synced dengan durasi pas dipakai lewat jalur cepat`() = runTest {
        val hasil = ambil(FakeLrclib(get = synced("get synced", 200.3), search = listOf(synced("search synced", 200.0))))
        assertEquals("get synced", hasil)
        assertEquals(ProbeSource.Get, dicatat.single().probe)
    }

    @Test
    fun `search berisi versi lain saja tetap kalah dari get plain yang pas`() = runTest {
        val hasil = ambil(FakeLrclib(get = plain("get plain", 200.0), search = listOf(synced("extended", 290.0))))
        assertEquals("get plain", hasil)
    }

    @Test
    fun `search tanpa synced memilih plain yang durasinya paling mirip`() = runTest {
        val hasil = ambil(FakeLrclib(get = null, search = listOf(plain("jauh", 230.0), plain("dekat", 199.0))))
        assertEquals("dekat", hasil)
    }
}
