package com.tglabs.spotivibe.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyTokensTest {

    private val now = 1_700_000_000_000L

    private fun tokens(
        access: String = "at",
        refresh: String? = "rt",
        expiresAtMs: Long = now + 3_600_000L,
    ) = SpotifyTokens(access, refresh, expiresAtMs)

    // ── isFresh ──────────────────────────────────────────────────

    @Test
    fun `token dengan sisa waktu panjang masih fresh`() {
        assertTrue(tokens().isFresh(now))
    }

    @Test
    fun `token yang sudah lewat expiry tidak fresh`() {
        assertFalse(tokens(expiresAtMs = now - 1).isFresh(now))
    }

    /**
     * Margin 60 detik. Tanpa ini, token yang lolos cek bisa sudah mati saat
     * request-nya benar-benar sampai ke Spotify.
     */
    @Test
    fun `token yang habis dalam margin dianggap sudah basi`() {
        val expiring = tokens(expiresAtMs = now + 30_000L)
        assertFalse(expiring.isFresh(now))
        assertTrue("di luar margin harus fresh", expiring.isFresh(now - 60_000L))
    }

    @Test
    fun `access token kosong tidak pernah fresh`() {
        assertFalse(tokens(access = "").isFresh(now))
    }

    // ── canRefresh ───────────────────────────────────────────────

    @Test
    fun `punya refresh token berarti bisa refresh`() {
        assertTrue(tokens().canRefresh)
    }

    /**
     * Bentuk sesi warisan implicit grant: ada access token, tanpa cara
     * memperbaruinya. Inilah akar bug lama.
     */
    @Test
    fun `sesi implicit grant lama tidak bisa refresh`() {
        assertFalse(tokens(refresh = null).canRefresh)
        assertFalse(tokens(refresh = "").canRefresh)
    }

    // ── fromResponse ─────────────────────────────────────────────

    @Test
    fun `expiry dihitung dari waktu sekarang plus expires_in`() {
        val t = SpotifyTokens.fromResponse(
            accessToken = "new",
            refreshToken = "r",
            expiresInSec = 3600,
            nowMs = now,
        )
        assertEquals(now + 3_600_000L, t.expiresAtMs)
    }

    /**
     * Spotify me-rotate refresh token: response refresh kadang tidak membawa
     * `refresh_token`. Menimpanya dengan null akan mematikan sesi diam-diam di
     * refresh berikutnya.
     */
    @Test
    fun `refresh token lama dipertahankan kalau response tidak membawa yang baru`() {
        val t = SpotifyTokens.fromResponse(
            accessToken = "new",
            refreshToken = null,
            expiresInSec = 3600,
            nowMs = now,
            previousRefresh = "rt-lama",
        )
        assertEquals("rt-lama", t.refreshToken)
    }

    @Test
    fun `refresh token baru menimpa yang lama saat dirotasi`() {
        val t = SpotifyTokens.fromResponse(
            accessToken = "new",
            refreshToken = "rt-baru",
            expiresInSec = 3600,
            nowMs = now,
            previousRefresh = "rt-lama",
        )
        assertEquals("rt-baru", t.refreshToken)
    }

    @Test
    fun `refresh token kosong diperlakukan sama dengan tidak ada`() {
        val t = SpotifyTokens.fromResponse("a", "", 3600, now, previousRefresh = "rt-lama")
        assertEquals("rt-lama", t.refreshToken)
    }

    @Test
    fun `expires_in negatif tidak menghasilkan expiry di masa lalu`() {
        val t = SpotifyTokens.fromResponse("a", "r", -100, now)
        assertEquals(now, t.expiresAtMs)
    }

    @Test
    fun `tanpa previous refresh hasilnya null`() {
        assertNull(SpotifyTokens.fromResponse("a", null, 3600, now).refreshToken)
    }

    // ── classifyTokenFailure ─────────────────────────────────────

    /**
     * Pembedaan yang paling menentukan di seluruh file ini: permanen berarti
     * kredensial dibuang dan user login ulang; sementara berarti dipertahankan.
     * Salah klasifikasi ke arah permanen berarti sinyal hilang sesaat memaksa
     * user login ulang.
     */
    @Test
    fun `invalid_grant permanen`() {
        val r = classifyTokenFailure(400, "invalid_grant")
        assertTrue(r is TokenResult.PermanentFailure)
    }

    @Test
    fun `invalid_client permanen`() {
        assertTrue(classifyTokenFailure(401, "invalid_client") is TokenResult.PermanentFailure)
    }

    @Test
    fun `error 5xx sementara`() {
        assertTrue(classifyTokenFailure(503, null) is TokenResult.TransientFailure)
    }

    @Test
    fun `rate limit sementara`() {
        assertTrue(classifyTokenFailure(429, null) is TokenResult.TransientFailure)
    }

    @Test
    fun `400 tanpa kode error dikenal tetap permanen`() {
        assertTrue(classifyTokenFailure(400, null) is TokenResult.PermanentFailure)
    }

    @Test
    fun `kode error tidak case sensitive`() {
        assertTrue(classifyTokenFailure(418, "INVALID_GRANT") is TokenResult.PermanentFailure)
    }
}
