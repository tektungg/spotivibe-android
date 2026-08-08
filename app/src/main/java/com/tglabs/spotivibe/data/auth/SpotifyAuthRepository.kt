package com.tglabs.spotivibe.data.auth

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Kredensial PKCE yang sedang menunggu redirect balik. */
data class PendingAuth(val verifier: String, val state: String)

/**
 * Penyimpanan kredensial. Diabstraksi dari DataStore supaya
 * [SpotifyAuthRepository] bisa diuji di JVM tanpa Android.
 */
interface AuthStorage {
    suspend fun loadTokens(): SpotifyTokens?
    suspend fun saveTokens(tokens: SpotifyTokens)
    suspend fun clearTokens()

    suspend fun savePendingAuth(pending: PendingAuth)
    suspend fun loadPendingAuth(): PendingAuth?
    suspend fun clearPendingAuth()
}

/** Sisi jaringan dari flow token. Diabstraksi supaya bisa di-fake di test. */
interface TokenEndpoint {
    suspend fun exchangeCode(code: String, codeVerifier: String): TokenResult
    suspend fun refresh(refreshToken: String): TokenResult
}

/**
 * Sumber kebenaran tunggal untuk sesi Spotify.
 *
 * Menggantikan flow implicit grant yang lama. Yang lama memakai
 * `AuthorizationResponse.Type.TOKEN`, yang tidak pernah mengembalikan refresh
 * token, jadi setelah satu jam setiap panggilan Web API gagal diam-diam:
 * deteksi Premium mengembalikan null selamanya, `isPremium` terkunci false, dan
 * user Premium kehilangan tombol transport-nya. Akar masalahnya bukan retry
 * yang kurang, melainkan token yang memang tidak bisa diperbarui.
 *
 * Semua pembacaan token lewat [validAccessToken], yang me-refresh saat perlu
 * di bawah satu mutex. Sepuluh pemanggil bersamaan menghasilkan satu request
 * refresh, bukan sepuluh.
 */
