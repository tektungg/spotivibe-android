package com.tglabs.spotivibe.domain

/**
 * Pengukuran untuk pertanyaan inti produk ini: **berapa persen lagu yang
 * diputar benar-benar dapat lirik ter-sync?**
 *
 * Sebelumnya tidak ada satu pun angka yang dicatat, jadi tidak ada cara tahu
 * apakah sebuah perubahan memperbaiki atau memperburuk. Itu juga yang menahan
 * pekerjaan berikutnya: mengurangi beban ke LRCLIB dari sekitar 9 request per
 * lagu jadi 2 sampai 3 tidak boleh dikerjakan tanpa bisa membuktikan kualitas
 * pencariannya tidak ikut turun.
 */

/**
 * Lapisan mana yang menjawab lookup.
 *
 * [Override] dipisah supaya angka coverage tidak terdistorsi oleh lagu yang
 * sudah diperbaiki manual: lagu itu memang pasti dapat lirik, tapi bukan karena
 * pencarian otomatisnya membaik.
 */
enum class LyricsSource { Override, MemCache, DiskCache, Network }

/** Endpoint LRCLIB mana yang menyediakan lirik saat lookup jaringan berhasil. */
enum class ProbeSource { Get, Search }

/** Hasil satu lookup, disederhanakan jadi empat kemungkinan yang saling lepas. */
enum class LyricsOutcome { Synced, PlainOnly, NotFound, Unavailable }

/** Satu peristiwa lookup. */
data class LyricsLookupEvent(
    val source: LyricsSource,
    val outcome: LyricsOutcome,
    /** Hanya terisi untuk [LyricsSource.Network] yang berhasil. */
    val probe: ProbeSource? = null,
)

fun outcomeOf(state: LyricsState): LyricsOutcome = when (state) {
    is LyricsState.Ready ->
        if (!state.result.synced.isNullOrEmpty()) LyricsOutcome.Synced
        else LyricsOutcome.PlainOnly
    LyricsState.NotFound -> LyricsOutcome.NotFound
    is LyricsState.Unavailable -> LyricsOutcome.Unavailable
    // Offline dicatat sebagai Unavailable: dari sisi coverage, keduanya sama-sama
    // "lirik ada mungkin, tapi tidak bisa kita jangkau sekarang".
    LyricsState.Offline -> LyricsOutcome.Unavailable
    LyricsState.Loading -> LyricsOutcome.Unavailable
}

/** Akumulasi peristiwa lookup. Semua penghitung monoton naik sampai di-reset. */
data class LyricsStats(
    val synced: Int = 0,
    val plainOnly: Int = 0,
    val notFound: Int = 0,
    val unavailable: Int = 0,
    val fromMem: Int = 0,
    val fromDisk: Int = 0,
    val fromNetwork: Int = 0,
    /** Lagu yang dijawab oleh lirik pilihan user, bukan oleh auto-match. */
    val fromOverride: Int = 0,
    val getWon: Int = 0,
    val searchWon: Int = 0,
) {
    /** Semua lookup, termasuk yang gagal dijangkau. */
    val total: Int get() = synced + plainOnly + notFound + unavailable

    /**
     * Lookup yang benar-benar TERJAWAB LRCLIB, entah ada liriknya atau tidak.
     *
     * [unavailable] sengaja dikeluarkan dari penyebut coverage. Jaringan mati
     * itu kegagalan kita menjangkau, bukan lubang di database LRCLIB.
     * Mencampurnya membuat angka coverage ikut turun setiap kali sinyal jelek,
     * dan metrik yang turun karena hal yang salah lebih buruk daripada tidak
     * punya metrik.
     */
    val answered: Int get() = synced + plainOnly + notFound

    /**
     * Metrik utama: dari lagu yang bisa ditanyakan, berapa persen dapat lirik
     * ter-sync. null kalau belum ada data, bukan nol, supaya "belum tahu" tidak
     * tersamar jadi "buruk".
     */
    val syncedRate: Float? get() = if (answered == 0) null else synced.toFloat() / answered

    /** Dapat lirik apa pun, ter-sync maupun teks polos. */
    val anyLyricsRate: Float?
        get() = if (answered == 0) null else (synced + plainOnly).toFloat() / answered

    /** Seberapa sering kita berhasil menjangkau LRCLIB sama sekali. */
    val reachRate: Float? get() = if (total == 0) null else answered.toFloat() / total

    /** Seberapa sering jawaban datang dari cache, bukan jaringan. */
    val cacheRate: Float?
        get() = if (total == 0) null else (fromMem + fromDisk).toFloat() / total

    /**
     * Dari lookup jaringan yang berhasil, berapa yang HANYA bisa dijawab
     * `/search`.
     *
     * Ini angka yang menentukan pekerjaan berikutnya. Sekarang `/get` dan
     * `/search` selalu ditembak paralel, yang menggandakan beban ke API
     * komunitas gratis. Kalau `/search` hampir tidak pernah menang, menembakkan
     * keduanya bersamaan cuma pemborosan dan `/search` bisa diturunkan jadi
     * fallback saat `/get` meleset.
     */
    val searchOnlyRate: Float?
        get() = (getWon + searchWon).let { if (it == 0) null else searchWon.toFloat() / it }

    /**
     * Lookup yang dijawab override SENGAJA tidak menaikkan penghitung outcome
     * apa pun, cuma [fromOverride].
     *
     * Kalau ikut dihitung, angka coverage naik setiap kali user memperbaiki
     * lirik secara manual, dan kita akan menyimpulkan pencarian otomatisnya
     * membaik padahal justru sebaliknya: makin banyak override berarti
     * auto-match makin sering gagal. [fromOverride] sendiri yang jadi sinyal
     * itu, terpisah dan tidak mengotori metrik utama.
     */
    operator fun plus(event: LyricsLookupEvent): LyricsStats {
        if (event.source == LyricsSource.Override) {
            return copy(fromOverride = fromOverride + 1)
        }
        return copy(
            synced = synced + if (event.outcome == LyricsOutcome.Synced) 1 else 0,
            plainOnly = plainOnly + if (event.outcome == LyricsOutcome.PlainOnly) 1 else 0,
            notFound = notFound + if (event.outcome == LyricsOutcome.NotFound) 1 else 0,
            unavailable = unavailable + if (event.outcome == LyricsOutcome.Unavailable) 1 else 0,
            fromMem = fromMem + if (event.source == LyricsSource.MemCache) 1 else 0,
            fromDisk = fromDisk + if (event.source == LyricsSource.DiskCache) 1 else 0,
            fromNetwork = fromNetwork + if (event.source == LyricsSource.Network) 1 else 0,
            getWon = getWon + if (event.probe == ProbeSource.Get) 1 else 0,
            searchWon = searchWon + if (event.probe == ProbeSource.Search) 1 else 0,
        )
    }
}

/** Sink untuk peristiwa lookup. Diabstraksi supaya repository bisa diuji. */
fun interface LyricsStatsRecorder {
    suspend fun record(event: LyricsLookupEvent)

    companion object {
        /** Tidak mencatat apa pun. Default untuk test dan pemakaian tanpa Context. */
        val NoOp = LyricsStatsRecorder { }
    }
}
