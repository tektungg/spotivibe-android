package com.tglabs.spotivibe.domain

import androidx.compose.runtime.Immutable

/**
 * Clean domain model untuk track yang sedang diputar.
 * `id` adalah Spotify URI format "spotify:track:xxxxx" — pakai untuk cache key.
 *
 * @Immutable promise ke Compose Compiler — semua field primitive/stable,
 * tidak pernah di-mutate, instance baru tiap track event.
 */
@Immutable
data class NowPlaying(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val imageUri: String?,
    val durationMs: Long,
    val progressMs: Long,
    val isPaused: Boolean,
    /**
     * Wall clock time (System.currentTimeMillis()) saat snapshot ini di-take
     * dari Spotify PlayerState. Dipakai UI untuk extrapolate progress secara
     * benar walau Composable dispose dan re-compose (mis. saat user buka
     * Settings lalu kembali). Tanpa ini, NowPlayingScreen pakai "now" sebagai
     * baseline time pas recomposition → progress reset ke nilai stale Spotify
     * push (sering 0 kalau track baru ganti).
     */
    val capturedAtMs: Long = System.currentTimeMillis(),
) {
    /**
     * Compute current progress dengan extrapolasi dari capturedAtMs.
     * Kalau paused, return progressMs as-is (tidak boleh maju).
     * Kalau playing, tambah elapsed wall clock sejak snapshot.
     */
    fun extrapolatedProgressMs(nowMs: Long = System.currentTimeMillis()): Long {
        if (isPaused) return progressMs
        val elapsed = (nowMs - capturedAtMs).coerceAtLeast(0L)
        return (progressMs + elapsed).coerceAtMost(durationMs)
    }
}
