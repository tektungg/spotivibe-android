package com.tglabs.spotivibe.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tglabs.spotivibe.data.auth.AuthStorage
import com.tglabs.spotivibe.data.auth.PendingAuth
import com.tglabs.spotivibe.data.auth.SpotifyTokens
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "spotivibe_prefs")

/**
 * Persist user preferences + overlay position. Survive app restart.
 *
 * Juga jadi implementasi [AuthStorage] untuk kredensial Spotify. File ini
 * di-exclude dari cloud backup lewat `res/xml/backup_rules.xml` +
 * `data_extraction_rules.xml` -- refresh token tidak boleh ikut terangkut ke
 * backup Google.
 */
class PreferencesRepository(private val context: Context) : AuthStorage {

    private val romanizationKey = booleanPreferencesKey("romanization_enabled")
    private val overlayEnabledKey = booleanPreferencesKey("overlay_enabled")
    private val overlayXKey = intPreferencesKey("overlay_x")
    private val overlayYKey = intPreferencesKey("overlay_y")
    private val spotifyTokenKey = stringPreferencesKey("spotify_access_token")
    private val spotifyRefreshTokenKey = stringPreferencesKey("spotify_refresh_token")
    private val spotifyTokenExpiresAtKey = longPreferencesKey("spotify_token_expires_at_ms")
    private val pkceVerifierKey = stringPreferencesKey("spotify_pkce_verifier")
    private val pkceStateKey = stringPreferencesKey("spotify_pkce_state")

    // Legacy: flag dari era implicit grant. Dihapus saat clearTokens() supaya
    // tidak ada dua sumber kebenaran soal "apakah user sudah login".
    private val legacyAuthorizedKey = booleanPreferencesKey("spotify_authorized")
    private val darkModeKey = booleanPreferencesKey("dark_mode")
    private val lyricsFontSizeKey = intPreferencesKey("lyrics_font_size")
    // Improvement set: per-user fine-tuning lyrics behavior
    private val lyricsOffsetMsKey = intPreferencesKey("lyrics_offset_ms")
    private val lineSpacingKey = intPreferencesKey("line_spacing_dp")
    private val highContrastKey = booleanPreferencesKey("high_contrast")
    private val smoothScrollKey = booleanPreferencesKey("smooth_scroll")
    private val hapticEnabledKey = booleanPreferencesKey("haptic_enabled")

