package com.tglabs.spotivibe.service

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.core.animation.doOnEnd
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import com.tglabs.spotivibe.domain.clampOverlayY
import com.tglabs.spotivibe.domain.overlayHorizontalBounds
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.tglabs.spotivibe.SpotivibeApp
import com.tglabs.spotivibe.data.PlaybackController
import com.tglabs.spotivibe.ui.overlay.OverlayContent

/**
 * Manage floating overlay window untuk lirik melayang ala Musixmatch.
 *
 * Tanggung jawab:
 *  - Attach/detach ComposeView ke WindowManager dengan TYPE_APPLICATION_OVERLAY
 *  - Setup lifecycle / viewmodel / savedstate tree owner (wajib supaya Compose
 *    di luar Activity context bisa hidup)
 *  - Forward drag delta dari Composable ke WindowManager layout params
 *  - Forward play/pause/next/previous ke SpotifyConnection
 *
 * Lifecycle:
 *  - [show] dipanggil dari Service / Activity setelah permission granted
 *  - [hide] dipanggil saat overlay di-disable atau service stop
 *  - Aman dipanggil berulang — internal guard via [isShown]
 *
 * Permission revoked at runtime: kalau user cabut SYSTEM_ALERT_WINDOW saat
 * overlay sudah running, Android otomatis remove view; [hide] tetap aman
 * dipanggil setelah itu karena removeView dibungkus try/catch.
 */
