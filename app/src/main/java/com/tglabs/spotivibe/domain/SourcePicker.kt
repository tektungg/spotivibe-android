package com.tglabs.spotivibe.domain

import androidx.compose.runtime.Immutable

/**
 * Pemilih sumber lirik per lagu: setiap sumber yang aktif, apa yang ia punya
 * untuk lagu ini, dan apa yang terjadi kalau dipilih.
 *
 * Perlombaan memilih otomatis, tapi pilihannya bisa salah selera: LRCLIB per
 * baris yang pas mungkin lebih disukai daripada versi per kata dengan timing
 * meleset, atau sumber yang kalah cepat justru punya duet. Di sini user melihat
 * semuanya dan memilih sendiri.
 */

/** Yang terjadi saat baris sebuah sumber diketuk. */
enum class SourceAction {
    /** Sumber ini yang liriknya sedang dipakai. */
    InUse,

    /** Liriknya sudah ada: pakai. */
    Use,

    /** Belum ditanya, gagal, atau dijeda: tanya sekarang, lalu pakai kalau ada. */
    Ask,

    /** Sumber ini sudah menjawab "tidak ada". */
    None,
}

@Immutable
data class SourcePickerModel(
    val entries: List<SourceEntry> = emptyList(),
    /** Sumber yang liriknya sedang tampil, null kalau tidak ada yang cocok. */
    val inUse: ProviderId? = null,
    /** Sumber yang sedang ditanya atas permintaan user. */
    val asking: ProviderId? = null,
)

/**
 * Baris pemilih untuk lagu [trackId]: setiap sumber yang AKTIF, dengan
 * jawabannya dari [report] kalau ada. Sumber yang belum pernah ditanya untuk
 * lagu ini (lagunya dijawab cache, atau perlombaan selesai lebih dulu) tampil
 * sebagai [SourceOutcome.NotAsked], jadi tetap bisa dipilih.
 *
 * "Sedang dipakai" dicocokkan dengan lirik yang benar-benar tampil
 * ([showing]), bukan dengan pemenang perlombaan: setelah user memilih sumber
 * lain, pemenang perlombaan sudah bukan yang tampil lagi.
 */
fun sourcePickerModel(
    report: SourceReport?,
    trackId: String,
    communitySources: Boolean,
    asking: ProviderId?,
    showing: LyricsResult?,
): SourcePickerModel {
    val usable = report?.takeIf { it.trackId == trackId }
    val enabled = ProviderId.entries.filter { it == ProviderId.Lrclib || communitySources }
    val byId = usable?.entries.orEmpty().associateBy { it.id }
    val entries = enabled.sortedBy { it.rank }.map { id -> byId[id] ?: SourceEntry(id, SourceOutcome.NotAsked) }
    val inUse = showing?.let { s -> entries.firstOrNull { it.result?.sameLyricsAs(s) == true }?.id }
    return SourcePickerModel(entries, inUse, asking)
}

/** Isi lirik sama, lepas dari trackId yang dipasang ke override. */
private fun LyricsResult.sameLyricsAs(other: LyricsResult): Boolean =
    synced == other.synced && plain == other.plain

fun sourceAction(entry: SourceEntry, inUse: ProviderId?): SourceAction = when {
    entry.result != null && entry.id == inUse -> SourceAction.InUse
    entry.result != null -> SourceAction.Use
    entry.outcome == SourceOutcome.NotFound -> SourceAction.None
    else -> SourceAction.Ask
}

/** Status satu sumber dalam kata-kata user, misalnya "Word-synced · 3:20". */
fun sourceStatusLabel(entry: SourceEntry): String = when (val o = entry.outcome) {
    is SourceOutcome.Found -> tierLabel(o.tier) + (entry.durationSec?.let { " · ${formatDuration(it.toInt())}" } ?: "")
    SourceOutcome.NotFound -> "No lyrics"
    is SourceOutcome.Failed -> "Failed · ${describeFailure(o.reason)}"
    SourceOutcome.Skipped -> "Paused after errors"
    SourceOutcome.NotAsked -> "Not asked yet"
}

private fun tierLabel(tier: TimingTier): String = when (tier) {
    TimingTier.Word -> "Word-synced"
    TimingTier.Line -> "Line-synced"
    TimingTier.Plain -> "Plain text"
}

/** Sumber selain LRCLIB tidak punya id LRCLIB; override menyimpan ini sebagai penanda. */
const val NO_LRCLIB_ID = 0L
