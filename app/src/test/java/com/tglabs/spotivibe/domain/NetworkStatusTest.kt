package com.tglabs.spotivibe.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkStatusTest {

    // ── isUsableNetwork ──────────────────────────────────────────

    @Test
    fun `jaringan berinternet tanpa captive portal dipakai`() {
        assertTrue(isUsableNetwork(hasInternet = true, captivePortal = false))
    }

    @Test
    fun `tanpa kapabilitas internet dianggap offline`() {
        assertFalse(isUsableNetwork(hasInternet = false, captivePortal = false))
    }

    /**
     * Wifi hotel yang menunggu login: semua request dibelokkan ke halaman
     * login. Menganggapnya online berarti setiap ganti lagu menunggu timeout.
     */
    @Test
    fun `captive portal yang terdeteksi dianggap offline`() {
        assertFalse(isUsableNetwork(hasInternet = true, captivePortal = true))
    }

    @Test
    fun `tanpa internet tetap offline walau captive portal terdeteksi`() {
        assertFalse(isUsableNetwork(hasInternet = false, captivePortal = true))
    }

    // ── shouldRefetchOnReconnect ─────────────────────────────────

    private val lirik = LyricsState.Ready(
        LyricsResult(trackId = "t", synced = null, plain = "halo")
    )

    /** REGRESI: dulu user harus memutar ulang lagu supaya liriknya dicoba lagi. */
    @Test
    fun `kembali online mengambil ulang lirik yang tadi offline`() {
        assertTrue(shouldRefetchOnReconnect(false, true, LyricsState.Offline))
    }

    @Test
    fun `kembali online mengambil ulang lirik yang tadi gagal`() {
        assertTrue(shouldRefetchOnReconnect(false, true, LyricsState.Unavailable("timeout")))
    }

    /** Sudah ada jawaban; menanyakannya lagi cuma membuang kuota LRCLIB. */
    @Test
    fun `jawaban yang sudah ada tidak diambil ulang`() {
        assertFalse(shouldRefetchOnReconnect(false, true, lirik))
        assertFalse(shouldRefetchOnReconnect(false, true, LyricsState.NotFound))
    }

    /** Fetch sedang berjalan; memulai yang kedua berarti dua request paralel. */
    @Test
    fun `fetch yang sedang berjalan tidak digandakan`() {
        assertFalse(shouldRefetchOnReconnect(false, true, LyricsState.Loading))
    }

    /**
     * Callback jaringan bisa terpanggil berulang tanpa perubahan status, misal
     * saat pindah dari wifi ke seluler. Hanya transisi yang memicu.
     */
    @Test
    fun `tetap online bukan transisi`() {
        assertFalse(shouldRefetchOnReconnect(true, true, LyricsState.Offline))
    }

    @Test
    fun `menjadi offline tidak memicu fetch`() {
        assertFalse(shouldRefetchOnReconnect(true, false, LyricsState.Unavailable("x")))
        assertFalse(shouldRefetchOnReconnect(false, false, LyricsState.Offline))
    }
}
