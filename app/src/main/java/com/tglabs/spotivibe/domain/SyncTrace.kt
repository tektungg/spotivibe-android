package com.tglabs.spotivibe.domain

import androidx.compose.runtime.Immutable

/** Satu kejadian di jejak sync. */
@Immutable
data class TraceEntry(val atMs: Long, val message: String)

/**
 * Beberapa kejadian terakhir di jalur playback dan lirik, untuk debug report.
 *
 * Log Android tidak bisa dibaca dari HP orang lain, dan masalah sync hampir
 * selalu muncul di player atau head unit yang tidak kita punya. Jejak ini
 * menyimpan [capacity] kejadian terakhir di memori (bukan di disk) supaya user
 * bisa menyalinnya ke laporan bug.
 *
 * Waktu dikirim pemanggil (`elapsedRealtime`), jadi kelas ini murni. Aman
 * dipakai dari beberapa thread: source menulis dari main, repository dari IO.
 */
class SyncTrace(private val capacity: Int = DEFAULT_CAPACITY) {

    private val entries = ArrayDeque<TraceEntry>(capacity)

    @Synchronized
    fun record(atMs: Long, message: String) {
        if (entries.size == capacity) entries.removeFirst()
        entries.addLast(TraceEntry(atMs, message))
    }

    /** Salinan, terlama dulu. */
    @Synchronized
    fun snapshot(): List<TraceEntry> = entries.toList()

    private companion object {
        const val DEFAULT_CAPACITY = 50
    }
}
