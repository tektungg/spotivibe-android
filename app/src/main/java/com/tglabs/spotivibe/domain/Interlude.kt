package com.tglabs.spotivibe.domain

import androidx.compose.runtime.Immutable

/**
 * Titik-titik saat jeda instrumental.
 *
 * File LRC menandai jeda dengan baris kosong bertimestamp. Dulu baris itu
 * tampil sebagai "♪" yang diam, jadi saat intro atau interlude panjang layar
 * tidak memberi tanda kapan lirik berikutnya mulai. Sekarang tiga titik menyala
 * bergantian selama jeda, lalu menyusut tepat sebelum baris berikutnya,
 * sehingga user bisa menebak kapan harus mulai bernyanyi lagi.
 *
 * Semua fungsi di sini murni supaya urutan dan waktunya bisa diuji di JVM.
 */

/**
 * Jeda terpendek yang diberi titik beranimasi. Di bawah tiga detik, tiga titik
 * yang menyala bergantian terlalu cepat untuk terbaca dan malah terlihat
 * seperti kedipan.
 */
const val INTERLUDE_MIN_GAP_MS = 3_000L

const val INTERLUDE_DOT_COUNT = 3

/** Waktu di akhir jeda yang tidak dibagi ke titik: titik terakhir sudah penuh lebih dulu. */
private const val DOTS_TAIL_MS = 550L

/** Durasi titik-titik menyusut sebelum baris berikutnya mulai. */
private const val DOTS_COLLAPSE_MS = 500L

/** Satu keadaan tampilan titik-titik. */
@Immutable
data class InterludeFrame(
    /** Tingkat nyala tiap titik, 0 (redup) sampai 1 (penuh), kiri ke kanan. */
    val dotLevels: List<Float>,
    /** Skala ketiganya: 1 normal, menuju 0 saat jeda hampir habis. */
    val scale: Float,
) {
    companion object {
        /** Titik diam: tidak sedang jeda, atau jedanya terlalu pendek untuk dianimasikan. */
        val Idle = InterludeFrame(List(INTERLUDE_DOT_COUNT) { 0f }, 1f)
    }
}

/**
 * Keadaan titik-titik [elapsedMs] setelah jeda sepanjang [gapMs] dimulai.
 *
 * Tiap titik mendapat jatah `(gap − 550) / 3` ms dan menyala dari kiri ke
 * kanan, jadi titik ketiga penuh 550 ms sebelum jeda habis. Lima ratus ms
 * terakhir dipakai untuk menyusut, sebagai aba-aba bahwa baris berikutnya
 * segera mulai.
 */
fun interludeFrame(elapsedMs: Long, gapMs: Long): InterludeFrame {
    if (gapMs < INTERLUDE_MIN_GAP_MS) return InterludeFrame.Idle
    val elapsed = elapsedMs.coerceIn(0L, gapMs)
    val perDot = (gapMs - DOTS_TAIL_MS) / INTERLUDE_DOT_COUNT
    val levels = List(INTERLUDE_DOT_COUNT) { i ->
        ((elapsed - i * perDot).toFloat() / perDot).coerceIn(0f, 1f)
    }
    val remaining = gapMs - elapsed
    val scale = if (remaining >= DOTS_COLLAPSE_MS) 1f else remaining.toFloat() / DOTS_COLLAPSE_MS
    return InterludeFrame(levels, scale)
}

/** Baris kosong adalah penanda jeda, bukan lirik. */
val SyncedLine.isInterlude: Boolean get() = text.isBlank()

/**
 * Panjang jeda yang dimulai baris ke-[index], atau null kalau baris itu bukan
 * penanda jeda, tidak punya baris sesudahnya, atau jedanya terlalu pendek.
 */
fun interludeGapMs(lines: List<SyncedLine>, index: Int): Long? {
    val line = lines.getOrNull(index) ?: return null
    if (!line.isInterlude) return null
    val next = lines.getOrNull(index + 1) ?: return null
    return (next.timeMs - line.timeMs).takeIf { it >= INTERLUDE_MIN_GAP_MS }
}

/**
 * Panjang intro sebelum baris pertama, atau null kalau terlalu pendek.
 *
 * Intro tidak punya baris penanda di kebanyakan file LRC: baris pertama
 * langsung berisi lirik di detik ke-15. Tanpa ini, intro panjang adalah layar
 * diam yang paling lama.
 */
fun introGapMs(lines: List<SyncedLine>): Long? {
    val first = lines.firstOrNull() ?: return null
    if (first.isInterlude) return null
    return first.timeMs.takeIf { it >= INTERLUDE_MIN_GAP_MS }
}

/**
 * Sisipkan baris kosong di setiap jeda >= [INTERLUDE_MIN_GAP_MS] antara AKHIR
 * satu baris dan awal baris berikutnya.
 *
 * LRC menandai jeda dengan baris kosong, tapi TTML dan JSON per kata tidak:
 * mereka punya waktu akhir tiap baris, jadi jedanya tersirat. Mengubahnya jadi
 * baris kosong membuat satu jalur tampilan ([interludeGapMs]) berlaku untuk
 * semua sumber, termasuk notification yang tidak tahu apa-apa soal waktu akhir.
 *
 * Baris tanpa [SyncedLine.endMs] tidak menyisipkan apa-apa: jedanya tidak
 * diketahui. Id ditetapkan ulang sesuai posisi.
 */
fun insertInterludeMarkers(lines: List<SyncedLine>): List<SyncedLine> {
    if (lines.size < 2) return lines.withPositionalIds()
    val out = ArrayList<SyncedLine>(lines.size + 4)
    lines.forEachIndexed { i, line ->
        out += line
        val next = lines.getOrNull(i + 1) ?: return@forEachIndexed
        val end = line.endMs ?: return@forEachIndexed
        if (line.isInterlude || next.isInterlude) return@forEachIndexed
        if (next.timeMs - end >= INTERLUDE_MIN_GAP_MS) out += SyncedLine(id = 0, timeMs = end, text = "")
    }
    return out.withPositionalIds()
}
