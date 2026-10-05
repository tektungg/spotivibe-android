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

    // ── Area aman ────────────────────────────────────────────────

    /**
     * REGRESI: posisi awal default Y 0 dulu membuat overlay muncul di bawah
     * status bar. Dengan inset atas, Y 0 dijepit ke bawah bar.
     */
    @Test
    fun `posisi awal nol didorong ke bawah bar atas`() {
        assertEquals(96, clampOverlayY(0, 0, 2000, 300, insetAtas = 96, insetBawah = 0))
    }

    /** Head unit: navigation bar di bawah tidak boleh menutupi tombol overlay. */
    @Test
    fun `tidak bisa digeser ke bawah navigation bar`() {
        assertEquals(1580, clampOverlayY(100, 5000, 2000, 300, insetAtas = 96, insetBawah = 120))
    }

    @Test
    fun `di dalam area aman delta tetap diikuti`() {
        assertEquals(650, clampOverlayY(600, 50, 2000, 300, insetAtas = 96, insetBawah = 120))
    }

    /** Bagian atas overlay (judul, tombol tutup) lebih penting tetap terlihat. */
    @Test
    fun `overlay lebih tinggi dari area aman menempel di batas atas`() {
        assertEquals(96, clampOverlayY(500, 0, 600, 500, insetAtas = 96, insetBawah = 120))
    }

    @Test
    fun `inset negatif dari OEM aneh diperlakukan sebagai nol`() {
        assertEquals(0, clampOverlayY(0, -10, 2000, 300, insetAtas = -5, insetBawah = -5))
        assertEquals(1700, clampOverlayY(0, 5000, 2000, 300, insetAtas = -5, insetBawah = -5))
    }

    @Test
    fun `tanpa inset perilakunya sama dengan sebelum safe area`() {
        for (y in listOf(-100, 0, 500, 1700, 9000)) {
            assertEquals(clampOverlayY(y, 0, 2000, 300), clampOverlayY(y, 0, 2000, 300, 0, 0))
        }
    }

    // ── Lebar overlay ────────────────────────────────────────────

    /** Navigation bar di kiri layar landscape, pola umum head unit. */
    @Test
    fun `navigation bar kiri menggeser overlay dan mempersempitnya`() {
        assertEquals(OverlayHorizontalBounds(x = 120, width = 1800), overlayHorizontalBounds(1920, 120, 0))
    }

    @Test
    fun `inset kiri dan kanan sama-sama dikurangi`() {
        assertEquals(OverlayHorizontalBounds(x = 50, width = 980), overlayHorizontalBounds(1080, 50, 50))
    }

    @Test
    fun `tanpa inset overlay selebar layar seperti dulu`() {
        assertEquals(OverlayHorizontalBounds(x = 0, width = 1080), overlayHorizontalBounds(1080, 0, 0))
    }

    @Test
    fun `inset yang menghabiskan seluruh lebar diabaikan`() {
        assertEquals(OverlayHorizontalBounds(x = 0, width = 1080), overlayHorizontalBounds(1080, 600, 600))
    }

    @Test
    fun `inset negatif diperlakukan sebagai nol`() {
        assertEquals(OverlayHorizontalBounds(x = 0, width = 1080), overlayHorizontalBounds(1080, -10, -10))
    }
}
