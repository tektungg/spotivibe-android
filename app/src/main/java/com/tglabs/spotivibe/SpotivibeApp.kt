package com.tglabs.spotivibe

import android.app.Application
import com.tglabs.spotivibe.data.AndroidNetworkMonitor
import com.tglabs.spotivibe.data.FileLyricsOverrideStore
import com.tglabs.spotivibe.data.LyricsCache
import com.tglabs.spotivibe.data.LyricsOverrideRepository
import com.tglabs.spotivibe.data.LyricsRepository
import com.tglabs.spotivibe.data.NetworkStatusSource
import com.tglabs.spotivibe.data.PlaybackController
import com.tglabs.spotivibe.data.PreferencesRepository
import com.tglabs.spotivibe.data.RomanizationService
import com.tglabs.spotivibe.data.SpotifyConnection
import com.tglabs.spotivibe.data.SpotifySessionSupervisor
import com.tglabs.spotivibe.data.WebApiClient
import com.tglabs.spotivibe.data.auth.SpotifyAuthApi
import com.tglabs.spotivibe.data.auth.SpotifyAuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Application-scoped container. Singletons di sini supaya survive Activity
 * recreation tanpa re-init dependencies berat (Retrofit, kuromoji dict, dll).
 *
 * applicationScope adalah CoroutineScope yang hidup selama proses Android hidup —
 * cocok untuk PlaybackController yang harus terus emit ke ViewModel dan
 * NotificationService secara paralel.
 */
class SpotivibeApp : Application() {

    val applicationScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate
    )

    val preferencesRepository: PreferencesRepository by lazy { PreferencesRepository(this) }

    /** Status jaringan untuk mode offline. Satu callback seumur proses. */
    val networkMonitor: NetworkStatusSource by lazy { AndroidNetworkMonitor(this) }

    /**
     * Sumber kebenaran tunggal untuk kredensial Spotify. Semua pembacaan token
     * lewat sini supaya refresh terjadi sekali, bukan per pemanggil.
     */
    val authRepository: SpotifyAuthRepository by lazy {
        SpotifyAuthRepository(
            clientId = BuildConfig.SPOTIFY_CLIENT_ID,
            redirectUri = BuildConfig.SPOTIFY_REDIRECT_URI,
            storage = preferencesRepository,
            api = SpotifyAuthApi(
                clientId = BuildConfig.SPOTIFY_CLIENT_ID,
                redirectUri = BuildConfig.SPOTIFY_REDIRECT_URI,
            ),
        )
    }

    val spotifyConnection: SpotifyConnection by lazy {
        SpotifyConnection(authRepository, applicationScope)
    }

    /**
     * Pemilik siklus hidup koneksi. Hidup di application scope, bukan di
     * Activity, supaya koneksi yang putus disambung ulang sendiri walaupun
     * tidak ada layar yang terbuka.
     */
    val sessionSupervisor: SpotifySessionSupervisor by lazy {
        SpotifySessionSupervisor(spotifyConnection, authRepository, applicationScope)
    }
    /** Lirik pilihan user. Disimpan di filesDir, bukan cacheDir. */
    val lyricsOverrideRepository: LyricsOverrideRepository by lazy {
        LyricsOverrideRepository(FileLyricsOverrideStore(this))
    }

    val lyricsRepository: LyricsRepository by lazy {
        LyricsRepository(
            diskCache = LyricsCache(this),
            statsRecorder = { event -> preferencesRepository.recordLyricsLookup(event) },
            overrides = { trackId -> lyricsOverrideRepository.overrideFor(trackId) },
            network = networkMonitor,
        )
    }
    val romanizationService: RomanizationService by lazy { RomanizationService() }
    val webApiClient: WebApiClient by lazy {
        WebApiClient(tokenProvider = { authRepository.validAccessToken() })
    }

    val playbackController: PlaybackController by lazy {
        PlaybackController(
            connection = spotifyConnection,
            lyricsRepository = lyricsRepository,
            romanizationService = romanizationService,
            preferencesRepository = preferencesRepository,
            webApiClient = webApiClient,
            scope = applicationScope,
            network = networkMonitor,
        )
    }

}