    val romanizationEnabled: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[romanizationKey] ?: false }

    val overlayEnabled: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[overlayEnabledKey] ?: false }

    /** Posisi overlay (x, y) — default 0,0 (top-left). Service apply offset. */
    val overlayPosition: Flow<Pair<Int, Int>> = context.dataStore.data
        .map { prefs ->
            (prefs[overlayXKey] ?: 0) to (prefs[overlayYKey] ?: 0)
        }

    // ── AuthStorage ──────────────────────────────────────────────
    //
    // Token lama dari era implicit grant tidak punya refresh token. Saat
    // di-load, refreshToken-nya null, jadi SpotifyAuthRepository.restore()
    // menganggapnya SignedOut dan user login sekali lewat PKCE. Itu migrasi
    // yang diinginkan: sesi yang tidak bisa diperbarui memang tidak berguna.

    override suspend fun loadTokens(): SpotifyTokens? {
        val prefs = context.dataStore.data.first()
        val access = prefs[spotifyTokenKey]?.takeIf { it.isNotBlank() } ?: return null
        return SpotifyTokens(
            accessToken = access,
            refreshToken = prefs[spotifyRefreshTokenKey]?.takeIf { it.isNotBlank() },
            expiresAtMs = prefs[spotifyTokenExpiresAtKey] ?: 0L,
        )
    }

    override suspend fun saveTokens(tokens: SpotifyTokens) {
        context.dataStore.edit { prefs ->
            prefs[spotifyTokenKey] = tokens.accessToken
            prefs[spotifyTokenExpiresAtKey] = tokens.expiresAtMs
            val refresh = tokens.refreshToken
            if (refresh.isNullOrBlank()) {
                prefs.remove(spotifyRefreshTokenKey)
            } else {
                prefs[spotifyRefreshTokenKey] = refresh
            }
        }
    }

    override suspend fun clearTokens() {
        context.dataStore.edit { prefs ->
            prefs.remove(spotifyTokenKey)
            prefs.remove(spotifyRefreshTokenKey)
            prefs.remove(spotifyTokenExpiresAtKey)
            prefs.remove(legacyAuthorizedKey)
        }
    }

    override suspend fun savePendingAuth(pending: PendingAuth) {
        context.dataStore.edit { prefs ->
            prefs[pkceVerifierKey] = pending.verifier
            prefs[pkceStateKey] = pending.state
        }
    }

    override suspend fun loadPendingAuth(): PendingAuth? {
        val prefs = context.dataStore.data.first()
        val verifier = prefs[pkceVerifierKey]?.takeIf { it.isNotBlank() } ?: return null
        val state = prefs[pkceStateKey]?.takeIf { it.isNotBlank() } ?: return null
        return PendingAuth(verifier = verifier, state = state)
    }

    override suspend fun clearPendingAuth() {
        context.dataStore.edit { prefs ->
            prefs.remove(pkceVerifierKey)
            prefs.remove(pkceStateKey)
        }
    }

    /** Dark theme default true. User toggle via icon di header. */
    val darkMode: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[darkModeKey] ?: true }

    /** Lyrics font size in sp. Range 12-56, default 17. */
    val lyricsFontSize: Flow<Int> = context.dataStore.data
        .map { prefs -> (prefs[lyricsFontSizeKey] ?: 17).coerceIn(12, 56) }

    /**
     * Manual sync correction. Positive = lyrics dipercepat (kompensasi LRC lambat).
     * Negative = lyrics dilambatkan. Range ±2000 ms.
     */
    val lyricsOffsetMs: Flow<Int> = context.dataStore.data
        .map { prefs -> (prefs[lyricsOffsetMsKey] ?: 0).coerceIn(-2000, 2000) }

    /** Extra vertical spacing antar baris lyric (sp). Range 0-20, default 7 = paritas lama. */
    val lineSpacing: Flow<Int> = context.dataStore.data
        .map { prefs -> (prefs[lineSpacingKey] ?: 7).coerceIn(0, 20) }

    /** High contrast mode — heavier weights, no dim fade. Default false. */
    val highContrast: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[highContrastKey] ?: false }

    /**
     * Auto-scroll behavior. true (default) = animateScrollToItem dengan tween halus.
     * false = scrollToItem snap instan, lebih responsif tapi visually jumpy.
     */
    val smoothScroll: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[smoothScrollKey] ?: true }

    /** Haptic feedback saat baris lyric berganti. Default true. */
    val hapticEnabled: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[hapticEnabledKey] ?: true }

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

    suspend fun setDarkMode(dark: Boolean) {
        context.dataStore.edit { it[darkModeKey] = dark }
    }

    suspend fun setLyricsFontSize(size: Int) {
        context.dataStore.edit { it[lyricsFontSizeKey] = size.coerceIn(12, 56) }
    }

    /**
     * Atomic increment/decrement — baca current dan write di transaction yang sama
     * supaya tidak ada race condition kalau user tap A+/A− cepat-cepat.
     * Tanpa ini: read uiState.value (mungkin stale) → write → user tap lagi sebelum
     * state propagate → read stale lagi → effective increment 1× saja walau tap 2×.
     */
    suspend fun bumpLyricsFontSize(delta: Int) {
        context.dataStore.edit { prefs ->
            val current = (prefs[lyricsFontSizeKey] ?: 17).coerceIn(12, 56)
            prefs[lyricsFontSizeKey] = (current + delta).coerceIn(12, 56)
        }
    }

    suspend fun setLyricsOffsetMs(ms: Int) {
        context.dataStore.edit { it[lyricsOffsetMsKey] = ms.coerceIn(-2000, 2000) }
    }

    suspend fun setLineSpacing(dp: Int) {
        context.dataStore.edit { it[lineSpacingKey] = dp.coerceIn(0, 20) }
    }

    suspend fun setHighContrast(enabled: Boolean) {
        context.dataStore.edit { it[highContrastKey] = enabled }
    }

    suspend fun setSmoothScroll(enabled: Boolean) {
        context.dataStore.edit { it[smoothScrollKey] = enabled }
    }

    suspend fun setHapticEnabled(enabled: Boolean) {
        context.dataStore.edit { it[hapticEnabledKey] = enabled }
    }
}
