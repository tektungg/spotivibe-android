package com.tglabs.spotivibe.data

import com.tglabs.spotivibe.domain.bumpFontSize
import com.tglabs.spotivibe.domain.clampFontSize
import com.tglabs.spotivibe.domain.clampLineSpacing
import com.tglabs.spotivibe.domain.clampLyricsOffsetMs
import com.tglabs.spotivibe.domain.clampDeviceOffsetMs
import com.tglabs.spotivibe.domain.clampSongOffsetMs
import com.tglabs.spotivibe.domain.sanitizeKeyPart
import com.tglabs.spotivibe.domain.legacySpotifyCredentialKeys
import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tglabs.spotivibe.domain.LyricsLookupEvent
import com.tglabs.spotivibe.domain.LyricsOutcome
import com.tglabs.spotivibe.domain.LyricsSource
import com.tglabs.spotivibe.domain.LyricsStats
import com.tglabs.spotivibe.domain.ProbeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "spotivibe_prefs")

/**
 * Persist user preferences + overlay position. Survive app restart.
 *
 * Dulu juga menyimpan token login Spotify; sejak app membaca MediaSession,
 * tidak ada lagi kredensial apa pun. Sisa token di perangkat yang di-upgrade
 * dihapus oleh [purgeLegacySpotifyCredentials]. File ini tetap di-exclude dari
 * cloud backup lewat `res/xml/backup_rules.xml` + `data_extraction_rules.xml`,
 * supaya sisa itu tidak sempat ikut terangkut ke backup Google.
 */
class PreferencesRepository(private val context: Context) {

    private val romanizationKey = booleanPreferencesKey("romanization_enabled")
    private val overlayEnabledKey = booleanPreferencesKey("overlay_enabled")
    private val overlayXKey = intPreferencesKey("overlay_x")
    private val overlayYKey = intPreferencesKey("overlay_y")
    // Penghitung statistik lirik. Monoton naik sampai di-reset user.
    private val statSyncedKey = intPreferencesKey("stat_lyrics_synced")
    private val statPlainKey = intPreferencesKey("stat_lyrics_plain")
    private val statNotFoundKey = intPreferencesKey("stat_lyrics_not_found")
    private val statUnavailableKey = intPreferencesKey("stat_lyrics_unavailable")
    private val statMemKey = intPreferencesKey("stat_lyrics_from_mem")
    private val statDiskKey = intPreferencesKey("stat_lyrics_from_disk")
    private val statNetworkKey = intPreferencesKey("stat_lyrics_from_network")
    private val statOverrideKey = intPreferencesKey("stat_lyrics_from_override")
    private val statGetWonKey = intPreferencesKey("stat_lyrics_get_won")
    private val statSearchWonKey = intPreferencesKey("stat_lyrics_search_won")
    private val statCommunityWonKey = intPreferencesKey("stat_lyrics_community_won")
    private val communitySourcesKey = booleanPreferencesKey("community_sources")
    private val darkModeKey = booleanPreferencesKey("dark_mode")
    private val lyricsFontSizeKey = intPreferencesKey("lyrics_font_size")
    // Improvement set: per-user fine-tuning lyrics behavior
    private val lyricsOffsetMsKey = intPreferencesKey("lyrics_offset_ms")
    private val lineSpacingKey = intPreferencesKey("line_spacing_dp")
    private val highContrastKey = booleanPreferencesKey("high_contrast")
    private val smoothScrollKey = booleanPreferencesKey("smooth_scroll")
    private val hapticEnabledKey = booleanPreferencesKey("haptic_enabled")
    private val lyricEffectsKey = booleanPreferencesKey("lyric_effects")

