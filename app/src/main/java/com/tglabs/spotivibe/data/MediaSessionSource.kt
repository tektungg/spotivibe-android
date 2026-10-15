package com.tglabs.spotivibe.data

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.graphics.scale
import com.tglabs.spotivibe.domain.MediaSnapshot
import com.tglabs.spotivibe.domain.NowPlaying
import com.tglabs.spotivibe.domain.PlaybackClock
import com.tglabs.spotivibe.domain.PlayerControls
import com.tglabs.spotivibe.domain.PlayerState
import com.tglabs.spotivibe.domain.QueuedTrack
import com.tglabs.spotivibe.domain.isDisplayable
import com.tglabs.spotivibe.domain.pickSession
import com.tglabs.spotivibe.domain.toNowPlaying
import com.tglabs.spotivibe.domain.trackIdFor
import com.tglabs.spotivibe.service.SessionAccessService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sumber playback: lagu yang sedang diputar, cover, dan kontrolnya.
 *
 * Diabstraksi supaya [PlaybackController] dan UI tidak tahu dari mana lagunya
 * datang, dan supaya bisa diganti versi palsu di test.
 */
interface PlaybackSource {
    /** Notification access sudah diberikan, jadi sesi media bisa dibaca. */
    val hasAccess: StateFlow<Boolean>
    val nowPlaying: StateFlow<NowPlaying?>
    val albumBitmap: StateFlow<Bitmap?>
    val controls: StateFlow<PlayerControls>

    /** Nama app yang sedang diikuti ("Spotify", "YouTube Music"), null kalau belum ada. */
    val playerLabel: StateFlow<String?>

    /** Baca ulang status izin. Dipanggil saat kembali dari layar Settings sistem. */
    fun refreshAccess()

    fun play()
    fun pause()
    fun skipNext()
    fun skipPrevious()
    fun seekTo(positionMs: Long)

    /** Lagu-lagu berikutnya di antrean player, untuk preload lirik. Kosong kalau player tidak membaginya. */
    fun upcoming(limit: Int): List<QueuedTrack>
}

/**
 * [PlaybackSource] dari MediaSession sistem.
 *
 * Membaca sesi media app lain butuh dua hal: komponen
 * [SessionAccessService] (NotificationListenerService kosong) dan user yang
 * mengaktifkannya di Settings → Notification access. Setelah itu
 * `getActiveSessions` mengembalikan semua sesi, dan sesi yang diikuti dipilih
 * lewat [pickSession].
 *
 * Semua callback berjalan di main thread lewat satu [Handler]; nilai keluar
 * lewat StateFlow sehingga aman dibaca dari thread mana pun. Monitor ini
 * singleton seumur proses, jadi listener-nya tidak pernah dilepas kecuali izin
 * dicabut.
 */
class AndroidMediaSessionSource(private val context: Context) : PlaybackSource {

    private val sessionManager = context.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(context, SessionAccessService::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private val _hasAccess = MutableStateFlow(isListenerEnabled())
    override val hasAccess: StateFlow<Boolean> = _hasAccess.asStateFlow()

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    override val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private val _albumBitmap = MutableStateFlow<Bitmap?>(null)
    override val albumBitmap: StateFlow<Bitmap?> = _albumBitmap.asStateFlow()

    private val _controls = MutableStateFlow(PlayerControls())
    override val controls: StateFlow<PlayerControls> = _controls.asStateFlow()

    private val _playerLabel = MutableStateFlow<String?>(null)
    override val playerLabel: StateFlow<String?> = _playerLabel.asStateFlow()

    private var observing = false
    private var current: MediaController? = null

    /** Jam koreksi untuk sesi yang sedang diikuti. Dibuat ulang setiap pindah player. */
    private var clock = PlaybackClock()

    /** Callback kecil di SETIAP sesi aktif, supaya app lain yang mulai memutar langsung terdeteksi. */
    private val watchers = mutableMapOf<android.media.session.MediaSession.Token, Pair<MediaController, MediaController.Callback>>()

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        onSessionsChanged(controllers.orEmpty())
    }