class SpotifyAuthRepository(
    private val clientId: String,
    private val redirectUri: String,
    private val storage: AuthStorage,
    private val api: TokenEndpoint,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {

    sealed interface AuthState {
        /** Belum pernah login, atau sudah logout. */
        data object SignedOut : AuthState

        /** Punya kredensial yang bisa dipakai atau di-refresh. */
        data object SignedIn : AuthState

        /** Kredensial ditolak permanen oleh Spotify. Wajib login ulang. */
        data object NeedsReauth : AuthState
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.SignedOut)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val refreshMutex = Mutex()

    @Volatile
    private var cached: SpotifyTokens? = null

    @Volatile
    private var loadedFromStorage = false

    /**
     * Muat kredensial tersimpan. Dipanggil sekali saat startup supaya
     * [hasSession] sudah benar sebelum UI memutuskan menampilkan tombol
     * Connect atau langsung auto-reconnect.
     */
    suspend fun restore() {
        val tokens = storage.loadTokens()
        cached = tokens
        loadedFromStorage = true
        _authState.value = if (tokens != null && (tokens.canRefresh || tokens.isFresh(nowMs()))) {
            AuthState.SignedIn
        } else {
            AuthState.SignedOut
        }
        Log.d(TAG, "restore(): state=${_authState.value}, canRefresh=${tokens?.canRefresh}")
    }

    /** Ada sesi yang layak dicoba auto-reconnect? */
    fun hasSession(): Boolean = _authState.value is AuthState.SignedIn

    /**
     * Mulai flow authorize. Generate PKCE + state, simpan, kembalikan URL yang
     * harus dibuka di Custom Tab.
     *
     * Verifier disimpan SEBELUM URL dikembalikan: kalau proses mati saat user
     * ada di layar consent, redirect yang balik masih bisa ditukar.
     */
    suspend fun beginAuthorization(showDialog: Boolean = false): String {
        val codes = PkceCodes.generate()
        val state = PkceCodes.randomState()
        storage.savePendingAuth(PendingAuth(verifier = codes.verifier, state = state))
        return SpotifyAuthUrls.buildAuthorizeUrl(
            clientId = clientId,
            redirectUri = redirectUri,
            codeChallenge = codes.challenge,
            state = state,
            showDialog = showDialog,
        )
    }

    sealed interface CompleteResult {
        data object Success : CompleteResult

        /** User menolak di layar consent. */
        data class Denied(val error: String) : CompleteResult

        /** URI bukan milik kita, atau state tidak cocok. Diamkan saja. */
        data object Ignored : CompleteResult

        /** Penukaran gagal. [permanent] false berarti layak dicoba lagi. */
        data class Failed(val reason: String, val permanent: Boolean) : CompleteResult
    }

    /**
     * Proses URI redirect. Aman dipanggil untuk intent apapun -- URI yang bukan
     * milik kita mengembalikan [CompleteResult.Ignored].
     */
    suspend fun completeRedirect(uri: String?): CompleteResult {
        val pending = storage.loadPendingAuth()
        // SELALU catat apa yang masuk. Sebelumnya jalur NotOurs diam total dan
        // juga tidak mengubah state, jadi redirect yang ditolak karena selisih
        // satu karakter menghasilkan UI terkunci di Connecting tanpa satu pun
        // baris log untuk mendiagnosisnya.
        Log.d(
            TAG,
            "completeRedirect: ${SpotifyAuthUrls.describeRedirect(uri)}, " +
                "harap=$redirectUri, adaPending=${pending != null}",
        )
        return when (val parsed = SpotifyAuthUrls.parseRedirect(uri, redirectUri, pending?.state)) {
            SpotifyAuthUrls.Redirect.NotOurs -> {
                Log.d(TAG, "Bukan redirect kita -- diabaikan")
                CompleteResult.Ignored
            }

            SpotifyAuthUrls.Redirect.StateMismatch -> {
                Log.w(TAG, "Redirect state tidak cocok -- dibuang")
                CompleteResult.Ignored
            }

            SpotifyAuthUrls.Redirect.Malformed -> {
                storage.clearPendingAuth()
                CompleteResult.Failed("redirect tanpa code maupun error", permanent = false)
            }

            is SpotifyAuthUrls.Redirect.Denied -> {
                storage.clearPendingAuth()
                CompleteResult.Denied(parsed.error)
            }

            is SpotifyAuthUrls.Redirect.Code -> {
                // pending pasti non-null: parseRedirect hanya mengembalikan Code
                // kalau state cocok, dan state cocok hanya kalau pending ada.
                val verifier = pending?.verifier
                    ?: return CompleteResult.Failed("code_verifier hilang", permanent = false)
                exchange(parsed.code, verifier)
            }
        }
    }

    private suspend fun exchange(code: String, verifier: String): CompleteResult =
        when (val result = api.exchangeCode(code, verifier)) {
            is TokenResult.Success -> {
                storage.saveTokens(result.tokens)
                storage.clearPendingAuth()
                cached = result.tokens
                loadedFromStorage = true
                _authState.value = AuthState.SignedIn
                Log.d(TAG, "Token exchange sukses, refreshable=${result.tokens.canRefresh}")
                CompleteResult.Success
            }

            is TokenResult.PermanentFailure -> {
                storage.clearPendingAuth()
                Log.w(TAG, "Token exchange ditolak: ${result.error}")
                CompleteResult.Failed(result.error, permanent = true)
            }

            is TokenResult.TransientFailure -> {
                // Pending SENGAJA tidak dihapus: code masih berlaku sebentar,
                // jadi percobaan ulang masih mungkin berhasil.
                Log.w(TAG, "Token exchange gagal sementara: ${result.reason}")
                CompleteResult.Failed(result.reason, permanent = false)
            }
        }

    /**
     * Access token yang dijamin masih berlaku, atau null kalau tidak bisa
     * disediakan sekarang.
     *
     * Null punya dua arti berbeda, dibedakan lewat [authState]:
     * [AuthState.NeedsReauth] berarti user harus login lagi;
     * [AuthState.SignedIn] dengan hasil null berarti refresh gagal sementara
     * (jaringan) dan pemanggil boleh mencoba lagi nanti.
     */
    suspend fun validAccessToken(): String? {
        if (!loadedFromStorage) restore()

        cached?.let { if (it.isFresh(nowMs())) return it.accessToken }

        return refreshMutex.withLock {
            // Cek ulang di dalam lock: pemanggil lain mungkin sudah refresh
            // selagi kita antre.
            cached?.let { if (it.isFresh(nowMs())) return@withLock it.accessToken }

            val refreshToken = cached?.refreshToken
            if (refreshToken.isNullOrBlank()) {
                if (cached != null) {
                    // Punya access token basi tanpa cara memperbarui. Ini bentuk
                    // sesi implicit-grant lama; user harus login ulang.
                    _authState.value = AuthState.NeedsReauth
                }
                return@withLock null
            }

            when (val result = api.refresh(refreshToken)) {
                is TokenResult.Success -> {
                    storage.saveTokens(result.tokens)
                    cached = result.tokens
                    _authState.value = AuthState.SignedIn
                    Log.d(TAG, "Access token di-refresh")
                    result.tokens.accessToken
                }

                is TokenResult.PermanentFailure -> {
                    Log.w(TAG, "Refresh ditolak permanen (${result.error}) -- perlu login ulang")
                    storage.clearTokens()
                    cached = null
                    _authState.value = AuthState.NeedsReauth
                    null
                }

                is TokenResult.TransientFailure -> {
                    // Kredensial DIPERTAHANKAN. Membuangnya di sini berarti
                    // sinyal hilang sesaat memaksa user login ulang.
                    Log.w(TAG, "Refresh gagal sementara: ${result.reason}")
                    null
                }
            }
        }
    }

    /** Buang kredensial lokal. Tidak mencabut grant di sisi Spotify. */
    suspend fun signOut() {
        storage.clearTokens()
        storage.clearPendingAuth()
        cached = null
        loadedFromStorage = true
        _authState.value = AuthState.SignedOut
    }

    companion object {
        private const val TAG = "SpotifyAuthRepository"
    }
}
