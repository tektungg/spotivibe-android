package com.tglabs.spotivibe.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.isSpecified

/**
 * Lapis kedua sistem responsif: penyesuaian halus ukuran, padanan `.w`/`.sp`
 * milik flutter_screenutil.
 *
 * Lapis pertama ([SvWindowClass]) memutuskan STRUKTUR. Lapis ini menyesuaikan
 * ANGKA di dalam struktur yang sudah dipilih, supaya HP 320 dp dan HP 430 dp
 * tidak mendapat layout yang identik kaku.
 *
 * ## Aturan pakai
 *
 * - Nilai desain di kanvas 375x812 dp. `44.svDp` berarti "44 dp di HP desain".
 * - Pakai untuk ukuran yang PROPORSIONAL terhadap layar: album art, thumbnail,
 *   tombol, jarak antar blok besar.
 * - JANGAN pakai untuk hairline, border, dan radius. Garis 1 dp yang diskala
 *   jadi 0,85 dp hasilnya sub-piksel yang buram, dan sudut 2 dp yang jadi 2,5 dp
 *   tidak menambah apa pun. [SvRadius] sengaja tidak diskala.
 * - JANGAN pakai untuk target sentuh yang sudah berada di 48 dp. Menskala turun
 *   ke 40,8 dp melanggar ambang aksesibilitas Material.
 *
 * ## Kenapa faktornya dijepit
 *
 * flutter_screenutil mengalikan lebar layar terhadap lebar desain tanpa batas.
 * Di HP penguji, landscape lebarnya 834 dp, jadi faktornya 2,22x dan album 92 dp
 * akan membengkak jadi 204 dp di layar yang tingginya cuma 375 dp. [svScale]
 * mengambil rasio dimensi yang lebih langka lalu menjepitnya di 0,85..1,25,
 * sehingga tidak pernah bisa jadi penyebab elemen tumpah keluar layar. Beda yang
 * lebih besar dari jepitan itu urusan kelas ukuran, bukan urusan perkalian.
 */

/** Faktor skala jendela aktif. 1f kalau tema belum membungkus (preview, test). */
val currentSvScale: Float
    @Composable @ReadOnlyComposable
    get() = LocalSvWindow.current.let { svScale(it.widthDp, it.heightDp) }

/**
 * dp yang menyesuaikan ukuran layar.
 *
 * `44.svDp` -> 44 dp di HP desain, ~37 dp di HP kecil, ~55 dp di layar besar.
 */
val Int.svDp: Dp
    @Composable @ReadOnlyComposable
    get() = (this * currentSvScale).dp

val Float.svDp: Dp
    @Composable @ReadOnlyComposable
    get() = (this * currentSvScale).dp

/**
 * sp yang menyesuaikan ukuran layar.
 *
 * Perkalian ini di ATAS `sp`, bukan menggantikannya, jadi setelan ukuran font
 * pengguna tetap berlaku penuh. Itu bedanya dengan menskala lewat piksel mentah,
 * yang membuang setelan aksesibilitas pengguna diam-diam.
 */
val Int.svSp: TextUnit
    @Composable @ReadOnlyComposable
    get() = (this * currentSvScale).sp

val Float.svSp: TextUnit
    @Composable @ReadOnlyComposable
    get() = (this * currentSvScale).sp

/** Skala [Dp] yang sudah jadi, untuk token seperti [SvSpace]. */
@Composable
@ReadOnlyComposable
fun Dp.scaled(): Dp = scaleDp(this, currentSvScale)

/**
 * Bagian murni dari [scaled], dipisah supaya bisa diuji tanpa Compose.
 *
 * Nilai tak-terhingga dilewatkan apa adanya. `Dp.Unspecified` adalah `Float.NaN`
 * dan mengalikannya menghasilkan NaN yang lolos diam-diam sampai jadi crash
 * pengukuran di layar, jadi ditahan di sini.
 */
fun scaleDp(nilai: Dp, faktor: Float): Dp = when {
    !nilai.isSpecified -> nilai
    nilai == Dp.Infinity -> nilai
    else -> (nilai.value * faktor).dp
}