    private val mainCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish(metadataChanged = true)
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish(metadataChanged = false)
        override fun onSessionDestroyed() = reloadSessions()
    }

    init {
        if (_hasAccess.value) startObserving()
    }

    override fun refreshAccess() {
        val granted = isListenerEnabled()
        _hasAccess.value = granted
        if (granted && !observing) startObserving()
        if (!granted && observing) stopObserving()
    }

    /**
     * Izin milik komponen listener, bukan milik paket. Dibandingkan per
     * ComponentName persis: kalau kelas listener pernah berganti nama, paketnya
     * masih tercantum tapi pembacaan sesi tetap ditolak.
     */
    private fun isListenerEnabled(): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, ENABLED_LISTENERS_KEY).orEmpty()
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == listenerComponent }
    }

    private fun startObserving() {
        val manager = sessionManager ?: return
        runCatching {
            manager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, handler)
            observing = true
            onSessionsChanged(manager.getActiveSessions(listenerComponent))
        }.onFailure {
            // SecurityException: izin dicabut di antara pengecekan dan pemakaian.
            Log.w(TAG, "Gagal membaca sesi media: ${it.message}")
            _hasAccess.value = false
        }
    }

    private fun stopObserving() {
        runCatching { sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener) }
            .onFailure { Log.w(TAG, "Gagal melepas listener sesi: ${it.message}") }
        observing = false
        watchers.values.forEach { (controller, cb) -> controller.unregisterCallback(cb) }
        watchers.clear()
        attach(null)
    }

    private fun reloadSessions() {
        val manager = sessionManager ?: return
        runCatching { onSessionsChanged(manager.getActiveSessions(listenerComponent)) }
            .onFailure { Log.w(TAG, "Gagal memuat ulang sesi: ${it.message}") }
    }

    private fun onSessionsChanged(controllers: List<MediaController>) {
        val tokens = controllers.map { it.sessionToken }.toSet()
        watchers.keys.filterNot { it in tokens }.forEach { token ->
            watchers.remove(token)?.let { (controller, cb) -> controller.unregisterCallback(cb) }
        }
        controllers.filterNot { it.sessionToken in watchers }.forEach { controller ->
            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = reselect()
                override fun onSessionDestroyed() = reloadSessions()
            }
            controller.registerCallback(cb, handler)
            watchers[controller.sessionToken] = controller to cb
        }
        reselect()
    }

    private fun reselect() {
        val controllers = watchers.values.map { it.first }
        val snapshots = controllers.map { it.toSnapshot() }
        val picked = pickSession(snapshots, current?.packageName)
        attach(picked?.let { controllers[snapshots.indexOf(it)] })
    }

    private fun attach(controller: MediaController?) {
        if (controller?.sessionToken == current?.sessionToken) {
            if (controller != null) publish(metadataChanged = false)
            return
        }
        current?.unregisterCallback(mainCallback)
        current = controller
        clock = PlaybackClock()
        controller?.registerCallback(mainCallback, handler)
        _playerLabel.value = controller?.let { appLabel(it.packageName) }
        Log.d(TAG, "Mengikuti ${controller?.packageName}")
        publish(metadataChanged = true)
    }

    private fun publish(metadataChanged: Boolean) {
        val controller = current
        val snapshot = controller?.toSnapshot()
        if (snapshot == null || !isDisplayable(snapshot)) {
            _nowPlaying.value = null
            _controls.value = PlayerControls()
            if (metadataChanged) _albumBitmap.value = null
            return
        }
        val reading = clock.onReport(snapshot, SystemClock.elapsedRealtime())
        _nowPlaying.value = snapshot.toNowPlaying(reading, System.currentTimeMillis())
        _controls.value = snapshot.controls
        if (metadataChanged) _albumBitmap.value = controller.metadata?.artwork()
    }

    private fun appLabel(packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    // ── Kontrol ──────────────────────────────────────────────────

    override fun play() = control { it.play() }
    override fun pause() = control { it.pause() }
    override fun skipNext() = control { it.skipToNext() }
    override fun skipPrevious() = control { it.skipToPrevious() }

    /** Ditolak di sini kalau player tidak mengizinkan seek, daripada perintahnya diabaikan diam-diam. */
    override fun seekTo(positionMs: Long) {
        if (!_controls.value.canSeek) return
        val target = positionMs.coerceAtLeast(0L)
        // Jam langsung pindah: lirik tidak menunggu laporan player berikutnya.
        clock.onSeek(target, SystemClock.elapsedRealtime())
        control { it.seekTo(target) }
    }

    private fun control(block: (MediaController.TransportControls) -> Unit) {
        val controller = current ?: return
        runCatching { block(controller.transportControls) }
            .onFailure { Log.w(TAG, "Kontrol ke ${controller.packageName} gagal: ${it.message}") }
    }

    override fun upcoming(limit: Int): List<QueuedTrack> {
        val controller = current ?: return emptyList()
        val queue = runCatching { controller.queue }.getOrNull().orEmpty()
        val activeId = controller.playbackState?.activeQueueItemId
        val start = queue.indexOfFirst { it.queueId == activeId }.let { if (it < 0) 0 else it + 1 }
        return queue.drop(start).take(limit).mapNotNull { item ->
            val d = item.description
            val title = d.title?.toString().orEmpty()
            if (title.isBlank()) return@mapNotNull null
            val snapshot = MediaSnapshot(
                packageName = controller.packageName,
                mediaId = d.mediaId,
                mediaUri = d.mediaUri?.toString(),
                title = title,
                artist = d.subtitle?.toString().orEmpty(),
            )
            QueuedTrack(
                id = trackIdFor(snapshot),
                title = title,
                artist = snapshot.artist,
                album = d.description?.toString().orEmpty(),
                durationMs = d.extras?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            )
        }
    }

    private companion object {
        const val TAG = "MediaSessionSource"
        const val ENABLED_LISTENERS_KEY = "enabled_notification_listeners"

        /** Cover lebih besar dari ini hanya membuang memori: dipakai untuk blur dan warna aksen. */
        const val MAX_ART_PX = 512
    }

    private fun MediaMetadata.artwork(): Bitmap? {
        val art = getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: return null
        val longest = maxOf(art.width, art.height)
        if (longest <= MAX_ART_PX) return art
        val ratio = MAX_ART_PX.toFloat() / longest
        return runCatching { art.scale((art.width * ratio).toInt(), (art.height * ratio).toInt()) }.getOrDefault(art)
    }
}

