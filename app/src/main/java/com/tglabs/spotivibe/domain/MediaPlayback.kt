package com.tglabs.spotivibe.domain

import androidx.compose.runtime.Immutable
import java.security.MessageDigest

/**
 * Mengikuti player apa pun lewat MediaSession Android.
 *
 * Dulu Spotivibe hanya bisa membaca Spotify, lewat App Remote SDK yang butuh
 * login OAuth, dan kontrolnya butuh Premium. Semua player musik di Android
 * (Spotify, YouTube Music, Apple Music, pemutar lokal) sudah menerbitkan
 * MediaSession: judul, posisi, dan tombol kontrol. Membacanya cukup dengan satu
 * izin sistem (notification access), tanpa akun apa pun.
 *
 * File ini memuat semua keputusan yang tidak butuh tipe Android, supaya bisa
 * diuji di JVM. Adapter Android (`data/MediaSessionSource.kt`) hanya
 * menerjemahkan objek sistem ke [MediaSnapshot] dan sebaliknya.
 */

/** Keadaan player, disederhanakan dari `PlaybackState.STATE_*`. */
enum class PlayerState {
    Playing,
    Paused,
    Stopped,

    /** Buffering, connecting, atau sedang pindah lagu: sebentar lagi berubah. */
    Transient,
    Error,
    None,
}

/** Tombol yang diizinkan player, dari bit `PlaybackState.actions`. */
@Immutable
data class PlayerControls(
    val canPlayPause: Boolean = false,
    val canSeek: Boolean = false,
    val canSkipNext: Boolean = false,
    val canSkipPrevious: Boolean = false,
) {
    val hasAny: Boolean get() = canPlayPause || canSeek || canSkipNext || canSkipPrevious

    companion object {
        /** Belum tahu: semua tampil, supaya kontrol tidak muncul-hilang saat player baru terbaca. */
        val Optimistic = PlayerControls(canPlayPause = true, canSeek = true, canSkipNext = true, canSkipPrevious = true)
    }
}

/** Satu sesi media, lepas dari tipe Android. */
@Immutable
data class MediaSnapshot(
    val packageName: String,
    val mediaId: String? = null,
    val mediaUri: String? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
    val state: PlayerState = PlayerState.None,
    val positionMs: Long = 0L,
    /** `PlaybackState.lastPositionUpdateTime`, basis `SystemClock.elapsedRealtime()`. */
    val positionUpdatedAtElapsedMs: Long = 0L,
    val speed: Float = 1f,
    val controls: PlayerControls = PlayerControls(),
    /** false untuk sesi relay/remote (cast, KDE Connect): posisinya kurang bisa dipercaya. */
    val isLocal: Boolean = true,
)

const val SPOTIFY_PACKAGE = "com.spotify.music"

private const val SPOTIFY_TRACK_PREFIX = "spotify:track:"
private val SPOTIFY_ID = Regex("""^[A-Za-z0-9]{22}$""")
private val SPOTIFY_TRACK_URL = Regex("""/track/([A-Za-z0-9]{22})""")
private const val MEDIA_KEY_PREFIX = "media:"
private const val MEDIA_KEY_HASH_LENGTH = 16

/**
 * ID Spotify polos dari mediaId atau URI, kalau ada.
 *
 * Spotify menaruh URI-nya (`spotify:track:<id>`) di mediaId MediaSession.
 * Bentuk lain yang dikenal: URL `open.spotify.com/track/<id>`, dan ID 22
 * karakter polos, yang terakhir hanya dari paket Spotify karena di app lain
 * string 22 karakter bisa berarti apa saja.
 */
fun spotifyIdOf(snapshot: MediaSnapshot): String? =
    listOfNotNull(snapshot.mediaId, snapshot.mediaUri).firstNotNullOfOrNull { raw ->
        when {
            raw.startsWith(SPOTIFY_TRACK_PREFIX) -> raw.substringAfterLast(':').takeIf { SPOTIFY_ID.matches(it) }
            SPOTIFY_TRACK_URL.containsMatchIn(raw) -> SPOTIFY_TRACK_URL.find(raw)?.groupValues?.get(1)
            snapshot.packageName == SPOTIFY_PACKAGE && SPOTIFY_ID.matches(raw) -> raw
            else -> null
        }
    }

/**
 * Identitas lagu, dipakai untuk cache lirik, override, dan offset per lagu.
 *
 * Lagu Spotify tetap memakai `spotify:track:<id>`, sama persis dengan era App
 * Remote, jadi cache, override, dan offset yang sudah tersimpan tetap terpakai.
 * Lagu dari player lain memakai hash judul dan artis yang sudah dinormalisasi:
 * sama untuk lagu yang sama di YouTube Music dan pemutar lain, dan tidak ikut
 * berubah saat player mengisi album atau durasi belakangan. Durasi sengaja
 * tidak ikut: YouTube Music mengirimnya setelah judul, dan identitas yang
 * berubah di tengah lagu berarti lirik dimuat dua kali.
 */
