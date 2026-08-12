package com.tglabs.spotivibe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayDecisionsTest {

    // ── Siklus hidup jendela ─────────────────────────────────────

    @Test
    fun `dinyalakan dan belum ada berarti dibuat`() {
        assertEquals(OverlayAction.Buat, overlayAction(enabled = true, sudahAda = false))
    }

    /**
     * REGRESI: preferensi memancar ulang dengan nilai enabled yang sama setiap
     * kali POSISI berubah, dan posisi berubah tiap kali user menggeser overlay.
     * Membuat jendela lagi di situ menumpuk dua overlay yang saling menutupi,
     * dan hanya yang paling atas yang tombol tutupnya bisa ditekan.
     */
    @Test
    fun `dinyalakan tapi sudah ada berarti tidak melakukan apa-apa`() {
        assertEquals(OverlayAction.DiamSaja, overlayAction(enabled = true, sudahAda = true))
    }

    @Test
    fun `dimatikan dan masih ada berarti disembunyikan`() {
        assertEquals(OverlayAction.Sembunyikan, overlayAction(enabled = false, sudahAda = true))
    }

    @Test
    fun `dimatikan dan memang tidak ada berarti tidak melakukan apa-apa`() {
        assertEquals(OverlayAction.DiamSaja, overlayAction(enabled = false, sudahAda = false))
    }

    @Test
    fun `keempat kombinasi tercakup dan tidak ada yang ambigu`() {
        val hasil = listOf(true, false).flatMap { e ->
            listOf(true, false).map { a -> (e to a) to overlayAction(e, a) }
        }
        assertEquals(4, hasil.size)
        assertEquals(
            "hanya satu kombinasi yang boleh membuat jendela",
            1,
            hasil.count { it.second == OverlayAction.Buat },
        )
    }

    // ── Kegagalan menampilkan ────────────────────────────────────

    /**
     * Izin yang dicabut atau keanehan OEM membuat overlay tidak akan pernah
     * muncul. Tanpa mematikan preferensinya, setiap penyambungan berikutnya
     * mencoba lagi dan gagal lagi tanpa henti.
     */
    @Test
    fun `gagal tampil mematikan preferensinya`() {
        assertTrue(shouldDisableOverlayAfterFailure(tampilBerhasil = false))
        assertFalse(shouldDisableOverlayAfterFailure(tampilBerhasil = true))
    }

    // ── Penjepitan posisi ────────────────────────────────────────

    @Test
    fun `geser ke bawah dalam batas mengikuti delta`() {
        assertEquals(150, clampOverlayY(ySekarang = 100, delta = 50, tinggiLayar = 2000, tinggiOverlay = 300))
    }

    @Test
    fun `geser ke atas dalam batas mengikuti delta`() {
        assertEquals(50, clampOverlayY(100, -50, 2000, 300))
    }

    /**
     * Tanpa penjepitan, overlay bisa digeser sampai keluar layar dan tidak ada
     * cara mengembalikannya selain mematikan fiturnya lewat Settings.
     */
    @Test
    fun `tidak bisa digeser keluar lewat atas`() {
        assertEquals(0, clampOverlayY(100, -500, 2000, 300))
        assertEquals(0, clampOverlayY(0, -1, 2000, 300))
    }

    @Test
    fun `tidak bisa digeser keluar lewat bawah`() {
        // Batasnya tinggi layar dikurangi tinggi overlay, bukan tinggi layar.
        assertEquals(1700, clampOverlayY(100, 5000, 2000, 300))
    }

    @Test
    fun `batas bawah tepat di titiknya`() {
        assertEquals(1700, clampOverlayY(1699, 1, 2000, 300))
        assertEquals(1700, clampOverlayY(1700, 1, 2000, 300))
    }

    /**
     * Overlay yang lebih tinggi dari layar akan menghasilkan batas negatif.
     * Tanpa penjagaan, coerceIn dengan rentang terbalik melempar exception dan
     * app mati saat user menggeser.
     */
    @Test
    fun `overlay lebih tinggi dari layar tidak melempar exception`() {
        assertEquals(0, clampOverlayY(100, 50, 500, 900))
        assertEquals(0, clampOverlayY(100, -50, 500, 900))
    }

    @Test
    fun `ukuran nol tidak melempar exception`() {
        assertEquals(0, clampOverlayY(0, 0, 0, 0))
        assertEquals(0, clampOverlayY(100, 100, 0, 0))
    }

    @Test
    fun `hasil selalu di dalam batas untuk masukan apa pun`() {
        listOf(-9999, -1, 0, 100, 9999).forEach { y ->
            listOf(-9999, 0, 9999).forEach { d ->
                val hasil = clampOverlayY(y, d, 2000, 300)
                assertTrue("y=$y d=$d hasil=$hasil", hasil in 0..1700)
            }
        }
    }

    @Test
    fun `deterministik`() {
        repeat(5) { assertEquals(clampOverlayY(100, 50, 2000, 300), clampOverlayY(100, 50, 2000, 300)) }
    }

    // ── Animasi pengembalian ─────────────────────────────────────

    /**
     * Menganimasikan perpindahan sejauh nol piksel hanya membuang frame, dan di
     * overlay yang digambar di atas app lain itu terlihat sebagai kedipan.
     */
    @Test
    fun `posisi yang sudah benar tidak dianimasikan`() {
        assertFalse(needsSettleAnimation(yTarget = 500, ySekarang = 500))
        assertTrue(needsSettleAnimation(yTarget = 500, ySekarang = 480))
    }
}
