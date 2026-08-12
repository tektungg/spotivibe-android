package com.tglabs.spotivibe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionDecisionsTest {

    private val semuaFase = ConnPhase.entries

    // ── Klasifikasi kegagalan otorisasi ──────────────────────────

    /**
     * Bedanya menentukan nasib sesi. Masalah otorisasi membuang kredensial
     * lokal; kegagalan lain cuma ditampilkan. Salah membedakannya berarti
     * memaksa login ulang setiap kali app Spotify kebetulan belum jalan.
     */
    @Test
    fun `exception otorisasi dikenali dari namanya`() {
        assertTrue(authIssueOf("NotAuthorizedException", null))
        assertTrue(authIssueOf("UserNotAuthorizedException", "apa saja"))
        assertTrue(authIssueOf("NotLoggedInException", null))
    }

    @Test
    fun `pesan yang menyebut authorization juga dihitung`() {
        assertTrue(authIssueOf("SomeOtherException", "authorization failed"))
        assertTrue(authIssueOf("SomeOtherException", "AUTHORIZATION revoked"))
    }

    /**
     * Ini yang paling penting untuk TIDAK salah: Spotify belum jalan bukan
     * masalah otorisasi. Menganggapnya begitu akan membuang sesi yang sah.
     */
    @Test
    fun `kegagalan teknis bukan masalah otorisasi`() {
        assertFalse(authIssueOf("CouldNotFindSpotifyApp", "Spotify is not installed"))
        assertFalse(authIssueOf("SpotifyDisconnectedException", "connection lost"))
        assertFalse(authIssueOf("IOException", "timeout"))
        assertFalse(authIssueOf("UnknownHostException", null))
    }

    @Test
    fun `nama dan pesan kosong bukan masalah otorisasi`() {
        assertFalse(authIssueOf("", null))
        assertFalse(authIssueOf("", ""))
    }

    // ── Penjagaan auto-connect ───────────────────────────────────

    /**
     * REGRESI: tanpa penjagaan ini, kembali ke app saat bind masih berjalan
     * memulai bind KEDUA, dan callback yang datang belakangan menimpa state
     * milik yang pertama.
     */
    @Test
    fun `auto-connect ditolak saat sudah tersambung atau sedang menyambung`() {
        assertFalse(shouldStartAutoConnect(ConnPhase.Connected))
        assertFalse(shouldStartAutoConnect(ConnPhase.Connecting))
    }

    @Test
    fun `auto-connect diizinkan dari Disconnected dan Error`() {
        assertTrue(shouldStartAutoConnect(ConnPhase.Disconnected))
        assertTrue("dari layar error user harus bisa mencoba lagi", shouldStartAutoConnect(ConnPhase.Error))
    }

    // ── Kembali tanpa redirect ───────────────────────────────────

    /**
     * REGRESI: user menekan back di layar consent. Tanpa ini UI tertinggal di
     * Connecting selamanya, tombol Connect ikut hilang, dan satu-satunya jalan
     * keluar adalah menutup paksa app.
     */
    @Test
    fun `hanya fase Connecting yang boleh dibatalkan`() {
        assertTrue(shouldAbandonToDisconnected(ConnPhase.Connecting))
        semuaFase.filter { it != ConnPhase.Connecting }.forEach {
            assertFalse("$it tidak boleh ikut dibatalkan", shouldAbandonToDisconnected(it))
        }
    }

    /**
     * Membatalkan dari Connected akan memutus koneksi yang sedang berjalan
     * hanya karena user berpindah app lalu kembali.
     */
    @Test
    fun `pembatalan tidak boleh menjatuhkan koneksi yang sudah jalan`() {
        assertFalse(shouldAbandonToDisconnected(ConnPhase.Connected))
    }

    // ── Timeout ──────────────────────────────────────────────────

    /**
     * Callback yang datang tepat sebelum timer menyala sudah memindahkan fase.
     * Melaporkan timeout di situ akan menimpa koneksi yang sebenarnya berhasil
     * dengan layar error.
     */
    @Test
    fun `timeout hanya dilaporkan kalau masih menunggu`() {
        assertTrue(shouldReportTimeout(ConnPhase.Connecting))
        assertFalse("koneksi yang sudah berhasil tidak boleh ditimpa", shouldReportTimeout(ConnPhase.Connected))
        assertFalse(shouldReportTimeout(ConnPhase.Disconnected))
        assertFalse(shouldReportTimeout(ConnPhase.Error))
    }

    // ── Redirect ditolak ─────────────────────────────────────────

    @Test
    fun `redirect asing hanya dilaporkan saat sedang menunggu login`() {
        assertTrue(shouldReportRejectedRedirect(ConnPhase.Connecting))
        semuaFase.filter { it != ConnPhase.Connecting }.forEach {
            assertFalse("deep link asing tidak boleh mengganggu fase $it", shouldReportRejectedRedirect(it))
        }
    }

    // ── Kehilangan App Remote ────────────────────────────────────

    /**
     * REGRESI: koneksi zombie. Langganan berhenti berarti app Spotify sudah
     * mati, tapi dulu itu cuma di-log. Fase tetap Connected padahal tidak ada
     * event yang masuk lagi selamanya, dan supervisor tidak pernah tahu harus
     * menyambung ulang.
     */
    @Test
    fun `kehilangan remote ditangani saat sedang tersambung`() {
        assertTrue(shouldHandleRemoteLost(tearingDown = false, phase = ConnPhase.Connected))
    }

    /**
     * Teardown memicu callback onStop yang sama. Tanpa penyaring ini,
     * pemutusan yang kita lakukan sendiri terbaca sebagai kehilangan yang tidak
     * disengaja dan memicu penyambungan ulang yang tidak diminta.
     */
    @Test
    fun `pemutusan sendiri tidak dianggap kehilangan`() {
        assertTrue(shouldHandleRemoteLost(false, ConnPhase.Connected))
        assertFalse(shouldHandleRemoteLost(true, ConnPhase.Connected))
    }

    @Test
    fun `callback susulan setelah state turun diabaikan`() {
        semuaFase.filter { it != ConnPhase.Connected }.forEach {
            assertFalse("fase $it tidak punya remote untuk hilang", shouldHandleRemoteLost(false, it))
        }
    }

    @Test
    fun `kedua penjagaan wajib, bukan salah satu`() {
        assertFalse("tearingDown saja harus cukup menahan", shouldHandleRemoteLost(true, ConnPhase.Connected))
        assertFalse("fase saja harus cukup menahan", shouldHandleRemoteLost(false, ConnPhase.Disconnected))
    }

    // ── Album art ────────────────────────────────────────────────

    /**
     * Event player state datang berkali-kali per detik selama lagu berjalan.
     * Tanpa penjagaan ini setiap event memicu unduhan gambar.
     */
    @Test
    fun `album art hanya diambil saat URI berubah`() {
        assertFalse(shouldFetchAlbumArt("spotify:image:abc", "spotify:image:abc"))
        assertTrue(shouldFetchAlbumArt("spotify:image:xyz", "spotify:image:abc"))
    }

    @Test
    fun `perpindahan ke atau dari null tetap dihitung berubah`() {
        assertTrue(shouldFetchAlbumArt("spotify:image:abc", null))
        assertTrue(shouldFetchAlbumArt(null, "spotify:image:abc"))
    }

    @Test
    fun `null ke null tidak memicu apa-apa`() {
        assertFalse(shouldFetchAlbumArt(null, null))
    }

    // ── Pesan kegagalan ──────────────────────────────────────────

    @Test
    fun `pesan kegagalan memuat nama exception dan keterangannya`() {
        assertEquals("IOException: timeout", connectFailureMessage("IOException", "timeout"))
    }

    /**
     * REGRESI: pesan kosong membuat layar error tampak seperti app yang
     * menggantung. Nama exception ada, keterangannya hilang, dan user tidak
     * punya petunjuk apa pun.
     */
    @Test
    fun `keterangan kosong diganti kata pengganti`() {
        assertEquals("IOException: unknown", connectFailureMessage("IOException", null))
        assertEquals("IOException: unknown", connectFailureMessage("IOException", ""))
        assertEquals("IOException: unknown", connectFailureMessage("IOException", "   "))
    }

    @Test
    fun `deterministik`() {
        repeat(5) {
            assertEquals(
                connectFailureMessage("A", "b"),
                connectFailureMessage("A", "b"),
            )
        }
    }
}
