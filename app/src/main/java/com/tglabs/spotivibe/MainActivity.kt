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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tglabs.spotivibe.service.SpotivibeNotificationService
import com.tglabs.spotivibe.ui.ActivityActions
import com.tglabs.spotivibe.ui.AppRoot
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
        announceRefusedSeeks(app)

        setContent {
            AppRoot(
                viewModel = viewModel,
                darkModeFlow = app.preferencesRepository.darkMode,
                actions = ActivityActions(
                    openNotificationAccess = ::openNotificationAccess,
                    toggleOverlay = ::handleOverlayToggle,
                ),
            )
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

    /**
     * Seek yang diabaikan player tidak boleh terlihat seperti tap yang tidak
     * bekerja: beri tahu siapa yang menolak. Hanya selama layar tampil.
     */
    private fun announceRefusedSeeks(app: SpotivibeApp) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.playbackController.seekRefused.collect { player ->
                    Toast.makeText(this@MainActivity, "$player didn't accept the seek", Toast.LENGTH_SHORT).show()
                }
            }
        }
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
