package com.tglabs.spotivibe.data.auth

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyAuthRepositoryTest {

    private val redirectUri = "spotivibe://callback"
    private val now = 1_700_000_000_000L

    // ── Fakes ────────────────────────────────────────────────────

    private class FakeStorage(
        var tokens: SpotifyTokens? = null,
        var pending: PendingAuth? = null,
    ) : AuthStorage {
        var saveCount = 0
        var clearCount = 0

        override suspend fun loadTokens(): SpotifyTokens? = tokens
        override suspend fun saveTokens(tokens: SpotifyTokens) {
            this.tokens = tokens
            saveCount++
        }

        override suspend fun clearTokens() {
            tokens = null
            clearCount++
        }

        override suspend fun savePendingAuth(pending: PendingAuth) {
            this.pending = pending
        }

        override suspend fun loadPendingAuth(): PendingAuth? = pending
        override suspend fun clearPendingAuth() {
            pending = null
        }
    }

    private class FakeEndpoint(
        var exchangeResult: TokenResult = TokenResult.TransientFailure("belum diset"),
        var refreshResult: TokenResult = TokenResult.TransientFailure("belum diset"),
        /** Latency buatan supaya pemanggil bersamaan benar-benar tumpang tindih. */
        var latencyMs: Long = 0,
    ) : TokenEndpoint {
        var exchangeCount = 0
        var refreshCount = 0
        var lastVerifier: String? = null
        var lastRefreshToken: String? = null

        override suspend fun exchangeCode(code: String, codeVerifier: String): TokenResult {
            exchangeCount++
            lastVerifier = codeVerifier
            if (latencyMs > 0) delay(latencyMs)
            return exchangeResult
        }

        override suspend fun refresh(refreshToken: String): TokenResult {
            refreshCount++
            lastRefreshToken = refreshToken
            if (latencyMs > 0) delay(latencyMs)
            return refreshResult
        }
    }

    private fun repo(
        storage: FakeStorage = FakeStorage(),
        endpoint: FakeEndpoint = FakeEndpoint(),
        clock: () -> Long = { now },
    ) = SpotifyAuthRepository(
        clientId = "client",
        redirectUri = redirectUri,
        storage = storage,
        api = endpoint,
        nowMs = clock,
    )

    private fun freshTokens(refresh: String? = "rt") =
        SpotifyTokens("at-lama", refresh, now + 3_600_000L)

    private fun staleTokens(refresh: String? = "rt") =
        SpotifyTokens("at-basi", refresh, now - 1_000L)

    // ── restore ──────────────────────────────────────────────────

    @Test
    fun `tanpa kredensial tersimpan statusnya SignedOut`() = runTest {
        val r = repo()
        r.restore()
        assertEquals(SpotifyAuthRepository.AuthState.SignedOut, r.authState.value)
        assertFalse(r.hasSession())
    }

    @Test
    fun `token basi yang masih punya refresh token tetap dianggap sesi hidup`() = runTest {
        val r = repo(FakeStorage(tokens = staleTokens()))
        r.restore()
        assertEquals(SpotifyAuthRepository.AuthState.SignedIn, r.authState.value)
        assertTrue(r.hasSession())
    }

    /**
     * MIGRASI: sesi warisan implicit grant punya access token tanpa refresh
     * token. Setelah expiry, sesi itu tidak bisa dipulihkan, jadi user harus
     * dibawa ke layar Connect alih-alih dibiarkan di app yang setengah rusak.
     */
    @Test
    fun `sesi implicit grant lama yang sudah expired jadi SignedOut`() = runTest {
        val r = repo(FakeStorage(tokens = staleTokens(refresh = null)))
        r.restore()
        assertEquals(SpotifyAuthRepository.AuthState.SignedOut, r.authState.value)
    }

    // ── validAccessToken ─────────────────────────────────────────

    @Test
    fun `token yang masih fresh dipakai tanpa memanggil jaringan`() = runTest {
        val endpoint = FakeEndpoint()
        val r = repo(FakeStorage(tokens = freshTokens()), endpoint)
        r.restore()
        assertEquals("at-lama", r.validAccessToken())
        assertEquals(0, endpoint.refreshCount)
    }

    @Test
    fun `token basi di-refresh otomatis`() = runTest {
        val endpoint = FakeEndpoint(
            refreshResult = TokenResult.Success(SpotifyTokens("at-baru", "rt", now + 3_600_000L))
        )
        val storage = FakeStorage(tokens = staleTokens())
        val r = repo(storage, endpoint)
        r.restore()

        assertEquals("at-baru", r.validAccessToken())
        assertEquals(1, endpoint.refreshCount)
        assertEquals("rt", endpoint.lastRefreshToken)
        assertEquals("at-baru", storage.tokens?.accessToken)
    }

    /**
     * Single-flight. Sepuluh pemanggil bersamaan (ViewModel, notification
     * service, overlay, preload antrean) harus menghasilkan SATU request
     * refresh. Tanpa mutex, sepuluh refresh paralel berlomba dan yang kalah
     * bisa menimpa token bagus dengan yang sudah basi.
     */
    @Test
    fun `pemanggil bersamaan hanya memicu satu refresh`() = runTest {
        val endpoint = FakeEndpoint(
            refreshResult = TokenResult.Success(SpotifyTokens("at-baru", "rt", now + 3_600_000L)),
            latencyMs = 50,
        )
        val r = repo(FakeStorage(tokens = staleTokens()), endpoint)
        r.restore()

        val results = (1..10).map { async { r.validAccessToken() } }.awaitAll()

        assertEquals(1, endpoint.refreshCount)
        assertTrue("semua pemanggil dapat token baru", results.all { it == "at-baru" })
    }

    /**
     * REGRESI: kegagalan sementara TIDAK boleh membuang kredensial. Kalau
     * dibuang, sinyal hilang sesaat memaksa user login ulang.
     */
    @Test
    fun `refresh gagal sementara mempertahankan kredensial`() = runTest {
        val endpoint = FakeEndpoint(refreshResult = TokenResult.TransientFailure("timeout"))
        val storage = FakeStorage(tokens = staleTokens())
        val r = repo(storage, endpoint)
        r.restore()

        assertNull(r.validAccessToken())
        assertEquals("kredensial tidak boleh dibuang", 0, storage.clearCount)
        assertNotNull(storage.tokens)
        assertEquals(SpotifyAuthRepository.AuthState.SignedIn, r.authState.value)
    }

    @Test
    fun `refresh ditolak permanen membuang kredensial dan minta login ulang`() = runTest {
        val endpoint = FakeEndpoint(
            refreshResult = TokenResult.PermanentFailure("invalid_grant", null)
        )
        val storage = FakeStorage(tokens = staleTokens())
        val r = repo(storage, endpoint)
        r.restore()

        assertNull(r.validAccessToken())
        assertNull(storage.tokens)
        assertEquals(SpotifyAuthRepository.AuthState.NeedsReauth, r.authState.value)
    }

    @Test
    fun `token basi tanpa refresh token langsung minta login ulang`() = runTest {
        val endpoint = FakeEndpoint()
        val r = repo(FakeStorage(tokens = staleTokens(refresh = null)), endpoint)
        r.restore()

        assertNull(r.validAccessToken())
        assertEquals(0, endpoint.refreshCount)
        assertEquals(SpotifyAuthRepository.AuthState.NeedsReauth, r.authState.value)
    }

    @Test
    fun `refresh kedua dilewati selama token hasil refresh masih fresh`() = runTest {
        val endpoint = FakeEndpoint(
            refreshResult = TokenResult.Success(SpotifyTokens("at-baru", "rt", now + 3_600_000L))
        )
        val r = repo(FakeStorage(tokens = staleTokens()), endpoint)
        r.restore()

        r.validAccessToken()
        r.validAccessToken()
        r.validAccessToken()
        assertEquals(1, endpoint.refreshCount)
    }

    // ── beginAuthorization ───────────────────────────────────────

    @Test
    fun `beginAuthorization menyimpan verifier sebelum mengembalikan url`() = runTest {
        val storage = FakeStorage()
        val r = repo(storage)
        val url = r.beginAuthorization()

        val pending = storage.pending
        assertNotNull("verifier harus tersimpan sebelum user pergi ke consent", pending)
        assertTrue(url.contains("code_challenge=${PkceCodes.challengeFor(pending!!.verifier)}"))
        assertTrue(url.contains("state=${pending.state}"))
    }

    // ── completeRedirect ─────────────────────────────────────────

    @Test
    fun `redirect sukses menukar code dan menyimpan token`() = runTest {
        val storage = FakeStorage()
        val endpoint = FakeEndpoint(
            exchangeResult = TokenResult.Success(SpotifyTokens("at", "rt", now + 3_600_000L))
        )
        val r = repo(storage, endpoint)
        r.beginAuthorization()
        val state = storage.pending!!.state
        val verifier = storage.pending!!.verifier

        val result = r.completeRedirect("$redirectUri?code=AQC&state=$state")

        assertEquals(SpotifyAuthRepository.CompleteResult.Success, result)
        assertEquals(verifier, endpoint.lastVerifier)
        assertEquals("at", storage.tokens?.accessToken)
        assertNull("pending harus dibersihkan setelah sukses", storage.pending)
        assertEquals(SpotifyAuthRepository.AuthState.SignedIn, r.authState.value)
    }

    @Test
    fun `intent yang bukan redirect kita diabaikan tanpa efek samping`() = runTest {
        val storage = FakeStorage()
        val endpoint = FakeEndpoint()
        val r = repo(storage, endpoint)
        r.beginAuthorization()

        val result = r.completeRedirect("https://contoh.com/lain?code=X")

        assertEquals(SpotifyAuthRepository.CompleteResult.Ignored, result)
        assertEquals(0, endpoint.exchangeCount)
        assertNotNull("flow yang sedang jalan tidak boleh terganggu", storage.pending)
    }

    @Test
    fun `code dengan state asing tidak pernah ditukar`() = runTest {
        val storage = FakeStorage()
        val endpoint = FakeEndpoint()
        val r = repo(storage, endpoint)
        r.beginAuthorization()

        val result = r.completeRedirect("$redirectUri?code=AQC&state=PENYERANG")

        assertEquals(SpotifyAuthRepository.CompleteResult.Ignored, result)
        assertEquals(0, endpoint.exchangeCount)
    }

    @Test
    fun `user menolak menghasilkan Denied dan membersihkan pending`() = runTest {
        val storage = FakeStorage()
        val r = repo(storage)
        r.beginAuthorization()
        val state = storage.pending!!.state

        val result = r.completeRedirect("$redirectUri?error=access_denied&state=$state")

        assertEquals(
            SpotifyAuthRepository.CompleteResult.Denied("access_denied"),
            result,
        )
        assertNull(storage.pending)
    }

    /**
     * Penukaran yang gagal karena jaringan TIDAK boleh membuang verifier: code
     * masih berlaku beberapa saat, jadi percobaan ulang masih bisa berhasil.
     */
    @Test
    fun `penukaran gagal sementara mempertahankan pending untuk dicoba lagi`() = runTest {
        val storage = FakeStorage()
        val endpoint = FakeEndpoint(exchangeResult = TokenResult.TransientFailure("timeout"))
        val r = repo(storage, endpoint)
        r.beginAuthorization()
        val state = storage.pending!!.state

        val result = r.completeRedirect("$redirectUri?code=AQC&state=$state")

        assertTrue(result is SpotifyAuthRepository.CompleteResult.Failed)
        assertFalse((result as SpotifyAuthRepository.CompleteResult.Failed).permanent)
        assertNotNull(storage.pending)
    }

    @Test
    fun `penukaran ditolak permanen membersihkan pending`() = runTest {
        val storage = FakeStorage()
        val endpoint = FakeEndpoint(
            exchangeResult = TokenResult.PermanentFailure("invalid_grant", null)
        )
        val r = repo(storage, endpoint)
        r.beginAuthorization()
        val state = storage.pending!!.state

        val result = r.completeRedirect("$redirectUri?code=AQC&state=$state")

        assertTrue((result as SpotifyAuthRepository.CompleteResult.Failed).permanent)
        assertNull(storage.pending)
    }

    // ── signOut ──────────────────────────────────────────────────

    @Test
    fun `signOut membuang token dan pending`() = runTest {
        val storage = FakeStorage(tokens = freshTokens(), pending = PendingAuth("v", "s"))
        val r = repo(storage)
        r.restore()

        r.signOut()

        assertNull(storage.tokens)
        assertNull(storage.pending)
        assertEquals(SpotifyAuthRepository.AuthState.SignedOut, r.authState.value)
        assertNull(r.validAccessToken())
    }
}
