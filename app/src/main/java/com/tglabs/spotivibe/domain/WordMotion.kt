package com.tglabs.spotivibe.domain

import androidx.compose.runtime.Immutable
import kotlin.math.PI

/**
 * Gerak karaoke per kata: sapuan warna, skala, angkat, dan cahaya.
 *
 * Highlight lama mengganti warna kata dari redup ke aksen dalam satu langkah
 * begitu waktunya lewat. Untuk kata panjang ("baby~" ditahan dua detik) itu
 * berarti kata menyala penuh di awal lalu diam, dan user tidak bisa melihat
 * berapa lama lagi kata itu ditahan. Sekarang warna menyapu dari kiri ke kanan
 * selama kata dinyanyikan, kata sedikit membesar dan terangkat, lalu kembali.
 *
 * Kurvanya natural cubic spline lewat titik kunci, supaya geraknya mulus tanpa
 * sudut. Semua fungsi di sini murni dan diuji di JVM.
 */

/** Satu potongan teks yang punya waktu sendiri: suku kata, atau satu huruf dari kata panjang. */
@Immutable
data class KaraokeGlyph(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    /** Potongan dengan [wordIndex] sama tidak boleh dipisah ke baris berbeda. */
    val wordIndex: Int,
)

/** Keadaan gerak satu potongan pada satu saat. */
@Immutable
data class WordMotion(
    val scale: Float,
    /** Angkat vertikal dalam satuan em; negatif berarti naik. */
    val liftEm: Float,
    /** Kekuatan cahaya 0..1. */
    val glow: Float,
)

/**
 * Kata yang ditahan selama ini atau lebih dipecah per huruf, supaya sapuannya
 * terlihat merambat huruf demi huruf, bukan satu blok yang lambat.
 */
const val LETTER_SPLIT_MIN_MS = 1_000L

/** Dipakai kalau kata terakhir tidak punya waktu akhir sama sekali. */
private const val FALLBACK_WORD_MS = 500L

/** Lebar tepi sapuan sebagai pecahan lebar teks. */
const val WIPE_EDGE = 0.2f

/** Progress 0..1 potongan pada [nowMs]. Durasi nol: langsung penuh begitu mulai. */
fun glyphProgress(glyph: KaraokeGlyph, nowMs: Long): Float {
    if (nowMs <= glyph.startMs) return 0f
    val duration = glyph.endMs - glyph.startMs
    if (duration <= 0L) return 1f
    return ((nowMs - glyph.startMs).toFloat() / duration).coerceIn(0f, 1f)
}

/**
 * Akhir kata ke-[index]: waktu akhirnya sendiri (TTML, LRCMux), awal kata
 * berikutnya (enhanced LRC hanya punya waktu mulai), akhir baris, atau jatah
 * cadangan.
 */
fun wordEndMs(words: List<SyncedWord>, index: Int, lineEndMs: Long?): Long {
    val word = words[index]
    return word.endMs
        ?: words.getOrNull(index + 1)?.timeMs
        ?: lineEndMs?.takeIf { it > word.timeMs }
        ?: (word.timeMs + FALLBACK_WORD_MS)
}

/**
 * Potongan karaoke untuk satu baris.
 *
 * Suku kata yang berakhir dengan spasi menutup sebuah kata; suku kata tanpa
 * spasi menyambung ke berikutnya ("e" + "nough"). Suku kata yang ditahan
 * minimal [LETTER_SPLIT_MIN_MS] dipecah per huruf dengan jatah waktu rata;
 * spasi penutupnya ikut huruf terakhir.
 */
fun karaokeGlyphs(words: List<SyncedWord>, lineEndMs: Long?): List<KaraokeGlyph> {
    val out = ArrayList<KaraokeGlyph>(words.size)
    var wordIndex = 0
    words.forEachIndexed { i, w ->
        val end = wordEndMs(words, i, lineEndMs)
        out += splitIfLong(w.text, w.timeMs, end, wordIndex)
        if (w.text.endsWith(' ')) wordIndex++
    }
    return out
}

private fun splitIfLong(text: String, start: Long, end: Long, wordIndex: Int): List<KaraokeGlyph> {
    val core = text.trimEnd()
    val tail = text.substring(core.length)
    val letters = core.codePointsAsStrings()
    if (end - start < LETTER_SPLIT_MIN_MS || letters.size < 2) {
        return listOf(KaraokeGlyph(text, start, end, wordIndex))
    }
    val step = (end - start) / letters.size
    return letters.mapIndexed { i, letter ->
        val isLast = i == letters.lastIndex
        KaraokeGlyph(
            text = if (isLast) letter + tail else letter,
            startMs = start + i * step,
            endMs = if (isLast) end else start + (i + 1) * step,
            wordIndex = wordIndex,
        )
    }
}

