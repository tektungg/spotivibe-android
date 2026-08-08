package com.tglabs.spotivibe.domain

/**
 * Kapan harus mencoba menyambung ulang ke Spotify, dan setelah menunggu berapa
 * lama.
 *
 * Sebelumnya tidak ada kebijakan sama sekali. Siklus koneksi dimiliki
 * `MainActivity`: Activity yang memanggil `tryAutoConnect`, dan Activity juga
 * yang menghidup-matikan foreground service berdasarkan `connectionState`.
 * Akibatnya, begitu App Remote putus (Spotify di-kill, MIUI membunuh proses,
 * user swipe Spotify dari recents), state jatuh ke Disconnected, service
 * memanggil `stopSelf()`, notification hilang, dan tidak ada apapun yang
 * mencoba menyambung lagi. User harus membuka app ini secara manual. Untuk
 * sesuatu yang posisinya companion yang hidup di background, itu kebalikan
 * dari yang seharusnya.
 */
enum class LinkState { Disconnected, Connecting, Connected, Error }

sealed interface ReconnectDecision {
    /** Tidak ada sesi. Jangan sambung, dan companion boleh berhenti. */
    data object Idle : ReconnectDecision

    /** Sudah tersambung atau sedang menyambung. Tidak ada yang perlu dilakukan. */
    data object AlreadyLive : ReconnectDecision

    /** Coba lagi setelah [delayMs]. Nol berarti langsung. */
    data class RetryAfter(val delayMs: Long) : ReconnectDecision
}

/**
 * Backoff eksponensial: percobaan pertama langsung, lalu 1, 2, 4, 8 detik dan
 * seterusnya, dipotong di [MAX_RECONNECT_DELAY_MS].
 *
 * Batas atasnya 5 menit, bukan beberapa detik, karena kegagalan yang beruntun
 * biasanya berarti Spotify memang tidak jalan. Mencoba tiap detik dalam kondisi
 * itu cuma membakar baterai. Lima menit tetap cukup cepat supaya companion
 * pulih sendiri tanpa user menyentuh apapun setelah membuka Spotify lagi.
 */
fun reconnectDelayMs(consecutiveFailures: Int): Long {
    require(consecutiveFailures >= 0) { "consecutiveFailures tidak boleh negatif" }
    if (consecutiveFailures == 0) return 0L
    val exponent = (consecutiveFailures - 1).coerceAtMost(30)
    val delay = 1_000L shl exponent.coerceAtMost(20)
    return delay.coerceAtMost(MAX_RECONNECT_DELAY_MS)
}

const val MAX_RECONNECT_DELAY_MS = 300_000L

/**
 * Keputusan tunggal yang dijalankan supervisor tiap putaran.
 *
 * Sengaja TIDAK ada keadaan "menyerah" untuk kegagalan transport. Selama masih
 * ada sesi, companion terus mencoba, cuma makin jarang. Satu-satunya cara
 * berhenti adalah sesinya hilang, dan itu terjadi lewat logout atau saat
 * Spotify mencabut otorisasi. Menyerah karena Spotify kebetulan sedang tidak
 * jalan akan mengembalikan persis masalah yang sedang diperbaiki.
 */
fun decideReconnect(
    hasSession: Boolean,
    state: LinkState,
    consecutiveFailures: Int,
): ReconnectDecision = when {
    !hasSession -> ReconnectDecision.Idle
    state == LinkState.Connected || state == LinkState.Connecting -> ReconnectDecision.AlreadyLive
    else -> ReconnectDecision.RetryAfter(reconnectDelayMs(consecutiveFailures))
}
