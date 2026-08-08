package com.tglabs.spotivibe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectPolicyTest {

    // ── reconnectDelayMs ─────────────────────────────────────────

    @Test
    fun `percobaan pertama langsung tanpa menunggu`() {
        assertEquals(0L, reconnectDelayMs(0))
    }

    @Test
    fun `backoff naik eksponensial`() {
        assertEquals(1_000L, reconnectDelayMs(1))
        assertEquals(2_000L, reconnectDelayMs(2))
        assertEquals(4_000L, reconnectDelayMs(3))
        assertEquals(8_000L, reconnectDelayMs(4))
    }

    @Test
    fun `backoff dipotong di lima menit`() {
        assertEquals(MAX_RECONNECT_DELAY_MS, reconnectDelayMs(20))
        assertEquals(MAX_RECONNECT_DELAY_MS, reconnectDelayMs(100))
    }

    /**
     * Kegagalan beruntun tidak boleh membuat delay meluap jadi negatif atau
     * nol, karena itu akan berubah jadi busy loop yang menyambung terus-menerus.
     */
    @Test
    fun `kegagalan ekstrem tidak meluap jadi delay kecil`() {
        for (n in intArrayOf(30, 60, 64, 100, 1_000, Int.MAX_VALUE)) {
            val d = reconnectDelayMs(n)
            assertTrue("n=$n delay=$d", d in 1_000L..MAX_RECONNECT_DELAY_MS)
        }
    }

    @Test
    fun `delay naik monoton lalu datar`() {
        var sebelumnya = -1L
        for (n in 0..40) {
            val d = reconnectDelayMs(n)
            assertTrue("mundur di n=$n", d >= sebelumnya)
            sebelumnya = d
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `kegagalan negatif ditolak`() {
        reconnectDelayMs(-1)
    }

    // ── decideReconnect ──────────────────────────────────────────

    @Test
    fun `tanpa sesi tidak menyambung`() {
        assertEquals(
            ReconnectDecision.Idle,
            decideReconnect(hasSession = false, LinkState.Disconnected, 0),
        )
    }

    @Test
    fun `tanpa sesi tetap Idle walau state error`() {
        assertEquals(
            ReconnectDecision.Idle,
            decideReconnect(hasSession = false, LinkState.Error, 5),
        )
    }

    @Test
    fun `sudah tersambung tidak melakukan apa-apa`() {
        assertEquals(
            ReconnectDecision.AlreadyLive,
            decideReconnect(hasSession = true, LinkState.Connected, 0),
        )
    }

    @Test
    fun `sedang menyambung tidak memicu percobaan kedua`() {
        assertEquals(
            ReconnectDecision.AlreadyLive,
            decideReconnect(hasSession = true, LinkState.Connecting, 3),
        )
    }

    /**
     * REGRESI: inti perbaikan ini.
     *
     * Dulu koneksi putus berarti MainActivity mematikan foreground service,
     * notification hilang, dan tidak ada apapun yang mencoba menyambung lagi.
     * User harus membuka app ini secara manual. Selama masih ada sesi,
     * putusnya link harus menghasilkan percobaan ulang, bukan menyerah.
     */
    @Test
    fun `link putus dengan sesi masih ada memicu percobaan ulang`() {
        val d = decideReconnect(hasSession = true, LinkState.Disconnected, 0)
        assertEquals(ReconnectDecision.RetryAfter(0L), d)
    }

    @Test
    fun `state error juga memicu percobaan ulang`() {
        val d = decideReconnect(hasSession = true, LinkState.Error, 1)
        assertEquals(ReconnectDecision.RetryAfter(1_000L), d)
    }

    /**
     * Tidak ada keadaan menyerah untuk kegagalan transport. Spotify yang
     * kebetulan sedang tidak jalan bukan alasan mematikan companion selamanya;
     * itu justru mengembalikan masalah yang sedang diperbaiki.
     */
    @Test
    fun `tidak pernah menyerah selama sesi masih ada`() {
        for (n in intArrayOf(1, 10, 100, 10_000)) {
            val d = decideReconnect(hasSession = true, LinkState.Disconnected, n)
            assertTrue("n=$n menyerah", d is ReconnectDecision.RetryAfter)
        }
    }

    @Test
    fun `delay yang dipakai keputusan sama dengan kebijakan backoff`() {
        for (n in 0..12) {
            val d = decideReconnect(hasSession = true, LinkState.Disconnected, n)
            assertEquals(
                ReconnectDecision.RetryAfter(reconnectDelayMs(n)),
                d,
            )
        }
    }

    /**
     * Simulasi Spotify mati lama: 12 percobaan beruntun tidak boleh menghabiskan
     * baterai dengan mencoba tiap detik, tapi juga tidak boleh berhenti.
     */
    @Test
    fun `dua belas kegagalan beruntun mencapai batas atas tanpa berhenti`() {
        var total = 0L
        var percobaan = 0
        repeat(12) {
            val d = decideReconnect(hasSession = true, LinkState.Disconnected, percobaan)
            assertTrue(d is ReconnectDecision.RetryAfter)
            total += (d as ReconnectDecision.RetryAfter).delayMs
            percobaan++
        }
        assertEquals(MAX_RECONNECT_DELAY_MS, reconnectDelayMs(percobaan))
        assertTrue("12 percobaan seharusnya menyebar lebih dari 10 menit", total > 600_000L)
    }
}
