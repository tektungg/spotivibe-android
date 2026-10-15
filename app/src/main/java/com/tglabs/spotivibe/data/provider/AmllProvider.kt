package com.tglabs.spotivibe.data.provider

import com.tglabs.spotivibe.domain.ProbeSource
import com.tglabs.spotivibe.domain.ProviderId
import com.tglabs.spotivibe.domain.ProviderProbe
import com.tglabs.spotivibe.domain.durationSec
import com.tglabs.spotivibe.domain.parseTtml
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * AMLL TTML DB (api.amll.dev): lirik TTML buatan komunitas, sering per suku
 * kata dengan vokal latar dan duet.
 *
 * Pencariannya fuzzy: untuk "Blinding Lights" item pertamanya "Wasted Times".
 * Karena itu hasil HANYA diterima kalau item mencantumkan ID Spotify lagu yang
 * sedang diputar. Lebih baik tidak dapat lirik dari sini daripada lirik lagu
 * lain dengan timing yang meyakinkan.
 */
class AmllProvider(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl = BASE_URL.toHttpUrl(),
) : LyricsProvider {

    override val id = ProviderId.Amll

    override suspend fun lookup(query: TrackQuery): ProviderProbe = probeCatching("amll") {
        val search = client.fetch(
            baseUrl.newBuilder().addPathSegments("v1/lyrics/search")
                .addQueryParameter("trackName", query.title)
                .addQueryParameter("artistName", query.artist)
                .build(),
        )
        if (!search.isSuccessful) return@probeCatching search.failureProbe()
        val id = pickAmllId(search.body, query.spotifyId) ?: return@probeCatching ProviderProbe.Absent

        val get = client.fetch(
            baseUrl.newBuilder().addPathSegments("v1/lyrics/get")
                .addQueryParameter("id", id.toString())
                .build(),
        )
        if (!get.isSuccessful) return@probeCatching get.failureProbe()
        amllProbe(get.body, query.trackId)
    }

    private companion object {
        const val BASE_URL = "https://api.amll.dev/"
    }
}

/** ID item AMLL yang mencantumkan [spotifyId], atau null. */
internal fun pickAmllId(searchJson: String, spotifyId: String): Long? {
    if (spotifyId.isBlank()) return null
    val items = ProviderJson.parse<AmllSearchResponse>(searchJson)?.data?.items.orEmpty()
    return items.firstOrNull { spotifyId in it.spotifyIds.orEmpty() }?.id
}

/** Respons `/v1/lyrics/get` jadi probe. TTML rusak berarti tidak ada lirik yang bisa dipakai. */
internal fun amllProbe(getJson: String, trackId: String): ProviderProbe {
    val ttml = ProviderJson.parse<AmllGetResponse>(getJson)?.data?.lyrics ?: return ProviderProbe.Absent
    val parsed = parseTtml(ttml) ?: return ProviderProbe.Absent
    val result = parsed.toLyricsResult(trackId)
    if (!result.hasContent) return ProviderProbe.Absent
    return ProviderProbe.Content(result, parsed.durationSec, ProbeSource.Amll)
}
