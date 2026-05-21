package com.tglabs.spotivibe

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.tglabs.spotivibe.data.SpotifyConnection
import com.tglabs.spotivibe.service.SpotivibeNotificationService
import com.tglabs.spotivibe.ui.screen.MainScreen
import com.tglabs.spotivibe.ui.theme.SpotivibeTheme
import com.tglabs.spotivibe.ui.vm.SpotivibeViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: SpotivibeViewModel by viewModels {
        val app = application as SpotivibeApp
        SpotivibeViewModel.Factory(
            controller = app.playbackController,
            connection = app.spotifyConnection,
            preferencesRepository = app.preferencesRepository,
        )
    }

    private val spotifyAuthLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleAuthResult(this, result.resultCode, result.data)
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d(TAG, "POST_NOTIFICATIONS granted=$granted")
    }

    /**
     * Launcher untuk Settings overlay permission. JANGAN auto-enable saat granted
     * — user explicit tap toggle lagi. Mencegah crash loop kalau show() gagal
     * di Compose layer (state stay OFF di DataStore sampai user benar2 mau).
     */
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = Settings.canDrawOverlays(this)
        Log.d(TAG, "Overlay permission result: granted=$granted")
        // Tidak auto-enable. User akan lihat toast/snackbar atau tap toggle lagi.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermissionIfNeeded()

        val app = application as SpotivibeApp

        // Auto-reconnect kalau user sudah pernah authorize di session sebelumnya.
        // Skip Connect button, langsung bind ke Spotify app service.
        lifecycleScope.launch {
            val authorized = app.preferencesRepository.spotifyAuthorized.first()
            if (authorized) {
                app.spotifyConnection.tryAutoConnect(this@MainActivity)
            }
        }

        // Service lifecycle: start saat Connected, stop saat Disconnected/Error
        lifecycleScope.launch {
            app.spotifyConnection.connectionState
                .collect { state ->
                    when (state) {
                        SpotifyConnection.ConnectionState.Connected -> {
                            SpotivibeNotificationService.start(this@MainActivity)
                        }
                        SpotifyConnection.ConnectionState.Disconnected,
                        is SpotifyConnection.ConnectionState.Error -> {
                            SpotivibeNotificationService.stop(this@MainActivity)
                        }
                        else -> Unit
                    }
                }
        }

        setContent {
            SpotivibeTheme {
                val state = viewModel.uiState.collectAsStateWithLifecycle().value
                MainScreen(
                    state = state,
                    onConnect = { viewModel.startAuth(this, spotifyAuthLauncher) },
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onNext = { viewModel.next() },
                    onPrevious = { viewModel.previous() },
                    onSeek = { positionMs -> viewModel.seekTo(positionMs) },
                    onToggleRomanization = { viewModel.toggleRomanization() },
                    onToggleOverlay = { handleOverlayToggle() },
                )
            }
        }
    }

    /**
     * Logic toggle overlay:
     * - Kalau saat ini ON → langsung OFF (no permission check needed)
     * - Kalau OFF dan permission ada → ON
     * - Kalau OFF dan permission tidak ada → buka Settings minta permission
     */
    private fun handleOverlayToggle() {
        val playing = viewModel.uiState.value as? com.tglabs.spotivibe.domain.UiState.Playing
        val current = playing?.overlayEnabled ?: false
        if (current) {
            viewModel.setOverlayEnabled(false)
            return
        }
        if (Settings.canDrawOverlays(this)) {
            viewModel.setOverlayEnabled(true)
        } else {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
