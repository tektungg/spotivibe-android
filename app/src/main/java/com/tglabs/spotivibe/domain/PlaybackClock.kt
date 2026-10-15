package com.tglabs.spotivibe.domain

import androidx.compose.runtime.Immutable
import kotlin.math.abs

/**
 * Jam posisi lagu yang mengoreksi laporan MediaSession player.
 *
 * [positionNowMs] saja percaya laporan apa adanya, dan beberapa player
 * melaporkan dengan kebiasaan yang membuat lirik mundur atau berhenti:
 *
 * - **Pause basi.** YouTube Music melaporkan posisi pause sekitar 1,3 s di
 *   BELAKANG posisi audio sebenarnya, lalu terus menghitung dari angka basi itu
 *   setelah resume. Tanpa koreksi, setiap pause memundurkan baris aktif satu
 *   baris atau lebih, dan setelah resume lirik tertinggal terus.
 * - **Jeda kecil saat pause.** Laporan pause yang sedikit di belakang jam
 *   (efek fade-out) membuat baris berkedip mundur.
 * - **Buffering di akhir lagu.** Spotify melaporkan buffering menjelang akhir
 *   lagu saat menyiapkan lagu berikutnya, padahal audio masih jalan.
 * - **Jitter.** Laporan yang berbeda beberapa milidetik dari jam tidak perlu
 *   menggeser apa pun.
 *
 * Satu instance per sesi player; dibuat ulang saat Spotivibe pindah player.
 * Waktu memakai basis `SystemClock.elapsedRealtime()` dan di-inject, jadi
 * semua aturan diuji di JVM. TIDAK thread-safe: dipakai dari main handler.
 */
class PlaybackClock {

    private var trackId: String? = null
    private var anchorPositionMs = 0L
    private var anchorAtMs = 0L
    private var speed = 1f
    private var durationMs = 0L
    private var running = false

    /** Ditambahkan ke laporan player setelah resume dari pause basi. */
    private var biasMs = 0L

    /** Pause basi yang menunggu resume: posisi mentah yang dilaporkan, dan bias yang dibutuhkan. */
    private var pendingBias: PendingBias? = null

    private data class PendingBias(val reportedPauseMs: Long, val biasMs: Long)

    /** Posisi jam pada [nowMs], dijepit ke 0..durasi. */
    fun positionAt(nowMs: Long): Long {
        val base = if (running) {
            anchorPositionMs + ((nowMs - anchorAtMs).coerceAtLeast(0L) * speed).toLong()
        } else {
            anchorPositionMs
        }
        val clamped = base.coerceAtLeast(0L)
        return if (durationMs > 0L) clamped.coerceAtMost(durationMs) else clamped
    }

    /** Masukkan satu laporan player, dapatkan posisi dan status yang dipakai lirik. */
    fun onReport(snapshot: MediaSnapshot, nowMs: Long): ClockReading {
        val id = trackIdFor(snapshot)
        if (id != trackId) startTrack(id, snapshot, nowMs) else update(snapshot, nowMs)
        return ClockReading(positionMs = positionAt(nowMs), isPaused = !running)
    }

    /**
     * Seek yang dikirim Spotivibe sendiri: jam langsung pindah supaya lirik
     * tidak menunggu laporan player. Kalau player menolaknya, laporan
     * berikutnya mengembalikan jam lewat aturan lompatan.
     */
    fun onSeek(targetMs: Long, nowMs: Long) {
        anchor(targetMs, nowMs)
        biasMs = 0L
        pendingBias = null
    }

    private fun startTrack(id: String, snapshot: MediaSnapshot, nowMs: Long) {
        trackId = id
        biasMs = 0L
        pendingBias = null
        speed = snapshot.speed
        durationMs = snapshot.durationMs
        running = snapshot.state == PlayerState.Playing
        anchor(positionNowMs(snapshot, nowMs), nowMs)
    }

    private fun update(snapshot: MediaSnapshot, nowMs: Long) {
        // Posisi lama dihitung dengan kecepatan lama, baru kecepatan baru dipakai.
        val clockNow = positionAt(nowMs)
        speed = snapshot.speed
        durationMs = snapshot.durationMs
        anchor(clockNow, nowMs)
        val raw = positionNowMs(snapshot, nowMs)
        val wantRunning = shouldRun(snapshot.state, clockNow)
        when {
            running && !wantRunning -> pause(raw, nowMs)
            !running && wantRunning -> resume(raw, nowMs)
            running -> follow(raw, nowMs)
            else -> followPaused(raw, nowMs)
        }
    }

