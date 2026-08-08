package com.tglabs.spotivibe.data

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.Image
import com.spotify.protocol.types.ImageUri
import com.spotify.protocol.types.PlayerState
import com.tglabs.spotivibe.BuildConfig
import com.tglabs.spotivibe.data.auth.SpotifyAuthRepository
import com.tglabs.spotivibe.domain.NowPlaying
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Mengelola koneksi Spotify. Dua langkah, terpisah dan berbeda sifat:
 *
 * 1. **Authorization** lewat Authorization Code + PKCE di Custom Tab
 *    ([SpotifyAuthRepository]). Menghasilkan access token yang bisa di-refresh.
 * 2. **App Remote** bind ke service app Spotify, `showAuthView=false` karena
 *    grant dari langkah 1 sudah mencakup scope `app-remote-control`.
 *
 * Kenapa bukan `AuthorizationClient` bawaan SDK lagi: SDK cuma bisa memberi
 * implicit grant (`Type.TOKEN`) di jalur native, dan implicit grant tidak
 * punya refresh token. Jalur `Type.CODE` + `setCustomParam("code_challenge")`
 * juga tidak jalan, karena `SpotifyNativeAuthUtil.startAuthActivity()` cuma
 * meneruskan VERSION/CLIENT_ID/REDIRECT_URI/RESPONSE_TYPE/SCOPES/STATE ke
 * intent dan membuang custom param. Lihat catatan di [PkceCodes].
 */
