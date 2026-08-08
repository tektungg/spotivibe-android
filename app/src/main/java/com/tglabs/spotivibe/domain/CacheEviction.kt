package com.tglabs.spotivibe.domain

/**
 * Kebijakan pembatasan cache disk.
 *
 * Cache lirik sebelumnya cuma punya `get` dan `put`. File hanya terhapus kalau
 * KEBETULAN dibaca lagi setelah kedaluwarsa, jadi lagu yang tidak pernah diputar
 * ulang menetap selamanya. Lokasinya `cacheDir` sehingga sistem BOLEH
 * menghapusnya, tapi baru saat penyimpanan sesak, dan saat itu terjadi sistem
 * membuang SELURUH isinya sekaligus, bukan yang paling tidak berguna.
 *
 * Dipisah jadi fungsi murni supaya kebijakannya bisa diuji tanpa menyentuh
 * filesystem, termasuk kasus yang merepotkan disiapkan secara nyata seperti
 * cache yang sudah membengkak berbulan-bulan.
 */

/** Metadata satu file cache, cukup untuk memutuskan tanpa membaca isinya. */
data class CacheFileInfo(
    val name: String,
    val sizeBytes: Long,
    /**
     * Kapan terakhir dipakai. Di implementasi nyata ini `File.lastModified()`,
     * yang di-sentuh ulang saat pembacaan supaya perilakunya LRU dan bukan
     * sekadar FIFO.
     */
    val lastAccessMs: Long,
)

/**
 * Tentukan file mana yang harus dibuang.
 *
 * Dua tahap, berurutan:
 *
 * 1. Semua yang lebih tua dari [maxAgeMs] dibuang, apa pun kondisi lainnya.
 *    Ini yang menangani ekor panjang: lagu yang diputar sekali setahun lalu dan
 *    tidak pernah disentuh lagi.
 * 2. Dari yang tersisa, kalau jumlahnya masih melebihi [maxEntries] ATAU total
 *    ukurannya melebihi [maxBytes], buang yang paling lama tidak dipakai sampai
 *    keduanya terpenuhi.
 *
 * Batas usia di sini sengaja longgar, dipasang sama dengan TTL positif. TTL
 * negatif yang jauh lebih pendek tetap ditegakkan saat pembacaan, karena
 * menentukannya butuh membuka isi file dan itu terlalu mahal untuk dilakukan ke
 * seluruh direktori.
 */
fun filesToEvict(
    files: List<CacheFileInfo>,
    nowMs: Long,
    maxAgeMs: Long,
    maxEntries: Int,
    maxBytes: Long,
): List<String> {
    require(maxEntries >= 0) { "maxEntries tidak boleh negatif" }
    require(maxBytes >= 0) { "maxBytes tidak boleh negatif" }

    val buang = mutableListOf<String>()

    // Tahap 1: kedaluwarsa karena usia.
    val (kedaluwarsa, hidup) = files.partition { nowMs - it.lastAccessMs > maxAgeMs }
    buang += kedaluwarsa.map { it.name }

    // Tahap 2: masih kelebihan jatah. Yang paling lama tidak dipakai pergi dulu.
    //
    // Urutan stabil lewat nama sebagai pemecah seri, supaya dua file dengan
    // stempel waktu identik selalu dibuang dengan urutan yang sama. Tanpa itu
    // hasilnya bergantung urutan listing direktori dan tidak bisa diuji.
    val urut = hidup.sortedWith(compareBy({ it.lastAccessMs }, { it.name }))
    var jumlah = urut.size
    var byte = urut.sumOf { it.sizeBytes }

    for (f in urut) {
        if (jumlah <= maxEntries && byte <= maxBytes) break
        buang += f.name
        jumlah--
        byte -= f.sizeBytes
    }

    return buang
}
