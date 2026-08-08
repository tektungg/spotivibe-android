package com.tglabs.spotivibe.data.auth

/**
 * Token set hasil Authorization Code + PKCE.
 *
 * Beda penting dari flow implicit yang lama: ada [refreshToken], jadi sesi
 * bisa hidup lebih dari satu jam tanpa menyeret user ke dialog login lagi.
 *
 * [expiresAtMs] disimpan sebagai epoch expiry ASLI dari Spotify. Safety margin
 * TIDAK dibakar ke dalam nilai ini, melainkan diterapkan saat baca lewat
 * [isFresh], supaya margin bisa diubah tanpa membuat token tersimpan jadi tidak
 * konsisten.
 */
data class SpotifyTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtMs: Long,
) {
    /**
     * Token masih aman dipakai pada [nowMs]?
     *
     * [skewMs] memberi ruang untuk latensi jaringan dan jam device yang meleset
     * sedikit. Token yang habis dalam 60 detik ke depan sudah dianggap basi.
     */
    fun isFresh(nowMs: Long, skewMs: Long = DEFAULT_SKEW_MS): Boolean =
        accessToken.isNotBlank() && nowMs < expiresAtMs - skewMs

    /** Ada jalan untuk memperbarui diri tanpa interaksi user? */
    val canRefresh: Boolean get() = !refreshToken.isNullOrBlank()

    companion object {
        /** Anggap basi 60 detik sebelum expiry sebenarnya. */
        const val DEFAULT_SKEW_MS = 60_000L

        /**
         * Bentuk [SpotifyTokens] dari response token endpoint.
         *
         * Spotify me-rotate refresh token: response refresh kadang membawa
         * `refresh_token` baru, kadang tidak. Kalau tidak ada, [previousRefresh]
         * dipertahankan. Menimpanya dengan null akan mematikan sesi secara
         * senyap di refresh berikutnya.
         */
        fun fromResponse(
            accessToken: String,
            refreshToken: String?,
            expiresInSec: Long,
            nowMs: Long,
            previousRefresh: String? = null,
        ): SpotifyTokens = SpotifyTokens(
            accessToken = accessToken,
            refreshToken = refreshToken?.takeIf { it.isNotBlank() } ?: previousRefresh,
            expiresAtMs = nowMs + expiresInSec.coerceAtLeast(0L) * 1000L,
        )
    }
}

/** Hasil pemanggilan token endpoint. */
sealed interface TokenResult {
    data class Success(val tokens: SpotifyTokens) : TokenResult

    /**
     * Spotify menolak grant-nya secara permanen (`invalid_grant`,
     * `invalid_client`, dst). Refresh token sudah mati: kredensial harus
     * dibuang dan user login ulang.
     */
    data class PermanentFailure(val error: String, val description: String?) : TokenResult

    /**
     * Gagal sementara: jaringan mati, 5xx, atau rate limit. Kredensial JANGAN
     * dibuang -- percobaan berikutnya bisa berhasil.
     */
    data class TransientFailure(val reason: String) : TokenResult
}

/**
 * Klasifikasi kegagalan token endpoint jadi permanen vs sementara.
 *
 * Ini dipisah sebagai fungsi murni karena keputusannya menentukan apakah user
 * dipaksa login ulang. Salah klasifikasi berarti sesi valid dibuang gara-gara
 * sinyal hilang sesaat.
 *
 * @param httpCode kode HTTP response.
 * @param oauthError isi field `error` di body, kalau ada.
 */
fun classifyTokenFailure(httpCode: Int, oauthError: String?): TokenResult {
    val err = oauthError?.trim()?.lowercase().orEmpty()
    val permanentError = err in PERMANENT_OAUTH_ERRORS
    return when {
        permanentError -> TokenResult.PermanentFailure(
            error = err,
            description = null,
        )
        // 400/401 tanpa error yang dikenal tetap dianggap permanen: request kita
        // yang salah bentuk atau grant sudah dicabut. Retry tidak akan menolong.
        httpCode == 400 || httpCode == 401 -> TokenResult.PermanentFailure(
            error = err.ifBlank { "http_$httpCode" },
            description = null,
        )
        else -> TokenResult.TransientFailure("http_$httpCode")
    }
}

private val PERMANENT_OAUTH_ERRORS = setOf(
    "invalid_grant",
    "invalid_client",
    "invalid_request",
    "unauthorized_client",
    "unsupported_grant_type",
)
