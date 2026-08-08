package com.tglabs.spotivibe.data.auth

/**
 * Konstruksi URL authorize + parsing redirect, murni string.
 *
 * Sengaja TIDAK memakai `android.net.Uri` supaya seluruh logika ini bisa diuji
 * di JVM unit test tanpa Robolectric. Percent-encoding dan parsing query
 * ditulis manual mengikuti RFC 3986 (bukan form-encoding: `+` TIDAK dianggap
 * spasi di komponen query).
 */
object SpotifyAuthUrls {

    const val AUTHORIZE_ENDPOINT = "https://accounts.spotify.com/authorize"
    const val TOKEN_ENDPOINT = "https://accounts.spotify.com/api/token"

    /**
     * Scope yang diminta saat authorize.
     *
     * `app-remote-control` wajib supaya grant yang dibuat lewat flow web ini
     * juga mengizinkan App Remote bind tanpa dialog kedua.
     */
    val SCOPES: List<String> = listOf(
        "app-remote-control",
        "user-read-currently-playing",
        "user-read-playback-state",
        "user-modify-playback-state",
        "user-read-private",
    )

    /** Charset unreserved RFC 3986 -- karakter ini lolos tanpa di-encode. */
    private const val UNRESERVED =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

    private const val HEX = "0123456789ABCDEF"

    /**
     * Bangun URL `/authorize` untuk Authorization Code + PKCE.
     *
     * Urutan parameter sengaja dibuat deterministik supaya bisa di-assert
     * persis di test.
     */
    fun buildAuthorizeUrl(
        clientId: String,
        redirectUri: String,
        codeChallenge: String,
        state: String,
        scopes: List<String> = SCOPES,
        showDialog: Boolean = false,
    ): String {
        require(clientId.isNotBlank()) { "clientId kosong" }
        require(redirectUri.isNotBlank()) { "redirectUri kosong" }
        require(codeChallenge.isNotBlank()) { "codeChallenge kosong" }
        require(state.isNotBlank()) { "state kosong" }

        val params = listOf(
            "client_id" to clientId,
            "response_type" to "code",
            "redirect_uri" to redirectUri,
            "code_challenge_method" to PkceCodes.CHALLENGE_METHOD,
            "code_challenge" to codeChallenge,
            "state" to state,
            "scope" to scopes.joinToString(" "),
            "show_dialog" to showDialog.toString(),
        )
        return params.joinToString(
            separator = "&",
            prefix = "$AUTHORIZE_ENDPOINT?",
        ) { (k, v) -> "${percentEncode(k)}=${percentEncode(v)}" }
    }

    /** Hasil parsing URI redirect yang mendarat di Activity. */
    sealed interface Redirect {
        /** Authorization code siap ditukar. */
        data class Code(val code: String) : Redirect

        /** User menolak, atau Spotify mengembalikan error (mis. `access_denied`). */
        data class Denied(val error: String) : Redirect

        /** `state` tidak cocok -- kemungkinan CSRF atau redirect basi. Buang. */
        data object StateMismatch : Redirect

        /** URI ini bukan redirect kita (deep link lain / launcher biasa). */
        data object NotOurs : Redirect

        /** Redirect kita, tapi tidak ada `code` maupun `error`. */
        data object Malformed : Redirect
    }

    /**
     * Parse URI yang masuk lewat `onNewIntent` / `onCreate`.
     *
     * @param expectedState state yang kita simpan saat memulai flow. Kalau null
     *   atau kosong, artinya tidak ada flow yang sedang jalan, jadi redirect
     *   apapun dianggap [Redirect.StateMismatch] dan dibuang.
     */
    fun parseRedirect(
        uri: String?,
        expectedRedirectUri: String,
        expectedState: String?,
    ): Redirect {
        if (uri.isNullOrBlank()) return Redirect.NotOurs

        val queryStart = uri.indexOf('?')
        val base = if (queryStart >= 0) uri.substring(0, queryStart) else uri
        if (!base.equals(expectedRedirectUri, ignoreCase = true)) return Redirect.NotOurs
        if (queryStart < 0) return Redirect.Malformed

        // Buang fragment kalau ada -- Spotify tidak memakainya di flow code,
        // tapi browser bisa menempelkannya.
        val rawQuery = uri.substring(queryStart + 1).substringBefore('#')
        val params = parseQuery(rawQuery)

        // Cek state sebelum apapun. Redirect tanpa flow aktif harus dibuang,
        // bukan diproses, supaya code dari sesi lama tidak dipakai ulang.
        if (expectedState.isNullOrBlank()) return Redirect.StateMismatch
        if (params["state"] != expectedState) return Redirect.StateMismatch

        params["error"]?.takeIf { it.isNotBlank() }?.let { return Redirect.Denied(it) }
        params["code"]?.takeIf { it.isNotBlank() }?.let { return Redirect.Code(it) }
        return Redirect.Malformed
    }

    /**
     * Parse query string jadi map. Key duplikat: yang pertama menang.
     * Pasangan tanpa `=` diabaikan.
     */
    internal fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (pair in query.split('&')) {
            if (pair.isBlank()) continue
            val eq = pair.indexOf('=')
            if (eq <= 0) continue
            val key = percentDecode(pair.substring(0, eq))
            val value = percentDecode(pair.substring(eq + 1))
            out.putIfAbsent(key, value)
        }
        return out
    }

    /** Percent-encode per RFC 3986. Spasi jadi `%20`, bukan `+`. */
    internal fun percentEncode(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (byte in raw.toByteArray(Charsets.UTF_8)) {
            val ch = byte.toInt().toChar()
            if (byte >= 0 && UNRESERVED.indexOf(ch) >= 0) {
                sb.append(ch)
            } else {
                val v = byte.toInt() and 0xFF
                sb.append('%').append(HEX[v shr 4]).append(HEX[v and 0x0F])
            }
        }
        return sb.toString()
    }

    /**
     * Percent-decode per RFC 3986. `+` dipertahankan apa adanya (komponen query
     * URI, bukan `application/x-www-form-urlencoded`). Escape rusak dibiarkan
     * literal daripada melempar exception -- redirect cacat lebih baik jadi
     * [Redirect.Malformed] ketimbang crash.
     */
    internal fun percentDecode(raw: String): String {
        if ('%' !in raw) return raw
        val bytes = ArrayList<Byte>(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '%' && i + 2 < raw.length) {
                val hi = Character.digit(raw[i + 1], 16)
                val lo = Character.digit(raw[i + 2], 16)
                if (hi >= 0 && lo >= 0) {
                    bytes.add(((hi shl 4) or lo).toByte())
                    i += 3
                    continue
                }
            }
            for (b in c.toString().toByteArray(Charsets.UTF_8)) bytes.add(b)
            i++
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }
}
