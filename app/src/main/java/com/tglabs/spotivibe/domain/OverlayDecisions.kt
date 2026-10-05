package com.tglabs.spotivibe.domain

/**
 * Keputusan murni milik overlay melayang.
 *
 * Overlay dijalankan oleh Service, digambar lewat `WindowManager`, dan
 * posisinya dihitung dari ukuran layar nyata. Tidak satu pun bisa dijalankan di
 * JVM, padahal overlay sudah pernah membuat app crash fatal sekali karena
 * asumsi yang tidak diperiksa siapa pun.
 */

/** Apa yang harus dilakukan terhadap jendela overlay. */
enum class OverlayAction { Buat, Sembunyikan, DiamSaja }

/**
 * Terjemahkan preferensi jadi tindakan.
 *
 * Preferensi bisa memancar ulang dengan nilai yang sama, misalnya saat posisi
 * berubah sementara flag enabled tidak. Membuat jendela lagi di situ akan
 * menumpuk dua overlay yang saling menutupi dan hanya satu yang bisa ditutup.
 */
fun overlayAction(enabled: Boolean, sudahAda: Boolean): OverlayAction = when {
    enabled && !sudahAda -> OverlayAction.Buat
    enabled -> OverlayAction.DiamSaja
    sudahAda -> OverlayAction.Sembunyikan
    else -> OverlayAction.DiamSaja
}

/**
 * Overlay gagal tampil, jadi preferensinya harus dimatikan.
 *
 * Tanpa ini, izin yang dicabut atau keanehan OEM membuat setiap penyambungan
 * berikutnya mencoba menampilkan overlay yang tidak akan pernah muncul, dan
 * gagalnya berulang tanpa henti.
 */
fun shouldDisableOverlayAfterFailure(tampilBerhasil: Boolean): Boolean = !tampilBerhasil

/**
 * Posisi Y baru setelah digeser, dijepit ke dalam AREA AMAN layar.
 *
 * X sengaja tidak ikut: overlay selebar area aman, jadi menggesernya mendatar
 * hanya akan menyisakan pita kosong di satu sisi.
 *
 * Dulu batasnya seluruh layar, 0 sampai tinggi layar. Posisi awal default 0
 * berarti overlay muncul DI BAWAH status bar, dan di head unit bisa diseret ke
 * bawah navigation bar sampai tombolnya tidak bisa ditekan.
 *
 * Kalau overlay lebih tinggi dari area aman, ia menempel di batas atas: bagian
 * atas (judul + tombol tutup) lebih penting tetap terlihat daripada bawahnya.
 *
 * @param tinggiLayar tinggi layar dalam piksel.
 * @param tinggiOverlay tinggi jendela overlay dalam piksel.
 * @param insetAtas tinggi bar sistem / cutout di atas, piksel. Default 0
 *   sama persis dengan perilaku lama.
 * @param insetBawah tinggi bar sistem di bawah, piksel.
 */
fun clampOverlayY(
    ySekarang: Int,
    delta: Int,
    tinggiLayar: Int,
    tinggiOverlay: Int,
    insetAtas: Int = 0,
    insetBawah: Int = 0,
): Int {
    val minY = insetAtas.coerceAtLeast(0)
    val maxY = (tinggiLayar - tinggiOverlay - insetBawah.coerceAtLeast(0)).coerceAtLeast(minY)
    return (ySekarang + delta).coerceIn(minY, maxY)
}

/** Posisi X dan lebar jendela overlay, piksel. */
data class OverlayHorizontalBounds(val x: Int, val width: Int)

/**
 * Overlay mengisi lebar area aman, bukan seluruh layar.
 *
 * Head unit sering menaruh navigation bar di sisi KIRI layar landscape.
 * Dengan lebar `MATCH_PARENT` di x 0, tombol previous di ujung kiri overlay
 * berada tepat di bawah bar itu.
 *
 * Inset yang tidak masuk akal (jumlahnya menghabiskan seluruh lebar) diabaikan:
 * overlay selebar layar masih lebih berguna daripada jendela selebar 0.
 */
fun overlayHorizontalBounds(lebarLayar: Int, insetKiri: Int, insetKanan: Int): OverlayHorizontalBounds {
    val kiri = insetKiri.coerceAtLeast(0)
    val kanan = insetKanan.coerceAtLeast(0)
    val lebar = lebarLayar - kiri - kanan
    if (lebar <= 0) return OverlayHorizontalBounds(x = 0, width = lebarLayar.coerceAtLeast(0))
    return OverlayHorizontalBounds(x = kiri, width = lebar)
}

/**
 * Posisi perlu dianimasikan kembali ke dalam layar saat jari dilepas.
 *
 * Kalau sudah di dalam batas, animasi hanya membuang frame untuk perpindahan
 * sejauh nol piksel.
 */
fun needsSettleAnimation(yTarget: Int, ySekarang: Int): Boolean = yTarget != ySekarang
