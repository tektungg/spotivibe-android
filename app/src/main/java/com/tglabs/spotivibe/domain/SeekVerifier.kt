package com.tglabs.spotivibe.domain

/**
 * Memastikan player benar-benar menjalankan seek yang dikirim Spotivibe.
 *
 * Sebagian player mengiklankan `ACTION_SEEK_TO` tapi diam-diam mengabaikannya:
 * Spotify Free melakukan itu. Tanpa pengecekan, tap baris lirik terlihat
 * seperti tidak bekerja sama sekali, dan user tidak tahu yang menolak adalah
 * player, bukan Spotivibe.
 *
 * Seek dianggap jalan kalau dalam [CONFIRM_WINDOW_MS] player melaporkan posisi
 * di sekitar target, memperhitungkan waktu yang lewat sejak seek dikirim.
 * Kalau tenggatnya lewat tanpa laporan seperti itu, seek dianggap ditolak.
 *
 * Murni dan diuji di JVM; waktu berbasis `elapsedRealtime` di-inject.
 */
class SeekVerifier {

    private data class Pending(val targetMs: Long, val sentAtMs: Long)

    private var pending: Pending? = null

    fun onSeek(targetMs: Long, nowMs: Long) {
        pending = Pending(targetMs, nowMs)
    }

    /** Laporan posisi mentah player. Mengembalikan hasil kalau seek sudah bisa diputuskan. */
    fun onReport(reportedMs: Long, nowMs: Long): SeekOutcome? {
        val p = pending ?: return null
        val elapsed = (nowMs - p.sentAtMs).coerceAtLeast(0L)
        val arrived = reportedMs in (p.targetMs - TOLERANCE_MS)..(p.targetMs + elapsed + TOLERANCE_MS)
        return when {
            arrived -> SeekOutcome.Confirmed.also { pending = null }
            elapsed >= CONFIRM_WINDOW_MS -> SeekOutcome.Refused.also { pending = null }
            else -> null
        }
    }

    /** Dipanggil saat tenggat habis tanpa laporan baru. */
    fun onDeadline(nowMs: Long): SeekOutcome? {
        val p = pending ?: return null
        if (nowMs - p.sentAtMs < CONFIRM_WINDOW_MS) return null
        pending = null
        return SeekOutcome.Refused
    }

    /** Lupakan seek yang menunggu, misalnya saat lagu atau player berganti. */
    fun reset() {
        pending = null
    }

    companion object {
        /** Batas tunggu laporan setelah seek. */
        const val CONFIRM_WINDOW_MS = 1_500L

        /** Jarak dari target yang masih dianggap sampai. */
        const val TOLERANCE_MS = 750L
    }
}

enum class SeekOutcome { Confirmed, Refused }
