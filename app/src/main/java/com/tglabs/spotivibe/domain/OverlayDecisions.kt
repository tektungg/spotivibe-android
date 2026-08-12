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
 * Posisi Y baru setelah digeser, dijepit ke dalam layar.
 *
 * X sengaja tidak ikut: overlay selebar layar penuh, jadi menggesernya
 * mendatar hanya akan menyisakan pita kosong di satu sisi.
 *
 * @param tinggiLayar tinggi layar dalam piksel.
 * @param tinggiOverlay tinggi jendela overlay dalam piksel.
 */
fun clampOverlayY(ySekarang: Int, delta: Int, tinggiLayar: Int, tinggiOverlay: Int): Int {
    val maxY = (tinggiLayar - tinggiOverlay).coerceAtLeast(0)
    return (ySekarang + delta).coerceIn(0, maxY)
}

/**
 * Posisi perlu dianimasikan kembali ke dalam layar saat jari dilepas.
 *
 * Kalau sudah di dalam batas, animasi hanya membuang frame untuk perpindahan
 * sejauh nol piksel.
 */
fun needsSettleAnimation(yTarget: Int, ySekarang: Int): Boolean = yTarget != ySekarang
