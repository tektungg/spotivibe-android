package com.tglabs.spotivibe.data

import android.content.Context
import android.util.Log
import com.tglabs.spotivibe.data.auth.SpotifyAuthRepository
import com.tglabs.spotivibe.domain.LinkState
import com.tglabs.spotivibe.domain.ReconnectDecision
import com.tglabs.spotivibe.domain.decideReconnect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Terjemahkan state koneksi jadi bentuk yang dipahami kebijakan reconnect. */
fun SpotifyConnection.ConnectionState.toLinkState(): LinkState = when (this) {
    SpotifyConnection.ConnectionState.Connected -> LinkState.Connected
    SpotifyConnection.ConnectionState.Connecting -> LinkState.Connecting
    SpotifyConnection.ConnectionState.Disconnected -> LinkState.Disconnected
    is SpotifyConnection.ConnectionState.Error -> LinkState.Error
}

/**
 * Pemilik siklus hidup koneksi Spotify.
 *
 * Sebelumnya `MainActivity` yang memegang peran ini: Activity memanggil
 * `tryAutoConnect`, dan Activity juga yang menghidup-matikan foreground service
 * berdasarkan `connectionState`. Dua akibatnya buruk. Pertama, koneksi putus
 * berarti service berhenti dan notification hilang, tanpa ada yang mencoba
 * menyambung lagi. Kedua, service yang dibangkitkan sistem lewat START_STICKY
 * hidup tanpa ada apapun yang menyambungkannya ke Spotify, jadi cuma jadi
 * notification kosong.
 *
 * Sekarang supervisor ini hidup di application scope dan terus menjaga koneksi
 * selama masih ada sesi. Service tinggal menampilkan hasilnya dan menjaga
 * proses tetap hidup.
 *
 * Activity TETAP yang memulai service, karena Android 12+ melarang menyalakan
 * foreground service dari background. Yang pindah ke sini adalah keputusan
 * kapan menyambung dan kapan berhenti, bukan siapa yang menyalakan service.
 */
class SpotifySessionSupervisor(
    private val connection: SpotifyConnection,
    private val authRepository: SpotifyAuthRepository,
    private val scope: CoroutineScope,
) {

    /**
     * Apakah companion masih perlu jalan.
     *
     * [CompanionState.Undetermined] penting: nilai awal TIDAK boleh Stopped.
     * Service mulai mengoleksi flow ini sebelum supervisor sempat berputar
     * sekali, dan nilai awal Stopped akan membuat service langsung membunuh
     * dirinya sendiri beberapa milidetik setelah dinyalakan.
     *
     * Jadi Stopped hanya kalau sesinya benar-benar hilang (logout, atau
     * otorisasi dicabut Spotify). Koneksi putus TIDAK membuatnya Stopped: itu
     * kondisi yang harus dipulihkan, bukan alasan mematikan notification.
     */
    enum class CompanionState { Undetermined, Running, Stopped }

    private val _shouldRun = MutableStateFlow(CompanionState.Undetermined)
    val shouldRun: StateFlow<CompanionState> = _shouldRun.asStateFlow()

    /** Sedang dalam masa tunggu sebelum mencoba lagi. Dipakai untuk teks notification. */
    private val _reconnecting = MutableStateFlow(false)
    val reconnecting: StateFlow<Boolean> = _reconnecting.asStateFlow()

    private var job: Job? = null

    /** Idempoten. Panggilan kedua saat sudah jalan tidak melakukan apa-apa. */
    fun start(context: Context) {
        if (job?.isActive == true) return
        val appContext = context.applicationContext
        Log.d(TAG, "start()")
        job = scope.launch { supervise(appContext) }
    }

    fun stop() {
        Log.d(TAG, "stop()")
        job?.cancel()
        job = null
        _shouldRun.value = CompanionState.Stopped
        _reconnecting.value = false
    }

    /** Jembatan ke keputusan murni di domain. */
    private fun companionState(decision: ReconnectDecision): CompanionState =
        when (com.tglabs.spotivibe.domain.companionFor(decision)) {
            com.tglabs.spotivibe.domain.Companion.Undetermined -> CompanionState.Undetermined
            com.tglabs.spotivibe.domain.Companion.Running -> CompanionState.Running
            com.tglabs.spotivibe.domain.Companion.Stopped -> CompanionState.Stopped
        }

    private suspend fun supervise(context: Context) {
        var consecutiveFailures = 0

        while (scope.isActive) {
            val decision = decideReconnect(
                hasSession = authRepository.hasSession(),
                state = connection.connectionState.value.toLinkState(),
                consecutiveFailures = consecutiveFailures,
            )

            when (decision) {
                ReconnectDecision.Idle -> {
                    _shouldRun.value = companionState(decision)
                    _reconnecting.value = com.tglabs.spotivibe.domain.reconnectingFor(decision)
                    consecutiveFailures = 0
                    Log.d(TAG, "Tidak ada sesi — menunggu login")
                    // Menggantung sampai user login lagi. Tidak ada polling.
                    authRepository.authState.first {
                        it is SpotifyAuthRepository.AuthState.SignedIn
                    }
                }

                ReconnectDecision.AlreadyLive -> {
                    _shouldRun.value = companionState(decision)
                    _reconnecting.value = com.tglabs.spotivibe.domain.reconnectingFor(decision)
                    consecutiveFailures = 0
                    // Tidur sampai link keluar dari kondisi hidup.
                    connection.connectionState.first {
                        com.tglabs.spotivibe.domain.linkNeedsReconnect(it.toLinkState())
                    }
                    Log.d(TAG, "Link putus — akan menyambung ulang")
                }

                is ReconnectDecision.RetryAfter -> {
                    _shouldRun.value = companionState(decision)
                    if (com.tglabs.spotivibe.domain.reconnectingFor(decision)) {
                        _reconnecting.value = true
                        Log.d(
                            TAG,
                            "Percobaan ${consecutiveFailures + 1} dalam ${decision.delayMs}ms",
                        )
                        // Balapan tunggu vs logout. Kalau cuma delay(), user
                        // yang logout saat backoff sudah 5 menit harus melihat
                        // notification menggantung selama sisa waktu itu.
                        withTimeoutOrNull(decision.delayMs) {
                            authRepository.authState.first {
                                it !is SpotifyAuthRepository.AuthState.SignedIn
                            }
                        }
                    }
                    // Sesi bisa saja hilang selagi kita menunggu.
                    if (!authRepository.hasSession()) continue

                    consecutiveFailures++
                    connection.tryAutoConnect(context)
                    // Tunggu percobaan ini selesai, sukses atau gagal.
                    connection.connectionState.first {
                        it.toLinkState() != LinkState.Connecting
                    }
                    _reconnecting.value = false
                }
            }
        }
    }

    companion object {
        private const val TAG = "SessionSupervisor"
    }
}