/** Sesi Android jadi [MediaSnapshot]. */
private fun MediaController.toSnapshot(): MediaSnapshot {
    val m = metadata
    val state = playbackState
    return MediaSnapshot(
        packageName = packageName,
        mediaId = m?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) ?: m?.description?.mediaId,
        mediaUri = m?.getString(MediaMetadata.METADATA_KEY_MEDIA_URI) ?: m?.description?.mediaUri?.toString(),
        title = m?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty(),
        artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE).orEmpty(),
        album = m?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
        durationMs = m?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L,
        state = playerStateOf(state?.state ?: PlaybackState.STATE_NONE),
        positionMs = state?.position ?: 0L,
        positionUpdatedAtElapsedMs = state?.lastPositionUpdateTime ?: 0L,
        speed = state?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
        controls = controlsOf(state?.actions ?: 0L),
        isLocal = playbackInfo?.playbackType != MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE,
    )
}

/** `PlaybackState.STATE_*` jadi [PlayerState]. */
internal fun playerStateOf(code: Int): PlayerState = when (code) {
    PlaybackState.STATE_PLAYING, PlaybackState.STATE_FAST_FORWARDING, PlaybackState.STATE_REWINDING -> PlayerState.Playing
    PlaybackState.STATE_PAUSED -> PlayerState.Paused
    PlaybackState.STATE_STOPPED -> PlayerState.Stopped
    PlaybackState.STATE_BUFFERING,
    PlaybackState.STATE_CONNECTING,
    PlaybackState.STATE_SKIPPING_TO_NEXT,
    PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
    PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> PlayerState.Transient
    PlaybackState.STATE_ERROR -> PlayerState.Error
    else -> PlayerState.None
}

/**
 * Bit `PlaybackState.actions` jadi [PlayerControls]. Play/pause cukup salah
 * satu dari PLAY, PAUSE, atau PLAY_PAUSE; player berbeda mengiklankan yang
 * berbeda untuk tombol yang sama.
 */
internal fun controlsOf(actions: Long): PlayerControls {
    fun has(bit: Long) = (actions and bit) != 0L
    return PlayerControls(
        canPlayPause = has(PlaybackState.ACTION_PLAY) || has(PlaybackState.ACTION_PAUSE) ||
            has(PlaybackState.ACTION_PLAY_PAUSE),
        canSeek = has(PlaybackState.ACTION_SEEK_TO),
        canSkipNext = has(PlaybackState.ACTION_SKIP_TO_NEXT),
        canSkipPrevious = has(PlaybackState.ACTION_SKIP_TO_PREVIOUS),
    )
}
