package com.tglabs.spotivibe.data.provider

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.tglabs.spotivibe.domain.LyricsResult
import com.tglabs.spotivibe.domain.ParsedTtml
import com.tglabs.spotivibe.domain.plainText

/**
 * Bentuk respons JSON sumber komunitas, direkam dari API aslinya 2026-10-15.
 *
 * Semua field nullable dengan default: API komunitas berubah tanpa versi, dan
 * field yang hilang harus berarti "tidak ada lirik", bukan crash parse.
 */
internal object ProviderJson {
    val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    inline fun <reified T> parse(json: String): T? =
        runCatching { moshi.adapter(T::class.java).fromJson(json) }.getOrNull()
}

// ── AMLL: api.amll.dev ──────────────────────────────────────────

@JsonClass(generateAdapter = false)
internal data class AmllSearchResponse(val data: AmllSearchData? = null)

@JsonClass(generateAdapter = false)
internal data class AmllSearchData(val items: List<AmllItem>? = null)

@JsonClass(generateAdapter = false)
internal data class AmllItem(
    val id: Long? = null,
    val spotifyIds: List<String>? = null,
)

@JsonClass(generateAdapter = false)
internal data class AmllGetResponse(val data: AmllLyricsData? = null)

@JsonClass(generateAdapter = false)
internal data class AmllLyricsData(val lyrics: String? = null)

// ── lrc.red: lrc.red/api/v1 ─────────────────────────────────────

@JsonClass(generateAdapter = false)
internal data class LrcRedSearchResponse(val results: List<LrcRedResult>? = null)

@JsonClass(generateAdapter = false)
internal data class LrcRedResult(
    @param:Json(name = "track_name") val trackName: String? = null,
    @param:Json(name = "artist_name") val artistName: String? = null,
    val duration: Double? = null,
    val lyricsUrl: String? = null,
    @param:Json(name = "timing_type") val timingType: String? = null,
)

// ── LRCMux: api.lrcmux.dev ──────────────────────────────────────

@JsonClass(generateAdapter = false)
internal data class LrcMuxResponse(
    val track: LrcMuxTrack? = null,
    val lines: List<LrcMuxLine>? = null,
)

@JsonClass(generateAdapter = false)
internal data class LrcMuxTrack(val duration: Double? = null)

@JsonClass(generateAdapter = false)
internal data class LrcMuxLine(
    val text: String? = null,
    val start: Long? = null,
    val end: Long? = null,
    val words: List<LrcMuxWord>? = null,
)

@JsonClass(generateAdapter = false)
internal data class LrcMuxWord(
    val text: String? = null,
    val start: Long? = null,
    val end: Long? = null,
)

/** TTML terparse jadi hasil lirik: synced kalau bertimer, teks polos kalau tidak. */
internal fun ParsedTtml.toLyricsResult(trackId: String): LyricsResult = LyricsResult(
    trackId = trackId,
    synced = lines.takeIf { isSynced && it.isNotEmpty() },
    plain = if (isSynced) null else plainText(),
)
