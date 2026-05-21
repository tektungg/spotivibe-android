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
)
