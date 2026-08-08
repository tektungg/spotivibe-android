package com.tglabs.spotivibe.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyAuthUrlsTest {

    private val redirect = "spotivibe://callback"

    // ── buildAuthorizeUrl ────────────────────────────────────────

    @Test
    fun `authorize url memuat semua parameter PKCE`() {
        val url = SpotifyAuthUrls.buildAuthorizeUrl(
            clientId = "abc123",
            redirectUri = redirect,
            codeChallenge = "CHALLENGE",
            state = "STATE",
        )
        assertTrue(url.startsWith("https://accounts.spotify.com/authorize?"))
        assertTrue(url.contains("client_id=abc123"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("code_challenge=CHALLENGE"))
        assertTrue(url.contains("state=STATE"))
    }

    @Test
    fun `redirect uri di-percent-encode`() {
        val url = SpotifyAuthUrls.buildAuthorizeUrl(
            clientId = "abc",
            redirectUri = redirect,
            codeChallenge = "c",
            state = "s",
        )
        assertTrue(url.contains("redirect_uri=spotivibe%3A%2F%2Fcallback"))
    }

    /**
     * `app-remote-control` wajib ikut: kalau hilang, grant dari flow web tidak
     * mengizinkan App Remote bind dan user kena dialog consent kedua.
     */
    @Test
    fun `scope memuat app-remote-control dan dipisah persen-20`() {
        val url = SpotifyAuthUrls.buildAuthorizeUrl(
            clientId = "abc",
            redirectUri = redirect,
            codeChallenge = "c",
            state = "s",
        )
        assertTrue(SpotifyAuthUrls.SCOPES.contains("app-remote-control"))
        assertTrue(url.contains("scope=app-remote-control%20user-read-currently-playing"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `client id kosong ditolak`() {
        SpotifyAuthUrls.buildAuthorizeUrl("", redirect, "c", "s")
    }

    // ── parseRedirect ────────────────────────────────────────────

    @Test
    fun `code diterima saat state cocok`() {
        val result = SpotifyAuthUrls.parseRedirect(
            uri = "$redirect?code=AQC123&state=STATE",
            expectedRedirectUri = redirect,
            expectedState = "STATE",
        )
        assertEquals(SpotifyAuthUrls.Redirect.Code("AQC123"), result)
    }

    @Test
    fun `user menolak menghasilkan Denied`() {
        val result = SpotifyAuthUrls.parseRedirect(
            uri = "$redirect?error=access_denied&state=STATE",
            expectedRedirectUri = redirect,
            expectedState = "STATE",
        )
        assertEquals(SpotifyAuthUrls.Redirect.Denied("access_denied"), result)
    }

    /** Proteksi CSRF: code dari state asing tidak boleh pernah ditukar. */
    @Test
    fun `state berbeda ditolak`() {
        val result = SpotifyAuthUrls.parseRedirect(
            uri = "$redirect?code=AQC123&state=PENYERANG",
            expectedRedirectUri = redirect,
            expectedState = "STATE",
        )
        assertEquals(SpotifyAuthUrls.Redirect.StateMismatch, result)
    }

    /** Tidak ada flow aktif berarti redirect apapun harus dibuang. */
    @Test
    fun `redirect tanpa pending state ditolak`() {
        val result = SpotifyAuthUrls.parseRedirect(
            uri = "$redirect?code=AQC123&state=STATE",
            expectedRedirectUri = redirect,
            expectedState = null,
        )
        assertEquals(SpotifyAuthUrls.Redirect.StateMismatch, result)
    }

    @Test
    fun `deep link lain diabaikan`() {
        val result = SpotifyAuthUrls.parseRedirect(
            uri = "https://example.com/callback?code=AQC123&state=STATE",
            expectedRedirectUri = redirect,
            expectedState = "STATE",
        )
        assertEquals(SpotifyAuthUrls.Redirect.NotOurs, result)
    }

    @Test
    fun `intent launcher biasa tanpa data diabaikan`() {
        assertEquals(
            SpotifyAuthUrls.Redirect.NotOurs,
            SpotifyAuthUrls.parseRedirect(null, redirect, "STATE"),
        )
    }

    @Test
    fun `redirect kita tanpa query dianggap malformed`() {
        assertEquals(
            SpotifyAuthUrls.Redirect.Malformed,
            SpotifyAuthUrls.parseRedirect(redirect, redirect, "STATE"),
        )
    }

    @Test
    fun `redirect dengan state cocok tapi tanpa code maupun error malformed`() {
        assertEquals(
            SpotifyAuthUrls.Redirect.Malformed,
            SpotifyAuthUrls.parseRedirect("$redirect?state=STATE", redirect, "STATE"),
        )
    }

    @Test
    fun `fragment yang ditempel browser tidak mengganggu parsing`() {
        val result = SpotifyAuthUrls.parseRedirect(
            uri = "$redirect?code=AQC123&state=STATE#_=_",
            expectedRedirectUri = redirect,
            expectedState = "STATE",
        )
        assertEquals(SpotifyAuthUrls.Redirect.Code("AQC123"), result)
    }

    @Test
    fun `error diprioritaskan di atas code`() {
        val result = SpotifyAuthUrls.parseRedirect(
            uri = "$redirect?code=AQC&error=access_denied&state=STATE",
            expectedRedirectUri = redirect,
            expectedState = "STATE",
        )
        assertEquals(SpotifyAuthUrls.Redirect.Denied("access_denied"), result)
    }

    // ── encoding helper ──────────────────────────────────────────

    @Test
    fun `percent decode tidak mengubah plus jadi spasi`() {
        // Komponen query URI, bukan form-encoding. Code Spotify bisa memuat
        // '+', dan mengubahnya jadi spasi akan merusak penukaran token.
        assertEquals("a+b", SpotifyAuthUrls.percentDecode("a+b"))
    }

    @Test
    fun `percent decode menangani escape valid`() {
        assertEquals("spotivibe://callback", SpotifyAuthUrls.percentDecode("spotivibe%3A%2F%2Fcallback"))
    }

    @Test
    fun `percent decode membiarkan escape rusak apa adanya`() {
        assertEquals("100%zz", SpotifyAuthUrls.percentDecode("100%zz"))
    }

    @Test
    fun `percent encode memakai persen-20 untuk spasi`() {
        assertEquals("a%20b", SpotifyAuthUrls.percentEncode("a b"))
    }

    @Test
    fun `percent encode meloloskan karakter unreserved`() {
        assertEquals("aZ0-._~", SpotifyAuthUrls.percentEncode("aZ0-._~"))
    }

    @Test
    fun `parse query mengambil kemunculan pertama untuk key duplikat`() {
        assertEquals(
            mapOf("a" to "1", "b" to "2"),
            SpotifyAuthUrls.parseQuery("a=1&b=2&a=3"),
        )
    }
}