    /**
     * Keadaan sementara (buffering, pindah lagu) membekukan jam, karena audio
     * memang berhenti, KECUALI di [END_BUFFER_MS] terakhir lagu: di sana Spotify
     * melapor buffering untuk lagu berikutnya sementara audio masih jalan.
     */
    private fun shouldRun(state: PlayerState, clockNowMs: Long): Boolean = when (state) {
        PlayerState.Playing -> true
        PlayerState.Transient -> running && isNearEnd(clockNowMs)
        PlayerState.Paused, PlayerState.Stopped, PlayerState.Error, PlayerState.None -> false
    }

    private fun isNearEnd(clockNowMs: Long): Boolean =
        durationMs > 0L && durationMs - clockNowMs <= END_BUFFER_MS

    /**
     * Pause. Laporan sedikit di belakang jam diabaikan; laporan yang jauh di
     * belakang (pause basi) juga diabaikan tapi selisihnya dicatat, karena
     * player akan melanjutkan dari angka basi itu. Laporan di depan jam atau
     * terlalu jauh di belakang dipercaya: itu seek, bukan kebiasaan player.
     */
    private fun pause(raw: Long, nowMs: Long) {
        val clockNow = positionAt(nowMs)
        val behind = clockNow - (raw + biasMs)
        running = false
        when {
            behind in 0L..SMALL_PAUSE_LAG_MS -> anchor(clockNow, nowMs)
            behind in SMALL_PAUSE_LAG_MS..STALE_PAUSE_MAX_MS -> {
                anchor(clockNow, nowMs)
                pendingBias = PendingBias(reportedPauseMs = raw, biasMs = biasMs + behind)
            }
            else -> anchor(raw + biasMs, nowMs)
        }
    }

    /**
     * Resume. Kalau player melanjutkan dari posisi pause basinya sendiri, bias
     * yang dicatat saat pause mulai berlaku. Resume dari posisi lain berarti
     * user seek selama pause: bias dibuang.
     */
    private fun resume(raw: Long, nowMs: Long) {
        val pending = pendingBias
        biasMs = if (pending != null && abs(raw - pending.reportedPauseMs) <= RESUME_MATCH_MS) pending.biasMs else 0L
        pendingBias = null
        running = true
        val adjusted = raw + biasMs
        anchor(if (abs(adjusted - anchorPositionMs) <= SMALL_PAUSE_LAG_MS) anchorPositionMs else adjusted, nowMs)
    }

    /** Memutar: jitter kecil diabaikan, drift diikuti, lompatan besar berarti seek. */
    private fun follow(raw: Long, nowMs: Long) {
        val clockNow = positionAt(nowMs)
        val drift = (raw + biasMs) - clockNow
        when {
            abs(drift) >= SEEK_JUMP_MS -> {
                biasMs = 0L
                anchor(raw, nowMs)
            }
            abs(drift) >= DRIFT_MS -> anchor(raw + biasMs, nowMs)
        }
    }

    /** Diam: hanya seek selama pause (lompatan besar) yang menggeser jam. */
    private fun followPaused(raw: Long, nowMs: Long) {
        if (abs((raw + biasMs) - anchorPositionMs) < SEEK_JUMP_MS) return
        biasMs = 0L
        pendingBias = null
        anchor(raw, nowMs)
    }

    private fun anchor(positionMs: Long, nowMs: Long) {
        anchorPositionMs = positionMs.coerceAtLeast(0L)
        anchorAtMs = nowMs
    }

    private companion object {
        /** Selisih laporan yang diikuti saat memutar. Di bawah ini cuma jitter. */
        const val DRIFT_MS = 15L

        /** Selisih yang pasti seek, bukan drift. */
        const val SEEK_JUMP_MS = 1_000L

        /** Laporan pause sejauh ini di belakang jam adalah efek fade, bukan posisi baru. */
        const val SMALL_PAUSE_LAG_MS = 150L

        /** Pause basi terjauh yang masih dianggap kebiasaan player. */
        const val STALE_PAUSE_MAX_MS = 5_000L

        /** Resume dianggap melanjutkan pause basi kalau mulai sedekat ini dari posisinya. */
        const val RESUME_MATCH_MS = 50L

        /** Buffering sedekat ini dengan akhir lagu tidak membekukan jam. */
        const val END_BUFFER_MS = 2_000L
    }
}

/** Hasil jam untuk satu saat. */
@Immutable
data class ClockReading(val positionMs: Long, val isPaused: Boolean)
