package com.tglabs.spotivibe

import android.app.Application
import com.tglabs.spotivibe.data.LyricsRepository
import com.tglabs.spotivibe.data.PlaybackController
import com.tglabs.spotivibe.data.PreferencesRepository
import com.tglabs.spotivibe.data.RomanizationService
import com.tglabs.spotivibe.data.SpotifyConnection
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
    val spotifyConnection: SpotifyConnection by lazy {
        SpotifyConnection(preferencesRepository, applicationScope)
    }
    val lyricsRepository: LyricsRepository by lazy { LyricsRepository(this) }
    val romanizationService: RomanizationService by lazy { RomanizationService() }

    val playbackController: PlaybackController by lazy {
        PlaybackController(
            connection = spotifyConnection,
            lyricsRepository = lyricsRepository,
            romanizationService = romanizationService,
            preferencesRepository = preferencesRepository,
            scope = applicationScope,
        )
    }
}
