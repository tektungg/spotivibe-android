package com.tglabs.spotivibe.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyTokensTest {

    private val now = 1_700_000_000_000L

    private fun token(
        access: String = "AT",
        refresh: String? = "RT",
        expiresAt: Long = now + 3_600_000L,
    ) = SpotifyTokens(access, refresh, expiresAt)

    // ── Kesegaran token ──────────────────────────────────────────

    @Test
    fun `token yang masih lama masa berlakunya segar`() {
        assertTrue(token().isFresh(now))
    }

    @Test
    fun `token yang sudah lewat tidak segar`() {
        assertFalse(token(expiresAt = now - 1).isFresh(now))
    }

    /**
     * Margin ada untuk latensi jaringan dan jam device yang meleset sedikit.
     * Tanpa margin, token yang habis dalam hitungan detik akan dipakai untuk
     * request yang baru sampai ke server setelah kedaluwarsa, dan gagalnya
     * terlihat seperti sesi dicabut.
     */
    @Test
    fun `token yang habis dalam margin sudah dianggap basi`() {
        val hampirHabis = token(expiresAt = now + 30_000L)
        assertFalse("30 detik lagi harus sudah dianggap basi", hampirHabis.isFresh(now))
        assertTrue("tanpa margin ia masih terlihat sah", now < hampirHabis.expiresAtMs)
    }

    @Test
    fun `batas margin tepat di titiknya`() {
        val skew = SpotifyTokens.DEFAULT_SKEW_MS
        assertFalse(token(expiresAt = now + skew).isFresh(now))
        assertTrue(token(expiresAt = now + skew + 1).isFresh(now))
    }

    @Test
    fun `margin bisa diatur`() {
        val t = token(expiresAt = now + 30_000L)
        assertTrue("dengan margin kecil masih segar", t.isFresh(now, skewMs = 1_000L))
        assertFalse("dengan margin besar sudah basi", t.isFresh(now, skewMs = 120_000L))
    }

    @Test
    fun `access token kosong tidak pernah segar`() {
        assertFalse(token(access = "").isFresh(now))
        assertFalse(token(access = "   ").isFresh(now))
    }

    // ── Kemampuan refresh ────────────────────────────────────────

    @Test
    fun `bisa refresh hanya kalau refresh token ada isinya`() {
        assertTrue(token(refresh = "RT").canRefresh)
        assertFalse(token(refresh = null).canRefresh)
        assertFalse(token(refresh = "").canRefresh)
        assertFalse(token(refresh = "   ").canRefresh)
    }

    // ── Perakitan dari response ──────────────────────────────────

    @Test
    fun `expiry dihitung dari sekarang plus umur token`() {
        val t = SpotifyTokens.fromResponse("AT", "RT", expiresInSec = 3600, nowMs = now)
        assertEquals(now + 3_600_000L, t.expiresAtMs)
    }

    /**
     * REGRESI: Spotify me-rotate refresh token. Response refresh KADANG membawa
     * `refresh_token` baru, kadang tidak. Menimpanya dengan null saat tidak ada
     * akan mematikan sesi secara senyap di refresh berikutnya, dan gejalanya
     * baru muncul satu jam kemudian sebagai "tiba-tiba diminta login lagi".
     */
    @Test
    fun `refresh token lama dipertahankan kalau response tidak membawa yang baru`() {
        val t = SpotifyTokens.fromResponse(
            accessToken = "AT2",
            refreshToken = null,
            expiresInSec = 3600,
            nowMs = now,
            previousRefresh = "RT_LAMA",
        )
        assertEquals("RT_LAMA", t.refreshToken)
    }

    @Test
    fun `refresh token kosong juga dianggap tidak ada`() {
        val t = SpotifyTokens.fromResponse("AT2", "   ", 3600, now, previousRefresh = "RT_LAMA")
        assertEquals("RT_LAMA", t.refreshToken)
    }

    @Test
    fun `refresh token baru menimpa yang lama`() {
        val t = SpotifyTokens.fromResponse("AT2", "RT_BARU", 3600, now, previousRefresh = "RT_LAMA")
        assertEquals("RT_BARU", t.refreshToken)
    }

    @Test
    fun `tanpa refresh lama maupun baru hasilnya null`() {
        assertNull(SpotifyTokens.fromResponse("AT", null, 3600, now).refreshToken)
    }

    @Test
    fun `umur negatif tidak membuat expiry mundur`() {
        val t = SpotifyTokens.fromResponse("AT", "RT", expiresInSec = -100, nowMs = now)
        assertEquals("expiry tidak boleh sebelum sekarang", now, t.expiresAtMs)
    }

    // ── Klasifikasi kegagalan ────────────────────────────────────

    /**
     * Keputusan ini menentukan apakah user dipaksa login ulang. Salah
     * mengklasifikasi kegagalan sementara sebagai permanen berarti membuang
     * sesi yang masih sah gara-gara sinyal hilang sesaat.
     */
    @Test
    fun `error OAuth yang dikenal itu permanen`() {
        listOf(
            "invalid_grant",
            "invalid_client",
            "invalid_request",
            "unauthorized_client",
            "unsupported_grant_type",
        ).forEach { err ->
            val hasil = classifyTokenFailure(400, err)
            assertTrue("$err harus permanen, dapat $hasil", hasil is TokenResult.PermanentFailure)
        }
    }

    @Test
    fun `error dinormalkan huruf kecil dan dipangkas`() {
        val hasil = classifyTokenFailure(400, "  INVALID_GRANT  ")
        assertTrue(hasil is TokenResult.PermanentFailure)
        assertEquals("invalid_grant", (hasil as TokenResult.PermanentFailure).error)
    }

    @Test
    fun `empat ratus dan empat nol satu permanen walau errornya tidak dikenal`() {
        assertTrue(classifyTokenFailure(400, null) is TokenResult.PermanentFailure)
        assertTrue(classifyTokenFailure(401, "") is TokenResult.PermanentFailure)
        assertEquals(
            "http_400",
            (classifyTokenFailure(400, null) as TokenResult.PermanentFailure).error,
        )
    }

    /**
     * 5xx, rate limit, dan jaringan mati harus dianggap sementara. Membuang
     * kredensial di sini akan memaksa login ulang setiap kali server Spotify
     * sedang bermasalah.
     */
    @Test
    fun `lima ratusan dan rate limit itu sementara`() {
        listOf(429, 500, 502, 503, 504, 0, -1).forEach { kode ->
            val hasil = classifyTokenFailure(kode, null)
            assertTrue("$kode harus sementara, dapat $hasil", hasil is TokenResult.TransientFailure)
        }
    }

    /** Error permanen menang walau kode HTTP-nya bukan 400/401. */
    @Test
    fun `error permanen menang atas kode HTTP`() {
        assertTrue(classifyTokenFailure(500, "invalid_grant") is TokenResult.PermanentFailure)
    }

    @Test
    fun `error yang tidak dikenal di kode sementara tetap sementara`() {
        val hasil = classifyTokenFailure(503, "server_overloaded")
        assertTrue(hasil is TokenResult.TransientFailure)
        assertEquals("http_503", (hasil as TokenResult.TransientFailure).reason)
    }

    @Test
    fun `deterministik`() {
        repeat(5) {
            assertEquals(
                classifyTokenFailure(400, "invalid_grant"),
                classifyTokenFailure(400, "invalid_grant"),
            )
        }
    }
}
