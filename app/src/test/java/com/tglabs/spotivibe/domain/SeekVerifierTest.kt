package com.tglabs.spotivibe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeekVerifierTest {

    private val v = SeekVerifier()

    @Test
    fun `tanpa seek tidak ada keputusan`() {
        assertNull(v.onReport(10_000, 0))
        assertNull(v.onDeadline(99_000))
    }

    @Test
    fun `laporan di dekat target mengonfirmasi`() {
        v.onSeek(60_000, 1_000)
        assertEquals(SeekOutcome.Confirmed, v.onReport(60_300, 1_200))
        // Sudah diputuskan: tenggat tidak memicu penolakan lagi.
        assertNull(v.onDeadline(5_000))
    }

    /** Lagu terus jalan selama menunggu laporan: target + waktu yang lewat juga sah. */
    @Test
    fun `laporan yang sudah maju sejak seek tetap terhitung sampai`() {
        v.onSeek(60_000, 1_000)
        assertEquals(SeekOutcome.Confirmed, v.onReport(61_200, 2_200))
    }

    @Test
    fun `laporan dari posisi lama belum memutuskan sebelum tenggat`() {
        v.onSeek(60_000, 1_000)
        assertNull(v.onReport(12_000, 1_400))
    }

    /** REGRESI desain: Spotify Free mengabaikan seek tanpa kabar. */
    @Test
    fun `tenggat lewat tanpa laporan di target berarti ditolak`() {
        v.onSeek(60_000, 1_000)
        assertNull(v.onDeadline(2_000))
        assertEquals(SeekOutcome.Refused, v.onDeadline(2_500))
        assertNull(v.onDeadline(3_000))
    }

    @Test
    fun `laporan dari posisi lama setelah tenggat berarti ditolak`() {
        v.onSeek(60_000, 1_000)
        assertEquals(SeekOutcome.Refused, v.onReport(13_000, 2_600))
    }

    @Test
    fun `reset melupakan seek yang menunggu`() {
        v.onSeek(60_000, 1_000)
        v.reset()
        assertNull(v.onDeadline(9_000))
    }
}
