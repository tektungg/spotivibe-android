package com.tglabs.spotivibe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupervisorDecisionsTest {

    private val semuaKeputusan = listOf(
        ReconnectDecision.Idle,
        ReconnectDecision.AlreadyLive,
        ReconnectDecision.RetryAfter(0),
        ReconnectDecision.RetryAfter(1000),
        ReconnectDecision.RetryAfter(300_000),
    )

    // ── Keadaan companion ────────────────────────────────────────

    /**
     * REGRESI: dulu Disconnected memicu `stopSelf()`, jadi Spotify yang
     * dimatikan sekali berarti notification hilang SELAMANYA sampai user
     * membuka app ini lagi. Link yang putus tidak boleh menghentikan companion;
     * hanya ketiadaan sesi yang boleh.
     */
    @Test
    fun `link putus tidak menghentikan companion`() {
        assertEquals(Companion.Running, companionFor(ReconnectDecision.RetryAfter(0)))
        assertEquals(Companion.Running, companionFor(ReconnectDecision.RetryAfter(60_000)))
    }

    @Test
    fun `hanya ketiadaan sesi yang menghentikan companion`() {
        assertEquals(Companion.Stopped, companionFor(ReconnectDecision.Idle))
        assertEquals(
            "tepat satu keputusan yang boleh menghentikan",
            1,
            semuaKeputusan.count { companionFor(it) == Companion.Stopped },
        )
    }

    @Test
    fun `link hidup berarti companion jalan`() {
        assertEquals(Companion.Running, companionFor(ReconnectDecision.AlreadyLive))
    }

    /**
     * Undetermined adalah nilai AWAL sebelum keputusan pertama diambil. Ia tidak
     * boleh pernah jadi hasil pemetaan, karena itu berarti service kehilangan
     * arah di tengah jalan.
     */
    @Test
    fun `Undetermined tidak pernah jadi hasil`() {
        semuaKeputusan.forEach {
            assertTrue("$it menghasilkan Undetermined", companionFor(it) != Companion.Undetermined)
        }
    }

    // ── Tanda menyambung ulang ───────────────────────────────────

    /**
     * Percobaan pertama jedanya nol dan terjadi seketika. Menyalakan tanda di
     * situ cuma membuat teks notifikasi berkedip antara lirik dan
     * "Reconnecting…" tanpa ada yang benar-benar ditunggu.
     */
    @Test
    fun `percobaan seketika tidak menyalakan tanda`() {
        assertFalse(reconnectingFor(ReconnectDecision.RetryAfter(0)))
    }

    @Test
    fun `jeda backoff menyalakan tanda`() {
        assertTrue(reconnectingFor(ReconnectDecision.RetryAfter(1)))
        assertTrue(reconnectingFor(ReconnectDecision.RetryAfter(300_000)))
    }

    @Test
    fun `keadaan tenang tidak menyalakan tanda`() {
        assertFalse(
            "tidak ada sesi bukan berarti sedang menyambung",
            reconnectingFor(ReconnectDecision.Idle),
        )
        assertFalse(
            "sudah tersambung bukan berarti sedang menyambung",
            reconnectingFor(ReconnectDecision.AlreadyLive),
        )
    }

    // ── Penyetelan ulang hitungan gagal ──────────────────────────

    /**
     * Tanpa penyetelan ulang, koneksi yang berhasil setelah beberapa kali gagal
     * tetap membawa hitungan lamanya, dan kegagalan berikutnya langsung memakai
     * backoff terpanjang seolah tidak pernah ada keberhasilan di antaranya.
     */
    @Test
    fun `keberhasilan menyetel ulang hitungan gagal`() {
        assertTrue(shouldResetFailures(ReconnectDecision.AlreadyLive))
        assertTrue(shouldResetFailures(ReconnectDecision.Idle))
    }

    @Test
    fun `percobaan ulang tidak menyetel ulang hitungan`() {
        assertFalse(shouldResetFailures(ReconnectDecision.RetryAfter(0)))
        assertFalse(shouldResetFailures(ReconnectDecision.RetryAfter(5000)))
    }

    // ── Kondisi link ─────────────────────────────────────────────

    @Test
    fun `link mati perlu disambung ulang`() {
        assertTrue(linkNeedsReconnect(LinkState.Disconnected))
        assertTrue(linkNeedsReconnect(LinkState.Error))
    }

    /**
     * Connecting ikut dihitung hidup. Menganggapnya mati akan memulai
     * penyambungan KEDUA di atas yang pertama yang masih berjalan, dan callback
     * yang datang belakangan menimpa hasil yang pertama.
     */
    @Test
    fun `sedang menyambung dihitung hidup`() {
        assertFalse(linkNeedsReconnect(LinkState.Connecting))
        assertFalse(linkNeedsReconnect(LinkState.Connected))
    }

    @Test
    fun `semua keadaan link tercakup`() {
        val perlu = LinkState.entries.count { linkNeedsReconnect(it) }
        val tidak = LinkState.entries.count { !linkNeedsReconnect(it) }
        assertEquals(LinkState.entries.size, perlu + tidak)
        assertEquals("tepat dua yang dihitung hidup", 2, tidak)
    }

    @Test
    fun `deterministik`() {
        repeat(5) {
            assertEquals(
                companionFor(ReconnectDecision.RetryAfter(5)),
                companionFor(ReconnectDecision.RetryAfter(5)),
            )
        }
    }
}
