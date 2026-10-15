package com.tglabs.spotivibe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPlaybackTest {

    private fun snap(
        pkg: String = "com.google.android.apps.youtube.music",
        title: String = "Blinding Lights",
        artist: String = "The Weeknd",
        state: PlayerState = PlayerState.Playing,
        mediaId: String? = null,
        isLocal: Boolean = true,
    ) = MediaSnapshot(packageName = pkg, mediaId = mediaId, title = title, artist = artist, state = state, isLocal = isLocal)

    // ── Identitas lagu ───────────────────────────────────────────

    /** REGRESI desain: cache, override, dan offset dari era App Remote memakai URI ini. */
    @Test
    fun `lagu Spotify tetap memakai URI spotify track`() {
        val s = snap(pkg = SPOTIFY_PACKAGE, mediaId = "spotify:track:0VjIjW4GlUZAMYd2vXMi3b")
        assertEquals("spotify:track:0VjIjW4GlUZAMYd2vXMi3b", trackIdFor(s))
        assertTrue(isSpotifyTrackId(trackIdFor(s)))
    }

    @Test
    fun `ID Spotify dari URL dan dari ID polos milik Spotify`() {
        val url = snap(mediaId = "https://open.spotify.com/track/0VjIjW4GlUZAMYd2vXMi3b?si=x")
        assertEquals("0VjIjW4GlUZAMYd2vXMi3b", spotifyIdOf(url))
        val bare = snap(pkg = SPOTIFY_PACKAGE, mediaId = "0VjIjW4GlUZAMYd2vXMi3b")
        assertEquals("0VjIjW4GlUZAMYd2vXMi3b", spotifyIdOf(bare))
    }

    @Test
    fun `string 22 karakter dari app lain bukan ID Spotify`() {
        assertNull(spotifyIdOf(snap(mediaId = "0VjIjW4GlUZAMYd2vXMi3b")))
        assertNull(spotifyIdOf(snap(pkg = SPOTIFY_PACKAGE, mediaId = "spotify:track:pendek")))
    }

    @Test
    fun `lagu dari player lain diberi kunci media dari judul dan artis`() {
        val id = trackIdFor(snap())
        assertTrue(id.startsWith("media:"))
        assertFalse(isSpotifyTrackId(id))
        assertEquals("media:".length + 16, id.length)
    }

    /** Lagu yang sama di dua player non-Spotify berbagi cache lirik dan offset. */
    @Test
    fun `kunci media sama lintas app dan tahan beda huruf atau aksen`() {
        val ytm = trackIdFor(snap())
        val lain = trackIdFor(snap(pkg = "org.lain.player", title = "BLINDING LIGHTS", artist = "the weeknd"))
        assertEquals(ytm, lain)
        assertEquals(trackIdFor(snap(title = "Café")), trackIdFor(snap(title = "Cafe")))
    }

    @Test
    fun `kunci media beda untuk lagu berbeda`() {
        assertNotEquals(trackIdFor(snap()), trackIdFor(snap(title = "Save Your Tears")))
        assertNotEquals(trackIdFor(snap()), trackIdFor(snap(artist = "Artis Lain")))
    }

    // ── Memilih sesi ─────────────────────────────────────────────

    @Test
    fun `sesi yang sedang memutar di perangkat ini menang`() {
        val diam = snap(pkg = "a", state = PlayerState.Paused)
        val remote = snap(pkg = "b", state = PlayerState.Playing, isLocal = false)
        val lokal = snap(pkg = "c", state = PlayerState.Playing)
        assertEquals("c", pickSession(listOf(diam, remote, lokal), currentPackage = "a")?.packageName)
        assertEquals("b", pickSession(listOf(diam, remote), currentPackage = "a")?.packageName)
    }

    /** Notifikasi diam dari app lain tidak boleh merebut layar dari player yang sedang diikuti. */
    @Test
    fun `tanpa yang memutar tetap di sesi yang sedang diikuti`() {
        val a = snap(pkg = "a", state = PlayerState.Paused)
        val b = snap(pkg = "b", state = PlayerState.Paused)
        assertEquals("b", pickSession(listOf(a, b), currentPackage = "b")?.packageName)
        assertEquals("a", pickSession(listOf(a, b), currentPackage = null)?.packageName)
        assertNull(pickSession(emptyList(), currentPackage = "a"))
    }

    // ── Posisi ───────────────────────────────────────────────────

    private fun posisi(state: PlayerState, speed: Float = 1f, duration: Long = 0L) = MediaSnapshot(
        packageName = "p",
        state = state,
        positionMs = 10_000,
        positionUpdatedAtElapsedMs = 1_000,
        speed = speed,
        durationMs = duration,
    )

    @Test
    fun `posisi maju sejak laporan terakhir selama memutar`() {
        assertEquals(12_000L, positionNowMs(posisi(PlayerState.Playing), nowElapsedMs = 3_000))
        assertEquals(14_000L, positionNowMs(posisi(PlayerState.Playing, speed = 2f), nowElapsedMs = 3_000))
    }

    @Test
    fun `posisi diam saat pause dan dijepit ke durasi`() {
        assertEquals(10_000L, positionNowMs(posisi(PlayerState.Paused), nowElapsedMs = 99_000))
        assertEquals(11_000L, positionNowMs(posisi(PlayerState.Playing, duration = 11_000), nowElapsedMs = 99_000))
    }

    @Test
    fun `jam mundur tidak membuat posisi mundur`() {
        assertEquals(10_000L, positionNowMs(posisi(PlayerState.Playing), nowElapsedMs = 0))
    }

    // ── Pause ────────────────────────────────────────────────────

    /** Buffering sebentar tidak boleh membuat lirik berhenti lalu jalan lagi. */
    @Test
    fun `keadaan sementara mempertahankan pause sebelumnya`() {
        assertFalse(isPausedFor(PlayerState.Transient, wasPaused = false))
        assertTrue(isPausedFor(PlayerState.Transient, wasPaused = true))
        assertFalse(isPausedFor(PlayerState.Playing, wasPaused = true))
        assertTrue(isPausedFor(PlayerState.Stopped, wasPaused = false))
    }

    // ── Konversi ─────────────────────────────────────────────────

    @Test
    fun `snapshot jadi NowPlaying dengan posisi saat ini`() {
        val s = posisi(PlayerState.Playing).copy(title = "Judul", artist = "Artis", album = "Album")
        val np = s.toNowPlaying(nowElapsedMs = 2_000, nowWallMs = 50_000, wasPaused = true)
        assertEquals(11_000L, np.progressMs)
        assertEquals(50_000L, np.capturedAtMs)
        assertFalse(np.isPaused)
        assertEquals(trackIdFor(s), np.id)
        assertEquals("Album", np.album)
    }

    @Test
    fun `sesi tanpa judul tidak ditampilkan`() {
        assertFalse(isDisplayable(snap(title = " ")))
        assertTrue(isDisplayable(snap()))
    }

    @Test
    fun `kontrol optimistis menyalakan semua tombol`() {
        assertTrue(PlayerControls.Optimistic.canSeek && PlayerControls.Optimistic.canSkipNext)
        assertFalse(PlayerControls().hasAny)
    }
}
