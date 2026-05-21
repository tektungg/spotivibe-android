package com.tglabs.spotivibe.data

import com.squareup.moshi.JsonClass

/**
 * Response payload dari LRCLIB `/api/get` dan setiap item di `/api/search`.
 *
 * Field names cocok dengan JSON LRCLIB (camelCase) — Moshi reflective adapter
 * akan map otomatis.
 *
 * - [syncedLyrics] berisi LRC format mentah `[mm:ss.xx]text`.
 * - [plainLyrics] adalah lirik biasa tanpa timing. Kadang null kalau cuma
 *   instrumental atau LRCLIB belum punya plain version.
 * - [instrumental] true berarti track instrumental — tidak ada lirik sama sekali.
 */
@JsonClass(generateAdapter = false)
data class LrclibDto(
    val id: Long? = null,
    val name: String? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean? = null,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)
