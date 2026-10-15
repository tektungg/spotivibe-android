package com.tglabs.spotivibe.data.provider

import com.tglabs.spotivibe.domain.ProbeSource
import com.tglabs.spotivibe.domain.ProviderId
import com.tglabs.spotivibe.domain.ProviderProbe
import com.tglabs.spotivibe.domain.artistsMatch
import com.tglabs.spotivibe.domain.titlesMatch
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
 * Karena itu lagu Spotify HANYA diterima kalau item mencantumkan ID Spotify
 * lagu yang sedang diputar. Lagu dari player lain tidak punya ID Spotify, jadi
 * dicocokkan lewat judul dan artis ([titlesMatch], [artistsMatch]); durasinya
 * dinilai perlombaan dari `<body dur>` TTML.
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
        val id = pickAmllId(search.body, query) ?: return@probeCatching ProviderProbe.Absent

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

/**
 * ID item AMLL untuk [query], atau null. Lagu Spotify: item yang mencantumkan
 * ID Spotify-nya. Lagu lain: item pertama yang judul DAN artisnya cocok.
 */
internal fun pickAmllId(searchJson: String, query: TrackQuery): Long? {
    val items = ProviderJson.parse<AmllSearchResponse>(searchJson)?.data?.items.orEmpty()
    val spotifyId = query.spotifyId
    if (spotifyId != null) return items.firstOrNull { spotifyId in it.spotifyIds.orEmpty() }?.id
    return items.firstOrNull { item ->
        item.musicNames.orEmpty().any { titlesMatch(query.title, it) } &&
            item.artistNames.orEmpty().any { artistsMatch(query.artist, it) }
    }?.id
}

/** Respons `/v1/lyrics/get` jadi probe. TTML rusak berarti tidak ada lirik yang bisa dipakai. */
internal fun amllProbe(getJson: String, trackId: String): ProviderProbe {
    val ttml = ProviderJson.parse<AmllGetResponse>(getJson)?.data?.lyrics ?: return ProviderProbe.Absent
    val parsed = parseTtml(ttml) ?: return ProviderProbe.Absent
    val result = parsed.toLyricsResult(trackId)
    if (!result.hasContent) return ProviderProbe.Absent
    return ProviderProbe.Content(result, parsed.durationSec, ProbeSource.Amll)
}