fun trackIdFor(snapshot: MediaSnapshot): String {
    spotifyIdOf(snapshot)?.let { return SPOTIFY_TRACK_PREFIX + it }
    val basis = "${normalizeTitle(snapshot.title)}|${normalizeArtist(snapshot.artist)}"
    val hash = MessageDigest.getInstance("SHA-256").digest(basis.toByteArray(Charsets.UTF_8))
    return MEDIA_KEY_PREFIX + hash.joinToString("") { "%02x".format(it) }.take(MEDIA_KEY_HASH_LENGTH)
}

/** Lagu dari Spotify, jadi sumber lirik yang mencocokkan lewat ID Spotify bisa dipakai. */
fun isSpotifyTrackId(trackId: String): Boolean = trackId.startsWith(SPOTIFY_TRACK_PREFIX)

/**
 * Sesi yang diikuti dari beberapa yang aktif.
 *
 * Urutan: sedang memutar di perangkat ini, sedang memutar di mana pun, sesi
 * yang sedang diikuti (supaya tidak berpindah hanya karena app lain sempat
 * menerbitkan sesi diam), lalu yang pertama di daftar sistem, yang diurutkan
 * dari paling baru aktif. Jadi memutar lagu di app lain otomatis memindahkan
 * Spotivibe ke sana, tapi notifikasi diam dari app lain tidak.
 */
fun pickSession(sessions: List<MediaSnapshot>, currentPackage: String?): MediaSnapshot? =
    sessions.firstOrNull { it.state == PlayerState.Playing && it.isLocal }
        ?: sessions.firstOrNull { it.state == PlayerState.Playing }
        ?: sessions.firstOrNull { it.packageName == currentPackage }
        ?: sessions.firstOrNull()

/**
 * Posisi sekarang dari laporan terakhir player.
 *
 * Player tidak melaporkan posisi terus-menerus; ia melaporkan posisi pada satu
 * saat plus kecepatan. Selama memutar posisinya maju `(now − saat itu) × speed`,
 * selain itu diam. Dijepit ke durasi kalau durasinya diketahui.
 */
fun positionNowMs(snapshot: MediaSnapshot, nowElapsedMs: Long): Long {
    val base = snapshot.positionMs.coerceAtLeast(0L)
    val moving = snapshot.state == PlayerState.Playing && snapshot.positionUpdatedAtElapsedMs > 0L
    val position = if (moving) {
        val elapsed = (nowElapsedMs - snapshot.positionUpdatedAtElapsedMs).coerceAtLeast(0L)
        base + (elapsed * snapshot.speed).toLong()
    } else {
        base
    }
    return if (snapshot.durationMs > 0L) position.coerceAtMost(snapshot.durationMs) else position
}

/**
 * Apakah lagu dianggap berhenti, mengingat keadaan sebelumnya.
 *
 * Buffering dan pindah lagu berlangsung sebentar. Kalau ikut dianggap pause,
 * lirik berhenti lalu jalan lagi tiap kali jaringan tersendat, dan baris aktif
 * berkedip. Keadaan sementara mempertahankan nilai sebelumnya.
 */
fun isPausedFor(state: PlayerState, wasPaused: Boolean): Boolean = when (state) {
    PlayerState.Playing -> false
    PlayerState.Transient -> wasPaused
    PlayerState.Paused, PlayerState.Stopped, PlayerState.Error, PlayerState.None -> true
}

/**
 * Sesi yang layak ditampilkan: sudah punya judul. Player sering menerbitkan
 * sesi kosong dulu lalu mengisi metadata sesudahnya, dan lagu tanpa judul
 * tidak bisa dicari liriknya.
 */
fun isDisplayable(snapshot: MediaSnapshot): Boolean = snapshot.title.isNotBlank()

/**
 * [snapshot] sebagai [NowPlaying].
 *
 * Posisi dihitung ke saat ini dulu, lalu dicap dengan jam dinding [nowWallMs],
 * karena [NowPlaying.extrapolatedProgressMs] mengekstrapolasi dari jam dinding.
 */
fun MediaSnapshot.toNowPlaying(nowElapsedMs: Long, nowWallMs: Long, wasPaused: Boolean): NowPlaying =
    NowPlaying(
        id = trackIdFor(this),
        title = title,
        artist = artist,
        album = album,
        imageUri = null,
        durationMs = durationMs.coerceAtLeast(0L),
        progressMs = positionNowMs(this, nowElapsedMs),
        isPaused = isPausedFor(state, wasPaused),
        capturedAtMs = nowWallMs,
    )

/** Satu lagu di antrean player, untuk preload lirik sebelum lagunya diputar. */
@Immutable
data class QueuedTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
)
