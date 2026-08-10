package com.tglabs.spotivibe.ui.theme

/**
 * Sistem responsif project ini.
 *
 * Dibuat karena keputusan responsif sebelumnya tersebar sebagai cek
 * `orientation == LANDSCAPE` mentah di empat file, tanpa satu pun tempat yang
 * menjamin konsistensi. Akibatnya sudah terbukti: album di landscape cuma
 * dibatasi lebar, memakan 267 dari 295 dp, dan mendorong judul lagu keluar
 * layar tanpa ada yang menyadarinya sampai dipakai.
 *
 * ## Kenapa BUKAN skala linear ala flutter_screenutil
 *
 * Model itu mengalikan semua ukuran dengan `lebarLayar / lebarDesain`. Di HP
 * penguji, landscape lebarnya 834 dp; dengan kanvas desain 375 dp faktornya
 * jadi 2,22x. Album 92 dp akan membengkak jadi 204 dp di layar yang tingginya
 * cuma 375 dp, yaitu bug yang baru saja diperbaiki, kembali lebih parah.
 *
 * Akar masalahnya: skala linear mengasumsikan rasio layar tetap seperti HP
 * portrait. Saat dirotasi, dimensi yang LANGKA berpindah dari lebar ke tinggi,
 * dan faktor berbasis lebar tidak punya cara mengetahuinya.
 *
 * Dua alasan tambahan yang khusus Android:
 *
 * - `dp` sudah density-independent. Di Flutter, `.w`/`.h` sebagian mengerjakan
 *   tugas itu; di sini menumpuknya di atas `dp` cuma menggandakan pekerjaan.
 * - Menskala `sp` secara linear MENIMPA setelan ukuran font pengguna. Itu
 *   regresi aksesibilitas, dan tidak terlihat sampai ada yang mengeluh.
 *
 * ## Yang dipakai
 *
 * Dua lapis, dan keduanya wajib:
 *
 * 1. **Kelas ukuran** ([SvWindowClass]) untuk keputusan STRUKTUR: satu kolom
 *    atau dua, album besar atau kecil, tipografi display atau ringkas. Ini yang
 *    menangani perbedaan yang tidak bisa diselesaikan dengan mengalikan angka.
 *    Ambangnya mengikuti breakpoint resmi Android.
 * 2. **Faktor skala terbatas** ([svScale]) untuk penyesuaian HALUS di dalam satu
 *    kelas, supaya HP 320 dp dan HP 430 dp tidak dapat layout yang identik
 *    kaku. Ini bagian yang mirip flutter_screenutil, tapi dijepit di rentang
 *    sempit dan dihitung dari dimensi yang LANGKA, bukan selalu dari lebar.
 */

/**
 * Klasifikasi jendela yang berlaku, disediakan oleh [SpotivibeTheme].
 *
 * Baca dari sini, JANGAN memeriksa `LocalConfiguration.orientation` langsung di
 * layar. Orientasi tidak bisa membedakan HP landscape dari tablet landscape,
 * padahal keduanya butuh perlakuan yang berbeda.
 */
val LocalSvWindow = androidx.compose.runtime.staticCompositionLocalOf {
    // Default kanvas desain. Dipakai preview dan test yang tidak membungkus tema.
    classifyWindow(DESIGN_WIDTH_DP, DESIGN_HEIGHT_DP)
}

/** Breakpoint resmi Android, dalam dp. */
const val COMPACT_MAX_DP = 600f
const val MEDIUM_MAX_DP = 840f

enum class SvSizeClass { Compact, Medium, Expanded }

/**
 * Klasifikasi jendela.
 *
 * [heightClass] sama pentingnya dengan [widthClass], dan justru itu yang
 * terlewat sebelumnya. HP landscape punya lebar Expanded tapi tinggi Compact;
 * melihat lebar saja membuat layout mengira dirinya ada di tablet.
 */
data class SvWindowClass(
    val widthClass: SvSizeClass,
    val heightClass: SvSizeClass,
    val widthDp: Float,
    val heightDp: Float,
) {
    /** Muat dua kolom berdampingan tanpa memaksa. */
    val isWide: Boolean get() = widthClass != SvSizeClass.Compact

    /**
     * Tinggi sempit. Ini penanda yang benar untuk "kecilkan album dan
     * tipografi", bukan `orientation == LANDSCAPE`: tablet landscape tingginya
     * lega dan tidak boleh ikut mengecil.
     */
    val isShort: Boolean get() = heightClass == SvSizeClass.Compact
}

fun classifyWindow(widthDp: Float, heightDp: Float): SvWindowClass = SvWindowClass(
    widthClass = sizeClassOf(widthDp),
    heightClass = sizeClassOf(heightDp),
    widthDp = widthDp.coerceAtLeast(0f),
    heightDp = heightDp.coerceAtLeast(0f),
)

private fun sizeClassOf(dp: Float): SvSizeClass = when {
    dp < COMPACT_MAX_DP -> SvSizeClass.Compact
    dp < MEDIUM_MAX_DP -> SvSizeClass.Medium
    else -> SvSizeClass.Expanded
}

/** Kanvas desain: HP portrait, sama dengan yang dipakai design handoff. */
const val DESIGN_WIDTH_DP = 375f
const val DESIGN_HEIGHT_DP = 812f

/** Jepitan skala. Di luar ini layout lebih baik ganti struktur, bukan diregangkan. */
const val SCALE_MIN = 0.85f
const val SCALE_MAX = 1.25f

/**
 * Faktor skala halus untuk ukuran di dalam satu kelas.
 *
 * Dihitung dari dimensi yang **lebih langka** relatif terhadap kanvas desain,
 * bukan selalu dari lebar. Itu perbedaan pentingnya dengan flutter_screenutil:
 * saat layar dirotasi, yang langka berpindah ke tinggi, dan mengambil minimum
 * dari kedua rasio membuat faktornya ikut menyusut alih-alih membengkak.
 *
 * Dijepit di [SCALE_MIN]..[SCALE_MAX] supaya tidak pernah bisa jadi penyebab
 * elemen membengkak keluar layar. Perbedaan yang lebih besar dari itu adalah
 * urusan kelas ukuran, bukan urusan perkalian.
 */
fun svScale(widthDp: Float, heightDp: Float): Float {
    if (widthDp <= 0f || heightDp <= 0f) return 1f
    val rasio = minOf(widthDp / DESIGN_WIDTH_DP, heightDp / DESIGN_HEIGHT_DP)
    return rasio.coerceIn(SCALE_MIN, SCALE_MAX)
}
