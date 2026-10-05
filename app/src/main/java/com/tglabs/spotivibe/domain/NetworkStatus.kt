package com.tglabs.spotivibe.domain

/**
 * Keputusan soal konektivitas, murni.
 *
 * Kelas Android-nya (`AndroidNetworkMonitor`) hanya meneruskan bit kapabilitas
 * dari `ConnectivityManager`. Semua yang bisa salah ditaruh di sini supaya
 * teruji di JVM tanpa perangkat.
 */

/**
 * Jaringan ini layak dipakai untuk memanggil LRCLIB dan Spotify Web API?
 *
 * Sengaja TIDAK mensyaratkan `NET_CAPABILITY_VALIDATED`. Validasi Android
 * bergantung pada endpoint cek milik Google, yang diblokir di sebagian jaringan
 * kantor dan beberapa negara. Mensyaratkannya membuat app menganggap dirinya
 * offline selamanya di jaringan yang sebenarnya bisa menjangkau LRCLIB, dan
 * itu lebih buruk daripada kebalikannya: kalau kita salah mengira online,
 * probe gagal lalu lirik basi tetap dipakai lewat [resolveWithStale].
 *
 * Yang dipakai hanya bukti positif: tidak ada kapabilitas internet, atau
 * Android sudah MENDETEKSI captive portal (wifi hotel yang menunggu login).
 */
fun isUsableNetwork(hasInternet: Boolean, captivePortal: Boolean): Boolean =
    hasInternet && !captivePortal

/**
 * Ambil ulang lirik lagu yang sedang diputar saat jaringan kembali?
 *
 * Hanya pada TRANSISI offline ke online, dan hanya kalau lirik yang tampil
 * memang gagal dimuat karena jaringan. [LyricsState.Loading] dilewati karena
 * fetch sedang berjalan; [LyricsState.NotFound] dan [LyricsState.Ready] sudah
 * merupakan jawaban, jadi menanyakannya lagi cuma membuang kuota LRCLIB.
 */
fun shouldRefetchOnReconnect(wasOnline: Boolean, isOnline: Boolean, state: LyricsState): Boolean =
    !wasOnline && isOnline &&
        (state is LyricsState.Offline || state is LyricsState.Unavailable)
