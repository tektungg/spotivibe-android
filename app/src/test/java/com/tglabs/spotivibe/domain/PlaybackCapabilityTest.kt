package com.tglabs.spotivibe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCapabilityTest {

    /**
     * REGRESI: ini bug yang sebenarnya.
     *
     * Dulu deteksi Premium adalah `Boolean` dengan nilai awal `false`, jadi
     * "belum tahu" tidak bisa dibedakan dari "akun Free". Karena token implicit
     * grant tidak bisa di-refresh, panggilan `/me` gagal selamanya setelah satu
     * jam, `isPremium` terkunci `false`, dan user Premium kehilangan tombol
     * transport plus dapat banner "PREMIUM REQUIRED".
     *
     * Belum tahu harus berarti Unknown, dan Unknown harus tetap menampilkan
     * kontrol: kontrolnya jalan lewat App Remote, bukan Web API.
     */
    @Test
    fun `product belum diketahui berarti Unknown dan transport tetap tampil`() {
        val capability = resolveCapability(product = null, remoteRejectedControl = false)
        assertEquals(PlaybackCapability.Unknown, capability)
        assertTrue("transport harus tampil selama belum ada bukti", capability.showsTransport)
        assertFalse("banner tidak boleh muncul tanpa bukti", capability.showsUpgradeNotice)
    }

    @Test
    fun `product premium berarti Full`() {
        assertEquals(
            PlaybackCapability.Full,
            resolveCapability("premium", remoteRejectedControl = false),
        )
    }

    @Test
    fun `product premium tidak case sensitive`() {
        assertEquals(
            PlaybackCapability.Full,
            resolveCapability("Premium", remoteRejectedControl = false),
        )
    }

    @Test
    fun `product free berarti Restricted`() {
        val capability = resolveCapability("free", remoteRejectedControl = false)
        assertEquals(PlaybackCapability.Restricted, capability)
        assertFalse(capability.showsTransport)
        assertTrue(capability.showsUpgradeNotice)
    }

    @Test
    fun `product open berarti Restricted`() {
        assertEquals(
            PlaybackCapability.Restricted,
            resolveCapability("open", remoteRejectedControl = false),
        )
    }

    /**
     * Penolakan App Remote adalah bukti dari jalur yang benar-benar dipakai
     * untuk mengontrol, jadi menang atas label akun apapun dari Web API.
     */
    @Test
    fun `penolakan App Remote menang atas product premium`() {
        assertEquals(
            PlaybackCapability.Restricted,
            resolveCapability("premium", remoteRejectedControl = true),
        )
    }

    @Test
    fun `penolakan App Remote menentukan walau product belum diketahui`() {
        assertEquals(
            PlaybackCapability.Restricted,
            resolveCapability(null, remoteRejectedControl = true),
        )
    }

    @Test
    fun `Full menampilkan transport tanpa banner`() {
        assertTrue(PlaybackCapability.Full.showsTransport)
        assertFalse(PlaybackCapability.Full.showsUpgradeNotice)
    }
}
