package com.tglabs.spotivibe.data.auth

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Klien untuk `https://accounts.spotify.com/api/token`.
 *
 * Public client: TIDAK ada client secret. PKCE (`code_verifier`) yang menjadi
 * bukti bahwa penukar code adalah pihak yang sama dengan yang memulai flow.
 * Karena itu client secret tidak pernah ada di APK sama sekali.
 */
class SpotifyAuthApi(
    private val clientId: String,
    private val redirectUri: String,
    private val httpClient: OkHttpClient = defaultClient(),
    private val nowMs: () -> Long = System::currentTimeMillis,
) : TokenEndpoint {

    /** Tukar authorization code jadi access + refresh token. */
    override suspend fun exchangeCode(code: String, codeVerifier: String): TokenResult =
        post(
            FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", redirectUri)
                .add("client_id", clientId)
                .add("code_verifier", codeVerifier)
                .build(),
            previousRefresh = null,
            label = "exchangeCode",
        )

    /**
     * Tukar refresh token jadi access token baru.
     *
     * [refreshToken] diteruskan sebagai `previousRefresh` supaya kalau Spotify
     * tidak mengirim refresh token baru, yang lama tetap dipertahankan.
     */
    override suspend fun refresh(refreshToken: String): TokenResult =
        post(
            FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("client_id", clientId)
                .build(),
            previousRefresh = refreshToken,
            label = "refresh",
        )

    private suspend fun post(
        body: FormBody,
        previousRefresh: String?,
        label: String,
    ): TokenResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(SpotifyAuthUrls.TOKEN_ENDPOINT)
            .post(body)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .build()

        try {
            httpClient.newCall(request).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    val oauthError = runCatching {
                        JSONObject(raw).optString("error").takeIf { it.isNotBlank() }
                    }.getOrNull()
                    Log.w(TAG, "$label failed: HTTP ${res.code}, error=$oauthError")
                    return@withContext classifyTokenFailure(res.code, oauthError)
                }
                parseSuccess(raw, previousRefresh)
                    ?: TokenResult.TransientFailure("body tidak bisa di-parse")
            }
        } catch (e: IOException) {
            // Jaringan mati / timeout. Sementara -- kredensial jangan dibuang.
            Log.w(TAG, "$label network error: ${e.message}")
            TokenResult.TransientFailure(e.message ?: "network error")
        } catch (t: Throwable) {
            Log.w(TAG, "$label threw: ${t.message}", t)
            TokenResult.TransientFailure(t.message ?: "unknown")
        }
    }

    private fun parseSuccess(raw: String, previousRefresh: String?): TokenResult.Success? {
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val access = json.optString("access_token").takeIf { it.isNotBlank() } ?: return null
        return TokenResult.Success(
            SpotifyTokens.fromResponse(
                accessToken = access,
                refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() },
                expiresInSec = json.optLong("expires_in", 3600L),
                nowMs = nowMs(),
                previousRefresh = previousRefresh,
            )
        )
    }

    companion object {
        private const val TAG = "SpotifyAuthApi"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
