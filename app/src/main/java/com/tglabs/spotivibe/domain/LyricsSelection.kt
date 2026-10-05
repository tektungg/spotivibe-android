package com.tglabs.spotivibe.domain

import kotlin.math.abs

/**
 * Memilih lirik terbaik dari beberapa kandidat LRCLIB.
 *
 * Dulu pemilihannya urutan kaku: konten `/get` selalu menang, walau cuma plain
 * lyrics, dan dari `/search` diambil item synced PERTAMA tanpa melihat
 * durasinya. Akibatnya plain lyrics menang atas versi synced yang ada, dan
 * versi live/extended bisa terpilih sehingga timing setiap baris meleset.
 *
 * Sekarang: synced dulu, durasi paling mirip, dalam batas toleransi.
 */

/**
 * Selisih durasi terbesar yang masih dianggap "lagu yang sama" untuk lirik synced.
 *
 * Synced dari versi lain (radio edit, live, extended) punya timestamp yang
 * meleset di sepanjang lagu, jadi plain yang durasinya pas lebih jujur daripada
 * synced yang salah waktu. `/api/get` LRCLIB sendiri hanya menoleransi ±2 s,
 * dan beda rilis album vs single biasanya ≤ 3 s; 5 s memberi ruang sedikit di
 * atas itu tanpa menerima edit yang berbeda.
 */
const val SYNC_DURATION_TOLERANCE_SEC = 5.0

/** Satu kandidat lirik beserta asal dan durasinya menurut LRCLIB. */
data class LyricsCandidate(
    val result: LyricsResult,
    /** Durasi versi ini menurut LRCLIB, detik. Null kalau tidak dilaporkan. */
    val durationSec: Double?,
    val source: ProbeSource,
)

private val LyricsCandidate.isSynced: Boolean get() = !result.synced.isNullOrEmpty()

/**
 * Kandidat terbaik untuk lagu berdurasi [targetSec] detik, atau null kalau
 * tidak ada kandidat yang berisi.
 *
 * Kunci urutan, makin kecil makin baik:
 * 1. Tier: synced dalam toleransi (0), plain dalam toleransi (1), synced di
 *    luar toleransi (2), plain di luar toleransi (3).
 * 2. Selisih durasi.
 * 3. Sumber: `/get` sebelum `/search`, karena `/get` juga dicocokkan dengan album.
 * 4. Urutan asli (`sortedWith` stabil).
 *
 * Durasi kandidat yang tidak dilaporkan dianggap di luar toleransi: kita tidak
 * bisa menjamin timing-nya. Kalau durasi LAGU yang tidak diketahui ([targetSec]
 * null atau ≤ 0), durasi tidak bisa dibandingkan sama sekali, jadi semua
 * dianggap dalam toleransi dan yang menentukan hanya synced lalu sumber.
 */
fun pickBestCandidate(candidates: List<LyricsCandidate>, targetSec: Double?): LyricsCandidate? {
    val target = targetSec?.takeIf { it > 0.0 }

    fun selisih(c: LyricsCandidate): Double = when {
        target == null -> 0.0
        c.durationSec == null -> Double.MAX_VALUE
        else -> abs(c.durationSec - target)
    }

    fun tier(c: LyricsCandidate): Int {
        val dalamToleransi = selisih(c) <= SYNC_DURATION_TOLERANCE_SEC
        return when {
            c.isSynced && dalamToleransi -> 0
            dalamToleransi -> 1
            c.isSynced -> 2
            else -> 3
        }
    }

    return candidates
        .filter { it.result.hasContent }
        .sortedWith(
            compareBy<LyricsCandidate>({ tier(it) }, { selisih(it) }, { it.source.ordinal }),
        )
        .firstOrNull()
}

/**
 * `/get` sudah tidak mungkin dikalahkan secara berarti: synced dan durasinya
 * nyaris persis. Dipakai untuk jalur cepat supaya tidak menunggu `/search`.
 */
fun isUnbeatable(candidate: LyricsCandidate, targetSec: Double?): Boolean {
    if (candidate.result.synced.isNullOrEmpty()) return false
    val target = targetSec?.takeIf { it > 0.0 } ?: return false
    val durasi = candidate.durationSec ?: return false
    return abs(durasi - target) <= UNBEATABLE_DELTA_SEC
}

private const val UNBEATABLE_DELTA_SEC = 1.0

/**
 * Versi aturan pemilihan lirik yang tercatat di setiap entri cache disk.
 *
 * Naikkan setiap kali [pickBestCandidate] berubah. Entri dengan versi lebih
 * lama diperlakukan basi oleh `LyricsCache.get`: saat online lagunya dipilih
 * ulang dengan aturan baru, saat offline entri lama tetap tersaji sebagai
 * cadangan. Tanpa ini, lirik pilihan aturan lama bertahan sampai 30 hari.
 *
 * 1 = synced dulu, durasi paling mirip (0.8.1). Entri tanpa field ini (0)
 * dipilih dengan aturan lama: konten `/get` selalu menang.
 */
const val LYRICS_SELECTION_VERSION = 1

fun isSelectionOutdated(selectionVersion: Int): Boolean = selectionVersion < LYRICS_SELECTION_VERSION
