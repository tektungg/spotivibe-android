package com.tglabs.spotivibe.data

import com.tglabs.spotivibe.domain.LyricsLookupEvent
import com.tglabs.spotivibe.domain.LyricsOverrideSource
import com.tglabs.spotivibe.domain.LyricsResult
import com.tglabs.spotivibe.domain.LyricsSource
import com.tglabs.spotivibe.domain.LyricsState
import com.tglabs.spotivibe.domain.SyncedLine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Urutan resolusi lirik: override → mem cache → disk cache → jaringan.
 *
 * Ini wiring, dan wiring yang tidak diuji berulang kali jadi tempat bug
 * bersembunyi di project ini. Bisa diuji karena lapis disk di-inject lewat
 * [LyricsDiskCache]; jalur jaringan sengaja tidak disentuh di sini, dan semua
 * kasus di bawah memang berhenti sebelum sampai ke sana.
 */
class LyricsResolutionOrderTest {

    private class FakeDisk(var isi: LyricsResult? = null) : LyricsDiskCache {
        var jumlahGet = 0
        var jumlahPut = 0
        override fun get(trackId: String): LyricsResult? {
            jumlahGet++
            return isi
        }
        override fun put(result: LyricsResult) {
            jumlahPut++
            isi = result
        }
    }

    private fun lirik(teks: String) = LyricsResult(
        trackId = "t1",
        synced = listOf(SyncedLine(id = 0, timeMs = 0, text = teks)),
        plain = null,
    )

    private fun teksDari(state: LyricsState): String? =
        (state as? LyricsState.Ready)?.result?.synced?.firstOrNull()?.text

    private suspend fun LyricsRepository.ambil() = fetchLyrics(
        trackId = "t1",
        title = "judul",
        artist = "artis",
        album = "album",
        durationMs = 200_000,
        allowRetry = false,
    )

    /**
     * REGRESI: inti fitur override. Kalau urutannya terbalik, lirik salah yang
     * sudah ter-cache akan terus menang dan pilihan user tidak pernah terpakai.
     */
    @Test
    fun `override menang atas disk cache`() = runTest {
        val disk = FakeDisk(lirik("dari disk"))
        val repo = LyricsRepository(
            diskCache = disk,
            overrides = { lirik("pilihan user") },
        )

        assertEquals("pilihan user", teksDari(repo.ambil()))
    }

    /**
     * Bukan sekadar soal siapa yang menang, tapi bahwa lapis di bawahnya
     * TIDAK DISENTUH sama sekali. Lagu yang sudah diperbaiki manual harus
     * berhenti menanyakan LRCLIB selamanya.
     */
    @Test
    fun `override tidak menyentuh disk maupun jaringan`() = runTest {
        val disk = FakeDisk(lirik("dari disk"))
        val repo = LyricsRepository(
            diskCache = disk,
            overrides = { lirik("pilihan user") },
        )

        repo.ambil()

        assertEquals("disk tidak boleh dibaca", 0, disk.jumlahGet)
        assertEquals("disk tidak boleh ditulis", 0, disk.jumlahPut)
    }

    @Test
    fun `tanpa override disk cache yang dipakai`() = runTest {
        val disk = FakeDisk(lirik("dari disk"))
        val repo = LyricsRepository(diskCache = disk)

        assertEquals("dari disk", teksDari(repo.ambil()))
    }

    @Test
    fun `override hanya berlaku untuk track yang cocok`() = runTest {
        val disk = FakeDisk(lirik("dari disk"))
        val repo = LyricsRepository(
            diskCache = disk,
            // Override cuma untuk track lain.
            overrides = { trackId -> if (trackId == "track-lain") lirik("pilihan") else null },
        )

        assertEquals("dari disk", teksDari(repo.ambil()))
    }

    @Test
    fun `mem cache dipakai di panggilan kedua tanpa menyentuh disk lagi`() = runTest {
        val disk = FakeDisk(lirik("dari disk"))
        val repo = LyricsRepository(diskCache = disk)

        repo.ambil()
        val getPertama = disk.jumlahGet
        repo.ambil()

        assertEquals("panggilan kedua harus dari memori", getPertama, disk.jumlahGet)
    }

    // ── Statistik ────────────────────────────────────────────────

    /**
     * Override dicatat dengan sumbernya sendiri, supaya angka coverage tidak
     * naik gara-gara user memperbaiki lirik secara manual.
     */
    @Test
    fun `override dicatat sebagai sumber Override`() = runTest {
        val dicatat = mutableListOf<LyricsLookupEvent>()
        val repo = LyricsRepository(
            diskCache = FakeDisk(),
            statsRecorder = { dicatat += it },
            overrides = { lirik("pilihan") },
        )

        repo.ambil()

        assertEquals(1, dicatat.size)
        assertEquals(LyricsSource.Override, dicatat[0].source)
    }

    @Test
    fun `lookup dari disk dicatat sebagai sumber DiskCache`() = runTest {
        val dicatat = mutableListOf<LyricsLookupEvent>()
        val repo = LyricsRepository(
            diskCache = FakeDisk(lirik("dari disk")),
            statsRecorder = { dicatat += it },
        )

        repo.ambil()

        assertTrue(dicatat.any { it.source == LyricsSource.DiskCache })
    }

    /**
     * Sama prinsipnya dengan LyricsStatsRecorder: kegagalan mencatat tidak
     * boleh menjatuhkan fetch lirik. Statistik itu pengamatan, bukan fitur.
     */
    @Test
    fun `pencatat yang meledak tidak menjatuhkan fetch`() = runTest {
        val repo = LyricsRepository(
            diskCache = FakeDisk(),
            statsRecorder = { error("pencatat rusak") },
            overrides = { lirik("pilihan") },
        )

        assertEquals("pilihan", teksDari(repo.ambil()))
    }
}
