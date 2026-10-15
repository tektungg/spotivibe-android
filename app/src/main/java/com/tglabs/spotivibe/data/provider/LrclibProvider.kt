package com.tglabs.spotivibe.data.provider

import android.util.Log
import com.tglabs.spotivibe.data.LrclibApi
import com.tglabs.spotivibe.data.LrclibDto
import com.tglabs.spotivibe.domain.LrclibProbe
import com.tglabs.spotivibe.domain.LyricsCandidate
import com.tglabs.spotivibe.domain.LyricsLookup
import com.tglabs.spotivibe.domain.LyricsResult
import com.tglabs.spotivibe.domain.ProbeSource
import com.tglabs.spotivibe.domain.ProviderId
import com.tglabs.spotivibe.domain.ProviderProbe
import com.tglabs.spotivibe.domain.combineProbes
import com.tglabs.spotivibe.domain.isUnbeatable
import com.tglabs.spotivibe.domain.parseLrc
import com.tglabs.spotivibe.domain.pickBestCandidate
import com.tglabs.spotivibe.domain.probeFromHttpFailure
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * LRCLIB (lrclib.net) sebagai satu peserta perlombaan.
 *
 * Isinya logika lama `LyricsRepository.probeNetwork` yang dipindah apa adanya:
 * `/get` dan `/search` ditembak paralel, `/get` synced yang durasinya nyaris
 * persis menang lewat jalur cepat, sisanya diputuskan [combineProbes]. Yang
 * baru hanya bentuk keluarannya: [ProviderProbe] yang membawa durasi versi
 * terpilih, supaya perlombaan bisa membandingkannya dengan sumber lain.
 */
class LrclibProvider(private val api: LrclibApi) : LyricsProvider {

    override val id = ProviderId.Lrclib

    override suspend fun lookup(query: TrackQuery): ProviderProbe = coroutineScope {
        val targetSec = query.durationSec
        val getDeferred = async { probeGet(query) }
        val searchDeferred = async { probeSearch(query, targetSec) }

        val getQuick = withTimeoutOrNull(PREFER_GET_TIMEOUT_MS) { getDeferred.await() }
        if (getQuick is LrclibProbe.Content &&
            isUnbeatable(LyricsCandidate(getQuick.result, getQuick.durationSec, ProbeSource.Get), targetSec)
        ) {
            Log.d(TAG, "/get menang (jalur cepat) untuk ${query.trackId}")
            searchDeferred.cancel()
            return@coroutineScope ProviderProbe.Content(getQuick.result, getQuick.durationSec, ProbeSource.Get)
        }
        val getProbe = getQuick ?: getDeferred.await()
        val searchProbe = searchDeferred.await()
        toProviderProbe(getProbe, searchProbe, targetSec).also {
            Log.d(TAG, "Lookup ${query.trackId}: get=$getProbe search=$searchProbe -> $it")
        }
    }

    /**
     * Hit `/api/get`. 404 berarti kombinasi judul + artis + album + durasi
     * memang tidak ada di database, jadi [LrclibProbe.Absent]. Status lain yang
     * tidak sukses bisa berubah, jadi [LrclibProbe.Failed].
     */
    private suspend fun probeGet(query: TrackQuery): LrclibProbe = lrclibCatching("get") {
        val resp = api.get(
            trackName = query.title,
            artistName = query.artist,
            albumName = query.album,
            duration = query.durationWholeSec,
        )
        if (!resp.isSuccessful) return@lrclibCatching probeFromHttpFailure(resp.code())
        val dto = resp.body() ?: return@lrclibCatching LrclibProbe.Absent
        dto.toLyricsResult(query.trackId).asProbe(dto.duration)
    }

    /**
     * Hit `/api/search`. Array kosong adalah jawaban sungguhan "tidak ada".
     * Dari semua item dipilih lewat [pickBestCandidate]: synced dulu, durasi
     * paling mirip dengan [targetSec].
     */
    private suspend fun probeSearch(query: TrackQuery, targetSec: Double?): LrclibProbe =
        lrclibCatching("search") {
            val resp = api.search(trackName = query.title, artistName = query.artist)
            if (!resp.isSuccessful) return@lrclibCatching probeFromHttpFailure(resp.code())
            val items = resp.body().orEmpty()
            if (items.isEmpty()) return@lrclibCatching LrclibProbe.Absent
            val kandidat = items.map { dto ->
                LyricsCandidate(dto.toLyricsResult(query.trackId), dto.duration, ProbeSource.Search)
            }
            val pick = pickBestCandidate(kandidat, targetSec) ?: return@lrclibCatching LrclibProbe.Absent
            LrclibProbe.Content(pick.result, pick.durationSec)
        }

    private companion object {
        const val TAG = "LrclibProvider"

        /** Berapa lama `/get` ditunggu sendirian sebelum `/search` ikut menentukan. */
        const val PREFER_GET_TIMEOUT_MS = 1500L
    }
}

/**
 * Keputusan [combineProbes] dalam bentuk [ProviderProbe], dengan durasi versi
 * yang menang. Durasi itu yang dipakai perlombaan untuk menilai apakah lirik
 * LRCLIB milik versi lagu yang sama.
 */
internal fun toProviderProbe(get: LrclibProbe, search: LrclibProbe, targetSec: Double?): ProviderProbe =
    when (val lookup = combineProbes(get, search, targetSec)) {
        is LyricsLookup.Found -> {
            val from = if (lookup.probe == ProbeSource.Get) get else search
            ProviderProbe.Content(lookup.result, (from as? LrclibProbe.Content)?.durationSec, lookup.probe)
        }
        LyricsLookup.NotFound -> ProviderProbe.Absent
        is LyricsLookup.Unavailable -> ProviderProbe.Failed(lookup.reason)
    }

/** IOException, timeout, parse error: semuanya sementara. Pembatalan dilempar ulang. */
private suspend fun lrclibCatching(label: String, block: suspend () -> LrclibProbe): LrclibProbe =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Log.w("LrclibProvider", "/$label error: ${t.message}")
        LrclibProbe.Failed(t.message ?: "$label gagal")
    }

/** Hasil tanpa isi sama artinya dengan "tidak ada", bukan konten kosong. */
private fun LyricsResult.asProbe(durationSec: Double?): LrclibProbe =
    if (hasContent) LrclibProbe.Content(this, durationSec) else LrclibProbe.Absent

internal fun LrclibDto.toLyricsResult(trackId: String): LyricsResult {
    val synced = syncedLyrics?.takeIf { it.isNotBlank() }?.let { parseLrc(it) }
    val plain = plainLyrics?.takeIf { it.isNotBlank() }
    return LyricsResult(
        trackId = trackId,
        synced = synced?.takeIf { it.isNotEmpty() },
        plain = plain,
    )
}
