package com.tglabs.spotivibe.domain

/**
 * Nama key DataStore dari era login Spotify (PKCE + App Remote).
 *
 * Disalin persis dari `PreferencesRepository` versi 0.8.x. Kalau satu saja
 * salah ketik, key itu tidak pernah terhapus dan refresh token-nya tertinggal
 * di perangkat selamanya.
 */
val LEGACY_SPOTIFY_PREF_KEYS: Set<String> = setOf(
    "spotify_access_token",
    "spotify_refresh_token",
    "spotify_token_expires_at_ms",
    "spotify_pkce_verifier",
    "spotify_pkce_state",
    "spotify_authorized",
    "last_known_product",
)

/** Key di antara [present] yang merupakan sisa kredensial Spotify. */
fun legacySpotifyCredentialKeys(present: Collection<String>): Set<String> =
    present.filterTo(mutableSetOf()) { it in LEGACY_SPOTIFY_PREF_KEYS }