class OverlayManager(
    private val context: Context,
    private val controller: PlaybackController,
    private val onPositionChange: (x: Int, y: Int) -> Unit,
    private val onCloseRequested: () -> Unit,
    initialX: Int,
    initialY: Int,
) {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var params: WindowManager.LayoutParams? = null

    @Volatile
    private var isShown: Boolean = false

    private var currentX: Int = initialX
    private var currentY: Int = initialY

    fun isShown(): Boolean = isShown

    /**
     * Attach overlay ke WindowManager. No-op kalau sudah shown.
     * Kalau permission belum granted, log warning dan return tanpa crash.
     */
    /**
     * Show overlay. Return true kalau sukses, false kalau gagal. Caller
     * (service) WAJIB cek return value — kalau false, set overlayEnabled=false
     * di DataStore biar tidak crash loop saat session berikutnya.
     */
    fun show(): Boolean {
        if (isShown) {
            Log.d(TAG, "show() ignored — already shown")
            return true
        }
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "show() aborted — SYSTEM_ALERT_WINDOW belum granted")
            return false
        }

        // Wrap ENTIRE show flow — apapun yang throw, kita catch + return false
        return try {
            val owner = OverlayLifecycleOwner().also { lifecycleOwner = it }
            val layout = buildLayoutParams(currentX, currentY).also { params = it }

            val view = ComposeView(context).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeViewModelStoreOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setContent {
                    // Wrap dengan SpotivibeTheme supaya LocalSvColors tersedia
                    // di OverlayContent. Accent diread dari controller flow
                    // — overlay reactive ke ganti track sama seperti app utama.
                    val accent = controller.accent.collectAsState(initial = null).value
                    com.tglabs.spotivibe.ui.theme.SpotivibeTheme(
                        darkTheme = true,
                        accent = accent,
                    ) {
                        OverlayContent(
                            trackFlow = controller.track,
                            bitmapFlow = controller.albumBitmap,
                            lyricsFlow = controller.lyrics,
                            currentLineIndexFlow = controller.currentLineIndex,
                            romajiFlow = controller.romaji,
                            onDrag = { dx, dy -> handleDrag(dx, dy) },
                            onDragEnd = { snapToNearestEdge() },
                            onPlayPause = { handlePlayPause() },
                            onNext = { handleNext() },
                            onPrevious = { handlePrevious() },
                            onClose = onCloseRequested,
                        )
                    }
                }
            }
            composeView = view

            owner.onStart()
            windowManager.addView(view, layout)
            // Insets dan tinggi view baru diketahui setelah layout pertama.
            // Posisi tersimpan (default Y 0, di bawah status bar) dijepit ke
            // area aman di sini, sebelum user sempat melihat posisi salahnya.
            view.doOnLayout { applySafeBounds() }
            isShown = true
            Log.d(TAG, "Overlay shown at ($currentX, $currentY)")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "show() FAILED — cleaning up state", t)
            // Reset semua biar fresh attempt berikutnya tidak corrupt
            runCatching { lifecycleOwner?.onStop() }
            runCatching {
                composeView?.let { runCatching { windowManager.removeView(it) } }
            }
            composeView = null
            lifecycleOwner = null
            params = null
            isShown = false
            false
        }
    }

    /**
     * Detach view + tear down lifecycle. Aman dipanggil berkali-kali.
     */
    fun hide() {
        if (!isShown && composeView == null) {
            Log.d(TAG, "hide() ignored — not shown")
            return
        }
        val view = composeView
        if (view != null) {
            try {
                windowManager.removeView(view)
            } catch (t: Throwable) {
                // Bisa IllegalArgumentException kalau view sudah ke-remove
                // (mis. permission revoked → system auto-remove).
                Log.w(TAG, "removeView threw — view mungkin sudah detached: ${t.message}")
            }
        }
        lifecycleOwner?.onStop()

        composeView = null
        lifecycleOwner = null
        params = null
        isShown = false
        Log.d(TAG, "Overlay hidden")
    }

    /**
     * Update posisi window setiap drag delta. X locked karena overlay full width.
     * Y di-clamp ke bounds layar supaya tidak bisa drag keluar display.
     */
    private fun handleDrag(dx: Int, dy: Int) {
        val p = params ?: return
        val view = composeView ?: return
        val (sw, sh) = screenSize()
        val vh = view.height.coerceAtLeast(1)
        val ins = safeInsets(view)

        // X tidak bisa digeser: overlay selebar area aman.
        val h = overlayHorizontalBounds(sw, ins.left, ins.right)
        p.x = h.x
        p.width = h.width
        p.y = clampOverlayY(p.y, dy, sh, vh, ins.top, ins.bottom)
        currentX = h.x
        currentY = p.y

        try {
            windowManager.updateViewLayout(view, p)
        } catch (t: Throwable) {
            Log.w(TAG, "updateViewLayout failed: ${t.message}")
            return
        }
    }

    /**
     * Saat user release jari, settle Y dalam bounds + persist posisi.
     * Tidak ada snap horizontal karena overlay full width.
     */
    private fun snapToNearestEdge() {
        val p = params ?: return
        val view = composeView ?: return
        val (_, sh) = screenSize()
        val vh = view.height.coerceAtLeast(1)
        val ins = safeInsets(view)
        val targetY = clampOverlayY(currentY, 0, sh, vh, ins.top, ins.bottom)

        // Kalau Y sudah dalam bounds, langsung persist tanpa animasi
        if (!com.tglabs.spotivibe.domain.needsSettleAnimation(targetY, currentY)) {
            onPositionChange(0, currentY)
            return
        }

        val startY = currentY
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                currentY = (startY + (targetY - startY) * t).toInt()
                p.y = currentY
                try {
                    windowManager.updateViewLayout(view, p)
                } catch (_: Throwable) {
                    cancel()
                }
            }
            doOnEnd {
                onPositionChange(0, currentY)
            }
            start()
        }
    }

    /**
     * Ukuran display PENUH dalam piksel, termasuk area bar sistem.
     *
     * Harus penuh karena insets dari [safeInsets] diukur dari tepi display.
     * `displayMetrics` di bawah API 30 sudah mengurangi navigation bar, jadi
     * memakainya bersama inset bawah berarti mengurangi bar itu dua kali.
     */
    private fun screenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            bounds.width() to bounds.height()
        } else {
            val dm = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels to dm.heightPixels
        }
    }

    /**
     * Bar sistem + cutout yang ditimpa jendela overlay, piksel.
     *
     * Dibaca dari root insets view overlay sendiri, yang jalan di semua level
     * API (head unit banyak yang masih Android 9/10). "IgnoringVisibility"
     * supaya bar yang sedang tersembunyi sementara tetap dihindari; bar itu
     * muncul lagi begitu disentuh dan akan menutupi overlay.
     *
     * Belum ter-attach atau OEM tidak melaporkan insets: [Insets.NONE], yang
     * berarti batasnya seluruh layar, persis perilaku sebelum safe area.
     */
    private fun safeInsets(view: android.view.View): Insets =
        ViewCompat.getRootWindowInsets(view)
            ?.getInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            ?: Insets.NONE

    /** Jepit posisi dan lebar overlay ke area aman, lalu simpan kalau Y berubah. */
    private fun applySafeBounds() {
        val p = params ?: return
        val view = composeView ?: return
        val (sw, sh) = screenSize()
        val ins = safeInsets(view)
        val h = overlayHorizontalBounds(sw, ins.left, ins.right)
        val y = clampOverlayY(currentY, 0, sh, view.height.coerceAtLeast(1), ins.top, ins.bottom)
        if (p.x == h.x && p.width == h.width && p.y == y) return
        val yBerubah = y != currentY
        p.x = h.x
        p.width = h.width
        p.y = y
        currentX = h.x
        currentY = y
        Log.d(TAG, "Safe area overlay: insets=$ins -> x=${h.x} w=${h.width} y=$y")
        try {
            windowManager.updateViewLayout(view, p)
        } catch (t: Throwable) {
            Log.w(TAG, "updateViewLayout (safe area) failed: ${t.message}")
            return
        }
        if (yBerubah) onPositionChange(0, y)
    }

    private fun handlePlayPause() {
        val app = context.applicationContext as? SpotivibeApp ?: return
        val conn = app.spotifyConnection
        // Toggle berdasarkan state terakhir dari controller.track
        val paused = controller.track.value?.isPaused ?: true
        if (paused) conn.play() else conn.pause()
    }

    private fun handleNext() {
        (context.applicationContext as? SpotivibeApp)?.spotifyConnection?.skipNext()
    }

    private fun handlePrevious() {
        (context.applicationContext as? SpotivibeApp)?.spotifyConnection?.skipPrevious()
    }

    private fun buildLayoutParams(x: Int, y: Int): WindowManager.LayoutParams {
        // Full-width overlay — width = MATCH_PARENT (screen width). Composable inner
        // pakai outer padding 16dp untuk breathing room dari edge. X selalu 0 karena
        // sudah full width, cuma Y yang variabel via drag.
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = 0   // locked — overlay full width tidak bisa di-drag horizontal
            this.y = y
        }
    }

    companion object {
        private const val TAG = "OverlayManager"
    }
}

/**
 * Tree-owner stub untuk ComposeView yang hidup di luar Activity. Tanpa ini,
 * Compose runtime crash karena `LocalLifecycleOwner` / `LocalSavedStateRegistryOwner`
 * tidak ter-resolve.
 */
private class OverlayLifecycleOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    // PENTING pakai get() accessor — bukan value assignment. SavedStateRegistry
    // internally call this.lifecycle saat performAttach(), kalau pakai
    // `= lifecycleRegistry` property ini belum ter-init saat init {} block jalan
    // karena urutan deklarasi (init block fire SEBELUM override val di-assign).
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    init {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
    }

    fun onStart() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun onStop() {
        // Wajib step-down dulu sebelum DESTROYED supaya observer terima
        // STOP → DESTROYED dalam urutan benar.
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}
