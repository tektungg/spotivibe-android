package com.tglabs.spotivibe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyCredentialsTest {

    /** REGRESI potensial: satu nama yang salah berarti refresh token tertinggal selamanya. */
    @Test
    fun `semua key login Spotify lama dikenali`() {
        val lama = listOf(
            "spotify_access_token", "spotify_refresh_token", "spotify_token_expires_at_ms",
            "spotify_pkce_verifier", "spotify_pkce_state", "spotify_authorized", "last_known_product",
        )
        assertEquals(lama.toSet(), legacySpotifyCredentialKeys(lama))
    }

    @Test
    fun `setelan user tidak ikut terhapus`() {
        val setelan = listOf("dark_mode", "lyrics_offset_ms", "song_offset_spotify_track_abc", "community_sources")
        assertTrue(legacySpotifyCredentialKeys(setelan).isEmpty())
    }

    @Test
    fun `hanya yang ada yang dikembalikan`() {
        assertEquals(setOf("spotify_refresh_token"), legacySpotifyCredentialKeys(listOf("spotify_refresh_token", "dark_mode")))
    }
}
