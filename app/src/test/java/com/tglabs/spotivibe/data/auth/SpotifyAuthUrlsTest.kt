package com.tglabs.spotivibe.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyAuthUrlsTest {

    private val redirect = "spotivibe://callback"
    private val state = "STATE123"

    private fun parse(uri: String?, expectedState: String? = state) =
        SpotifyAuthUrls.parseRedirect(uri, redirect, expectedState)

    // ── Regresi: redirect sah dibuang gara-gara satu garis miring ─

    /**
     * REGRESI PRODUKSI. Ini bug yang membuat app terkunci di layar
     * "Connecting" selamanya setelah login berhasil.
     *
     * Server OAuth menormalkan redirect URI dengan menambahkan garis miring saat
     * path-nya kosong, jadi `spotivibe://callback` yang didaftarkan kembali
     * sebagai `spotivibe://callback/`. Perbandingan string persis membuang
     * redirect yang benar-benar sah sebagai NotOurs.
     *
     * Yang membuatnya sulit dilacak: NotOurs adalah SATU-SATUNYA hasil yang
     * sekaligus tidak menulis log dan tidak mengubah state. Tidak ada crash,
     * tidak ada pesan, tidak ada jejak di logcat sama sekali.
     */
    @Test
    fun `redirect dengan garis miring akhir tetap milik kita`() {
        val hasil = parse("spotivibe://callback/?code=AQC123&state=$state")
        assertEquals(SpotifyAuthUrls.Redirect.Code("AQC123"), hasil)
    }

    /** Bentuk persis yang tertangkap di logcat perangkat saat bug itu terjadi. */
    @Test
    fun `bentuk nyata dari logcat perangkat`() {
        val hasil = parse("spotivibe://callback/?code=AQC123&state=$state&ubi=xyz")
        assertEquals(SpotifyAuthUrls.Redirect.Code("AQC123"), hasil)
    }

    @Test
    fun `perbandingan persis akan menolak bentuk itu`() {
        // Mendokumentasikan bug-nya: inilah yang dilakukan versi lama.
        assertFalse("spotivibe://callback/" == redirect)
        assertTrue(SpotifyAuthUrls.basesMatch("spotivibe://callback/", redirect))
    }

    @Test
    fun `normalisasi base membuang garis miring dan spasi tepi`() {
        assertEquals("spotivibe://callback", SpotifyAuthUrls.normalizeBase("  spotivibe://callback//  "))
        assertEquals("spotivibe://callback", SpotifyAuthUrls.normalizeBase(redirect))
    }

    @Test
    fun `pencocokan base tidak peduli besar kecil huruf`() {
        assertTrue(SpotifyAuthUrls.basesMatch("SPOTIVIBE://CALLBACK", redirect))
    }

    // ── URI yang bukan milik kita ────────────────────────────────

    @Test
    fun `skema lain diabaikan`() {
        assertEquals(SpotifyAuthUrls.Redirect.NotOurs, parse("https://example.com/?code=X&state=$state"))
        assertEquals(SpotifyAuthUrls.Redirect.NotOurs, parse("otherapp://callback?code=X&state=$state"))
    }

    @Test
    fun `uri kosong atau null diabaikan`() {
        assertEquals(SpotifyAuthUrls.Redirect.NotOurs, parse(null))
        assertEquals(SpotifyAuthUrls.Redirect.NotOurs, parse(""))
        assertEquals(SpotifyAuthUrls.Redirect.NotOurs, parse("   "))
    }

    @Test
    fun `redirect kita tanpa query itu cacat bukan bukan-milik-kita`() {
        assertEquals(SpotifyAuthUrls.Redirect.Malformed, parse(redirect))
    }

    // ── State, proteksi CSRF ─────────────────────────────────────

    /**
     * Tanpa flow yang sedang berjalan, redirect apa pun harus dibuang. Kalau
     * tidak, `code` dari sesi lama bisa dipakai ulang.
     */
    @Test
    fun `tanpa state tersimpan redirect dibuang`() {
        assertEquals(
            SpotifyAuthUrls.Redirect.StateMismatch,
            parse("spotivibe://callback?code=X&state=$state", expectedState = null),
        )
        assertEquals(
            SpotifyAuthUrls.Redirect.StateMismatch,
            parse("spotivibe://callback?code=X&state=$state", expectedState = ""),
        )
    }

    @Test
    fun `state berbeda ditolak`() {
        assertEquals(
            SpotifyAuthUrls.Redirect.StateMismatch,
            parse("spotivibe://callback?code=X&state=LAIN"),
        )
    }

    @Test
    fun `state hilang ditolak`() {
        assertEquals(SpotifyAuthUrls.Redirect.StateMismatch, parse("spotivibe://callback?code=X"))
    }

    /** State dicek SEBELUM error, supaya redirect basi tidak lolos jadi Denied. */
    @Test
    fun `state dicek lebih dulu daripada error`() {
        assertEquals(
            SpotifyAuthUrls.Redirect.StateMismatch,
            parse("spotivibe://callback?error=access_denied&state=LAIN"),
        )
    }

    // ── Hasil ────────────────────────────────────────────────────

    @Test
    fun `user menolak menghasilkan Denied`() {
        assertEquals(
            SpotifyAuthUrls.Redirect.Denied("access_denied"),
            parse("spotivibe://callback?error=access_denied&state=$state"),
        )
    }

    /** Kalau keduanya ada, penolakan yang menang. Code-nya tidak akan berguna. */
    @Test
    fun `error menang atas code`() {
        assertEquals(
            SpotifyAuthUrls.Redirect.Denied("access_denied"),
            parse("spotivibe://callback?code=X&error=access_denied&state=$state"),
        )
    }

    @Test
    fun `error kosong tidak dianggap penolakan`() {
        assertEquals(
            SpotifyAuthUrls.Redirect.Code("X"),
            parse("spotivibe://callback?error=&code=X&state=$state"),
        )
    }

    @Test
    fun `tanpa code maupun error itu cacat`() {
        assertEquals(SpotifyAuthUrls.Redirect.Malformed, parse("spotivibe://callback?foo=bar&state=$state"))
    }

    @Test
    fun `fragment dibuang`() {
        assertEquals(
            SpotifyAuthUrls.Redirect.Code("ABC"),
            parse("spotivibe://callback?code=ABC&state=$state#sisa"),
        )
    }

    @Test
    fun `code yang di-encode ikut ter-decode`() {
        val hasil = parse("spotivibe://callback?code=a%2Fb%2Bc&state=$state")
        assertEquals(SpotifyAuthUrls.Redirect.Code("a/b+c"), hasil)
    }

    // ── Query parsing ────────────────────────────────────────────

    @Test
    fun `key duplikat dimenangkan yang pertama`() {
        assertEquals(mapOf("a" to "1"), SpotifyAuthUrls.parseQuery("a=1&a=2"))
    }

    @Test
    fun `pasangan tanpa sama dengan diabaikan`() {
        assertEquals(mapOf("b" to "2"), SpotifyAuthUrls.parseQuery("a&b=2&&"))
    }

    @Test
    fun `query kosong menghasilkan map kosong`() {
        assertEquals(emptyMap<String, String>(), SpotifyAuthUrls.parseQuery(""))
        assertEquals(emptyMap<String, String>(), SpotifyAuthUrls.parseQuery("   "))
    }

    @Test
    fun `nilai boleh kosong`() {
        assertEquals(mapOf("a" to ""), SpotifyAuthUrls.parseQuery("a="))
    }

    // ── Encoding ─────────────────────────────────────────────────

    /**
     * Komponen query URI, BUKAN form-encoding. `+` adalah karakter literal di
     * sini; memperlakukannya sebagai spasi akan merusak authorization code yang
     * memang boleh mengandung `+`.
     */
    @Test
    fun `plus bukan spasi`() {
        assertEquals("a+b", SpotifyAuthUrls.percentDecode("a+b"))
        assertEquals("a%2Bb", SpotifyAuthUrls.percentEncode("a+b"))
    }

    @Test
    fun `spasi jadi persen dua nol`() {
        assertEquals("a%20b", SpotifyAuthUrls.percentEncode("a b"))
        assertEquals("a b", SpotifyAuthUrls.percentDecode("a%20b"))
    }

    @Test
    fun `karakter unreserved lolos tanpa diubah`() {
        val unreserved = "ABCxyz019-._~"
        assertEquals(unreserved, SpotifyAuthUrls.percentEncode(unreserved))
    }

    @Test
    fun `bolak-balik encode decode utuh termasuk non-ASCII`() {
        listOf("hello world", "a/b?c=d&e", "日本語", "한국어", "a+b~c-d_e.f", "%", "%%%").forEach {
            assertEquals(it, SpotifyAuthUrls.percentDecode(SpotifyAuthUrls.percentEncode(it)))
        }
    }

    /**
     * Escape rusak dibiarkan literal, bukan melempar exception. Redirect cacat
     * lebih baik jadi Malformed daripada membuat app crash saat menerima intent.
     */
    @Test
    fun `escape rusak tidak melempar exception`() {
        assertEquals("%zz", SpotifyAuthUrls.percentDecode("%zz"))
        assertEquals("%4", SpotifyAuthUrls.percentDecode("%4"))
        assertEquals("%", SpotifyAuthUrls.percentDecode("%"))
        assertEquals("ab%", SpotifyAuthUrls.percentDecode("ab%"))
    }

    @Test
    fun `escape di ujung akhir tetap ter-decode`() {
        assertEquals("ab ", SpotifyAuthUrls.percentDecode("ab%20"))
    }

    // ── Log tidak boleh membocorkan kredensial ───────────────────

    /**
     * `code` bisa ditukar jadi access token. Ia tidak boleh pernah mendarat di
     * logcat, yang bisa dibaca lewat adb oleh siapa pun yang memegang perangkat.
     */
    @Test
    fun `deskripsi log tidak memuat nilai code`() {
        val ringkas = SpotifyAuthUrls.describeRedirect(
            "spotivibe://callback/?code=RAHASIA_SEKALI&state=$state&ubi=xyz"
        )
        assertFalse("code bocor ke log: $ringkas", ringkas.contains("RAHASIA_SEKALI"))
        assertFalse("state bocor ke log: $ringkas", ringkas.contains(state))
        assertTrue("nama parameter harus tetap ada", ringkas.contains("code"))
        assertTrue(ringkas.contains("state"))
    }

    @Test
    fun `deskripsi log menangani bentuk aneh`() {
        assertEquals("<kosong>", SpotifyAuthUrls.describeRedirect(null))
        assertEquals("<kosong>", SpotifyAuthUrls.describeRedirect(""))
        assertTrue(SpotifyAuthUrls.describeRedirect(redirect).contains("tanpa query"))
    }

    // ── URL authorize ────────────────────────────────────────────

    @Test
    fun `url authorize memuat semua parameter wajib`() {
        val url = SpotifyAuthUrls.buildAuthorizeUrl(
            clientId = "CID",
            redirectUri = redirect,
            codeChallenge = "CHAL",
            state = state,
        )
        assertTrue(url.startsWith(SpotifyAuthUrls.AUTHORIZE_ENDPOINT + "?"))
        listOf(
            "client_id=CID",
            "response_type=code",
            "code_challenge_method=S256",
            "code_challenge=CHAL",
            "state=$state",
        ).forEach { assertTrue("hilang: $it dari $url", url.contains(it)) }
    }

    /**
     * `app-remote-control` yang membuat grant ini juga mengizinkan App Remote
     * bind tanpa dialog kedua. Tanpa scope itu, login berhasil tapi playback
     * tidak pernah tersambung.
     */
    @Test
    fun `scope app-remote-control selalu diminta`() {
        assertTrue(SpotifyAuthUrls.SCOPES.contains("app-remote-control"))
        val url = SpotifyAuthUrls.buildAuthorizeUrl("CID", redirect, "CHAL", state)
        assertTrue(url.contains("app-remote-control"))
    }

    @Test
    fun `scope dipisah spasi yang di-encode bukan koma`() {
        val url = SpotifyAuthUrls.buildAuthorizeUrl(
            "CID", redirect, "CHAL", state,
            scopes = listOf("a", "b"),
        )
        assertTrue("scope harus dipisah %20, dapat $url", url.contains("scope=a%20b"))
    }

    @Test
    fun `redirect uri di-encode`() {
        val url = SpotifyAuthUrls.buildAuthorizeUrl("CID", redirect, "CHAL", state)
        assertTrue(url.contains("redirect_uri=spotivibe%3A%2F%2Fcallback"))
    }

    @Test
    fun `urutan parameter deterministik`() {
        val a = SpotifyAuthUrls.buildAuthorizeUrl("CID", redirect, "CHAL", state)
        val b = SpotifyAuthUrls.buildAuthorizeUrl("CID", redirect, "CHAL", state)
        assertEquals(a, b)
    }

    @Test
    fun `masukan kosong ditolak di depan`() {
        listOf(
            { SpotifyAuthUrls.buildAuthorizeUrl("", redirect, "CHAL", state) },
            { SpotifyAuthUrls.buildAuthorizeUrl("CID", "", "CHAL", state) },
            { SpotifyAuthUrls.buildAuthorizeUrl("CID", redirect, "", state) },
            { SpotifyAuthUrls.buildAuthorizeUrl("CID", redirect, "CHAL", "") },
        ).forEach { blok ->
            try {
                blok()
                throw AssertionError("seharusnya menolak masukan kosong")
            } catch (e: IllegalArgumentException) {
                // memang ini yang diharapkan
            }
        }
    }
}
