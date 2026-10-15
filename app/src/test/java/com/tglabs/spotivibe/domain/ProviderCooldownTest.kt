package com.tglabs.spotivibe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCooldownTest {

    private var now = 1_000_000L
    private val cooldown = ProviderCooldown(nowMs = { now })
    private val gagal = ProviderProbe.Failed("http_503")

    @Test
    fun `gagal pertama tidak menjeda`() {
        cooldown.record(ProviderId.Amll, gagal)
        assertFalse(cooldown.isCoolingDown(ProviderId.Amll))
    }

    @Test
    fun `gagal kedua menjeda tiga puluh detik`() {
        repeat(2) { cooldown.record(ProviderId.Amll, gagal) }
        assertTrue(cooldown.isCoolingDown(ProviderId.Amll))
        now += 29_999
        assertTrue(cooldown.isCoolingDown(ProviderId.Amll))
        now += 1
        assertFalse(cooldown.isCoolingDown(ProviderId.Amll))
    }

    @Test
    fun `jeda menanjak lalu mentok di lima menit`() {
        val jeda = (1..7).map {
            cooldown.record(ProviderId.Amll, gagal)
            pauseLength()
        }
        assertEquals(listOf(0L, 30_000L, 60_000L, 120_000L, 300_000L, 300_000L, 300_000L), jeda)
    }

    /** Panjang jeda yang sedang berlaku, dicari dengan memajukan jam per detik. */
    private fun pauseLength(): Long {
        val start = now
        while (cooldown.isCoolingDown(ProviderId.Amll)) now += 1_000
        return (now - start).also { now = start }
    }

    @Test
    fun `Retry-After dari server menang atas tangga`() {
        cooldown.record(ProviderId.Amll, ProviderProbe.Failed("http_429", retryAfterMs = 90_000))
        assertTrue(cooldown.isCoolingDown(ProviderId.Amll))
        now += 90_000
        assertFalse(cooldown.isCoolingDown(ProviderId.Amll))
    }

    @Test
    fun `jawaban sukses mereset hitungan`() {
        repeat(2) { cooldown.record(ProviderId.Amll, gagal) }
        cooldown.record(ProviderId.Amll, ProviderProbe.Absent)
        assertFalse(cooldown.isCoolingDown(ProviderId.Amll))
        cooldown.record(ProviderId.Amll, gagal)
        assertFalse(cooldown.isCoolingDown(ProviderId.Amll))
    }

    @Test
    fun `sumber lain tidak ikut terjeda`() {
        repeat(2) { cooldown.record(ProviderId.Amll, gagal) }
        assertFalse(cooldown.isCoolingDown(ProviderId.LrcMux))
    }

    @Test
    fun `header Retry-After dibaca dalam detik dan dijepit`() {
        assertEquals(30_000L, parseRetryAfterMs("30"))
        assertEquals(300_000L, parseRetryAfterMs("86400"))
        assertNull(parseRetryAfterMs(null))
        assertNull(parseRetryAfterMs("Wed, 21 Oct 2026 07:28:00 GMT"))
        assertNull(parseRetryAfterMs("-5"))
    }
}