/** Per code point, bukan per `Char`, supaya emoji dan aksara di luar BMP tidak terbelah. */
private fun String.codePointsAsStrings(): List<String> {
    val out = mutableListOf<String>()
    var i = 0
    while (i < length) {
        val cp = codePointAt(i)
        val n = Character.charCount(cp)
        out += substring(i, i + n)
        i += n
    }
    return out
}

/**
 * Natural cubic spline lewat titik kunci ([xs] naik). Di luar rentang nilainya
 * dijepit ke titik ujung, karena progress memang dijepit 0..1.
 */
class NaturalCubicSpline(private val xs: FloatArray, private val ys: FloatArray) {

    init {
        require(xs.size == ys.size && xs.size >= 2) { "butuh minimal dua titik dengan jumlah x dan y sama" }
    }

    // Setelah init: validasi harus jalan sebelum perhitungan memakai indeksnya.
    private val m: FloatArray = secondDerivatives()

    /**
     * Turunan kedua di tiap titik, dari sistem tridiagonal spline natural
     * (turunan kedua nol di kedua ujung), diselesaikan dengan algoritma Thomas.
     */
    private fun secondDerivatives(): FloatArray {
        val n = xs.size
        val result = FloatArray(n)
        if (n < 3) return result
        val c = FloatArray(n)
        val d = FloatArray(n)
        for (i in 1 until n - 1) {
            val h0 = xs[i] - xs[i - 1]
            val h1 = xs[i + 1] - xs[i]
            val rhs = 6f * ((ys[i + 1] - ys[i]) / h1 - (ys[i] - ys[i - 1]) / h0)
            val diag = 2f * (h0 + h1) - h0 * c[i - 1]
            c[i] = h1 / diag
            d[i] = (rhs - h0 * d[i - 1]) / diag
        }
        for (i in n - 2 downTo 1) result[i] = d[i] - c[i] * result[i + 1]
        return result
    }

    fun at(x: Float): Float {
        if (x <= xs.first()) return ys.first()
        if (x >= xs.last()) return ys.last()
        val i = (1 until xs.size).first { x <= xs[it] } - 1
        val h = xs[i + 1] - xs[i]
        val a = (xs[i + 1] - x) / h
        val b = (x - xs[i]) / h
        return a * ys[i] + b * ys[i + 1] +
            ((a * a * a - a) * m[i] + (b * b * b - b) * m[i + 1]) * h * h / 6f
    }
}

private val SCALE_CURVE = NaturalCubicSpline(floatArrayOf(0f, 0.7f, 1f), floatArrayOf(0.95f, 1.0505f, 1f))
private val GLOW_CURVE = NaturalCubicSpline(floatArrayOf(0f, 0.15f, 0.6f, 1f), floatArrayOf(0f, 1f, 1f, 0f))
private val LIFT_CURVE = NaturalCubicSpline(floatArrayOf(0f, 0.9f, 1f), floatArrayOf(0.01f, -1f / 60f, 0f))

/**
 * Gerak pada progress [p]. Sebelum dinyanyikan kata sedikit lebih kecil,
 * saat dinyanyikan membesar melewati ukuran normal dan bercahaya, sesudahnya
 * kembali normal tanpa cahaya.
 */
fun wordMotion(p: Float): WordMotion {
    val x = p.coerceIn(0f, 1f)
    return WordMotion(
        scale = SCALE_CURVE.at(x),
        liftEm = LIFT_CURVE.at(x),
        glow = GLOW_CURVE.at(x).coerceIn(0f, 1f),
    )
}

/**
 * Posisi tepi sapuan (pecahan lebar teks) untuk progress [p]: mulai di
 * `-WIPE_EDGE` (seluruhnya belum dinyanyikan) dan berakhir di `1` (tepi lewat
 * ujung kanan, seluruhnya sudah).
 */
fun wipePosition(p: Float): Float = -WIPE_EDGE + (1f + WIPE_EDGE) * p.coerceIn(0f, 1f)

/** Parameter pegas dalam bentuk yang dipakai Compose `spring()`. */
@Immutable
data class SpringParams(val stiffness: Float, val dampingRatio: Float)

/**
 * Pegas yang ditulis sebagai frekuensi (Hz) dan redaman, gaya spr.lua, jadi
 * kekakuan dan rasio redaman. Untuk massa 1: kekakuan = (2πf)².
 */
fun springFromFrequency(frequencyHz: Float, damping: Float): SpringParams {
    val omega = 2f * PI.toFloat() * frequencyHz
    return SpringParams(stiffness = omega * omega, dampingRatio = damping)
}
