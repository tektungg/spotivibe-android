package com.tglabs.spotivibe.domain

import androidx.compose.runtime.Immutable

/**
 * Apa yang dijawab setiap sumber lirik untuk satu lagu.
 *
 * Perlombaan hanya mengembalikan pemenang. Tanpa catatan ini, "tidak ada lirik"
 * tidak bisa dibedakan dari "semua sumber menjawab tidak ada" atau "dua sumber
 * tidak terjangkau", dan user tidak bisa memilih versi dari sumber lain.
 * Dipakai oleh pesan lirik, pemilih sumber, dan debug report.
 */

/** Hasil satu sumber dalam satu lookup. */
@Immutable
sealed interface SourceOutcome {
    data class Found(val tier: TimingTier) : SourceOutcome
    data object NotFound : SourceOutcome
    data class Failed(val reason: String) : SourceOutcome

    /** Tidak ditanya karena sumber ini sedang dijeda setelah gagal berturut-turut. */
    data object Skipped : SourceOutcome

    /** Perlombaan sudah selesai sebelum sumber ini menjawab, jadi dibatalkan. */
    data object NotAsked : SourceOutcome
}

@Immutable
data class SourceEntry(
    val id: ProviderId,
    val outcome: SourceOutcome,
    /** Lirik yang didapat, hanya untuk [SourceOutcome.Found]. */
    val result: LyricsResult? = null,
    val durationSec: Double? = null,
)

@Immutable
data class SourceReport(
    val trackId: String,
    /** Urut menurut peringkat sumber. */
    val entries: List<SourceEntry>,
    /** Sumber yang liriknya dipakai, null kalau tidak ada yang berisi. */
    val winner: ProviderId?,
) {
    val failures: List<SourceEntry> get() = entries.filter { it.outcome is SourceOutcome.Failed }
}

/** Nama sumber untuk UI dan laporan. */
val ProviderId.displayName: String
    get() = when (this) {
        ProviderId.Amll -> "AMLL"
        ProviderId.LrcRed -> "lrc.red"
        ProviderId.LrcMux -> "LRCMux"
        ProviderId.Lrclib -> "LRCLIB"
    }

fun ProviderProbe.toOutcome(): SourceOutcome = when (this) {
    is ProviderProbe.Content -> SourceOutcome.Found(timingTier(result))
    ProviderProbe.Absent -> SourceOutcome.NotFound
    is ProviderProbe.Failed -> SourceOutcome.Failed(reason)
}

/**
 * Report dari satu perlombaan. Setiap sumber yang ikut atau dijeda muncul
 * sekali: yang menjawab dengan hasilnya, yang ikut tapi belum menjawab saat
 * perlombaan selesai sebagai [SourceOutcome.NotAsked].
 */
fun sourceReportOf(
    trackId: String,
    answers: List<ProviderAnswer>,
    raced: Collection<ProviderId>,
    skipped: Collection<ProviderId>,
    winner: ProviderId?,
): SourceReport {
    val byId = answers.associateBy { it.id }
    val entries = (raced + skipped).distinct().sortedBy { it.rank }.map { id ->
        val answer = byId[id]
        val content = answer?.probe as? ProviderProbe.Content
        SourceEntry(
            id = id,
            outcome = when {
                answer != null -> answer.probe.toOutcome()
                id in skipped -> SourceOutcome.Skipped
                else -> SourceOutcome.NotAsked
            },
            result = content?.result,
            durationSec = content?.durationSec,
        )
    }
    return SourceReport(trackId, entries, winner)
}

/** Report dengan satu sumber diperbarui, misalnya setelah ditanya ulang oleh user. */
fun SourceReport.with(entry: SourceEntry): SourceReport =
    copy(entries = (entries.filterNot { it.id == entry.id } + entry).sortedBy { it.id.rank })

/**
 * Alasan gagal dalam kata-kata user. Alasan mentahnya pesan exception atau
 * `http_<kode>`; yang berguna bagi user hanya golongannya.
 */
fun describeFailure(reason: String): String {
    val r = reason.lowercase()
    val code = HTTP_CODE.find(r)?.groupValues?.get(1)?.toIntOrNull()
    return when {
        "timeout" in r || "timed out" in r -> "timed out"
        code == HTTP_TOO_MANY_REQUESTS -> "rate limited"
        code != null && code >= HTTP_SERVER_ERROR -> "server error"
        code != null -> "refused the request"
        NO_CONNECTION.any { it in r } -> "no connection"
        else -> "unreachable"
    }
}

/**
 * Pesan pengganti lirik untuk [state], atau null kalau tampilan bawaan sudah
 * tepat (lirik ada, sedang memuat, offline, atau tidak ada report).
 *
 * "Tidak ada lirik" yang disertai sumber gagal tetap jawaban "tidak ada":
 * LRCLIB sudah menjawab pasti. Tapi menyebut yang gagal memberi tahu user bahwa
 * mencoba lagi nanti masuk akal.
 */
fun lyricsNotice(state: LyricsState, report: SourceReport?): String? {
    val failed = report?.failures.orEmpty()
    if (failed.isEmpty()) return null
    val detail = joinNames(failed.map { "${it.id.displayName} (${describeFailure((it.outcome as SourceOutcome.Failed).reason)})" })
    return when (state) {
        LyricsState.NotFound -> "No lyrics found. Couldn't reach $detail."
        is LyricsState.Unavailable -> "Couldn't load lyrics: $detail. Replay the song to try again."
        else -> null
    }
}

/** "A", "A and B", "A, B and C". */
fun joinNames(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> names.single()
    else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
}

private val HTTP_CODE = Regex("""http_(\d{3})""")
private val NO_CONNECTION = listOf("unknownhost", "unable to resolve", "connect", "network", "socket")
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_SERVER_ERROR = 500
