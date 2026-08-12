package com.tglabs.spotivibe.domain

/**
 * Keputusan murni milik lapisan koneksi Spotify.
 *
 * Ditarik keluar dari `SpotifyConnection` karena di sanalah setiap keputusannya
 * terkubur sebagai method privat di kelas yang menahan `SpotifyAppRemote`,
 * `Handler`, dan `Context`. Tidak ada satu pun yang bisa diuji tanpa perangkat,
 * padahal justru di situ dua bug produksi lahir: redirect sah yang dibuang
 * diam-diam, dan koneksi zombie yang tetap mengaku Connected setelah app
 * Spotify mati.
 *
 * Semuanya di sini tidak menyentuh Android sama sekali.
 */

/** Fase koneksi, cermin dari `SpotifyConnection.ConnectionState` tanpa pesannya. */
enum class ConnPhase { Disconnected, Connecting, Connected, Error }

/**
 * Kegagalan bind ini soal otorisasi, bukan soal teknis lain.
 *
 * Bedanya menentukan nasib sesi: masalah otorisasi berarti grant sudah tidak
 * berlaku dan kredensial lokal harus dibuang, sementara kegagalan lain cuma
 * perlu ditampilkan. Salah membedakannya berarti memaksa login ulang setiap
 * kali app Spotify kebetulan belum jalan.
 */
fun authIssueOf(exceptionName: String, message: String?): Boolean {
    val pesan = message.orEmpty().lowercase()
    return "NotAuthorized" in exceptionName ||
        "NotLoggedIn" in exceptionName ||
        "authorization" in pesan
}

/**
 * Auto-connect saat startup boleh dimulai.
 *
 * Ditolak saat sudah Connected atau sedang Connecting. Tanpa penjagaan ini,
 * kembali ke app saat bind masih berjalan memulai bind KEDUA, dan callback yang
 * datang belakangan menimpa state milik yang pertama.
 */
fun shouldStartAutoConnect(phase: ConnPhase): Boolean =
    phase != ConnPhase.Connected && phase != ConnPhase.Connecting

/**
 * User kembali ke app tanpa membawa redirect: menekan back di layar consent,
 * menutup tab, atau redirect ditelan komponen lain.
 *
 * Hanya berlaku saat Connecting. Tanpa ini UI tertinggal di Connecting
 * selamanya, tombol Connect ikut hilang, dan satu-satunya jalan keluar adalah
 * menutup paksa app.
 */
fun shouldAbandonToDisconnected(phase: ConnPhase): Boolean = phase == ConnPhase.Connecting

/**
 * Timeout hanya berarti kalau kita memang masih menunggu. Callback yang datang
 * tepat sebelum timer menyala sudah memindahkan fase, dan melaporkan timeout di
 * situ akan menimpa koneksi yang sebenarnya berhasil.
 */
fun shouldReportTimeout(phase: ConnPhase): Boolean = phase == ConnPhase.Connecting

/**
 * Redirect ditolak sementara kita sedang menunggu login.
 *
 * Diam di sini mengunci UI di Connecting tanpa pesan apa pun. Di luar fase itu,
 * redirect asing memang harus diabaikan tanpa efek samping.
 */
fun shouldReportRejectedRedirect(phase: ConnPhase): Boolean = phase == ConnPhase.Connecting

/**
 * Langganan player state berhenti dan itu berarti link ke app Spotify mati.
 *
 * Dua penjagaan, dan keduanya perlu. [tearingDown] menyaring pemutusan yang kita
 * lakukan sendiri, karena teardown juga memicu callback yang sama. Fase Connected
 * menyaring callback susulan yang datang setelah state sudah turun.
 *
 * Tanpa ini koneksi jadi zombie: fase tetap Connected padahal tidak ada event
 * yang masuk lagi selamanya.
 */
fun shouldHandleRemoteLost(tearingDown: Boolean, phase: ConnPhase): Boolean =
    !tearingDown && phase == ConnPhase.Connected

/**
 * Ambil album art hanya kalau URI-nya berubah.
 *
 * Tanpa penjagaan ini setiap event player state memicu unduhan gambar, dan
 * event itu datang berkali-kali per detik selama lagu berjalan.
 */
fun shouldFetchAlbumArt(newUri: String?, lastUri: String?): Boolean = newUri != lastUri

/**
 * Pesan kegagalan bind yang dilihat user.
 *
 * Diuji karena pesan kosong pernah membuat layar error tampak seperti app yang
 * menggantung: nama exception ada tapi keterangannya hilang.
 */
fun connectFailureMessage(exceptionName: String, message: String?): String {
    val keterangan = message.orEmpty().ifBlank { "unknown" }
    return "$exceptionName: $keterangan"
}
