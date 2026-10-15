package com.tglabs.spotivibe.data

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tglabs.spotivibe.domain.AudioOutput
import com.tglabs.spotivibe.domain.OutputDevice
import com.tglabs.spotivibe.domain.OutputKind
import com.tglabs.spotivibe.domain.pickMediaOutput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Perangkat output yang sedang dipakai, sebagai aliran, supaya
 * [PlaybackController] bisa diuji dengan nilai yang dikendalikan test.
 */
interface AudioOutputSource {
    /** Null kalau sistem tidak melaporkan perangkat yang dikenali. */
    val current: StateFlow<AudioOutput?>

    /** Untuk konstruksi tanpa monitor, misalnya di test: tidak pernah tahu. */
    object Unknown : AudioOutputSource {
        override val current: StateFlow<AudioOutput?> = MutableStateFlow<AudioOutput?>(null).asStateFlow()
    }
}

/**
 * Mengikuti perangkat output lewat [AudioManager.registerAudioDeviceCallback].
 *
 * Callback didaftarkan sekali seumur proses dan tidak pernah dilepas, sama
 * seperti [AndroidNetworkMonitor]: monitor ini singleton di application
 * container, jadi tidak ada siklus hidup yang lebih pendek untuk diikuti.
 * Sistem memanggil callback sekali saat pendaftaran dengan semua perangkat
 * yang sudah ada, jadi nilai awal tidak perlu dibaca terpisah.
 *
 * Kalau pendaftaran gagal, nilainya tetap null dan offset perangkat tidak
 * berlaku. Itu sama persis dengan perilaku sebelum fitur ini ada.
 */
class AndroidAudioOutputMonitor(context: Context) : AudioOutputSource {

    private val audioManager = context.getSystemService(AudioManager::class.java)

    private val _current = MutableStateFlow<AudioOutput?>(null)
    override val current: StateFlow<AudioOutput?> = _current.asStateFlow()

    init {
        runCatching {
            audioManager?.registerAudioDeviceCallback(object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = refresh()
                override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = refresh()
            }, Handler(Looper.getMainLooper()))
        }.onFailure { Log.w(TAG, "Gagal mendaftarkan callback perangkat audio: ${it.message}") }
    }

    private fun refresh() {
        val devices = runCatching {
            audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty().toList()
        }.getOrElse {
            Log.w(TAG, "Gagal membaca perangkat output: ${it.message}")
            emptyList()
        }
        val picked = pickMediaOutput(devices.mapNotNull { it.toOutputDevice() })
        val output = picked?.let { AudioOutput(it) }
        if (output == _current.value) return
        _current.value = output
        Log.d(TAG, "output=${output?.label}")
    }

    private fun AudioDeviceInfo.toOutputDevice(): OutputDevice? {
        val kind = outputKindOf(type) ?: return null
        return OutputDevice(kind = kind, typeCode = type, name = productName?.toString().orEmpty())
    }

    private companion object {
        const val TAG = "AudioOutputMonitor"
    }
}

/**
 * Golongan untuk satu `AudioDeviceInfo.TYPE_*`, atau null untuk perangkat yang
 * tidak pernah memutar musik (earpiece telepon, SCO panggilan, submix internal).
 *
 * SCO sengaja tidak dihitung Bluetooth: itu jalur suara panggilan, dan
 * menganggapnya output musik membuat offset mobil berlaku saat musik
 * sebenarnya keluar dari speaker.
 */
internal fun outputKindOf(type: Int): OutputKind? = when (type) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER,
    AudioDeviceInfo.TYPE_BLE_BROADCAST,
    AudioDeviceInfo.TYPE_HEARING_AID -> OutputKind.Bluetooth

    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_USB_ACCESSORY -> OutputKind.Wired

    AudioDeviceInfo.TYPE_BUS,
    AudioDeviceInfo.TYPE_HDMI,
    AudioDeviceInfo.TYPE_LINE_ANALOG,
    AudioDeviceInfo.TYPE_LINE_DIGITAL,
    AudioDeviceInfo.TYPE_AUX_LINE -> OutputKind.External

    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> OutputKind.BuiltIn
    else -> null
}
