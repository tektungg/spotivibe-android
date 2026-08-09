package com.tglabs.spotivibe.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tglabs.spotivibe.MainActivity
import com.tglabs.spotivibe.R
import com.tglabs.spotivibe.SpotivibeApp
import com.tglabs.spotivibe.data.SpotifySessionSupervisor
import com.tglabs.spotivibe.domain.LyricsResult
import com.tglabs.spotivibe.domain.NowPlaying
import com.tglabs.spotivibe.domain.SyncedLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Foreground service yang menampilkan notification dengan synced lyric line.
 *
 * Tujuan: tampil PARALEL dengan Spotify's media notification (HyperOS Dynamic
 * Island / lock screen). Kita TIDAK pakai MediaSession + MediaStyle karena itu
 * akan "hijack" media indicator system dari Spotify — user tap dynamic island
 * malah masuk ke kita, bukan Spotify.
 *
 * Strategi: pakai foregroundServiceType=specialUse (bukan mediaPlayback) supaya
 * tidak claim media playback role, dan pakai BigText style biasa untuk
 * notification — Spotify tetap punya media indicator-nya sendiri.
 *
 * Lifecycle:
 * - start() dari MainActivity setelah connection Connected
 * - Service stopSelf() kalau connectionState turun ke Disconnected
 */
class SpotivibeNotificationService : Service() {

    private lateinit var app: SpotivibeApp
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null

    private var lastTrack: NowPlaying? = null
    private var lastBitmap: Bitmap? = null
    private var lastLine: SyncedLine? = null
    private var lastPrevLine: SyncedLine? = null
    private var lastNextLine: SyncedLine? = null
    private var isReconnecting: Boolean = false

    private val actionReceiver = ActionReceiver()

