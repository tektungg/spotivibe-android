package com.tglabs.spotivibe.domain

/**
 * Gestur di cover: geser untuk pindah lagu, ketuk dua kali untuk play/pause.
 *
 * Di mobil, tombol transport kecil dan jauh dari jari; cover atau header
 * adalah target terbesar di layar. Arahnya mengikuti kebiasaan player musik:
 * geser ke kiri membawa lagu berikutnya masuk dari kanan.
 */
enum class CoverSwipe { Next, Previous }

/**
 * Lagu tujuan untuk geseran sejauh [totalDx] piksel (negatif = ke kiri), atau
 * null kalau belum melewati [thresholdPx]. Ambangnya mencegah geseran kecil
 * saat menggulir atau menyentuh tidak sengaja ikut memindah lagu.
 */
fun swipeAction(totalDx: Float, thresholdPx: Float): CoverSwipe? = when {
    totalDx <= -thresholdPx -> CoverSwipe.Next
    totalDx >= thresholdPx -> CoverSwipe.Previous
    else -> null
}

/** Geseran yang boleh dijalankan, mengingat apa yang diizinkan player. */
fun allowedSwipe(swipe: CoverSwipe?, controls: PlayerControls): CoverSwipe? = when (swipe) {
    CoverSwipe.Next -> swipe.takeIf { controls.canSkipNext }
    CoverSwipe.Previous -> swipe.takeIf { controls.canSkipPrevious }
    null -> null
}
