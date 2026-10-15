package com.tglabs.spotivibe.domain

import kotlin.math.abs

/**
 * Perlombaan beberapa sumber lirik untuk satu lagu.
 *
 * LRCLIB hanya punya LRC: per baris, kadang per kata tanpa waktu akhir. Sumber
 * komunitas lain (AMLL, lrc.red, LRCMux) sering punya timing per suku kata,
 * vokal latar, dan duet untuk lagu yang sama. Semua sumber ditanya bersamaan,
 * lalu yang timing-nya paling halus menang. Peringkat tetap hanya memecah seri.
 *
 * Semua aturan di sini murni supaya bisa diuji tanpa jaringan.
 */

/**
 * Satu sumber lirik. [rank] makin kecil makin dipercaya saat timing-nya setara.
 *
 * AMLL paling atas karena untuk lagu Spotify dicocokkan lewat ID Spotify yang
 * pasti; lagu dari player lain dicocokkan lewat judul dan artis, sama seperti
 * lrc.red dan LRCMux, dan durasinya tetap dinilai di sini. LRCLIB
 * paling bawah tapi tetap jadi jaring terakhir, dan satu-satunya yang jawaban
 * "tidak ada"-nya dipercaya untuk cache negatif.
 */
enum class ProviderId(val rank: Int) {
    Amll(10),
    LrcRed(20),
    LrcMux(30),
    Lrclib(100),
}

/** Hasil satu sumber. */
sealed interface ProviderProbe {
    /**
     * [probe] mencatat siapa yang menyediakan lirik untuk statistik; untuk
     * LRCLIB juga endpoint mana (`/get` atau `/search`).
     */
    data class Content(
        val result: LyricsResult,
        val durationSec: Double?,
        val probe: ProbeSource,
    ) : ProviderProbe

    /** Sumber menjawab dengan sukses: lagu ini tidak ada di sana. */
    data object Absent : ProviderProbe

    /** Tidak terjangkau atau error sementara. [retryAfterMs] dari header server. */
    data class Failed(val reason: String, val retryAfterMs: Long? = null) : ProviderProbe
}

data class ProviderAnswer(val id: ProviderId, val probe: ProviderProbe)

/** Kehalusan timing, makin kecil makin baik. */
enum class TimingTier { Word, Line, Plain }

fun timingTier(result: LyricsResult): TimingTier {
    val synced = result.synced
    return when {
        synced.isNullOrEmpty() -> TimingTier.Plain
        synced.any { it.words.isNotEmpty() } -> TimingTier.Word
        else -> TimingTier.Line
    }
}

private fun ProviderProbe.Content.withinTolerance(targetSec: Double?): Boolean {
    val target = targetSec?.takeIf { it > 0.0 } ?: return true
    // Sumber yang tidak melaporkan durasi dipercaya: AMLL dicocokkan lewat ID
    // Spotify, dan sumber lain sudah menyaring durasinya sendiri.
    val duration = durationSec ?: return true
    return abs(duration - target) <= SYNC_DURATION_TOLERANCE_SEC
}

/**
 * Jawaban berisi terbaik, atau null kalau belum ada satu pun.
 *
 * Kunci urutan: dalam toleransi durasi lebih dulu (versi lain lagunya punya
 * timing yang meleset), lalu kehalusan timing, lalu peringkat sumber.
 */
fun pickProviderWinner(answers: List<ProviderAnswer>, targetSec: Double?): ProviderAnswer? =
    answers
        .filter { (it.probe as? ProviderProbe.Content)?.result?.hasContent == true }
        .sortedWith(
            compareBy(
                { if ((it.probe as ProviderProbe.Content).withinTolerance(targetSec)) 0 else 1 },
                { timingTier((it.probe as ProviderProbe.Content).result).ordinal },
                { it.id.rank },
            ),
        )
        .firstOrNull()

/**
 * Perlombaan boleh berhenti sekarang: semua sudah menjawab, atau pemenang
 * sementara sudah per kata dalam toleransi DAN tidak ada sumber berperingkat
 * lebih tinggi yang masih berjalan. Sumber yang masih berjalan dengan peringkat
 * lebih rendah tidak mungkin mengalahkannya: timing-nya paling banter setara,
 * dan seri dimenangkan peringkat.
 */
fun isRaceSettled(answers: List<ProviderAnswer>, pending: Set<ProviderId>, targetSec: Double?): Boolean {
    if (pending.isEmpty()) return true
    val winner = pickProviderWinner(answers, targetSec) ?: return false
    val content = winner.probe as ProviderProbe.Content
    if (!content.withinTolerance(targetSec) || timingTier(content.result) != TimingTier.Word) return false
    return pending.none { it.rank < winner.id.rank }
}

/**
 * Satu keputusan dari semua jawaban yang terkumpul.
 *
 * - Ada konten: pemenang [pickProviderWinner].
 * - Tidak ada konten, LRCLIB menjawab "tidak ada": [LyricsLookup.NotFound],
 *   boleh di-cache negatif. Kegagalan sumber komunitas TIDAK membatalkan ini:
 *   mereka sering tidak terjangkau, dan menganggap itu "belum tahu" berarti
 *   lagu tanpa lirik ditanyakan ulang setiap kali diputar.
 * - Tanpa LRCLIB di perlombaan, NotFound hanya kalau semuanya Absent.
 * - Selebihnya [LyricsLookup.Unavailable], tidak di-cache.
 */
fun combineProviderAnswers(answers: List<ProviderAnswer>, targetSec: Double?): LyricsLookup {
    pickProviderWinner(answers, targetSec)?.let { winner ->
        val content = winner.probe as ProviderProbe.Content
        return LyricsLookup.Found(content.result, content.probe)
    }
    val lrclib = answers.firstOrNull { it.id == ProviderId.Lrclib }?.probe
    val notFound = when (lrclib) {
        null -> answers.isNotEmpty() && answers.all { it.probe is ProviderProbe.Absent }
        else -> lrclib is ProviderProbe.Absent
    }
    if (notFound) return LyricsLookup.NotFound
    val reason = (lrclib as? ProviderProbe.Failed)?.reason
        ?: answers.firstNotNullOfOrNull { (it.probe as? ProviderProbe.Failed)?.reason }
        ?: "no_answer"
    return LyricsLookup.Unavailable(reason)
}