    // Floating overlay (Phase 7) — di-show/hide reactive berdasarkan preference
    private var overlayManager: OverlayManager? = null
    private var overlayPositionJob: Job? = null
    private var pendingOverlayX = 0
    private var pendingOverlayY = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate()")
        app = application as SpotivibeApp
        createChannel()
        registerActionReceiver()
        observeOverlayPreference()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand()")
        try {
            // Android 14+ wajib pass foregroundServiceType di startForeground call.
            // FOREGROUND_SERVICE_TYPE_SPECIAL_USE — kita bukan media player, ini app
            // companion yang nampilin info dari app lain (Spotify).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIF_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIF_ID, buildNotification())
            }
            Log.d(TAG, "startForeground OK")
        } catch (t: Throwable) {
            Log.e(TAG, "startForeground FAILED", t)
            stopSelf()
            return START_NOT_STICKY
        }

        // Semua collector jadi ANAK dari observeJob. Versi lama memanggil
        // serviceScope.launch{} dari dalam observeControllerFlows(), jadi
        // collector itu anak serviceScope, bukan anak observeJob. Akibatnya
        // observeJob?.cancel() tidak membatalkannya, dan tiap onStartCommand
        // (termasuk kebangkitan START_STICKY) menambah satu collector permanen.
        observeJob?.cancel()
        observeJob = serviceScope.launch { observeControllerFlows() }
        return START_STICKY
    }

    private suspend fun observeControllerFlows() = coroutineScope {
        val controller = app.playbackController

        // Companion berhenti HANYA kalau sesinya hilang. Koneksi putus bukan
        // alasan berhenti: dulu Disconnected memicu stopSelf(), jadi Spotify
        // di-kill sekali berarti notification hilang selamanya sampai user
        // membuka app ini lagi. Sekarang supervisor yang menyambung ulang.
        launch {
            app.sessionSupervisor.shouldRun.collect { state ->
                if (state == SpotifySessionSupervisor.CompanionState.Stopped) {
                    Log.d(TAG, "Sesi hilang — stopSelf()")
                    stopSelf()
                }
            }
        }

        // Teks notification ikut berubah saat sedang menyambung ulang, supaya
        // user tidak menatap lirik basi tanpa tahu kenapa berhenti bergerak.
        launch {
            app.sessionSupervisor.reconnecting.collect {
                isReconnecting = it
                refreshNotification()
            }
        }

        // Combine 4: track + bitmap + lyrics + currentLineIndex.
        // Compute prev/next line dari lyrics.synced + index supaya bisa tampil
        // 3-baris karaoke style di expanded notification + lock screen.
        launch {
            combine(
                controller.track,
                controller.albumBitmap,
                controller.lyrics,
                controller.currentLineIndex,
            ) { track, bitmap, lyrics, idx ->
                LyricsSnapshot(track, bitmap, lyrics, idx)
            }.collect { snapshot ->
                val synced = snapshot.lyrics?.synced
                lastTrack = snapshot.track
                lastBitmap = snapshot.bitmap
                lastLine = synced?.getOrNull(snapshot.idx)
                lastPrevLine = synced?.getOrNull(snapshot.idx - 1)
                lastNextLine = synced?.getOrNull(snapshot.idx + 1)
                refreshNotification()
            }
        }
    }

    private data class LyricsSnapshot(
        val track: NowPlaying?,
        val bitmap: Bitmap?,
        val lyrics: LyricsResult?,
        val idx: Int,
    )

    /**
     * Reactive observer untuk overlay preference. Combine flag enabled + initial
     * position dari DataStore. Saat enabled berubah ON → create + show.
     * Saat OFF → hide + destroy.
     *
     * Position saat user drag persisted lewat schedulePersistOverlayPosition()
     * (debounce 500ms biar gak spam DataStore).
     */
    private fun observeOverlayPreference() {
        val prefs = app.preferencesRepository
        serviceScope.launch {
            try {
                combine(
                    prefs.overlayEnabled,
                    prefs.overlayPosition,
                ) { enabled, pos -> enabled to pos }
                    .collect { (enabled, pos) ->
                        try {
                            handleOverlayState(enabled, pos)
                        } catch (t: Throwable) {
                            Log.e(TAG, "Overlay state handler crashed — disabling", t)
                            disableOverlayInPrefs()
                        }
                    }
            } catch (t: Throwable) {
                Log.e(TAG, "Overlay observer crashed — disabling", t)
                disableOverlayInPrefs()
            }
        }
    }

    private fun handleOverlayState(enabled: Boolean, pos: Pair<Int, Int>) {
        if (enabled) {
            if (overlayManager == null) {
                Log.d(TAG, "Showing overlay at (${pos.first}, ${pos.second})")
                val manager = OverlayManager(
                    context = this@SpotivibeNotificationService,
                    controller = app.playbackController,
                    onPositionChange = { x, y -> schedulePersistOverlayPosition(x, y) },
                    onCloseRequested = {
                        // User tap X di overlay → set preference OFF. Observer
                        // di service ini akan re-fire dengan enabled=false dan
                        // call overlayManager.hide() + clear ref.
                        serviceScope.launch {
                            app.preferencesRepository.setOverlayEnabled(false)
                        }
                    },
                    initialX = pos.first,
                    initialY = pos.second,
                )
                val ok = manager.show()
                if (ok) {
                    overlayManager = manager
                } else {
                    Log.w(TAG, "Overlay show returned false — auto-disable")
                    disableOverlayInPrefs()
                }
            }
        } else {
            overlayManager?.let {
                Log.d(TAG, "Hiding overlay")
                it.hide()
            }
            overlayManager = null
        }
    }

    /**
     * Safety net — kalau overlay gagal show karena alasan apapun (Compose
     * error, MIUI permission quirk, dll), set state OFF di DataStore biar
     * tidak crash loop saat user reconnect.
     */
    private fun disableOverlayInPrefs() {
        serviceScope.launch {
            runCatching { app.preferencesRepository.setOverlayEnabled(false) }
        }
    }

    private fun schedulePersistOverlayPosition(x: Int, y: Int) {
        pendingOverlayX = x
        pendingOverlayY = y
        overlayPositionJob?.cancel()
        overlayPositionJob = serviceScope.launch {
            delay(500)
            app.preferencesRepository.setOverlayPosition(pendingOverlayX, pendingOverlayY)
        }
    }

    private fun refreshNotification() {
        val notif = buildNotification()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, notif)
    }

    private fun buildNotification(): android.app.Notification {
        val track = lastTrack
        val current = lastLine
        val prev = lastPrevLine
        val next = lastNextLine
        val isPaused = track?.isPaused ?: true

        val title = track?.title?.ifBlank { "Spotivibe" } ?: "Spotivibe"
        val subtitle = track?.artist ?: "Loading…"

        // 3-baris karaoke style — prev (dim) / current (highlighted ▸) / next (dim).
        // Blank line jadi "♪" supaya tetap occupy space tapi tidak ngosong.
        val prevText = prev?.text?.takeIf { it.isNotBlank() } ?: ""
        val currText = current?.text?.takeIf { it.isNotBlank() } ?: "♪"
        val nextText = next?.text?.takeIf { it.isNotBlank() } ?: ""
        val currMarked = if (isReconnecting) {
            "Reconnecting to Spotify…"
        } else {
            "▸ $currText"
        }

        val prevPi = actionPendingIntent(ActionReceiver.ACTION_PREVIOUS, REQ_PREV)
        val playPausePi = actionPendingIntent(ActionReceiver.ACTION_PLAY_PAUSE, REQ_PLAY_PAUSE)
        val nextPi = actionPendingIntent(ActionReceiver.ACTION_NEXT, REQ_NEXT)

        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPi = PendingIntent.getActivity(
            this,
            REQ_CONTENT,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val playPauseIcon = if (isPaused) {
            android.R.drawable.ic_media_play
        } else {
            android.R.drawable.ic_media_pause
        }
        val playPauseLabel = if (isPaused) "Play" else "Pause"

        // InboxStyle — multiple lines (up to 7 di expanded, biasanya 5 di lock screen).
        // Pakai 3 baris untuk karaoke effect prev/current/next.
        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle(title)
            .setSummaryText(subtitle)
            .addLine(prevText)
            .addLine(currMarked)
            .addLine(nextText)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(currMarked)
            .setSubText(subtitle)
            .setContentIntent(contentPi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setColor(ACCENT_COLOR)
            .setStyle(inboxStyle)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPi)
            .addAction(playPauseIcon, playPauseLabel, playPausePi)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPi)

        lastBitmap?.let { builder.setLargeIcon(it) }

        return builder.build()
    }

    private fun actionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(action).apply {
            setPackage(packageName)
        }
        return PendingIntent.getBroadcast(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Cleanup channel lama (jaga2 user upgrade dari versi lama)
        listOf("spotivibe_media", "spotivibe_lyrics").forEach { oldId ->
            runCatching { nm.deleteNotificationChannel(oldId) }
        }

        if (nm.getNotificationChannel(CHANNEL_ID) != null) return

        // IMPORTANCE_DEFAULT: muncul di lock screen, tapi tetap silent
        // karena setSound + enableVibration di-disable.
        // IMPORTANCE_LOW dulu di-filter HyperOS dari lock screen.
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Lyrics",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Synced lyrics in the status bar and on the lock screen"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)
    }

    private fun registerActionReceiver() {
        val filter = IntentFilter().apply {
            addAction(ActionReceiver.ACTION_PLAY_PAUSE)
            addAction(ActionReceiver.ACTION_NEXT)
            addAction(ActionReceiver.ACTION_PREVIOUS)
        }
        ContextCompat.registerReceiver(
            this,
            actionReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")
        overlayPositionJob?.cancel()
        overlayManager?.hide()
        overlayManager = null
        try {
            unregisterReceiver(actionReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Receiver already unregistered: ${e.message}")
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    class ActionReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val app = context.applicationContext as? SpotivibeApp ?: return
            when (intent.action) {
                ACTION_PLAY_PAUSE -> {
                    val isPaused = app.playbackController.track.value?.isPaused ?: true
                    if (isPaused) app.spotifyConnection.play() else app.spotifyConnection.pause()
                }
                ACTION_NEXT -> app.spotifyConnection.skipNext()
                ACTION_PREVIOUS -> app.spotifyConnection.skipPrevious()
            }
        }

        companion object {
            const val ACTION_PLAY_PAUSE = "com.tglabs.spotivibe.PLAY_PAUSE"
            const val ACTION_NEXT = "com.tglabs.spotivibe.NEXT"
            const val ACTION_PREVIOUS = "com.tglabs.spotivibe.PREVIOUS"
        }
    }

    companion object {
        private const val TAG = "SpotivibeNotifService"

        const val NOTIF_ID = 1001
        // V2 — bumped dari "spotivibe_lyrics" untuk force fresh channel creation
        // dengan IMPORTANCE_DEFAULT (channel importance sticky setelah dibuat,
        // tidak bisa di-update programmatic).
        const val CHANNEL_ID = "spotivibe_lyrics_v2"

        private const val REQ_CONTENT = 0
        private const val REQ_PREV = 1
        private const val REQ_PLAY_PAUSE = 2
        private const val REQ_NEXT = 3

        // Editorial coral accent — matches AccentCoralDark in ui/theme/Color.kt.
        // Notif chrome ditint dengan ini (icon ring, sub-text emphasis).
        // Idealnya dynamic per-track tapi RemoteViews API tidak support live
        // recolor easily — biarkan static brand accent saja.
        private const val ACCENT_COLOR: Int = 0xFFEC6A5C.toInt()

        fun start(context: Context) {
            val intent = Intent(context, SpotivibeNotificationService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, SpotivibeNotificationService::class.java)
            context.stopService(intent)
        }
    }
}
