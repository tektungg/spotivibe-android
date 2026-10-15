package com.tglabs.spotivibe.data.provider

import com.tglabs.spotivibe.domain.ProviderId
import com.tglabs.spotivibe.domain.ProviderProbe
import com.tglabs.spotivibe.domain.isSpotifyTrackId

/** Lagu yang dicari liriknya, dalam bentuk yang dipakai semua sumber. */
data class TrackQuery(
    /**
     * Identitas lagu dari `trackIdFor`: `spotify:track:<id>` untuk Spotify,
     * `media:<hash>` untuk player lain. Juga jadi `LyricsResult.trackId`.
     */
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
) {
    /**
     * ID Spotify polos, bagian terakhir URI. Null untuk lagu dari player lain
     * (`media:<hash>`): hash itu bukan ID Spotify dan tidak boleh dicocokkan
     * sebagai ID.
     */
    val spotifyId: String? get() = trackId.takeIf(::isSpotifyTrackId)?.substringAfterLast(':')

    /** Durasi dalam detik, null kalau tidak diketahui. */
    val durationSec: Double? get() = durationMs.takeIf { it > 0 }?.let { it / MS_PER_SECOND }

    /** Durasi dalam detik bulat, bentuk yang diminta parameter query API. */
    val durationWholeSec: Int get() = (durationMs / MS_PER_SECOND_LONG).toInt().coerceAtLeast(0)

    private companion object {
        const val MS_PER_SECOND = 1000.0
        const val MS_PER_SECOND_LONG = 1000L
    }
}

/**
 * Satu sumber lirik.
 *
 * [lookup] tidak boleh melempar untuk kegagalan biasa (jaringan, HTTP, parse):
 * semuanya jadi [ProviderProbe.Failed]. Pembatalan coroutine tetap dilempar,
 * karena perlombaan membatalkan sumber yang kalah.
 */
interface LyricsProvider {
    val id: ProviderId
    suspend fun lookup(query: TrackQuery): ProviderProbe
}
