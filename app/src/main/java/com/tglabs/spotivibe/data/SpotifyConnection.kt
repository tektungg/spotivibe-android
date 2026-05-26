package com.tglabs.spotivibe.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.types.Image
import com.spotify.protocol.types.ImageUri
import com.spotify.protocol.types.PlayerState
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import com.tglabs.spotivibe.BuildConfig
import com.tglabs.spotivibe.domain.NowPlaying
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manage Spotify connection — 2-step flow karena Android 14+ BAL restriction:
 *
 * 1. AuthorizationClient.createLoginActivityIntent() launched dari Activity
 *    (foreground context) → user approve auth dialog
 * 2. Setelah token received, SpotifyAppRemote.connect(showAuthView=false)
 *    untuk bind ke Spotify app service
 *
 * Step 1 wajib launched dari Activity supaya Background Activity Launch tidak
 * memblok. Step 2 bisa pakai Context biasa karena tidak start activity baru.
 */
class SpotifyConnection(
    private val preferencesRepository: PreferencesRepository,
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
    @Volatile
    private var accessToken: String? = null

    /** Public getter — WebApiClient pakai lambda ke method ini untuk Bearer header */
    fun currentAccessToken(): String? = accessToken

    init {
        // Load persisted token saat startup — supaya Web API calls (Premium check,
        // queue preload) jalan walaupun user pakai auto-reconnect (skip auth dialog).
        applicationScope.launch {
            val token = preferencesRepository.getValidAccessToken()
            if (!token.isNullOrBlank()) {
                accessToken = token
                Log.d(TAG, "Loaded persisted access token (length=${token.length})")
            } else {
                Log.d(TAG, "No valid persisted token — Web API features will be limited until auth")
            }
        }
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private val _albumBitmap = MutableStateFlow<Bitmap?>(null)
    val albumBitmap: StateFlow<Bitmap?> = _albumBitmap.asStateFlow()

    private var lastFetchedImageUri: String? = null

    /**
     * STEP 1: Launch Spotify auth dialog dari Activity (foreground context).
     * Hasil di-handle via handleAuthResult() — caller harus register
     * ActivityResultLauncher<Intent> dan call handleAuthResult dari callback.
     */
    fun startAuth(activity: Activity, launcher: ActivityResultLauncher<Intent>) {
        if (_connectionState.value is ConnectionState.Connected ||
            _connectionState.value is ConnectionState.Connecting
        ) {
            Log.d(TAG, "startAuth() ignored — already ${_connectionState.value}")
            return
        }
        if (BuildConfig.SPOTIFY_CLIENT_ID.isEmpty()) {
            _connectionState.value = ConnectionState.Error(
                "Spotify Client ID belum diset di local.properties"
            )
            return
        }

        Log.d(TAG, "startAuth() — launching AuthorizationClient")
        _connectionState.value = ConnectionState.Connecting
        SpotifyAppRemote.setDebugMode(true)

        val request = AuthorizationRequest.Builder(
            BuildConfig.SPOTIFY_CLIENT_ID,
            AuthorizationResponse.Type.TOKEN,
            BuildConfig.SPOTIFY_REDIRECT_URI,
        )
            .setScopes(
                arrayOf(
                    "app-remote-control",
                    "user-read-currently-playing",
                    "user-read-playback-state",
                    "user-modify-playback-state",
                    "user-read-private",
                )
            )
            .build()

        val intent = AuthorizationClient.createLoginActivityIntent(activity, request)
        launcher.launch(intent)
    }

    /** Called dari Activity onActivityResult / ActivityResultLauncher callback */
    fun handleAuthResult(context: Context, resultCode: Int, data: Intent?) {
        val response = AuthorizationClient.getResponse(resultCode, data)
        Log.d(TAG, "handleAuthResult — type=${response.type}, error=${response.error}")

        when (response.type) {
            AuthorizationResponse.Type.TOKEN -> {
                accessToken = response.accessToken
                val expiresInSec = response.expiresIn.coerceAtLeast(0)
                val expiresAt = System.currentTimeMillis() + expiresInSec * 1000L - 30_000L
                Log.d(TAG, "Got access token (length=${response.accessToken?.length ?: 0}, expiresInSec=$expiresInSec)")
                // Persist authorized flag + token + expiry — next cold start, skip
                // auth dialog AND tetap punya token untuk Web API calls.
                applicationScope.launch {
                    runCatching {
                        preferencesRepository.setSpotifyAuthorized(true)
                        response.accessToken?.let {
                            preferencesRepository.setSpotifyAccessToken(it, expiresAt)
                        }
                    }
                }
                connectAppRemote(context)
            }
            AuthorizationResponse.Type.ERROR -> {
                _connectionState.value = ConnectionState.Error(
                    "Auth error: ${response.error ?: "unknown"}"
                )
            }
            AuthorizationResponse.Type.EMPTY -> {
                _connectionState.value = ConnectionState.Error(
                    "Auth dibatalkan / dialog ditutup"
                )
            }
            else -> {
                _connectionState.value = ConnectionState.Error(
                    "Auth result tidak terduga: ${response.type}"
                )
            }
        }
    }

    /**
     * Auto-reconnect — dipanggil di MainActivity.onCreate kalau user sudah pernah
     * authorize sebelumnya (preferencesRepository.spotifyAuthorized = true).
     * Skip auth dialog, langsung bind ke App Remote.
     */
    fun tryAutoConnect(context: Context) {
        if (_connectionState.value is ConnectionState.Connected ||
            _connectionState.value is ConnectionState.Connecting
        ) {
            Log.d(TAG, "tryAutoConnect() ignored — already ${_connectionState.value}")
            return
        }
        if (BuildConfig.SPOTIFY_CLIENT_ID.isEmpty()) {
            _connectionState.value = ConnectionState.Error(
                "Spotify Client ID belum diset di local.properties"
            )
            return
        }
        Log.d(TAG, "tryAutoConnect() — skipping auth dialog (previously authorized)")
        _connectionState.value = ConnectionState.Connecting
        SpotifyAppRemote.setDebugMode(true)
        connectAppRemote(context)
    }

    /** STEP 2: Bind ke Spotify app service. Tidak butuh Activity karena tidak launch UI. */
    private fun connectAppRemote(context: Context) {
        Log.d(TAG, "connectAppRemote() — binding to Spotify service")
        scheduleTimeout()

        val params = ConnectionParams.Builder(BuildConfig.SPOTIFY_CLIENT_ID)
            .setRedirectUri(BuildConfig.SPOTIFY_REDIRECT_URI)
            .showAuthView(false) // user sudah authorized via AuthorizationClient di Step 1
            .build()

        SpotifyAppRemote.connect(context.applicationContext, params, object : Connector.ConnectionListener {
            override fun onConnected(remote: SpotifyAppRemote) {
                Log.d(TAG, "onConnected — App Remote linked")
                cancelTimeout()
                appRemote = remote
                _connectionState.value = ConnectionState.Connected
                subscribePlayerState(remote)
            }

            override fun onFailure(throwable: Throwable) {
                Log.e(TAG, "onFailure: ${throwable.javaClass.simpleName} — ${throwable.message}", throwable)
                cancelTimeout()
                // Kalau error karena user revoke / not authorized di Spotify side,
                // clear flag biar user lihat Connect button lagi (fresh auth flow).
                val name = throwable.javaClass.simpleName
                val msg = throwable.message ?: ""
                val isAuthIssue = "NotAuthorized" in name ||
                    "NotLoggedIn" in name ||
                    "authorization" in msg.lowercase()
                if (isAuthIssue) {
                    accessToken = null
                    applicationScope.launch {
                        runCatching {
                            preferencesRepository.setSpotifyAuthorized(false)
                            preferencesRepository.clearSpotifyAccessToken()
                        }
                    }
                    // Set Disconnected (bukan Error) supaya ConnectScreen muncul normal
                    _connectionState.value = ConnectionState.Disconnected
                } else {
                    _connectionState.value = ConnectionState.Error(
                        "$name: ${msg.ifBlank { "unknown" }}"
                    )
                }
            }
        })
    }

    fun disconnect() {
        Log.d(TAG, "disconnect()")
        cancelTimeout()
        appRemote?.let { SpotifyAppRemote.disconnect(it) }
        appRemote = null
        _connectionState.value = ConnectionState.Disconnected
        _nowPlaying.value = null
        _albumBitmap.value = null
        lastFetchedImageUri = null
    }

    private fun scheduleTimeout() {
        cancelTimeout()
        timeoutRunnable = Runnable {
            if (_connectionState.value is ConnectionState.Connecting) {
                Log.w(TAG, "Connection timeout — no callback fired in 15s")
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
                Log.d(TAG, "PlayerState — track=${state.track?.name}, paused=${state.isPaused}")
                _nowPlaying.value = state.toNowPlaying()
                maybeFetchAlbumArt(remote, state.track?.imageUri?.raw)
            }
            .setErrorCallback { t ->
                Log.e(TAG, "PlayerState subscription error", t)
            }
    }

    /** Fetch album cover bitmap saat track berubah. Cache via lastFetchedImageUri. */
    private fun maybeFetchAlbumArt(remote: SpotifyAppRemote, imageUriRaw: String?) {
        if (imageUriRaw == lastFetchedImageUri) return
        lastFetchedImageUri = imageUriRaw
        _albumBitmap.value = null
        if (imageUriRaw.isNullOrBlank()) return

        remote.imagesApi.getImage(ImageUri(imageUriRaw), Image.Dimension.LARGE)
            .setResultCallback { bitmap ->
                Log.d(TAG, "Album art fetched: ${bitmap.width}x${bitmap.height}")
                _albumBitmap.value = bitmap
            }
            .setErrorCallback { t ->
                Log.w(TAG, "Album art fetch failed: ${t.message}")
            }
    }

    fun play() { appRemote?.playerApi?.resume() }
    fun pause() { appRemote?.playerApi?.pause() }
    fun skipNext() { appRemote?.playerApi?.skipNext() }
    fun skipPrevious() { appRemote?.playerApi?.skipPrevious() }
    fun seekTo(positionMs: Long) {
        appRemote?.playerApi?.seekTo(positionMs.coerceAtLeast(0))
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
