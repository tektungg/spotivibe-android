package com.tglabs.spotivibe.domain

/**
 * Terjemahan keputusan reconnect jadi keadaan companion.
 *
 * `SpotifySessionSupervisor` sendiri adalah loop coroutine yang menunggu Flow,
 * jadi tidak bisa dijalankan tanpa perangkat. Yang bisa, dan yang paling
 * berbahaya kalau salah, adalah pemetaan ini: keadaan companion menentukan
 * kapan foreground service berhenti, dan berhenti pada saat yang salah berarti
 * notification hilang tanpa ada yang menyambungkan ulang.
 */

/** Keadaan companion, cermin dari `SpotifySessionSupervisor.CompanionState`. */
enum class Companion { Undetermined, Running, Stopped }

/**
 * Keadaan companion untuk sebuah keputusan reconnect.
 *
 * Hanya ketiadaan sesi yang boleh menghentikan companion. Link yang putus TIDAK
 * boleh, dan itu perbedaan yang pernah salah: dulu Disconnected memicu
 * `stopSelf()`, jadi Spotify yang dimatikan sekali berarti notification hilang
 * selamanya sampai user membuka app ini lagi.
 */
fun companionFor(decision: ReconnectDecision): Companion = when (decision) {
    ReconnectDecision.Idle -> Companion.Stopped
    ReconnectDecision.AlreadyLive -> Companion.Running
    is ReconnectDecision.RetryAfter -> Companion.Running
}

/**
 * Tanda "sedang menyambung ulang" untuk sebuah keputusan.
 *
 * Hanya menyala saat benar-benar menunggu jeda backoff. Percobaan pertama
 * jedanya nol dan terjadi seketika, jadi menyalakannya di situ cuma membuat
 * notification berkedip tanpa alasan.
 */
fun reconnectingFor(decision: ReconnectDecision): Boolean =
    decision is ReconnectDecision.RetryAfter && decision.delayMs > 0

/**
 * Hitungan kegagalan berturut-turut harus disetel ulang.
 *
 * Tanpa penyetelan ulang, koneksi yang berhasil setelah beberapa kali gagal
 * tetap membawa hitungan lamanya, dan kegagalan berikutnya langsung memakai
 * backoff terpanjang seolah tidak pernah ada keberhasilan di antaranya.
 */
fun shouldResetFailures(decision: ReconnectDecision): Boolean =
    decision !is ReconnectDecision.RetryAfter

/**
 * Link sudah keluar dari kondisi hidup, jadi perlu disambung ulang.
 *
 * Connecting ikut dihitung hidup. Menganggapnya mati akan memulai penyambungan
 * kedua di atas yang pertama yang masih berjalan.
 */
fun linkNeedsReconnect(state: LinkState): Boolean =
    state != LinkState.Connected && state != LinkState.Connecting
