package com.tglabs.spotivibe

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tglabs.spotivibe.service.SpotivibeNotificationService
import com.tglabs.spotivibe.ui.component.SongSyncDialog
import com.tglabs.spotivibe.ui.screen.LyricsSearchScreen
import com.tglabs.spotivibe.ui.screen.MainScreen
import com.tglabs.spotivibe.ui.screen.SettingsScreen
import com.tglabs.spotivibe.ui.theme.SpotivibeTheme
import com.tglabs.spotivibe.ui.vm.SpotivibeViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: SpotivibeViewModel by viewModels {
        val app = application as SpotivibeApp
        SpotivibeViewModel.Factory(
            controller = app.playbackController,
            preferencesRepository = app.preferencesRepository,
            lyricsRepository = app.lyricsRepository,
            overrideRepository = app.lyricsOverrideRepository,
        )
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

        // Satu-satunya hal yang wajib dari foreground: menyalakan foreground
        // service, karena Android 12+ melarang menyalakannya dari background.
        // Menyala begitu izin notification access ada, dan izin itu bisa
        // datang kapan saja user kembali dari Settings sistem.
        lifecycleScope.launch {
            app.playbackController.hasAccess.collect { granted ->
                if (granted) startCompanion()
            }
        }

        // Seek yang diabaikan player tidak boleh terlihat seperti tap yang
        // tidak bekerja: beri tahu siapa yang menolak. Hanya saat layar tampil.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.playbackController.seekRefused.collect { player ->
                    Toast.makeText(this@MainActivity, "$player didn't accept the seek", Toast.LENGTH_SHORT).show()
                }
            }
        }

        setContent {
            // Observe darkMode preference langsung — perlu di luar SpotivibeTheme
            // karena dipakai untuk theme selection. Default true sambil DataStore load.
            val darkMode by app.preferencesRepository.darkMode
                .collectAsState(initial = true)

            val state = viewModel.uiState.collectAsStateWithLifecycle().value
            // Accent dinamis per-track — extracted di PlaybackController via
            // AccentLock.lockedAccent(). Diteruskan ke SpotivibeTheme supaya
            // LocalSvColors.current.accent always reflects current song.
            val accent = (state as? com.tglabs.spotivibe.domain.UiState.Playing)?.accentColor
            SpotivibeTheme(darkTheme = darkMode, accent = accent) {
                var showSettings by rememberSaveable { mutableStateOf(false) }
                var showLyricsSearch by rememberSaveable { mutableStateOf(false) }
                var showSongSync by rememberSaveable { mutableStateOf(false) }

                val playing = state as? com.tglabs.spotivibe.domain.UiState.Playing

                if (showLyricsSearch && playing != null) {
                    LyricsSearchScreen(
                        initialTitle = playing.track.title,
                        initialArtist = playing.track.artist,
                        state = viewModel.searchState.collectAsStateWithLifecycle().value,
                        hasRememberedOverride = viewModel.hasRememberedOverride
                            .collectAsStateWithLifecycle().value,
                        onSearch = { t, a -> viewModel.searchLyrics(t, a) },
                        onPick = { hasil, ingat ->
                            viewModel.applySearchResult(hasil, ingat)
                            showLyricsSearch = false
                            viewModel.resetSearch()
                        },
                        onForget = {
                            viewModel.forgetOverride()
                            showLyricsSearch = false
                            viewModel.resetSearch()
                        },
                        onBack = {
                            showLyricsSearch = false
                            viewModel.resetSearch()
                        },
                    )
                } else if (showSettings) {
                    SettingsScreen(
                        state = state,
                        onBack = { showSettings = false },
                        onToggleDarkMode = { viewModel.toggleDarkMode() },
                        onSetFontSize = { size -> viewModel.setFontSize(size) },
                        onOpenAccess = { openNotificationAccess() },
                        onSetLyricsOffsetMs = { ms -> viewModel.setLyricsOffsetMs(ms) },
                        onSetDeviceOffsetMs = { ms -> viewModel.setDeviceOffsetMs(ms) },
                        onSetLineSpacing = { dp -> viewModel.setLineSpacing(dp) },
                        onToggleHighContrast = { viewModel.toggleHighContrast() },
                        onToggleSmoothScroll = { viewModel.toggleSmoothScroll() },
                        onToggleHaptic = { viewModel.toggleHaptic() },
                        onToggleLyricEffects = { viewModel.toggleLyricEffects() },
                        communitySources = viewModel.communitySources
                            .collectAsStateWithLifecycle().value,
                        onToggleCommunitySources = { viewModel.toggleCommunitySources() },
                        lyricsStats = viewModel.lyricsStats
                            .collectAsStateWithLifecycle().value,
                        onResetLyricsStats = { viewModel.resetLyricsStats() },
                    )
                } else {
                    MainScreen(
                        state = state,
                        onGrantAccess = { openNotificationAccess() },
                        onTogglePlayPause = { viewModel.togglePlayPause() },
                        onNext = { viewModel.next() },
                        onPrevious = { viewModel.previous() },
                        onSeek = { positionMs -> viewModel.seekTo(positionMs) },
                        onToggleRomanization = { viewModel.toggleRomanization() },
                        onToggleOverlay = { handleOverlayToggle() },
                        onToggleDarkMode = { viewModel.toggleDarkMode() },
                        onBumpFontSize = { delta -> viewModel.bumpFontSize(delta) },
                        onOpenSettings = { showSettings = true },
                        onSearchLyrics = { showLyricsSearch = true },
                        hasRememberedOverride = viewModel.hasRememberedOverride
                            .collectAsStateWithLifecycle().value,
                        onForgetOverride = { viewModel.forgetOverride() },
                        onSyncSong = { showSongSync = true },
                    )
                    if (showSongSync && playing != null) {
                        SongSyncDialog(
                            title = playing.track.title,
                            songOffsetMs = playing.syncOffsets.songMs,
                            onChange = { ms -> viewModel.setSongOffsetMs(ms) },
                            onDismiss = { showSongSync = false },
                        )
                    }
                }
            }
        }
    }

    /**
     * Izin bisa diberikan atau dicabut di Settings sistem selama app di
     * belakang, jadi dibaca ulang setiap kali layar ini kembali tampil.
     */
    override fun onResume() {
        super.onResume()
        viewModel.refreshAccess()
    }

    /** Nyalakan foreground service lirik. Idempoten. */
    private fun startCompanion() {
        SpotivibeNotificationService.start(this)
    }

    /**
     * Buka Settings → Notification access. Sebagian head unit menyembunyikan
     * layar itu; kalau intent-nya tidak bisa dibuka, jatuh ke Settings
     * aplikasi ini, dan README menjelaskan jalan lewat adb.
     */
    private fun openNotificationAccess() {
        val opened = runCatching {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }.isSuccess
        if (opened) return
        Log.w(TAG, "Layar notification access tidak tersedia, buka info app")
        runCatching {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }.onFailure { Log.e(TAG, "Settings sistem tidak bisa dibuka", it) }
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
