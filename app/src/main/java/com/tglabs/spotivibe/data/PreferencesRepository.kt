package com.tglabs.spotivibe.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "spotivibe_prefs")

/**
 * Persist user preferences + overlay position. Survive app restart.
 */
class PreferencesRepository(private val context: Context) {

    private val romanizationKey = booleanPreferencesKey("romanization_enabled")
    private val overlayEnabledKey = booleanPreferencesKey("overlay_enabled")
    private val overlayXKey = intPreferencesKey("overlay_x")
    private val overlayYKey = intPreferencesKey("overlay_y")
    private val spotifyAuthorizedKey = booleanPreferencesKey("spotify_authorized")
    private val darkModeKey = booleanPreferencesKey("dark_mode")
    private val lyricsFontSizeKey = intPreferencesKey("lyrics_font_size")

    val romanizationEnabled: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[romanizationKey] ?: false }

    val overlayEnabled: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[overlayEnabledKey] ?: false }

    /** Posisi overlay (x, y) — default 0,0 (top-left). Service apply offset. */
    val overlayPosition: Flow<Pair<Int, Int>> = context.dataStore.data
        .map { prefs ->
            (prefs[overlayXKey] ?: 0) to (prefs[overlayYKey] ?: 0)
        }

    /**
     * Apakah user sudah pernah authorize Spotify di session sebelumnya.
     * Kalau true, app start auto-reconnect tanpa show "Connect Spotify" prompt.
     */
    val spotifyAuthorized: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[spotifyAuthorizedKey] ?: false }

    /** Dark theme default true. User toggle via icon di header. */
    val darkMode: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[darkModeKey] ?: true }

    /** Lyrics font size in sp. Range 12-24, default 17. */
    val lyricsFontSize: Flow<Int> = context.dataStore.data
        .map { prefs -> (prefs[lyricsFontSizeKey] ?: 17).coerceIn(12, 24) }

    suspend fun setRomanizationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[romanizationKey] = enabled }
    }

    suspend fun setOverlayEnabled(enabled: Boolean) {
        context.dataStore.edit { it[overlayEnabledKey] = enabled }
    }

    suspend fun setOverlayPosition(x: Int, y: Int) {
        context.dataStore.edit { prefs ->
            prefs[overlayXKey] = x
            prefs[overlayYKey] = y
        }
    }

    suspend fun setSpotifyAuthorized(authorized: Boolean) {
        context.dataStore.edit { it[spotifyAuthorizedKey] = authorized }
    }

    suspend fun setDarkMode(dark: Boolean) {
        context.dataStore.edit { it[darkModeKey] = dark }
    }

    suspend fun setLyricsFontSize(size: Int) {
        context.dataStore.edit { it[lyricsFontSizeKey] = size.coerceIn(12, 24) }
    }

    /**
     * Atomic increment/decrement — baca current dan write di transaction yang sama
     * supaya tidak ada race condition kalau user tap A+/A− cepat-cepat.
     * Tanpa ini: read uiState.value (mungkin stale) → write → user tap lagi sebelum
     * state propagate → read stale lagi → effective increment 1× saja walau tap 2×.
     */
    suspend fun bumpLyricsFontSize(delta: Int) {
        context.dataStore.edit { prefs ->
            val current = (prefs[lyricsFontSizeKey] ?: 17).coerceIn(12, 24)
            prefs[lyricsFontSizeKey] = (current + delta).coerceIn(12, 24)
        }
    }
}
