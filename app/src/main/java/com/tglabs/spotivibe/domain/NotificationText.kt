package com.tglabs.spotivibe.domain

/**
 * Penyusunan teks notifikasi, murni.
 *
 * Ditarik keluar dari `SpotivibeNotificationService` karena di sana ia terjepit
 * di antara `PendingIntent`, `NotificationManager`, dan `Service`, sehingga
 * tidak ada satu pun cabangnya yang bisa dijalankan tanpa perangkat. Padahal
 * inilah permukaan yang paling sering dilihat user: notifikasi di lock screen
 * adalah tempat lirik dibaca saat layar terkunci.
 */

/** Tiga baris untuk tampilan karaoke: sebelum, aktif, sesudah. */
data class LyricWindow(
    val prev: String?,
    val current: String?,
    val next: String?,
)

/**
 * Ambil jendela tiga baris di sekitar [index].
 *
 * Aman terhadap tepi. Baris pertama tidak punya pendahulu dan baris terakhir
 * tidak punya penerus, dan index `-1` berarti lagu masih di intro sehingga
 * belum ada baris aktif sama sekali.
 */
fun lyricWindow(lines: List<String>?, index: Int): LyricWindow = LyricWindow(
    prev = lines?.getOrNull(index - 1),
    current = lines?.getOrNull(index),
    next = lines?.getOrNull(index + 1),
)

/** Teks siap pakai untuk notifikasi. */
data class NotificationText(
    val title: String,
    val subtitle: String,
    val prev: String,
    val current: String,
    val next: String,
)

const val NOTIF_FALLBACK_TITLE = "Spotivibe"
const val NOTIF_FALLBACK_SUBTITLE = "Loading…"
const val NOTIF_RECONNECTING = "Reconnecting to Spotify…"

/** Penanda baris aktif. Baris kosong tetap memakan ruang supaya tinggi notifikasi stabil. */
const val NOTIF_ACTIVE_MARK = "▸ "
const val NOTIF_BLANK_LINE = "♪"

/**
 * Rakit teks notifikasi.
 *
 * @param isReconnecting saat true, baris aktif diganti keterangan. Tanpa ini
 *   user menatap lirik basi yang berhenti bergerak tanpa tahu kenapa, dan itu
 *   terlihat seperti app yang hang.
 */
fun notificationText(
    title: String?,
    artist: String?,
    window: LyricWindow,
    isReconnecting: Boolean,
): NotificationText {
    val judul = title?.takeIf { it.isNotBlank() } ?: NOTIF_FALLBACK_TITLE
    // Artis yang null berarti belum ada lagu sama sekali; artis yang kosong
    // berarti lagunya ada tapi metadatanya tidak lengkap. Keduanya berbeda dan
    // hanya yang pertama yang layak disebut "Loading".
    val subjudul = artist ?: NOTIF_FALLBACK_SUBTITLE

    val barisAktif = window.current?.takeIf { it.isNotBlank() } ?: NOTIF_BLANK_LINE

    return NotificationText(
        title = judul,
        subtitle = subjudul,
        prev = window.prev?.takeIf { it.isNotBlank() }.orEmpty(),
        current = if (isReconnecting) NOTIF_RECONNECTING else NOTIF_ACTIVE_MARK + barisAktif,
        next = window.next?.takeIf { it.isNotBlank() }.orEmpty(),
    )
}
