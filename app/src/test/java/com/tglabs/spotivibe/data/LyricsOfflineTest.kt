package com.tglabs.spotivibe.data

import com.tglabs.spotivibe.domain.LyricsLookupEvent
import com.tglabs.spotivibe.domain.LyricsResult
import com.tglabs.spotivibe.domain.LyricsSearchState
import com.tglabs.spotivibe.domain.LyricsSource
import com.tglabs.spotivibe.domain.LyricsState
import com.tglabs.spotivibe.domain.SyncedLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Response
import java.io.IOException

/**
 * Mode offline di [LyricsRepository]: kapan jaringan disentuh, kapan lirik basi
 * dipakai, dan apa yang tampil kalau tidak ada keduanya.
 *
 * Berbeda dari [LyricsResolutionOrderTest], jalur jaringan DIUJI di sini lewat
 * [FakeLrclib]. Jumlah panggilannya adalah bukti utama: offline yang masih
 * memanggil LRCLIB berarti user menunggu timeout tiap ganti lagu.
 */
class LyricsOfflineTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class FakeNetwork(online: Boolean) : NetworkStatusSource {
        override val isOnline = MutableStateFlow(online)
    }

    private enum class Mode { Gagal, TidakAda, Ada }

    private class FakeLrclib(var mode: Mode = Mode.Gagal) : LrclibApi {
        var panggilan = 0
        var teks = "lirik dari jaringan"

        /** Dijalankan di setiap panggilan, misalnya untuk mematikan jaringan. */
        var saatDipanggil: () -> Unit = {}

        override suspend fun get(
            trackName: String,
            artistName: String,
            albumName: String,
            duration: Int,
        ): Response<LrclibDto> {
            panggilan++
            saatDipanggil()
            return when (mode) {
                Mode.Gagal -> throw IOException("Unable to resolve host lrclib.net")
                Mode.TidakAda -> Response.error(404, "".toResponseBody(null))
                Mode.Ada -> Response.success(LrclibDto(plainLyrics = teks))
            }
        }

        override suspend fun search(
            trackName: String,
            artistName: String,
        ): Response<List<LrclibDto>> {
            panggilan++
            saatDipanggil()
            return when (mode) {
                Mode.Gagal -> throw IOException("Unable to resolve host lrclib.net")
                Mode.TidakAda -> Response.success(emptyList())
                Mode.Ada -> Response.success(listOf(LrclibDto(plainLyrics = teks)))
            }
        }
    }

    /** [segar] untuk get(), [basi] hanya terlihat lewat getStale(). */
    private class FakeDisk(
        var segar: LyricsResult? = null,
        var basi: LyricsResult? = null,
    ) : LyricsDiskCache {
        val ditulis = mutableListOf<LyricsResult>()
        override fun get(trackId: String): LyricsResult? = segar
        override fun put(result: LyricsResult) {
            ditulis += result
            segar = result
        }
        override fun getStale(trackId: String): LyricsResult? =
            (segar ?: basi)?.takeIf { it.hasContent }
    }

    private fun lirik(teks: String, id: String = "t1") = LyricsResult(
        trackId = id,
        synced = listOf(SyncedLine(id = 0, timeMs = 0, text = teks)),
        plain = null,
    )

    private val kosong = LyricsResult(trackId = "t1", synced = null, plain = null)

    private fun teksDari(state: LyricsState): String? = (state as? LyricsState.Ready)?.result
        ?.let { it.synced?.firstOrNull()?.text ?: it.plain }

    private val dicatat = mutableListOf<LyricsLookupEvent>()

    private fun repo(
        disk: LyricsDiskCache,
        api: FakeLrclib,
        network: FakeNetwork,
        overrides: (String) -> LyricsResult? = { null },
    ) = LyricsRepository(
        diskCache = disk,
        statsRecorder = { dicatat += it },
        overrides = { trackId -> overrides(trackId) },
        network = network,
        lrclibApi = api,
    )

    private suspend fun LyricsRepository.ambil(
        trackId: String = "t1",
        allowRetry: Boolean = false,
    ) = fetchLyrics(
        trackId = trackId,
        title = "judul",
        artist = "artis",
        album = "album",
        durationMs = 200_000,
        allowRetry = allowRetry,
    )

    // ── Offline: jaringan tidak disentuh ─────────────────────────

    @Test
    fun `offline dengan cache segar memakai cache tanpa jaringan`() = runTest {
        val api = FakeLrclib()
        val state = repo(FakeDisk(segar = lirik("segar")), api, FakeNetwork(false)).ambil()
        assertEquals("segar", teksDari(state))
        assertEquals(0, api.panggilan)
    }

    /** REGRESI: inti mode offline. Dulu entri basi dihapus lalu lirik hilang. */
    @Test
    fun `offline dengan lirik basi menampilkan lirik basi tanpa jaringan`() = runTest {
        val api = FakeLrclib()
        val state = repo(FakeDisk(basi = lirik("lama")), api, FakeNetwork(false)).ambil()
        assertEquals("lama", teksDari(state))
        assertEquals(0, api.panggilan)
    }

    /**
     * REGRESI: dulu offline tetap memanggil LRCLIB tiga kali dengan backoff.
     * Panggilan nol adalah buktinya; jumlahnya tidak bergantung allowRetry.
     */
    @Test
    fun `offline tanpa cadangan menghasilkan Offline tanpa satu pun panggilan`() = runTest {
        val api = FakeLrclib()
        val state = repo(FakeDisk(), api, FakeNetwork(false)).ambil(allowRetry = true)
        assertEquals(LyricsState.Offline, state)
        assertEquals(0, api.panggilan)
    }

    @Test
    fun `entri negatif basi tidak menutupi status Offline`() = runTest {
        val state = repo(FakeDisk(basi = kosong), FakeLrclib(), FakeNetwork(false)).ambil()
        assertEquals(LyricsState.Offline, state)
    }

    @Test
    fun `override tetap menang saat offline`() = runTest {
        val state = repo(
            FakeDisk(basi = lirik("cache")),
            FakeLrclib(),
            FakeNetwork(false),
            overrides = { lirik("pilihan user") },
        ).ambil()
        assertEquals("pilihan user", teksDari(state))
    }

    @Test
    fun `offline tidak menulis apa pun ke disk`() = runTest {
        val disk = FakeDisk(basi = lirik("lama"))
        repo(disk, FakeLrclib(), FakeNetwork(false)).ambil()
        assertTrue(disk.ditulis.isEmpty())
    }

    // ── Online tapi gagal ────────────────────────────────────────

    /** Wifi tersambung tapi LRCLIB tidak terjangkau: lirik basi tetap dipakai. */
    @Test
    fun `online tapi jaringan gagal memakai lirik basi`() = runTest {
        val api = FakeLrclib(Mode.Gagal)
        val state = repo(FakeDisk(basi = lirik("lama")), api, FakeNetwork(true)).ambil()
        assertEquals("lama", teksDari(state))
        assertTrue(api.panggilan > 0)
    }

    @Test
    fun `online gagal tanpa cadangan tetap Unavailable`() = runTest {
        val state = repo(FakeDisk(), FakeLrclib(Mode.Gagal), FakeNetwork(true)).ambil()
        assertTrue(state is LyricsState.Unavailable)
    }

    /**
     * NotFound dari LRCLIB adalah jawaban pasti dan harus menang atas lirik
     * basi. Kalau entri LRCLIB dihapus karena salah lagu, lirik basi yang salah
     * itulah yang akan terus tampil.
     */
    @Test
    fun `jawaban pasti tidak ada tidak ditimpa lirik basi`() = runTest {
        val state = repo(FakeDisk(basi = lirik("lama")), FakeLrclib(Mode.TidakAda), FakeNetwork(true))
            .ambil()
        assertEquals(LyricsState.NotFound, state)
    }

    @Test
    fun `lirik segar dari jaringan menang atas lirik basi dan ditulis ke disk`() = runTest {
        val disk = FakeDisk(basi = lirik("lama"))
        val api = FakeLrclib(Mode.Ada).apply { teks = "baru" }
        val state = repo(disk, api, FakeNetwork(true)).ambil()
        assertEquals("baru", teksDari(state))
        assertEquals("baru", disk.ditulis.single().plain)
    }

    /**
     * Jaringan hilang di tengah percobaan: retry berhenti, dan statusnya
     * Offline (bukan Unavailable) supaya fetch ulang terpicu saat online lagi.
     */
    @Test
    fun `jaringan hilang di tengah percobaan menghentikan retry`() = runTest {
        val network = FakeNetwork(true)
        val api = FakeLrclib(Mode.Gagal).apply { saatDipanggil = { network.isOnline.value = false } }
        val state = repo(FakeDisk(), api, network).ambil(allowRetry = true)
        assertEquals(LyricsState.Offline, state)
        // Satu probe = /get + /search paralel. Tanpa penghentian akan ada 6.
        assertEquals(2, api.panggilan)
    }

    // ── Mem cache ────────────────────────────────────────────────

    /**
     * REGRESI potensial: kalau lirik basi masuk mem cache, ia dicek lebih dulu
     * dari jaringan dan menang terus sepanjang sesi walau jaringan kembali.
     */
    @Test
    fun `lirik basi tidak masuk mem cache sehingga jaringan dicoba lagi saat online`() = runTest {
        val network = FakeNetwork(false)
        val api = FakeLrclib(Mode.Ada).apply { teks = "baru" }
        val r = repo(FakeDisk(basi = lirik("lama")), api, network)
        assertEquals("lama", teksDari(r.ambil()))
        network.isOnline.value = true
        assertEquals("baru", teksDari(r.ambil()))
    }

    // ── Statistik ────────────────────────────────────────────────

    @Test
    fun `lirik basi dicatat sebagai DiskCache`() = runTest {
        repo(FakeDisk(basi = lirik("lama")), FakeLrclib(), FakeNetwork(false)).ambil()
        assertEquals(LyricsSource.DiskCache, dicatat.single().source)
    }

    /** Statistik mengukur coverage LRCLIB; lookup yang tidak bertanya tidak dihitung. */
    @Test
    fun `offline tanpa cadangan tidak dicatat`() = runTest {
        repo(FakeDisk(), FakeLrclib(), FakeNetwork(false)).ambil()
        assertTrue(dicatat.isEmpty())
    }

    @Test
    fun `gagal jaringan saat online tetap dicatat seperti sebelumnya`() = runTest {
        repo(FakeDisk(), FakeLrclib(Mode.Gagal), FakeNetwork(true)).ambil()
        assertEquals(LyricsSource.Network, dicatat.single().source)
    }

    // ── Pencarian manual ─────────────────────────────────────────

    @Test
    fun `pencarian manual langsung gagal saat offline tanpa panggilan`() = runTest {
        val api = FakeLrclib(Mode.Ada)
        val hasil = repo(FakeDisk(), api, FakeNetwork(false)).searchLyrics("judul", "artis")
        assertEquals(LyricsSearchState.Failed("offline"), hasil)
        assertEquals(0, api.panggilan)
    }

    // ── Skenario end-to-end dengan cache disk sungguhan ──────────

    /**
     * Urutan nyata satu perjalanan: dengar lagu A saat online, naik pesawat,
     * sebulan lebih kemudian putar A lagi, lalu lagu B yang belum pernah
     * diputar, lalu mendarat. [LyricsCache] di sini asli (folder sementara,
     * jam palsu), jadi bug "entri basi dihapus saat dibaca" ikut tertangkap.
     */
    @Test
    fun `skenario perjalanan offline dengan cache disk sungguhan`() = runTest {
        var sekarang = 1_700_000_000_000L
        val cache = LyricsCache(tmp.newFolder("lyrics"), legacyDir = null, nowMs = { sekarang })
        val network = FakeNetwork(true)
        val api = FakeLrclib(Mode.Ada).apply { teks = "lirik A" }

        // 1. Online, lagu A dimuat dari jaringan dan tersimpan.
        assertEquals("lirik A", teksDari(repo(cache, api, network).ambil("A")))

        // 2. Offline, 31 hari kemudian (lewat TTL 30 hari). Repo baru = proses
        //    baru, mem cache kosong, persis seperti app dibuka ulang.
        network.isOnline.value = false
        sekarang += 31L * 24 * 60 * 60 * 1000
        val panggilanSebelum = api.panggilan
        assertEquals("lirik A", teksDari(repo(cache, api, network).ambil("A")))

        // 3. Lagu B belum pernah diputar: jujur bilang offline.
        val r = repo(cache, api, network)
        assertEquals(LyricsState.Offline, r.ambil("B"))
        assertEquals(panggilanSebelum, api.panggilan)

        // 4. Mendarat: B terambil.
        network.isOnline.value = true
        api.teks = "lirik B"
        assertEquals("lirik B", teksDari(r.ambil("B")))
    }
}
