package com.tglabs.spotivibe.domain

/**
 * Jeda per sumber lirik setelah gagal berturut-turut.
 *
 * Sumber komunitas gratis bisa mati berjam-jam atau membatasi request. Tanpa
 * jeda, setiap ganti lagu tetap menembak sumber yang sedang mati dan menunggu
 * timeout-nya, dan permintaan yang terus datang saat dibatasi justru
 * memperpanjang pembatasannya.
 *
 * Aturan: gagal pertama dibiarkan (gangguan sesaat). Mulai gagal kedua
 * berturut-turut, sumber diistirahatkan 30, 60, 120, lalu 300 detik. Header
 * `Retry-After` dari server menang atas tangga itu kalau ada. Jawaban apa pun
 * yang sukses, termasuk "tidak ada", mereset hitungan.
 *
 * Jam di-inject supaya test bisa memajukan waktu. Aman dipakai dari beberapa
 * coroutine sekaligus: preload antrean dan lagu aktif bisa berlomba bersamaan.
 */
class ProviderCooldown(private val nowMs: () -> Long = System::currentTimeMillis) {

    private data class State(val failures: Int = 0, val until: Long = 0L)

    private val states = mutableMapOf<ProviderId, State>()

    @Synchronized
    fun isCoolingDown(id: ProviderId): Boolean = nowMs() < (states[id]?.until ?: 0L)

    @Synchronized
    fun record(id: ProviderId, probe: ProviderProbe) {
        if (probe !is ProviderProbe.Failed) {
            states.remove(id)
            return
        }
        val failures = (states[id]?.failures ?: 0) + 1
        val pause = probe.retryAfterMs ?: ladderMs(failures)
        states[id] = State(failures = failures, until = nowMs() + pause)
    }

    private fun ladderMs(failures: Int): Long {
        if (failures < FAILURES_BEFORE_PAUSE) return 0L
        val step = (failures - FAILURES_BEFORE_PAUSE).coerceAtMost(LADDER_MS.lastIndex)
        return LADDER_MS[step]
    }

    private companion object {
        const val FAILURES_BEFORE_PAUSE = 2
        val LADDER_MS = longArrayOf(30_000L, 60_000L, 120_000L, 300_000L)
    }
}

/**
 * Nilai header `Retry-After` dalam milidetik. Hanya bentuk detik yang
 * didukung; bentuk tanggal HTTP jarang dipakai API JSON dan diabaikan.
 * Dijepit ke lima menit supaya server yang salah konfigurasi tidak bisa
 * mematikan sebuah sumber berhari-hari.
 */
fun parseRetryAfterMs(header: String?): Long? {
    val seconds = header?.trim()?.toLongOrNull() ?: return null
    if (seconds < 0) return null
    return (seconds * MS_PER_SECOND).coerceAtMost(MAX_RETRY_AFTER_MS)
}

private const val MS_PER_SECOND = 1000L
private const val MAX_RETRY_AFTER_MS = 300_000L
