package com.tglabs.spotivibe.domain

/**
 * Keputusan murni milik `PlaybackController`.
 *
 * Controller-nya sendiri hampir seluruhnya coroutine dan Flow di atas `Bitmap`,
 * `Palette`, dan `Color`, jadi tidak bisa dijalankan di JVM tanpa Robolectric.
 * Yang ditarik ke sini adalah bagian yang MEMUTUSKAN sesuatu, dan justru bagian
 * itulah yang punya konsekuensi kalau salah: lirik lagu lama menempel di lagu
 * baru, atau baris aktif meloncat-loncat.
 */

/**
 * Hasil fetch lirik boleh dipakai.
 *
 * Fetch berjalan asinkron. Kalau user mengganti lagu sebelum jaringan menjawab,
 * jawaban untuk lagu LAMA masih akan datang. Tanpa pemeriksaan ini, lirik lagu
 * sebelumnya menempel di lagu yang sedang diputar, dan itu tidak terlihat
 * seperti bug balapan melainkan seperti "liriknya salah".
 */
fun shouldApplyFetchedLyrics(fetchedForTrackId: String?, currentTrackId: String?): Boolean =
    !fetchedForTrackId.isNullOrBlank() && fetchedForTrackId == currentTrackId

/**
 * Index baris aktif diterbitkan hanya kalau berubah.
 *
 * Ticker berjalan terus selama lagu main. Menerbitkan nilai yang sama setiap
 * tick membangunkan seluruh konsumen: layar, notification, dan overlay.
 */
fun shouldPublishLineIndex(baru: Int, sekarang: Int): Boolean = baru != sekarang

/**
 * Ambil baris pada index, aman terhadap index di luar rentang.
 *
 * Index datang dari engine sync sementara daftar barisnya datang dari fetch, dan
 * keduanya berubah di waktu yang berbeda. Di sela itu index bisa menunjuk ke
 * luar daftar yang baru.
 */
fun <T> lineAt(lines: List<T>?, index: Int): T? = lines?.getOrNull(index)

/**
 * Track layak di-preload.
 *
 * Antrian dari Web API bisa memuat entri tanpa id, misalnya iklan atau episode
 * lokal. Memintanya ke LRCLIB hanya membuang kuota.
 */
fun shouldPreload(trackId: String?): Boolean = !trackId.isNullOrBlank()

/**
 * Pengecekan `/me` masih perlu dilakukan.
 *
 * Sekali jawabannya didapat, ia tidak berubah selama sesi. Mengulanginya tiap
 * ganti lagu memboroskan panggilan dan kuota rate limit.
 */
fun shouldCheckProduct(productSekarang: String?): Boolean = productSekarang == null

/**
 * Accent perlu dihitung ulang untuk bitmap ini.
 *
 * Dibandingkan lewat IDENTITAS, bukan isi. Album art yang sama datang sebagai
 * objek yang sama dari cache, sementara membandingkan isi bitmap berarti
 * memindai jutaan piksel hanya untuk memutuskan apakah perlu memindainya.
 */
fun shouldRecomputeAccent(bitmapBaru: Any?, bitmapTerakhir: Any?): Boolean =
    bitmapBaru !== bitmapTerakhir

/**
 * Hasil accent boleh dipakai.
 *
 * Ekstraksi berjalan di dispatcher lain. Kalau album sudah berganti saat
 * hasilnya selesai, warnanya milik lagu yang salah.
 */
fun shouldApplyAccent(bitmapSaatSelesai: Any?, bitmapSekarang: Any?): Boolean =
    bitmapSaatSelesai === bitmapSekarang

/**
 * Pilih warna accent dari kandidat Palette, berurutan.
 *
 * Palette mengembalikan nilai fallback yang kita berikan saat sebuah swatch
 * tidak ada, dan untuk `0` itu berarti "tidak ada". Album art hitam putih
 * seperti sampul monokrom sering tidak punya swatch vibrant sama sekali, dan
 * tanpa rantai ini accent-nya jadi hitam pekat yang tidak terbaca.
 *
 * @param dominant sudah membawa fallback-nya sendiri dari pemanggil, jadi nilai
 *   `0` di sini pun tetap dianggap jawaban.
 */
fun pickAccentArgb(
    vibrant: Int,
    lightVibrant: Int,
    dominant: Int,
    fallback: Int,
): Int = when {
    vibrant != 0 -> vibrant
    lightVibrant != 0 -> lightVibrant
    dominant != 0 -> dominant
    else -> fallback
}