class SpotifyConnection(
    private val authRepository: SpotifyAuthRepository,
    private val applicationScope: CoroutineScope,
) {

    sealed interface ConnectionState {
        data object Disconnected : ConnectionState
        data object Connecting : ConnectionState
        data object Connected : ConnectionState
        data class Error(val message: String) : ConnectionState
    }

    private var appRemote: SpotifyAppRemote? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private val _albumBitmap = MutableStateFlow<Bitmap?>(null)
    val albumBitmap: StateFlow<Bitmap?> = _albumBitmap.asStateFlow()

    /**
     * Menyala kalau App Remote pernah menolak perintah kontrol kita. Ini bukti
     * paling langsung bahwa akunnya tidak boleh mengontrol playback, jauh lebih
     * dapat dipercaya daripada menebak dari field `product` Web API.
     */
    private val _controlRejected = MutableStateFlow(false)
    val controlRejected: StateFlow<Boolean> = _controlRejected.asStateFlow()

    private var lastFetchedImageUri: String? = null

    /**
     * Menandai bahwa kita sendiri yang memutus. Tanpa ini, teardown memicu
     * callback onStop subscription dan [onRemoteLost] menganggapnya kehilangan
     * yang tidak disengaja.
     */
    @Volatile
    private var tearingDown: Boolean = false

    /** Sudah ada sesi tersimpan yang layak auto-reconnect? */
    fun hasSession(): Boolean = authRepository.hasSession()

    /**
     * LANGKAH 1: URL yang harus dibuka di Custom Tab untuk authorize.
     * Return null kalau Client ID belum diset.
     */
    suspend fun authorizeUrl(): String? {
        if (BuildConfig.SPOTIFY_CLIENT_ID.isEmpty()) {
            _connectionState.value = ConnectionState.Error(
                "Spotify Client ID belum diset di local.properties"
            )
            return null
        }
        _connectionState.value = ConnectionState.Connecting
        return runCatching { authRepository.beginAuthorization() }
            .onFailure {
                Log.e(TAG, "beginAuthorization gagal", it)
                _connectionState.value = ConnectionState.Error(
                    "Gagal memulai login: ${it.message ?: "unknown"}"
                )
            }
            .getOrNull()
    }

    /** Dipanggil saat browser gagal dibuka, supaya UI tidak tertinggal di Connecting. */
    fun onAuthorizeLaunchFailed(reason: String) {
        _connectionState.value = ConnectionState.Error(reason)
    }

    /**
     * User kembali ke app tanpa membawa redirect: menekan back di layar consent,
     * menutup tab, atau redirect ditelan komponen lain.
     *
     * Tanpa ini UI tertinggal di [ConnectionState.Connecting] selamanya, karena
     * [authorizeUrl] yang menaikkan state ke Connecting dan hanya
     * [handleRedirect] yang bisa menurunkannya. Tombol Connect pun ikut hilang,
     * jadi user tidak punya jalan untuk mencoba lagi selain menutup paksa app.
     *
     * Kredensial PKCE yang tertunda sengaja TIDAK dihapus: kalau redirect-nya
     * ternyata datang terlambat, penukaran code masih bisa berhasil, dan
     * percobaan berikutnya toh menimpanya.
     */
    fun abandonPendingAuthorization() {
        if (_connectionState.value is ConnectionState.Connecting) {
            Log.d(TAG, "Kembali tanpa redirect — kembalikan ke Disconnected")
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    /**
     * Proses URI redirect `spotivibe://callback`. Aman dipanggil untuk intent
     * apapun: URI yang bukan milik kita diabaikan tanpa efek samping.
     *
     * @return true kalau URI ini memang redirect auth kita.
     */
    suspend fun handleRedirect(context: Context, uri: String?): Boolean {
        return when (val result = authRepository.completeRedirect(uri)) {
            SpotifyAuthRepository.CompleteResult.Ignored -> false

            SpotifyAuthRepository.CompleteResult.Success -> {
                Log.d(TAG, "Authorization sukses -- bind App Remote")
                // allowAuthView: kita ada di Activity foreground di sini, jadi
                // kalau App Remote tetap menganggap belum ter-authorize, dialog
                // milik app Spotify boleh muncul tanpa kena batasan Background
                // Activity Launch.
                connectAppRemote(context, allowAuthView = true)
                true
            }

            is SpotifyAuthRepository.CompleteResult.Denied -> {
                _connectionState.value = ConnectionState.Disconnected
                Log.d(TAG, "User menolak authorization: ${result.error}")
                true
            }

            is SpotifyAuthRepository.CompleteResult.Failed -> {
                _connectionState.value = ConnectionState.Error(
                    if (result.permanent) {
                        "Login ditolak Spotify (${result.reason}). Coba lagi."
                    } else {
                        "Gagal menyelesaikan login: ${result.reason}. Cek koneksi lalu coba lagi."
                    }
                )
                true
            }
        }
    }

    /**
     * Auto-reconnect saat startup kalau sudah ada sesi tersimpan. Lewati layar
     * Connect, langsung bind App Remote.
     */
    fun tryAutoConnect(context: Context) {
        if (_connectionState.value is ConnectionState.Connected ||
            _connectionState.value is ConnectionState.Connecting
        ) {
            Log.d(TAG, "tryAutoConnect() diabaikan -- sudah ${_connectionState.value}")
            return
        }
        if (BuildConfig.SPOTIFY_CLIENT_ID.isEmpty()) {
            _connectionState.value = ConnectionState.Error(
                "Spotify Client ID belum diset di local.properties"
            )
            return
        }
        _connectionState.value = ConnectionState.Connecting
        connectAppRemote(context, allowAuthView = false)
    }

    /** LANGKAH 2: bind ke service app Spotify. */
    private fun connectAppRemote(context: Context, allowAuthView: Boolean) {
        Log.d(TAG, "connectAppRemote(allowAuthView=$allowAuthView)")
        if (BuildConfig.DEBUG) SpotifyAppRemote.setDebugMode(true)
        scheduleTimeout()

        val params = ConnectionParams.Builder(BuildConfig.SPOTIFY_CLIENT_ID)
            .setRedirectUri(BuildConfig.SPOTIFY_REDIRECT_URI)
            .showAuthView(false)
            .build()

        // showAuthView=false dipakai lebih dulu supaya jalur normal tidak pernah
        // memunculkan dialog kedua. Kalau App Remote tetap bilang belum
        // ter-authorize DAN kita sedang di konteks foreground, baru sekali retry
        // dengan dialog milik app Spotify.
        SpotifyAppRemote.connect(
            context.applicationContext,
            params,
            object : Connector.ConnectionListener {
                override fun onConnected(remote: SpotifyAppRemote) {
                    Log.d(TAG, "onConnected -- App Remote tersambung")
                    cancelTimeout()
                    appRemote = remote
                    _controlRejected.value = false
                    _connectionState.value = ConnectionState.Connected
                    subscribePlayerState(remote)
                }

                override fun onFailure(throwable: Throwable) {
                    Log.e(
                        TAG,
                        "onFailure: ${throwable.javaClass.simpleName} -- ${throwable.message}",
                        throwable,
                    )
                    cancelTimeout()
                    if (isAuthIssue(throwable)) {
                        if (allowAuthView) {
                            Log.d(TAG, "Auth issue -- retry sekali dengan auth view Spotify")
                            retryWithAuthView(context)
                        } else {
                            onAuthorizationLost()
                        }
                    } else {
                        _connectionState.value = ConnectionState.Error(
                            "${throwable.javaClass.simpleName}: " +
                                (throwable.message ?: "").ifBlank { "unknown" }
                        )
                    }
                }
            },
        )
    }

    private fun retryWithAuthView(context: Context) {
        scheduleTimeout()
        val params = ConnectionParams.Builder(BuildConfig.SPOTIFY_CLIENT_ID)
            .setRedirectUri(BuildConfig.SPOTIFY_REDIRECT_URI)
            .showAuthView(true)
            .build()

        SpotifyAppRemote.connect(context, params, object : Connector.ConnectionListener {
            override fun onConnected(remote: SpotifyAppRemote) {
                Log.d(TAG, "onConnected lewat auth view")
                cancelTimeout()
                appRemote = remote
                _controlRejected.value = false
                _connectionState.value = ConnectionState.Connected
                subscribePlayerState(remote)
            }

            override fun onFailure(throwable: Throwable) {
                Log.e(TAG, "Retry auth view gagal: ${throwable.message}", throwable)
                cancelTimeout()
                if (isAuthIssue(throwable)) {
                    onAuthorizationLost()
                } else {
                    _connectionState.value = ConnectionState.Error(
                        "${throwable.javaClass.simpleName}: " +
                            (throwable.message ?: "").ifBlank { "unknown" }
                    )
                }
            }
        })
    }

    private fun isAuthIssue(throwable: Throwable): Boolean {
        val name = throwable.javaClass.simpleName
        val msg = throwable.message.orEmpty().lowercase()
        return "NotAuthorized" in name || "NotLoggedIn" in name || "authorization" in msg
    }

    /**
     * Grant sudah tidak berlaku (user cabut akses di spotify.com, atau
     * kredensial rusak). Buang sesi lokal supaya UI kembali ke layar Connect
     * dengan flow login yang bersih, bukan macet di layar error.
     */
    private fun onAuthorizationLost() {
        applicationScope.launch { runCatching { authRepository.signOut() } }
        _connectionState.value = ConnectionState.Disconnected
    }

    fun disconnect() {
        Log.d(TAG, "disconnect()")
        tearingDown = true
        cancelTimeout()
        appRemote?.let { SpotifyAppRemote.disconnect(it) }
        appRemote = null
        _connectionState.value = ConnectionState.Disconnected
        _nowPlaying.value = null
        _albumBitmap.value = null
        lastFetchedImageUri = null
        tearingDown = false
    }

    /** Logout lokal: buang kredensial + putus App Remote. */
    suspend fun signOut() {
        authRepository.signOut()
        _controlRejected.value = false
        disconnect()
    }

    private fun scheduleTimeout() {
        cancelTimeout()
        timeoutRunnable = Runnable {
            if (_connectionState.value is ConnectionState.Connecting) {
                Log.w(TAG, "Connection timeout -- tidak ada callback dalam 15 detik")
                _connectionState.value = ConnectionState.Error(
                    "Timeout. Pastikan Spotify app running, lalu coba lagi."
                )
            }
        }.also { mainHandler.postDelayed(it, 15_000L) }
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    private fun subscribePlayerState(remote: SpotifyAppRemote) {
        remote.playerApi.subscribeToPlayerState()
            .setEventCallback { state ->
                _nowPlaying.value = state.toNowPlaying()
                maybeFetchAlbumArt(remote, state.track?.imageUri?.raw)
            }
            .setLifecycleCallback(object : Subscription.LifecycleCallback {
                override fun onStart() {
                    Log.d(TAG, "PlayerState subscription start")
                }

                override fun onStop() {
                    // Subscription berhenti berarti link ke app Spotify sudah
                    // mati: Spotify di-kill, di-swipe dari recents, atau dibunuh
                    // sistem. Dulu ini cuma di-log dan koneksi diam-diam jadi
                    // zombie: state tetap Connected, tapi tidak ada event yang
                    // masuk lagi selamanya.
                    onRemoteLost("player state subscription stop")
                }
            })
            .setErrorCallback { t ->
                Log.e(TAG, "PlayerState subscription error", t)
                onRemoteLost(t.message ?: "subscription error")
            }
    }

    /**
     * Link ke app Spotify hilang di tengah jalan. Turunkan state ke
     * Disconnected supaya supervisor tahu harus menyambung ulang. Sesi TIDAK
     * dibuang: kredensialnya masih sah, cuma app Spotify-nya yang pergi.
     */
    private fun onRemoteLost(reason: String) {
        if (tearingDown) return
        if (_connectionState.value !is ConnectionState.Connected) return
        Log.w(TAG, "App Remote hilang ($reason)")
        appRemote = null
        _nowPlaying.value = null
        _albumBitmap.value = null
        lastFetchedImageUri = null
        _connectionState.value = ConnectionState.Disconnected
    }

    /** Fetch album cover saat track berubah. Cache via lastFetchedImageUri. */
    private fun maybeFetchAlbumArt(remote: SpotifyAppRemote, imageUriRaw: String?) {
        if (imageUriRaw == lastFetchedImageUri) return
        lastFetchedImageUri = imageUriRaw
        _albumBitmap.value = null
        if (imageUriRaw.isNullOrBlank()) return

        remote.imagesApi.getImage(ImageUri(imageUriRaw), Image.Dimension.LARGE)
            .setResultCallback { bitmap -> _albumBitmap.value = bitmap }
            .setErrorCallback { t -> Log.w(TAG, "Album art fetch gagal: ${t.message}") }
    }

    // ── Kontrol playback ─────────────────────────────────────────
    //
    // Setiap perintah memasang error callback. Penolakan dicatat di
    // [controlRejected], dan itulah yang menurunkan capability jadi Restricted.
    // Sebelumnya kontrol disembunyikan berdasarkan tebakan dari Web API,
    // padahal jalur kontrolnya sendiri App Remote.

    fun play() = runControl("resume") { it.playerApi.resume() }
    fun pause() = runControl("pause") { it.playerApi.pause() }
    fun skipNext() = runControl("skipNext") { it.playerApi.skipNext() }
    fun skipPrevious() = runControl("skipPrevious") { it.playerApi.skipPrevious() }
    fun seekTo(positionMs: Long) = runControl("seekTo") {
        it.playerApi.seekTo(positionMs.coerceAtLeast(0))
    }

    private fun runControl(
        label: String,
        block: (SpotifyAppRemote) -> com.spotify.protocol.client.PendingResult<*>,
    ) {
        val remote = appRemote ?: return
        runCatching {
            block(remote).setErrorCallback { t ->
                Log.w(TAG, "Kontrol '$label' ditolak: ${t.message}")
                _controlRejected.value = true
            }
        }.onFailure { Log.w(TAG, "Kontrol '$label' throw: ${it.message}") }
    }

    private fun PlayerState.toNowPlaying(): NowPlaying? {
        val t = track ?: return null
        return NowPlaying(
            id = t.uri ?: "",
            title = t.name ?: "",
            artist = t.artist?.name ?: "",
            album = t.album?.name ?: "",
            imageUri = t.imageUri?.raw,
            durationMs = t.duration,
            progressMs = playbackPosition,
            isPaused = isPaused,
            // Wall clock saat callback fired. Dipakai UI buat extrapolation
            // yang benar walau Composable dispose+recompose.
            capturedAtMs = System.currentTimeMillis(),
        )
    }

    companion object {
        private const val TAG = "SpotifyConnection"
    }
}
