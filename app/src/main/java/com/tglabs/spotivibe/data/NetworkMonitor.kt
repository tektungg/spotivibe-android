package com.tglabs.spotivibe.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.tglabs.spotivibe.domain.isUsableNetwork
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Status konektivitas sebagai aliran, supaya [LyricsRepository] dan
 * [PlaybackController] bisa diuji dengan nilai yang dikendalikan test.
 */
interface NetworkStatusSource {
    val isOnline: StateFlow<Boolean>

    /** Untuk konstruksi tanpa monitor, misalnya di test lama: selalu online. */
    object AlwaysOnline : NetworkStatusSource {
        override val isOnline: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
    }
}

/**
 * Mengikuti default network lewat [ConnectivityManager.registerDefaultNetworkCallback].
 *
 * Default network, bukan semua network: itu yang benar-benar dipakai OkHttp.
 * Pindah dari wifi ke seluler memicu `onCapabilitiesChanged` untuk network
 * baru, dan `onLost` hanya datang kalau memang tidak ada pengganti.
 *
 * Callback didaftarkan sekali seumur proses dan tidak pernah dilepas. Monitor
 * ini singleton di application container, sama seperti [PlaybackController]
 * yang membacanya, jadi tidak ada siklus hidup yang lebih pendek untuk diikuti.
 *
 * Kalau pendaftaran gagal (ROM aneh, SecurityException), statusnya tetap online.
 * Salah mengira online hanya membuat probe gagal lalu jatuh ke lirik basi;
 * salah mengira offline membuat app berhenti mengambil lirik selamanya.
 */
class AndroidNetworkMonitor(context: Context) : NetworkStatusSource {

    private val cm = context.getSystemService(ConnectivityManager::class.java)

    private val _isOnline = MutableStateFlow(initialOnline())
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        runCatching {
            cm?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    publish(caps.usable())
                }

                override fun onLost(network: Network) {
                    publish(false)
                }
            })
        }.onFailure { Log.w(TAG, "Gagal mendaftarkan callback jaringan: ${it.message}") }
        Log.d(TAG, "online=${_isOnline.value} (awal)")
    }

    private fun initialOnline(): Boolean = runCatching {
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork) ?: return@runCatching false
        caps.usable()
    }.getOrDefault(true)

    private fun publish(online: Boolean) {
        if (_isOnline.value == online) return
        _isOnline.value = online
        Log.d(TAG, "online=$online")
    }

    private fun NetworkCapabilities.usable(): Boolean = isUsableNetwork(
        hasInternet = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
        captivePortal = hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL),
    )

    private companion object {
        const val TAG = "NetworkMonitor"
    }
}
