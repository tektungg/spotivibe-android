package com.tglabs.spotivibe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackClockTest {

    private val clock = PlaybackClock()

    /** Laporan player: posisi [pos] dicap pada [at]; durasi 200 s. */
    private fun report(
        state: PlayerState,
        pos: Long,
        at: Long,
        title: String = "Lagu",
        duration: Long = 200_000,
    ) = MediaSnapshot(
        packageName = "com.google.android.apps.youtube.music",
        title = title,
        artist = "Artis",
        durationMs = duration,
        state = state,
        positionMs = pos,
        positionUpdatedAtElapsedMs = at,
    )

    private fun feed(state: PlayerState, pos: Long, at: Long, now: Long = at) =
        clock.onReport(report(state, pos, at), now)

    // ── Dasar ────────────────────────────────────────────────────

    @Test
    fun `laporan pertama dipercaya dan jam maju selama memutar`() {
        val r = feed(PlayerState.Playing, pos = 10_000, at = 1_000)
        assertEquals(10_000L, r.positionMs)
        assertFalse(r.isPaused)
        assertEquals(12_000L, clock.positionAt(3_000))
    }

    @Test
    fun `jitter kecil tidak menggeser jam`() {
        feed(PlayerState.Playing, pos = 10_000, at = 1_000)
        // Player melapor 10 ms di depan jam: dibiarkan.
        val r = feed(PlayerState.Playing, pos = 11_010, at = 2_000)
        assertEquals(11_000L, r.positionMs)
    }

    @Test
    fun `drift diikuti`() {
        feed(PlayerState.Playing, pos = 10_000, at = 1_000)
        val r = feed(PlayerState.Playing, pos = 11_200, at = 2_000)
        assertEquals(11_200L, r.positionMs)
    }

    @Test
    fun `lompatan besar saat memutar adalah seek`() {
        feed(PlayerState.Playing, pos = 10_000, at = 1_000)
        val r = feed(PlayerState.Playing, pos = 60_000, at = 2_000)
        assertEquals(60_000L, r.positionMs)
    }

    @Test
    fun `ganti lagu memulai jam dari laporan lagu baru`() {
        feed(PlayerState.Playing, pos = 150_000, at = 1_000)
        val r = clock.onReport(report(PlayerState.Playing, pos = 0, at = 2_000, title = "Lagu Lain"), 2_000)
        assertEquals(0L, r.positionMs)
    }

    // ── Pause ────────────────────────────────────────────────────

    @Test
    fun `pause sedikit di belakang jam beku di posisi jam`() {
        feed(PlayerState.Playing, pos = 10_000, at = 0)
        // Jam di 15 000; player melapor pause di 14 900 (efek fade).
        val r = feed(PlayerState.Paused, pos = 14_900, at = 5_000)
        assertTrue(r.isPaused)
        assertEquals(15_000L, r.positionMs)
        assertEquals(15_000L, clock.positionAt(60_000))
    }

    /** REGRESI desain: pause basi YouTube Music memundurkan baris aktif tiap pause. */
    @Test
    fun `pause basi beku di posisi jam, bukan laporan`() {
        feed(PlayerState.Playing, pos = 10_000, at = 0)
        val r = feed(PlayerState.Paused, pos = 13_700, at = 5_000)
        assertEquals(15_000L, r.positionMs)
    }

    @Test
    fun `resume dari pause basi memakai bias sampai seek`() {
        feed(PlayerState.Playing, pos = 10_000, at = 0)
        feed(PlayerState.Paused, pos = 13_700, at = 5_000)
        // Player melanjutkan dari angka basinya sendiri, 1 300 ms di belakang.
        val resume = feed(PlayerState.Playing, pos = 13_700, at = 20_000)
        assertFalse(resume.isPaused)
        assertEquals(15_000L, resume.positionMs)
        // Laporan berikutnya tetap 1 300 di belakang audio: jam tidak mundur.
        val lanjut = feed(PlayerState.Playing, pos = 15_700, at = 22_000)
        assertEquals(17_000L, lanjut.positionMs)
        // Seek membuang bias.
        val seek = feed(PlayerState.Playing, pos = 90_000, at = 23_000)
        assertEquals(90_000L, seek.positionMs)
    }

    @Test
    fun `resume dari posisi lain membuang bias`() {
        feed(PlayerState.Playing, pos = 10_000, at = 0)
        feed(PlayerState.Paused, pos = 13_700, at = 5_000)
        val r = feed(PlayerState.Playing, pos = 40_000, at = 20_000)
        assertEquals(40_000L, r.positionMs)
        val lanjut = feed(PlayerState.Playing, pos = 41_000, at = 21_000)
        assertEquals(41_000L, lanjut.positionMs)
    }

    @Test
    fun `pause di depan jam atau terlalu jauh di belakang dipercaya`() {
        feed(PlayerState.Playing, pos = 10_000, at = 0)
        assertEquals(16_000L, feed(PlayerState.Paused, pos = 16_000, at = 5_000).positionMs)

        val lain = PlaybackClock()
        lain.onReport(report(PlayerState.Playing, pos = 10_000, at = 0), 0)
        assertEquals(2_000L, lain.onReport(report(PlayerState.Paused, pos = 2_000, at = 5_000), 5_000).positionMs)
    }

    @Test
    fun `seek selama pause menggeser jam`() {
        feed(PlayerState.Paused, pos = 10_000, at = 0)
        assertEquals(10_000L, feed(PlayerState.Paused, pos = 10_400, at = 1_000).positionMs)
        assertEquals(50_000L, feed(PlayerState.Paused, pos = 50_000, at = 2_000).positionMs)
    }

    // ── Buffering ────────────────────────────────────────────────

    @Test
    fun `buffering di tengah lagu membekukan jam`() {
        feed(PlayerState.Playing, pos = 10_000, at = 0)
        val r = feed(PlayerState.Transient, pos = 11_000, at = 1_000)
        assertTrue(r.isPaused)
        assertEquals(11_000L, clock.positionAt(9_000))
    }

    @Test
    fun `buffering di akhir lagu tetap jalan`() {
        feed(PlayerState.Playing, pos = 198_500, at = 0)
        val r = feed(PlayerState.Transient, pos = 199_000, at = 500)
        assertFalse(r.isPaused)
    }

    // ── Seek dari Spotivibe ──────────────────────────────────────

    @Test
    fun `seek sendiri langsung memindahkan jam`() {
        feed(PlayerState.Playing, pos = 10_000, at = 0)
        clock.onSeek(80_000, 1_000)
        assertEquals(81_000L, clock.positionAt(2_000))
    }

    /** Player yang menolak seek: laporannya mengembalikan jam. */
    @Test
    fun `seek yang ditolak dikoreksi laporan berikutnya`() {
        feed(PlayerState.Playing, pos = 10_000, at = 0)
        clock.onSeek(80_000, 1_000)
        assertEquals(12_000L, feed(PlayerState.Playing, pos = 12_000, at = 2_000).positionMs)
    }

    @Test
    fun `posisi dijepit ke durasi`() {
        feed(PlayerState.Playing, pos = 199_000, at = 0)
        assertEquals(200_000L, clock.positionAt(10_000))
    }
}
