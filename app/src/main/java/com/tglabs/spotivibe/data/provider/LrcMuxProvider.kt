package com.tglabs.spotivibe.data.provider

import com.tglabs.spotivibe.domain.LyricsResult
import com.tglabs.spotivibe.domain.ProbeSource
import com.tglabs.spotivibe.domain.ProviderId
import com.tglabs.spotivibe.domain.ProviderProbe
import com.tglabs.spotivibe.domain.SyncedLine
import com.tglabs.spotivibe.domain.SyncedWord
import com.tglabs.spotivibe.domain.insertInterludeMarkers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * LRCMux (api.lrcmux.dev): agregator yang mengembalikan JSON per baris dengan
 * kata bertimer mulai DAN akhir. Pencocokannya dikerjakan di server lewat
 * artis, judul, dan durasi.
 */
class LrcMuxProvider(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl = BASE_URL.toHttpUrl(),
) : LyricsProvider {

    override val id = ProviderId.LrcMux

    override suspend fun lookup(query: TrackQuery): ProviderProbe = probeCatching("lrcmux") {
        val res = client.fetch(
            baseUrl.newBuilder().addPathSegment("get")
                .addQueryParameter("artist", query.artist)
                .addQueryParameter("title", query.title)
                .addQueryParameter("duration", query.durationWholeSec.toString())
                .addQueryParameter("format", "json")
                .addQueryParameter("level", "word")
                .build(),
        )
        if (!res.isSuccessful) return@probeCatching res.failureProbe()
        lrcMuxProbe(res.body, query.trackId)
    }

    private companion object {
        const val BASE_URL = "https://api.lrcmux.dev/"
    }
}

/** Respons `/get` jadi probe. */
internal fun lrcMuxProbe(json: String, trackId: String): ProviderProbe {
    val response = ProviderJson.parse<LrcMuxResponse>(json) ?: return ProviderProbe.Absent
    val lines = response.lines.orEmpty().mapNotNull { it.toSyncedLine() }.sortedBy { it.timeMs }
    if (lines.isEmpty()) return ProviderProbe.Absent
    val result = LyricsResult(trackId = trackId, synced = insertInterludeMarkers(lines), plain = null)
    return ProviderProbe.Content(result, response.track?.duration, ProbeSource.LrcMux)
}

/**
 * Teks kata LRCMux sudah membawa spasinya sendiri ("I've ", "been "), sama
 * dengan bentuk [SyncedWord]. Kata tanpa waktu mulai dibuang; baris tanpa
 * waktu mulai atau tanpa teks tidak bisa ditampilkan dan ikut dibuang.
 */
private fun LrcMuxLine.toSyncedLine(): SyncedLine? {
    val start = start ?: return null
    val words = words.orEmpty().mapNotNull { w ->
        val wordStart = w.start ?: return@mapNotNull null
        w.text?.takeIf { it.isNotEmpty() }?.let { SyncedWord(wordStart, it, w.end) }
    }
    val text = text?.trim().orEmpty().ifEmpty { words.joinToString("") { it.text }.trim() }
    if (text.isEmpty()) return null
    return SyncedLine(id = 0, timeMs = start, text = text, words = words, endMs = end)
}
