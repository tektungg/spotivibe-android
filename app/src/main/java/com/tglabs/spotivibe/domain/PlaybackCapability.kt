package com.tglabs.spotivibe.domain

/**
 * Boleh tidak kita mengontrol playback lewat App Remote?
 *
 * Menggantikan `isPremium: Boolean` yang lama. Boolean itu punya cacat
 * struktural: nilai awalnya `false`, dan `false` dipakai untuk dua hal yang
 * sangat berbeda -- "sudah dicek, akunnya Free" dan "belum tahu". Karena
 * pengecekannya lewat Spotify Web API dan token lama tidak bisa di-refresh,
 * "belum tahu" jadi permanen, dan user Premium melihat banner
 * "PREMIUM REQUIRED" plus kehilangan tombol transport.
 *
 * Tiga keadaan memisahkan ketidaktahuan dari bukti:
 *
 * - [Unknown]  belum ada bukti. Tampilkan kontrol secara optimistis. Kontrol
 *              jalan lewat App Remote, bukan Web API, jadi menyembunyikannya
 *              karena satu panggilan Web API gagal itu salah alamat.
 * - [Full]     ada bukti positif kontrol boleh dipakai.
 * - [Restricted] ada bukti positif kontrol ditolak: `/me` bilang non-premium,
 *              atau App Remote menolak perintah kita.
 */
enum class PlaybackCapability {
    Unknown,
    Full,
    Restricted;

    /** Kontrol transport ditampilkan? Optimistis saat belum tahu. */
    val showsTransport: Boolean get() = this != Restricted

    /** Banner "butuh Premium" ditampilkan? Hanya kalau sudah terbukti. */
    val showsUpgradeNotice: Boolean get() = this == Restricted
}

/**
 * Turunkan capability dari bukti yang ada.
 *
 * @param product field `product` dari Spotify Web API `/me`, atau null kalau
 *   panggilannya belum berhasil.
 * @param remoteRejectedControl true kalau App Remote pernah menolak perintah
 *   play/pause/next/seek kita. Ini bukti paling kuat karena datang dari jalur
 *   yang benar-benar dipakai untuk mengontrol.
 *
 * Penolakan App Remote menang atas `/me`: kalau jalur kontrol yang sebenarnya
 * bilang tidak boleh, label akun apapun tidak relevan.
 */
fun resolveCapability(product: String?, remoteRejectedControl: Boolean): PlaybackCapability = when {
    remoteRejectedControl -> PlaybackCapability.Restricted
    product == null -> PlaybackCapability.Unknown
    product.equals("premium", ignoreCase = true) -> PlaybackCapability.Full
    else -> PlaybackCapability.Restricted
}
