package com.tglabs.spotivibe.data.provider

import com.tglabs.spotivibe.domain.ProbeSource
import com.tglabs.spotivibe.domain.ProviderId
import com.tglabs.spotivibe.domain.ProviderProbe
import com.tglabs.spotivibe.domain.SYNC_DURATION_TOLERANCE_SEC
import com.tglabs.spotivibe.domain.artistsMatch
import com.tglabs.spotivibe.domain.parseTtml
import com.tglabs.spotivibe.domain.titlesMatch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import kotlin.math.abs

/**
 * lrc.red: TTML per ISRC, banyak yang per kata.
 *
 * Endpoint pencariannya (`/api/v1`) juga yang dipakai BiniLyrics: API itu cuma
 * me-redirect 307 ke sini. Memanggilnya langsung menghemat satu lompatan.
 */
class LrcRedProvider(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl = BASE_URL.toHttpUrl(),
) : LyricsProvider {

    override val id = ProviderId.LrcRed

    override suspend fun lookup(query: TrackQuery): ProviderProbe = probeCatching("lrcred") {
        val search = client.fetch(
            baseUrl.newBuilder().addPathSegments("api/v1")
                .addQueryParameter("track", query.title)
                .addQueryParameter("artist", query.artist)
                .addQueryParameter("duration", query.durationWholeSec.toString())
                .build(),
        )
        if (!search.isSuccessful) return@probeCatching search.failureProbe()
        val pick = pickLrcRedResult(search.body, query) ?: return@probeCatching ProviderProbe.Absent
        val url = pick.lyricsUrl?.let(::allowedLrcRedUrl) ?: return@probeCatching ProviderProbe.Absent

        val doc = client.fetch(url)
        if (!doc.isSuccessful) return@probeCatching doc.failureProbe()
        val parsed = parseTtml(doc.body) ?: return@probeCatching ProviderProbe.Absent
        val result = parsed.toLyricsResult(query.trackId)
        if (!result.hasContent) return@probeCatching ProviderProbe.Absent
        ProviderProbe.Content(result, pick.duration ?: parsed.durationMs?.let { it / MS_PER_SECOND }, ProbeSource.LrcRed)
    }

    private companion object {
        const val BASE_URL = "https://lrc.red/"
        const val MS_PER_SECOND = 1000.0
    }
}

/**
 * Hasil terbaik untuk [query]: judul dan artis cocok, durasi dalam toleransi,
 * lalu timing per kata didahulukan, lalu durasi paling mirip. Hasil pencarian
 * memuat remix, versi kompilasi, dan cover oleh artis lain.
 */
internal fun pickLrcRedResult(searchJson: String, query: TrackQuery): LrcRedResult? {
    val target = query.durationSec
    return ProviderJson.parse<LrcRedSearchResponse>(searchJson)?.results.orEmpty()
        .filter { titlesMatch(query.title, it.trackName.orEmpty()) }
        .filter { artistsMatch(query.artist, it.artistName.orEmpty()) }
        .filter { r -> target == null || r.duration?.let { abs(it - target) <= SYNC_DURATION_TOLERANCE_SEC } == true }
        .sortedWith(
            compareBy(
                { if (it.timingType == TIMING_WORD) 0 else 1 },
                { r -> if (target == null) 0.0 else abs((r.duration ?: 0.0) - target) },
            ),
        )
        .firstOrNull()
}

/**
 * URL lirik dari hasil pencarian, HANYA kalau https dan milik lrc.red. URL itu
 * datang dari respons server pihak ketiga; tanpa allowlist, respons yang
 * dimanipulasi bisa membuat app mengambil alamat mana pun.
 */
internal fun allowedLrcRedUrl(raw: String): HttpUrl? {
    val url = raw.toHttpUrlOrNull() ?: return null
    if (!url.isHttps) return null
    return url.takeIf { it.host == LRC_RED_HOST }
}

private const val TIMING_WORD = "word"
private const val LRC_RED_HOST = "lrc.red"
