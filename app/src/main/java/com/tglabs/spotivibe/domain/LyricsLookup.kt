package com.tglabs.spotivibe.domain

import androidx.compose.runtime.Immutable

/**
 * Kenapa lirik kosong itu penting, bukan detail.
 *
 * Sebelumnya `LyricsRepository` menelan semua exception jadi satu
 * `LyricsResult` kosong, lalu menyimpannya ke disk dengan TTL negatif 1 hari.
 * Akibatnya: satu lagu diputar saat sinyal hilang, dan lirik lagu itu hilang
 * 24 jam penuh walaupun jaringan sudah balik semenit kemudian. Kegagalan
 * transport disamakan dengan jawaban "memang tidak ada lirik", padahal dua hal
 * itu punya masa berlaku yang sangat berbeda.
 */
sealed interface LyricsState {
    /** Fetch sedang jalan, atau belum mulai. */
    data object Loading : LyricsState

    /** Ada lirik. Dijamin [LyricsResult.hasContent] true. */
    @Immutable
    data class Ready(val result: LyricsResult) : LyricsState

    /** LRCLIB menjawab dengan pasti bahwa lagu ini tidak punya lirik. */
    data object NotFound : LyricsState

    /** Tidak bisa ditanyakan sekarang: jaringan mati, timeout, 5xx, rate limit. */
    data class Unavailable(val reason: String) : LyricsState
}

/** Hasil satu kali lookup, sebelum diputuskan mau di-cache atau tidak. */
sealed interface LyricsLookup {
    data class Found(val result: LyricsResult) : LyricsLookup

    /** Boleh di-cache negatif: jawabannya stabil untuk beberapa waktu. */
    data object NotFound : LyricsLookup

    /** JANGAN di-cache dalam bentuk apapun. Percobaan berikutnya bisa berhasil. */
    data class Unavailable(val reason: String) : LyricsLookup
}

/** Hasil satu probe ke satu endpoint LRCLIB. */
sealed interface LrclibProbe {
    /** Endpoint mengembalikan lirik yang benar-benar ada isinya. */
    data class Content(val result: LyricsResult) : LrclibProbe

    /** Endpoint menjawab dengan sukses, dan jawabannya "tidak ada". */
    data object Absent : LrclibProbe

    /** Endpoint tidak bisa dijangkau atau membalas error yang bisa berubah. */
    data class Failed(val reason: String) : LrclibProbe
}

/**
 * Gabungkan hasil probe `/get` dan `/search` jadi satu keputusan.
 *
 * Aturan yang menentukan:
 *
 * - Ada konten di salah satu, pakai itu. `/get` menang karena lebih akurat
 *   (dicocokkan dengan album + durasi).
 * - [LyricsLookup.NotFound] HANYA kalau KEDUA probe berhasil dan sama-sama
 *   bilang tidak ada. Kalau salah satunya gagal, kita tidak benar-benar tahu:
 *   yang gagal itu mungkin punya liriknya. Menyimpannya sebagai "tidak ada"
 *   akan menyembunyikan lirik selama masa TTL negatif.
 * - Sisanya [LyricsLookup.Unavailable], dan itu tidak boleh di-cache.
 */
fun combineProbes(get: LrclibProbe, search: LrclibProbe): LyricsLookup = when {
    get is LrclibProbe.Content -> LyricsLookup.Found(get.result)
    search is LrclibProbe.Content -> LyricsLookup.Found(search.result)
    get is LrclibProbe.Absent && search is LrclibProbe.Absent -> LyricsLookup.NotFound
    else -> {
        val reason = (get as? LrclibProbe.Failed)?.reason
            ?: (search as? LrclibProbe.Failed)?.reason
            ?: "unknown"
        LyricsLookup.Unavailable(reason)
    }
}

/**
 * Terjemahkan status HTTP LRCLIB jadi probe, untuk response yang TIDAK sukses.
 *
 * 404 dari `/api/get` adalah jawaban sungguhan: kombinasi judul, artis, album,
 * dan durasi itu tidak ada di database. Itu boleh di-cache negatif.
 *
 * Sisanya (429 rate limit, 5xx, 403, apapun) bisa berubah beberapa detik lagi,
 * jadi diperlakukan sebagai kegagalan sementara.
 */
fun probeFromHttpFailure(httpCode: Int): LrclibProbe =
    if (httpCode == 404) LrclibProbe.Absent else LrclibProbe.Failed("http_$httpCode")

/**
 * Apa yang harus ditulis ke cache untuk sebuah [lookup]. null berarti JANGAN
 * tulis apa-apa, tidak ke memory maupun disk.
 *
 * Keputusan ini sengaja jadi fungsi murni tersendiri, karena di sinilah bug
 * aslinya hidup: dulu repository memanggil `diskCache.put(result)` tanpa syarat
 * apapun, jadi kegagalan jaringan ikut tersimpan sebagai "tidak ada lirik"
 * dengan TTL 1 hari. Sekarang aturannya bisa diuji langsung, termasuk kasus
 * "jangan tulis" yang justru paling penting.
 *
 * [LyricsLookup.NotFound] ditulis sebagai hasil kosong; itulah penanda negatif
 * yang bikin `LyricsCache` memilih TTL pendek.
 */
fun cacheEntryFor(trackId: String, lookup: LyricsLookup): LyricsResult? = when (lookup) {
    is LyricsLookup.Found -> lookup.result
    LyricsLookup.NotFound -> LyricsResult(trackId = trackId, synced = null, plain = null)
    is LyricsLookup.Unavailable -> null
}

/**
 * Jeda sebelum percobaan ulang ke-[attempt] (1 = retry pertama).
 *
 * Backoff eksponensial dari 400 ms, dipotong di 3 detik. Sengaja pendek: user
 * sedang menatap layar menunggu lirik, jadi ini untuk menutup gangguan sesaat,
 * bukan untuk menunggu jaringan pulih total.
 */
fun retryBackoffMs(attempt: Int): Long {
    require(attempt >= 1) { "attempt mulai dari 1, dapat $attempt" }
    val exponent = (attempt - 1).coerceAtMost(8)
    return (400L shl exponent).coerceAtMost(3_000L)
}
