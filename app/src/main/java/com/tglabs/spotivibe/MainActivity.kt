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
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.tglabs.spotivibe.data.auth.SpotifyAuthRepository
import com.tglabs.spotivibe.service.SpotivibeNotificationService
import com.tglabs.spotivibe.ui.screen.MainScreen
import com.tglabs.spotivibe.ui.screen.SettingsScreen
import com.tglabs.spotivibe.ui.theme.SpotivibeTheme
import com.tglabs.spotivibe.ui.vm.SpotivibeViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /**
     * Menandai bahwa kita sedang menunggu redirect auth balik dari Custom Tab.
     * Dipakai [onResume] untuk mendeteksi user yang membatalkan.
     */
    private var awaitingAuthRedirect = false

    private val viewModel: SpotivibeViewModel by viewModels {
        val app = application as SpotivibeApp
        SpotivibeViewModel.Factory(
            controller = app.playbackController,
            connection = app.spotifyConnection,
            preferencesRepository = app.preferencesRepository,
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

        // Muat kredensial tersimpan. Sesi dianggap hidup kalau punya refresh
        // token, bukan sekadar flag "pernah login" seperti dulu -- flag itu
        // tetap true walaupun token sudah mati dan tidak bisa diperbarui.
        lifecycleScope.launch {
            runCatching { app.authRepository.restore() }
            // Cold start bisa saja dipicu oleh redirect auth itu sendiri.
            handleAuthRedirect(intent)
        }

        // Activity TIDAK lagi memiliki siklus koneksi. Yang tersisa di sini cuma
        // satu hal yang memang wajib dilakukan dari foreground: menyalakan
        // foreground service, karena Android 12+ melarang menyalakannya dari
        // background. Keputusan kapan menyambung, kapan menyambung ulang, dan
        // kapan berhenti sudah pindah ke SpotifySessionSupervisor yang hidup di
        // application scope.
        //
        // Dulu Activity juga yang mematikan service saat state jadi
        // Disconnected, jadi Spotify di-kill sekali berarti notification hilang
        // dan tidak ada apapun yang mencoba menyambung lagi.
        lifecycleScope.launch {
            app.authRepository.authState.collect { state ->
                if (state is SpotifyAuthRepository.AuthState.SignedIn) {
                    startCompanion()
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

                if (showSettings) {
                    SettingsScreen(
                        state = state,
                        onBack = { showSettings = false },
                        onToggleDarkMode = { viewModel.toggleDarkMode() },
                        onSetFontSize = { size -> viewModel.setFontSize(size) },
                        onLogout = {
                            viewModel.logout()
                            showSettings = false
                        },
                        onSetLyricsOffsetMs = { ms -> viewModel.setLyricsOffsetMs(ms) },
                        onSetLineSpacing = { dp -> viewModel.setLineSpacing(dp) },
                        onToggleHighContrast = { viewModel.toggleHighContrast() },
                        onToggleSmoothScroll = { viewModel.toggleSmoothScroll() },
                        onToggleHaptic = { viewModel.toggleHaptic() },
                        lyricsStats = viewModel.lyricsStats
                            .collectAsStateWithLifecycle().value,
                        onResetLyricsStats = { viewModel.resetLyricsStats() },
                    )
                } else {
                    MainScreen(
                        state = state,
                        onConnect = { startSpotifyAuthorization() },
                        onTogglePlayPause = { viewModel.togglePlayPause() },
                        onNext = { viewModel.next() },
                        onPrevious = { viewModel.previous() },
                        onSeek = { positionMs -> viewModel.seekTo(positionMs) },
                        onToggleRomanization = { viewModel.toggleRomanization() },
                        onToggleOverlay = { handleOverlayToggle() },
                        onToggleDarkMode = { viewModel.toggleDarkMode() },
                        onBumpFontSize = { delta -> viewModel.bumpFontSize(delta) },
                        onLogout = { viewModel.logout() },
                        onOpenSettings = { showSettings = true },
                    )
                }
            }
        }
    }

    /**
     * Redirect `spotivibe://callback` mendarat di sini. Activity ini
     * `launchMode="singleTask"` supaya redirect masuk lewat [onNewIntent] ke
     * instance yang sudah ada, bukan menumpuk instance kedua di atas yang lama.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthRedirect(intent)
    }

    /**
     * Kalau kita kembali ke layar ini setelah membuka Custom Tab TANPA redirect
     * yang masuk, artinya user membatalkan: menekan back di layar consent, atau
     * menutup tab-nya. Kembalikan state supaya tombol Connect muncul lagi.
     * Tanpa ini UI terkunci di Connecting selamanya dan satu-satunya jalan
     * keluar adalah menutup paksa app.
     *
     * onNewIntent selalu dipanggil sebelum onResume, jadi redirect yang sah
     * sudah menurunkan flag ini lebih dulu.
     */
    override fun onResume() {
        super.onResume()
        if (awaitingAuthRedirect) {
            awaitingAuthRedirect = false
            (application as SpotivibeApp).spotifyConnection.abandonPendingAuthorization()
        }
    }

    /**
     * Nyalakan supervisor + foreground service. Idempoten, jadi aman dipanggil
     * tiap kali authState memancarkan SignedIn.
     */
    private fun startCompanion() {
        val app = application as SpotivibeApp
        app.sessionSupervisor.start(this)
        SpotivibeNotificationService.start(this)
    }

    private fun handleAuthRedirect(intent: Intent?) {
        val uri = intent?.data?.toString() ?: return
        awaitingAuthRedirect = false
        viewModel.handleRedirect(this, uri)
    }

    /**
     * Buka layar consent Spotify di Custom Tab.
     *
     * Kenapa browser, bukan dialog native SDK: PKCE butuh `code_challenge`
     * ikut di request authorize, dan jalur native SDK membuang custom param.
     * Tanpa PKCE tidak ada refresh token, dan tanpa refresh token sesi mati
     * setelah satu jam.
     */
    private fun startSpotifyAuthorization() {
        viewModel.beginAuth { url ->
            val launched = runCatching {
                CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build()
                    .launchUrl(this, Uri.parse(url))
                true
            }.getOrElse { t ->
                Log.w(TAG, "Custom Tab gagal dibuka: ${t.message}")
                false
            }
            if (launched) {
                awaitingAuthRedirect = true
                return@beginAuth
            }

            // Tidak ada penyedia Custom Tabs. Jatuh ke browser biasa; redirect
            // tetap balik lewat intent filter yang sama.
            val fallback = runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                true
            }.getOrElse { t ->
                Log.e(TAG, "Tidak ada browser untuk membuka login", t)
                false
            }
            if (fallback) {
                awaitingAuthRedirect = true
            } else {
                (application as SpotivibeApp).spotifyConnection.onAuthorizeLaunchFailed(
                    "Tidak ada browser di device untuk membuka login Spotify."
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
