package com.tglabs.spotivibe.domain

/**
 * Penamaan berkas cache dan masa berlakunya, murni.
 *
 * Keduanya sebelumnya terkubur sebagai method privat di `LyricsCache`, kelas
 * yang memegang `Context` dan menulis ke disk. Dua hal yang paling mudah salah
 * di sana justru tidak bisa disentuh test: nama berkas yang tidak sah membuat
 * cache diam-diam tidak pernah menyimpan apa pun, dan masa berlaku yang tertukar
 * membuat hasil "tidak ketemu" bertahan sebulan.
 */

private const val HARI_MS = 24L * 60L * 60L * 1000L

/** Lirik yang ketemu jarang berubah, jadi boleh bertahan lama. */
const val POSITIVE_TTL_MS = 30L * HARI_MS

/**
 * "Tidak ketemu" bertahan sehari saja.
 *
 * LRCLIB terus bertambah isinya. Menyimpan hasil kosong selama sebulan berarti
 * lagu yang liriknya baru diunggah orang lain tetap tampak tidak punya lirik
 * sampai sebulan berlalu, dan tidak ada cara bagi user untuk memaksanya.
 */
const val NEGATIVE_TTL_MS = 1L * HARI_MS

fun cacheTtlMs(punyaIsi: Boolean): Long = if (punyaIsi) POSITIVE_TTL_MS else NEGATIVE_TTL_MS

fun isCacheExpired(cachedAtMs: Long, nowMs: Long, punyaIsi: Boolean): Boolean =
    nowMs - cachedAtMs > cacheTtlMs(punyaIsi)

/**
 * Nama berkas untuk sebuah track id.
 *
 * Track id Spotify berbentuk `spotify:track:xxxx`, dan titik dua tidak sah di
 * nama berkas pada penyimpanan eksternal maupun pada beberapa filesystem
 * Android. Kegagalannya senyap: berkas tidak pernah terbuat, cache tidak pernah
 * mengenai, dan setiap lagu dijemput ulang dari jaringan tanpa ada yang
 * menyadarinya.
 */
fun cacheFileName(trackId: String): String {
    val aman = trackId.map { c ->
        if (c.isLetterOrDigit() || c == '-' || c == '_') c else '_'
    }.joinToString("")
    return "$aman.json"
}
