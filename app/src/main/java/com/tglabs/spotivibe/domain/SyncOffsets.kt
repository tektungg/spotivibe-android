package com.tglabs.spotivibe.domain

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

/**
 * Tiga lapis koreksi sync lirik, dijumlahkan jadi satu offset.
 *
 * Satu offset global tidak cukup karena penyebab meleset ada di dua tempat yang
 * berbeda dan saling lepas:
 *
 * - **Perangkat output.** Speaker HP hampir tanpa jeda, sedangkan Bluetooth
 *   mobil atau earbud bisa 150 sampai 300 ms. User yang menyetel offset global
 *   untuk mobilnya mendapati lirik terlalu cepat begitu kembali ke speaker HP.
 * - **File lirik satu lagu.** Satu file LRC yang telat 800 ms tidak boleh
 *   memaksa semua lagu lain ikut digeser.
 *
 * Offset global yang lama tetap ada sebagai lapis ketiga, supaya setelan user
 * dari versi sebelumnya tidak hilang.
 */

/** Batas per lapis. Sama dengan offset global supaya ketiga slider sebanding. */
const val SONG_OFFSET_MIN_MS = -2000
const val SONG_OFFSET_MAX_MS = 2000
const val DEVICE_OFFSET_MIN_MS = -2000
const val DEVICE_OFFSET_MAX_MS = 2000

/**
 * Batas jumlah ketiganya. Di atas lima detik, yang salah bukan sync-nya
 * melainkan liriknya (versi lagu lain), dan itu urusan pencarian lirik.
 */
const val TOTAL_OFFSET_LIMIT_MS = 5000

/** Langkah tombol −/+ di dialog sync per lagu. */
const val SONG_OFFSET_STEP_MS = 100

fun clampSongOffsetMs(nilai: Int?): Int =
    (nilai ?: 0).coerceIn(SONG_OFFSET_MIN_MS, SONG_OFFSET_MAX_MS)

fun clampDeviceOffsetMs(nilai: Int?): Int =
    (nilai ?: 0).coerceIn(DEVICE_OFFSET_MIN_MS, DEVICE_OFFSET_MAX_MS)

/**
 * Offset yang benar-benar dipakai engine. Tiap lapis dijepit dulu, baru
 * jumlahnya dijepit, supaya nilai rusak di satu lapis tidak bisa menelan batas
 * lapis lain.
 */
fun combinedOffsetMs(globalMs: Int, deviceMs: Int, songMs: Int): Int {
    val total = clampLyricsOffsetMs(globalMs) + clampDeviceOffsetMs(deviceMs) +
        clampSongOffsetMs(songMs)
    return total.coerceIn(-TOTAL_OFFSET_LIMIT_MS, TOTAL_OFFSET_LIMIT_MS)
}

/** Ketiga lapis plus jumlahnya, untuk UI. */
@Immutable
data class SyncOffsets(
    val globalMs: Int = 0,
    val deviceMs: Int = 0,
    val songMs: Int = 0,
    /** Nama perangkat output aktif, null kalau belum diketahui. */
    val outputLabel: String? = null,
) {
    val totalMs: Int get() = combinedOffsetMs(globalMs, deviceMs, songMs)
}

/** Golongan perangkat output, diurutkan dari yang paling mungkin memutar musik. */
enum class OutputKind(val priority: Int) {
    Bluetooth(0),
    Wired(1),
    External(2),
    BuiltIn(3),
}

/** Satu perangkat output yang dilaporkan sistem, sudah lepas dari API Android. */
data class OutputDevice(
    val kind: OutputKind,
    /** Kode tipe asli `AudioDeviceInfo.TYPE_*`, ikut membentuk kunci. */
    val typeCode: Int,
    val name: String,
)

/** Perangkat output yang offset-nya sedang berlaku. */
@Immutable
data class AudioOutput(val key: String, val label: String)

/**
 * Perangkat yang kemungkinan besar sedang memutar musik.
 *
 * Android tidak memberi tahu aplikasi lain perangkat mana yang dipakai
 * Spotify. Tapi routing media bawaan Android selalu memilih urutan yang sama:
 * Bluetooth yang tersambung menang, lalu headset kabel atau USB, lalu output
 * eksternal (bus audio mobil, HDMI, line out), dan speaker internal paling
 * akhir. Kalau dua perangkat segolongan, urutan dari sistem dipertahankan.
 */
fun pickMediaOutput(devices: List<OutputDevice>): OutputDevice? =
    devices.minByOrNull { it.kind.priority }

/**
 * Kunci penyimpanan offset untuk satu perangkat.
 *
 * Dari tipe dan nama produk, BUKAN alamat MAC: membaca alamat Bluetooth butuh
 * izin `BLUETOOTH_CONNECT` di Android 12+, dan nama sudah cukup membedakan
 * mobil dari earbud. Di-hash supaya nama perangkat tidak tersimpan mentah di
 * DataStore, dan dipotong karena ini cuma kunci, bukan bukti integritas.
 */
fun outputKey(device: OutputDevice): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("${device.typeCode}:${device.name.trim()}".toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }.take(OUTPUT_KEY_LENGTH)
}

/** Label untuk UI. Nama kosong (speaker internal sering begitu) diberi nama golongan. */
fun outputLabel(device: OutputDevice): String = device.name.trim().ifBlank {
    when (device.kind) {
        OutputKind.Bluetooth -> "Bluetooth"
        OutputKind.Wired -> "Headphones"
        OutputKind.External -> "External audio"
        OutputKind.BuiltIn -> "Phone speaker"
    }
}

fun AudioOutput(device: OutputDevice): AudioOutput =
    AudioOutput(key = outputKey(device), label = outputLabel(device))

/**
 * Rakit ketiga lapis jadi satu aliran [SyncOffsets].
 *
 * Offset perangkat dan lagu dibaca ulang setiap kali perangkat atau lagunya
 * BERGANTI (`flatMapLatest`), bukan dibaca sekali. Tanpa itu, menyambung
 * Bluetooth mobil di tengah lagu tidak mengubah apa-apa sampai app dibuka ulang.
 * Lagu atau perangkat yang tidak diketahui menyumbang 0.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun syncOffsetsFlow(
    globalMs: Flow<Int>,
    output: Flow<AudioOutput?>,
    trackId: Flow<String?>,
    deviceOffsetMs: (String) -> Flow<Int>,
    songOffsetMs: (String) -> Flow<Int>,
): Flow<SyncOffsets> {
    val device: Flow<Pair<String?, Int>> = output.distinctUntilChanged().flatMapLatest { out ->
        if (out == null) flowOf(null to 0) else deviceOffsetMs(out.key).map { out.label to it }
    }
    val song: Flow<Int> = trackId.distinctUntilChanged().flatMapLatest { id ->
        if (id.isNullOrBlank()) flowOf(0) else songOffsetMs(id)
    }
    return combine(globalMs, device, song) { global, (label, deviceMs), songMs ->
        SyncOffsets(globalMs = global, deviceMs = deviceMs, songMs = songMs, outputLabel = label)
    }.distinctUntilChanged()
}

private const val OUTPUT_KEY_LENGTH = 16
