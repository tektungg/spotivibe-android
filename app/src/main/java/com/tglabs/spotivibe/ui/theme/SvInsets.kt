package com.tglabs.spotivibe.ui.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * Safe area: lapis ketiga sistem layout, terpisah dari [SvWindowClass] dan
 * [svScale].
 *
 * `enableEdgeToEdge()` (dan `targetSdk` 35+ yang memaksanya) membuat app
 * menggambar di belakang status bar dan navigation bar. Dulu tidak ada layar
 * yang memakai insets; jarak atas cuma angka tetap 16/32 dp yang kebetulan
 * cukup untuk status bar HP. Di head unit mobil, bar sistem lebih tebal dan
 * bisa berada di atas atau di samping, jadi header tertimpa navigasi.
 *
 * ## Aturan pakai
 *
 * - Latar DULU, insets KEMUDIAN: `.background(...).svSafeContent()`. Urutan
 *   modifier itu yang membuat latar terlukis sampai tepi layar sementara
 *   kontennya terdorong masuk ke area aman.
 * - Insets dipasang di container konten, bukan di `contentPadding` list.
 *   `LyricsList` mengukur posisi chrome lewat `positionInRoot`, jadi padding
 *   di parent sudah otomatis masuk hitungan anchor baris aktif.
 * - Jangan panggil `WindowInsets.safeDrawing` langsung. Semua lewat
 *   [svSafeInsets] supaya test bisa menyuntikkan insets palsu lewat
 *   [LocalSvSafeInsets]. `ResponsiveGuardTest` menjaga aturan ini.
 *
 * `safeDrawing` = status bar + navigation bar (sisi mana pun) + display
 * cutout + keyboard. Bar yang sedang disembunyikan (mode karaoke) melaporkan
 * 0, jadi yang tersisa hanya cutout.
 */

/** Override insets untuk test. Null = insets sistem sungguhan. */
val LocalSvSafeInsets = staticCompositionLocalOf<WindowInsets?> { null }

/** Insets area aman yang berlaku untuk komposisi ini. */
@Composable
fun svSafeInsets(): WindowInsets = LocalSvSafeInsets.current ?: WindowInsets.safeDrawing

/**
 * Dorong konten masuk ke area aman di sisi [sides].
 *
 * Panel landscape memakai sisi parsial: panel kiri tidak perlu menghindari
 * bar di sisi KANAN layar, dan sebaliknya. Kalau semua panel memakai semua
 * sisi, navigasi kiri head unit ikut memakan ruang panel kanan.
 */
fun Modifier.svSafeContent(
    sides: WindowInsetsSides = WindowInsetsSides.Horizontal + WindowInsetsSides.Vertical,
): Modifier = composed { windowInsetsPadding(svSafeInsets().only(sides)) }