    val romanizationEnabled: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[romanizationKey] ?: false }

    val overlayEnabled: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[overlayEnabledKey] ?: false }

    /** Posisi overlay (x, y) — default 0,0 (top-left). Service apply offset. */
    val overlayPosition: Flow<Pair<Int, Int>> = context.dataStore.data
        .map { prefs ->
            (prefs[overlayXKey] ?: 0) to (prefs[overlayYKey] ?: 0)
        }

    // ── Statistik lirik ──────────────────────────────────────────

    val lyricsStats: Flow<LyricsStats> = context.dataStore.data.map { prefs ->
        LyricsStats(
            synced = prefs[statSyncedKey] ?: 0,
            plainOnly = prefs[statPlainKey] ?: 0,
            notFound = prefs[statNotFoundKey] ?: 0,
            unavailable = prefs[statUnavailableKey] ?: 0,
            fromMem = prefs[statMemKey] ?: 0,
            fromDisk = prefs[statDiskKey] ?: 0,
            fromNetwork = prefs[statNetworkKey] ?: 0,
            fromOverride = prefs[statOverrideKey] ?: 0,
            getWon = prefs[statGetWonKey] ?: 0,
            searchWon = prefs[statSearchWonKey] ?: 0,
            communityWon = prefs[statCommunityWonKey] ?: 0,
        )
    }

    /**
     * Naikkan penghitung untuk satu lookup.
     *
     * Baca-ubah-tulis dilakukan di dalam SATU blok `edit`, yang atomik di
     * DataStore. Kalau dipecah jadi baca lalu tulis terpisah, dua lookup yang
     * berdekatan (fetch normal dan preload antrean berjalan bersamaan) bisa
     * saling menimpa dan angkanya hilang.
     */
    suspend fun recordLyricsLookup(event: LyricsLookupEvent) {
        context.dataStore.edit { prefs ->
            fun bump(key: androidx.datastore.preferences.core.Preferences.Key<Int>) {
                prefs[key] = (prefs[key] ?: 0) + 1
            }
            when (event.outcome) {
                LyricsOutcome.Synced -> bump(statSyncedKey)
                LyricsOutcome.PlainOnly -> bump(statPlainKey)
                LyricsOutcome.NotFound -> bump(statNotFoundKey)
                LyricsOutcome.Unavailable -> bump(statUnavailableKey)
            }
            when (event.source) {
                LyricsSource.Override -> bump(statOverrideKey)
                LyricsSource.MemCache -> bump(statMemKey)
                LyricsSource.DiskCache -> bump(statDiskKey)
                LyricsSource.Network -> bump(statNetworkKey)
            }
            when (event.probe) {
                ProbeSource.Get -> bump(statGetWonKey)
                ProbeSource.Search -> bump(statSearchWonKey)
                ProbeSource.Amll, ProbeSource.LrcRed, ProbeSource.LrcMux -> bump(statCommunityWonKey)
                null -> Unit
            }
        }
    }

    suspend fun resetLyricsStats() {
        context.dataStore.edit { prefs ->
            listOf(
                statSyncedKey, statPlainKey, statNotFoundKey, statUnavailableKey,
                statMemKey, statDiskKey, statNetworkKey, statOverrideKey,
                statGetWonKey, statSearchWonKey, statCommunityWonKey,
            ).forEach { prefs.remove(it) }
        }
    }

    /**
     * Hapus token, PKCE, dan status Premium sisa era login Spotify.
     *
     * Refresh token yang tertinggal di disk adalah kredensial hidup ke akun
     * Spotify user, padahal tidak ada lagi kode yang memakainya. Dipanggil di
     * setiap start; menulis HANYA kalau ada yang perlu dihapus, jadi setelah
     * sekali bersih biayanya cuma satu baca.
     *
     * @return jumlah key yang dihapus.
     */
    suspend fun purgeLegacySpotifyCredentials(): Int {
        val present = context.dataStore.data.first().asMap().keys
        val stale = present.filter { it.name in legacySpotifyCredentialKeys(present.map { k -> k.name }) }
        if (stale.isEmpty()) return 0
        context.dataStore.edit { prefs -> stale.forEach { prefs.remove(it) } }
        return stale.size
    }

    /** Dark theme default true. User toggle via icon di header. */
    val darkMode: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[darkModeKey] ?: true }

    /** Lyrics font size in sp. Range 12-56, default 17. */
    val lyricsFontSize: Flow<Int> = context.dataStore.data
        .map { prefs -> clampFontSize(prefs[lyricsFontSizeKey]) }

    /**
     * Manual sync correction. Positive = lyrics dipercepat (kompensasi LRC lambat).
     * Negative = lyrics dilambatkan. Range ±2000 ms.
     */
    val lyricsOffsetMs: Flow<Int> = context.dataStore.data
        .map { prefs -> clampLyricsOffsetMs(prefs[lyricsOffsetMsKey]) }

    /** Extra vertical spacing antar baris lyric (sp). Range 0-20, default 7 = paritas lama. */
    val lineSpacing: Flow<Int> = context.dataStore.data
        .map { prefs -> clampLineSpacing(prefs[lineSpacingKey]) }

    /** High contrast mode — heavier weights, no dim fade. Default false. */
    val highContrast: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[highContrastKey] ?: false }

    /**
     * Auto-scroll behavior. true (default) = animateScrollToItem dengan tween halus.
     * false = scrollToItem snap instan, lebih responsif tapi visually jumpy.
     */
    val smoothScroll: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[smoothScrollKey] ?: true }

    /**
     * Ikut bertanya ke sumber komunitas (AMLL, lrc.red, LRCMux) selain LRCLIB.
     * Default true. Dimatikan, app kembali hanya memakai LRCLIB.
     */
    val communitySources: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[communitySourcesKey] ?: true }

    suspend fun setCommunitySources(enabled: Boolean) {
        context.dataStore.edit { it[communitySourcesKey] = enabled }
    }

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
        context.dataStore.edit { it[lyricsFontSizeKey] = clampFontSize(size) }
    }

    /**
     * Atomic increment/decrement — baca current dan write di transaction yang sama
     * supaya tidak ada race condition kalau user tap A+/A− cepat-cepat.
     * Tanpa ini: read uiState.value (mungkin stale) → write → user tap lagi sebelum
     * state propagate → read stale lagi → effective increment 1× saja walau tap 2×.
     */
    suspend fun bumpLyricsFontSize(delta: Int) {
        context.dataStore.edit { prefs ->
            prefs[lyricsFontSizeKey] = bumpFontSize(prefs[lyricsFontSizeKey], delta)
        }
    }

    suspend fun setLyricsOffsetMs(ms: Int) {
        context.dataStore.edit { it[lyricsOffsetMsKey] = clampLyricsOffsetMs(ms) }
    }

    // ── Offset per perangkat dan per lagu ────────────────────────
    //
    // Key-nya dinamis, satu per perangkat atau lagu, dan HANYA ada selama
    // nilainya bukan nol. Jumlahnya tumbuh sebanyak yang pernah disetel user,
    // bukan sebanyak lagu yang pernah diputar, jadi tidak perlu dibatasi.

    private fun deviceOffsetKey(outputKey: String) = intPreferencesKey("device_offset_$outputKey")

    private fun songOffsetKey(trackId: String) =
        intPreferencesKey("song_offset_${sanitizeKeyPart(trackId)}")

    /** Offset untuk perangkat output dengan kunci [outputKey] (lihat `outputKey`). */
    fun deviceOffsetMs(outputKey: String): Flow<Int> = context.dataStore.data
        .map { prefs -> clampDeviceOffsetMs(prefs[deviceOffsetKey(outputKey)]) }

    suspend fun setDeviceOffsetMs(outputKey: String, ms: Int) {
        context.dataStore.edit { prefs -> prefs.putOrRemove(deviceOffsetKey(outputKey), clampDeviceOffsetMs(ms)) }
    }

    fun songOffsetMs(trackId: String): Flow<Int> = context.dataStore.data
        .map { prefs -> clampSongOffsetMs(prefs[songOffsetKey(trackId)]) }

    suspend fun setSongOffsetMs(trackId: String, ms: Int) {
        context.dataStore.edit { prefs -> prefs.putOrRemove(songOffsetKey(trackId), clampSongOffsetMs(ms)) }
    }

    /** Nol berarti "tidak ada koreksi", jadi key-nya dibuang, bukan disimpan sebagai 0. */
    private fun MutablePreferences.putOrRemove(key: Preferences.Key<Int>, value: Int) {
        if (value == 0) remove(key) else this[key] = value
    }

    suspend fun setLineSpacing(dp: Int) {
        context.dataStore.edit { it[lineSpacingKey] = clampLineSpacing(dp) }
    }

    suspend fun setHighContrast(enabled: Boolean) {
        context.dataStore.edit { it[highContrastKey] = enabled }
    }

    suspend fun setSmoothScroll(enabled: Boolean) {
        context.dataStore.edit { it[smoothScrollKey] = enabled }
    }

    /**
     * Karaoke penuh per suku kata (sapuan, skala, cahaya). Default true.
     * Dimatikan untuk head unit yang terlalu lemah menggambarnya tiap frame.
     */
    val lyricEffects: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[lyricEffectsKey] ?: true }

    suspend fun setLyricEffects(enabled: Boolean) {
        context.dataStore.edit { it[lyricEffectsKey] = enabled }
    }

    suspend fun setHapticEnabled(enabled: Boolean) {
        context.dataStore.edit { it[hapticEnabledKey] = enabled }
    }
}
