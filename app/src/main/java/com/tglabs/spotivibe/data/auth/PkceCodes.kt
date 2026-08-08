package com.tglabs.spotivibe.data.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * PKCE code pair per RFC 7636.
 *
 * Kenapa kita generate sendiri dan tidak lewat Spotify Auth SDK: SDK punya
 * [com.spotify.sdk.android.auth.AuthorizationRequest.Builder.setCustomParam],
 * tapi custom param HANYA ikut di jalur browser
 * (`AuthorizationRequest.toUri()`). Jalur native (app Spotify terpasang) lewat
 * `SpotifyNativeAuthUtil.startAuthActivity()` cuma mengirim VERSION, CLIENT_ID,
 * REDIRECT_URI, RESPONSE_TYPE, SCOPES, STATE ke intent -- `code_challenge`
 * dibuang diam-diam. Karena app ini mensyaratkan Spotify terpasang, jalur
 * native selalu menang, jadi challenge tidak pernah sampai ke Spotify dan
 * penukaran code pasti ditolak. Makanya flow authorize dijalankan sendiri
 * lewat Custom Tabs.
 *
 * [verifier] high-entropy random string dari charset unreserved RFC 3986.
 * [challenge] BASE64URL(SHA256(verifier)) tanpa padding, method S256.
 *
 * Murni: tidak menyentuh API Android, jadi bisa diuji di JVM unit test.
 * [Base64] dari `java.util` butuh API 26, dan minSdk kita memang 26.
 */
data class PkceCodes(
    val verifier: String,
    val challenge: String,
) {
    companion object {
        /** Charset unreserved RFC 3986: ALPHA / DIGIT / "-" / "." / "_" / "~" */
        private const val UNRESERVED =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

        const val MIN_VERIFIER_LENGTH = 43
        const val MAX_VERIFIER_LENGTH = 128
        const val DEFAULT_VERIFIER_LENGTH = 64

        const val CHALLENGE_METHOD = "S256"

        /**
         * Generate pasangan verifier + challenge baru.
         *
         * @param length panjang verifier, wajib 43..128 per RFC 7636 section 4.1.
         * @param random sumber entropi. Di-inject supaya test bisa deterministik.
         */
        fun generate(
            length: Int = DEFAULT_VERIFIER_LENGTH,
            random: SecureRandom = SecureRandom(),
        ): PkceCodes {
            require(length in MIN_VERIFIER_LENGTH..MAX_VERIFIER_LENGTH) {
                "code_verifier length harus $MIN_VERIFIER_LENGTH..$MAX_VERIFIER_LENGTH, dapat $length"
            }
            val sb = StringBuilder(length)
            repeat(length) {
                sb.append(UNRESERVED[random.nextInt(UNRESERVED.length)])
            }
            val verifier = sb.toString()
            return PkceCodes(verifier = verifier, challenge = challengeFor(verifier))
        }

        /**
         * BASE64URL-ENCODE(SHA256(ASCII(verifier))), tanpa padding.
         *
         * Test vector RFC 7636 appendix B:
         *   verifier  = dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk
         *   challenge = E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM
         */
        fun challengeFor(verifier: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(verifier.toByteArray(Charsets.US_ASCII))
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        }

        /** Random opaque string untuk param `state` (proteksi CSRF). */
        fun randomState(
            length: Int = 32,
            random: SecureRandom = SecureRandom(),
        ): String {
            require(length > 0) { "state length harus > 0" }
            val sb = StringBuilder(length)
            repeat(length) {
                sb.append(UNRESERVED[random.nextInt(UNRESERVED.length)])
            }
            return sb.toString()
        }
    }
}
