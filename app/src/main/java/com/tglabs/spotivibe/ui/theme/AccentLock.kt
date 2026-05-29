package com.tglabs.spotivibe.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * OkLCh-based accent locking.
 *
 * Tujuan: ekstrak hue dari album art lalu paksa chroma + lightness ke
 * nilai TETAP, supaya warna aktif lyric tetap readable terhadap ink-1
 * untuk LAGU APAPUN (K-pop merah, anime hijau, city-pop biru, dll).
 *
 * Dark mode target: L = 0.74, C = 0.17 (cukup terang + saturated supaya
 *   accent jelas tapi tidak silau di OLED dim).
 * Light mode target: L = 0.55, C = 0.13 (lebih gelap + desaturated supaya
 *   tetap kontras di paper-white background).
 * AccentDim target: L * 0.68, C * 0.66 — same hue, fade ~50% lightness,
 *   digunakan untuk romanization line.
 *
 * Math reference: https://bottosson.github.io/posts/oklab/
 */
object AccentLock {

    // Dark: accent harus terang + vivid biar pop di atas near-black bg.
    // 0.78 (naik dari 0.74) supaya hue warm/kuning tidak jadi muddy gold.
    private const val DARK_L = 0.78
    private const val DARK_C = 0.17
    // Light: accent harus lebih gelap + sedikit lebih saturated biar kontras
    // di atas paper-white bg.
    private const val LIGHT_L = 0.55
    private const val LIGHT_C = 0.14

    /**
     * Lock accent: extract hue dari input color, ganti chroma + lightness
     * ke nilai target sesuai mode. Hue dipertahankan; L+C dikunci supaya
     * kontras predictable terhadap ink-1 untuk lagu apapun.
     */
    fun lockedAccent(source: Color, dark: Boolean = true): Color {
        val hue = oklchHueOf(source)
        val (l, c) = if (dark) DARK_L to DARK_C else LIGHT_L to LIGHT_C
        return oklchToColor(l, c, hue)
    }

    /**
     * Accent-dim untuk romanization. Harus READABLE tapi lebih redup dari
     * active line. Versi sebelumnya terlalu gelap (L≈0.515) → teks italic
     * kecil susah dibaca di dark mode.
     *
     * - Dark: L=0.66 (jelas di atas dark bg, masih < active 0.78), chroma
     *   sedang supaya tetap ke-tie ke accent tanpa silau.
     * - Light: L=0.62 (sedikit lebih terang dari active 0.55 = kurang
     *   prominent, tapi tetap kontras di atas paper).
     */
    fun dimOf(accent: Color, dark: Boolean = true): Color {
        val hue = oklchHueOf(accent)
        return if (dark) {
            oklchToColor(0.66, 0.11, hue)
        } else {
            oklchToColor(0.62, 0.10, hue)
        }
    }

    // ── OkLCh / OkLab / sRGB conversion math ─────────────────────────
    // OkLab spec: https://bottosson.github.io/posts/oklab/

    private fun oklchHueOf(c: Color): Double {
        val (l, a, b) = sRgbToOklab(c.red.toDouble(), c.green.toDouble(), c.blue.toDouble())
        // hue in radians → return as-is; rebuild via cos/sin
        return atan2(b, a)
    }

    private fun oklchToColor(l: Double, chroma: Double, hueRad: Double): Color {
        val a = chroma * cos(hueRad)
        val b = chroma * sin(hueRad)
        val (r, g, blue) = oklabToSRgb(l, a, b)
        return Color(
            red   = r.toFloat().coerceIn(0f, 1f),
            green = g.toFloat().coerceIn(0f, 1f),
            blue  = blue.toFloat().coerceIn(0f, 1f),
            alpha = 1f,
        )
    }

    /**
     * sRGB (0..1 linear-encoded as gamma-corrected; Compose Color.red etc.
     * gives gamma-corrected) → OkLab.
     */
    private fun sRgbToOklab(r: Double, g: Double, b: Double): Triple<Double, Double, Double> {
        val lr = srgbToLinear(r)
        val lg = srgbToLinear(g)
        val lb = srgbToLinear(b)

        val lms_l = 0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb
        val lms_m = 0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb
        val lms_s = 0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb

        val l_ = cbrt(lms_l)
        val m_ = cbrt(lms_m)
        val s_ = cbrt(lms_s)

        val l = 0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_
        val a = 1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_
        val bb = 0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_
        return Triple(l, a, bb)
    }

    private fun oklabToSRgb(l: Double, a: Double, b: Double): Triple<Double, Double, Double> {
        val l_ = l + 0.3963377774 * a + 0.2158037573 * b
        val m_ = l - 0.1055613458 * a - 0.0638541728 * b
        val s_ = l - 0.0894841775 * a - 1.2914855480 * b

        val lms_l = l_ * l_ * l_
        val lms_m = m_ * m_ * m_
        val lms_s = s_ * s_ * s_

        val lr =  4.0767416621 * lms_l - 3.3077115913 * lms_m + 0.2309699292 * lms_s
        val lg = -1.2684380046 * lms_l + 2.6097574011 * lms_m - 0.3413193965 * lms_s
        val lb = -0.0041960863 * lms_l - 0.7034186147 * lms_m + 1.7076147010 * lms_s

        return Triple(linearToSrgb(lr), linearToSrgb(lg), linearToSrgb(lb))
    }

    private fun srgbToLinear(c: Double): Double =
        if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun linearToSrgb(c: Double): Double {
        val cc = c.coerceIn(0.0, 1.0)
        return if (cc <= 0.0031308) 12.92 * cc else 1.055 * cc.pow(1.0 / 2.4) - 0.055
    }
}
