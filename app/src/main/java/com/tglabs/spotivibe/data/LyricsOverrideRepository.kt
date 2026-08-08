package com.tglabs.spotivibe.data

import android.util.Log
import com.tglabs.spotivibe.domain.LyricsOverride
import com.tglabs.spotivibe.domain.LyricsOverrideStore
import com.tglabs.spotivibe.domain.LyricsResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Pemegang override lirik. SATU peta di memori untuk pilihan sesi maupun
 * permanen; bedanya cuma apakah ikut ditulis ke disk.
 *
 * ```
 * overrides
 *    ├─ startup       : diisi dari store disk
 *    ├─ tanpa centang : memori saja, hilang saat proses mati
 *    └─ dengan centang: memori DAN disk
 * ```
 *
 * Satu lookup, satu konsep. Kalau pilihan sesi dan permanen jadi dua lapis
 * terpisah, keduanya harus dijaga tetap konsisten dan itu sumber bug yang tidak
 * perlu.
 */
class LyricsOverrideRepository(
    private val store: LyricsOverrideStore,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {

    private val overrides = ConcurrentHashMap<String, LyricsResult>()

    /** Track mana yang override-nya ada di disk, bukan cuma sesi ini. */
    private val remembered = ConcurrentHashMap.newKeySet<String>()

    /**
     * Bertambah tiap kali isi override berubah. Dipakai UI untuk menyusun ulang
     * dirinya, karena ConcurrentHashMap sendiri tidak bisa diobservasi.
     */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    /** Muat yang permanen dari disk. Dipanggil sekali saat startup. */
    suspend fun restore() {
        runCatching { store.loadAll() }
            .onSuccess { daftar ->
                daftar.forEach {
                    overrides[it.trackId] = it.result
                    remembered += it.trackId
                }
                Log.d(TAG, "restore(): ${daftar.size} override permanen dimuat")
                if (daftar.isNotEmpty()) _revision.value++
            }
            .onFailure { Log.w(TAG, "Gagal memuat override: ${it.message}") }
    }

    fun overrideFor(trackId: String): LyricsResult? = overrides[trackId]

    fun isRemembered(trackId: String): Boolean = trackId in remembered

    /**
     * Terapkan lirik pilihan user.
     *
     * @param remember true berarti ikut ditulis ke disk dan bertahan setelah app
     *   ditutup. Kegagalan menulis TIDAK menjatuhkan penerapannya: pilihannya
     *   tetap berlaku di memori, dan itu jauh lebih baik daripada menolak
     *   perintah user gara-gara disk bermasalah.
     */
    suspend fun apply(
        trackId: String,
        result: LyricsResult,
        lrclibId: Long,
        remember: Boolean,
    ) {
        overrides[trackId] = result
        if (remember) {
            val ok = runCatching {
                store.save(
                    LyricsOverride(
                        trackId = trackId,
                        result = result,
                        lrclibId = lrclibId,
                        savedAtMs = nowMs(),
                    )
                )
            }.onFailure { Log.w(TAG, "Gagal menyimpan override $trackId: ${it.message}") }
                .isSuccess
            if (ok) remembered += trackId
        }
        _revision.value++
        Log.d(TAG, "Override diterapkan untuk $trackId (remember=$remember)")
    }

    /** Buang dari memori DAN disk sekaligus. */
    suspend fun forget(trackId: String) {
        overrides.remove(trackId)
        remembered -= trackId
        runCatching { store.delete(trackId) }
            .onFailure { Log.w(TAG, "Gagal menghapus override $trackId: ${it.message}") }
        _revision.value++
        Log.d(TAG, "Override dilupakan untuk $trackId")
    }

    companion object {
        private const val TAG = "LyricsOverrideRepo"
    }
}
