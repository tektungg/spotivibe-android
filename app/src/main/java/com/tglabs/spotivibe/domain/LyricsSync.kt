package com.tglabs.spotivibe.domain

/**
 * Satu-satunya implementasi pencocokan posisi playback ke baris lirik.
 *
 * Sebelumnya logika ini ada DUA kali, identik tapi terpisah: di
 * `PlaybackController` (dipakai notification + overlay, tick 500 ms) dan di
 * `LyricsList` (dipakai layar utama, tick 200 ms). Dua salinan berarti dua
 * clock, dan lebih buruk lagi: `lyricsOffsetMs` hanya diterapkan di salinan
 * milik UI. Jadi setting offset yang dikira global sebenarnya cuma mengubah
 * satu dari tiga permukaan, dan notification bisa menampilkan baris yang
 * berbeda dari layar.
 *
 * Semua fungsi di sini murni supaya bisa diuji langsung di JVM.
 */

/**
 * Index baris terakhir yang `timeMs <= ms`, atau -1 kalau [ms] masih sebelum
 * baris pertama (intro instrumental).
 *
 * [lines] WAJIB terurut naik menurut `timeMs`. `parseLrc` sudah menjamin itu.
 *
 * Kalau ada beberapa baris dengan timestamp identik, yang dipilih adalah yang
 * TERAKHIR. Deterministik, dan cocok dengan urutan baca: baris belakangan di
 * file muncul belakangan di layar.
 */
fun findActiveLineIndex(lines: List<SyncedLine>, ms: Long): Int {
    if (lines.isEmpty()) return -1
    var lo = 0
    var hi = lines.size - 1
    var ans = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (lines[mid].timeMs <= ms) {
            ans = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return ans
}

/**
 * Posisi yang dipakai untuk mencocokkan lirik, setelah koreksi manual user.
 *
 * Offset positif berarti lirik dipercepat (kompensasi file LRC yang telat):
 * kita berpura-pura lagu sudah lebih maju dari posisi sebenarnya.
 *
 * Ini BUKAN posisi untuk slider transport. Slider harus menampilkan posisi
 * playback yang sebenarnya, bukan yang sudah digeser.
 */
fun lyricsProgressMs(rawProgressMs: Long, offsetMs: Int): Long =
    (rawProgressMs + offsetMs).coerceAtLeast(0L)

/**
 * Posisi seek saat user tap sebuah baris.
 *
 * Kebalikan dari [lyricsProgressMs]. Tanpa kompensasi ini, tap baris dengan
 * offset non-nol akan mendarat di posisi yang justru membuat baris LAIN yang
 * aktif: seek ke `t` membuat progress lirik jadi `t + offset`, yang bisa sudah
 * melewati baris yang barusan ditap.
 *
 * Invarian yang dijaga:
 * `lyricsProgressMs(seekTargetMs(t, offset), offset) == t` untuk setiap
 * `t - offset >= 0`.
 */
fun seekTargetMs(lineTimeMs: Long, offsetMs: Int): Long =
    (lineTimeMs - offsetMs).coerceAtLeast(0L)

/**
 * Berapa lama boleh tidur sebelum baris aktif berikutnya mulai.
 *
 * Ticker sebelumnya bangun pada interval tetap (500 ms di controller, 200 ms
 * di UI). Interval tetap salah di dua arah sekaligus: terlalu jarang saat lagu
 * jalan sehingga baris telat menyala sampai setengah detik, dan terlalu sering
 * saat pause atau saat lagu tidak punya lirik sehingga membangunkan CPU tanpa
 * hasil.
 *
 * Fungsi ini menghitung jarak tepat ke batas baris berikutnya lalu memotongnya
 * ke [minMs]..[maxMs]. Batas atas menjaga responsif terhadap seek dan
 * perubahan baseline; batas bawah menjaga supaya tidak jadi busy loop.
 *
 * @param nextLineTimeMs waktu mulai baris berikutnya, atau null kalau sudah di
 *   baris terakhir.
 * @param lyricsNowMs posisi lirik saat ini (sudah termasuk offset).
 */
fun nextTickDelayMs(
    nextLineTimeMs: Long?,
    lyricsNowMs: Long,
    isPaused: Boolean,
    minMs: Long = 16L,
    maxMs: Long = 250L,
): Long {
    // Saat pause, posisi tidak bergerak sendiri. Tetap bangun sesekali supaya
    // perubahan dari luar (seek, ganti offset) tidak menunggu lama.
    if (isPaused) return maxMs
    if (nextLineTimeMs == null) return maxMs
    return (nextLineTimeMs - lyricsNowMs).coerceIn(minMs, maxMs)
}
