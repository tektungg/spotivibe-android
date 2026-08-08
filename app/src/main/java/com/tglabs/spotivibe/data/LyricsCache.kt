package com.tglabs.spotivibe.data

import android.content.Context
import android.util.Log
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.tglabs.spotivibe.domain.CacheFileInfo
import com.tglabs.spotivibe.domain.LyricsResult
import com.tglabs.spotivibe.domain.SyncedLine
import com.tglabs.spotivibe.domain.SyncedWord
import com.tglabs.spotivibe.domain.filesToEvict
import com.tglabs.spotivibe.domain.withPositionalIds
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * JSON file-based cache untuk hasil lookup LRCLIB.
 *
 * Disimpan di `context.cacheDir/lyrics-cache/<sanitized-trackId>.json`.
 *
 * TTL:
 * - Positive (track ketemu, [LyricsResult.hasContent] true) → 30 hari.
 * - Negative (kosong, biar nggak spam LRCLIB tiap kali user replay track yang
 *   memang tidak punya lirik) → 1 hari.
 *
 * Implementasi sengaja sederhana — satu file per track, no index, no lock.
 * Race condition write-write paling-paling overwrite dengan data identik.
 */
class LyricsCache(context: Context) {

    private val dir: File = File(context.cacheDir, "lyrics-cache").apply {
        if (!exists()) mkdirs()
    }

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(CachedEntry::class.java)

    /** Mulai dari 0 supaya penulisan PERTAMA sesi ini langsung memicu sapuan. */
    private val writesSinceSweep = AtomicInteger(0)

    /** Ambil cache kalau ada dan belum expired. Return null kalau miss/expired/corrupt. */
    fun get(trackId: String): LyricsResult? {
        val file = fileFor(trackId)
        if (!file.exists()) return null
        // Sentuh stempel waktunya supaya pembatasan berperilaku LRU, bukan FIFO:
        // lagu yang sering diputar ulang harus bertahan lebih lama daripada yang
        // ditulis belakangan tapi tidak pernah disentuh lagi.
        //
        // setLastModified() dikenal tidak selalu berhasil di sebagian filesystem
        // Android. Kalau gagal, kebijakannya merosot jadi FIFO, yang masih
        // terbatas dan tetap benar, cuma kurang pintar. Karena itu hasilnya
        // sengaja tidak diperiksa.
        runCatching { file.setLastModified(System.currentTimeMillis()) }
        return try {
            val json = file.readText()
            val entry = adapter.fromJson(json) ?: return null
            val ageMs = System.currentTimeMillis() - entry.cachedAt
            val ttl = if (entry.hasContent) POSITIVE_TTL_MS else NEGATIVE_TTL_MS
            if (ageMs > ttl) {
                Log.d(TAG, "Cache expired for $trackId (age=${ageMs}ms, ttl=${ttl}ms)")
                file.delete()
                return null
            }
            entry.toLyricsResult(trackId)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to read cache for $trackId: ${t.message}")
            runCatching { file.delete() }
            null
        }
    }

    /** Simpan hasil ke disk. Tidak throw — log saja kalau gagal. */
    fun put(result: LyricsResult) {
        try {
            val entry = CachedEntry(
                synced = result.synced?.map { line ->
                    SyncedLineDto(
                        timeMs = line.timeMs,
                        text = line.text,
                        words = line.words
                            .takeIf { it.isNotEmpty() }
                            ?.map { SyncedWordDto(it.timeMs, it.text) },
                    )
                },
                plain = result.plain,
                hasContent = result.hasContent,
                cachedAt = System.currentTimeMillis(),
            )
            fileFor(result.trackId).writeText(adapter.toJson(entry))
            maybeSweep()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to write cache for ${result.trackId}: ${t.message}")
        }
    }

    /**
     * Jalankan pembatasan sesekali, bukan tiap tulis.
     *
     * Menyapu di setiap `put` berarti melisting seluruh direktori setiap ganti
     * lagu. Sekali per [SWEEP_EVERY] penulisan sudah cukup: batasnya boleh
     * terlampaui sedikit di antara sapuan, dan itu tidak masalah karena yang
     * dijaga adalah pertumbuhan jangka panjang, bukan angka pasti di tiap saat.
     *
     * Sapuan pertama terjadi di penulisan pertama sesi, jadi cache yang sudah
     * membengkak dari pemakaian sebelumnya langsung dirapikan.
     */
    private fun maybeSweep() {
        if (writesSinceSweep.getAndIncrement() % SWEEP_EVERY != 0) return
        runCatching { sweep() }
            .onFailure { Log.w(TAG, "Sweep cache gagal: ${it.message}") }
    }

    private fun sweep() {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        val buang = filesToEvict(
            files = files.map {
                CacheFileInfo(
                    name = it.name,
                    sizeBytes = it.length(),
                    lastAccessMs = it.lastModified(),
                )
            },
            nowMs = System.currentTimeMillis(),
            maxAgeMs = POSITIVE_TTL_MS,
            maxEntries = MAX_ENTRIES,
            maxBytes = MAX_BYTES,
        )
        if (buang.isEmpty()) return
        val perNama = files.associateBy { it.name }
        buang.forEach { perNama[it]?.delete() }
        Log.d(TAG, "Sweep cache: ${buang.size} dari ${files.size} file dibuang")
    }

    /** Sanitize trackId untuk pakai sebagai nama file — `:` → `_`. */
    private fun fileFor(trackId: String): File {
        val safe = trackId.replace(':', '_')
        return File(dir, "$safe.json")
    }

    @JsonClass(generateAdapter = false)
    internal data class SyncedWordDto(
        val timeMs: Long,
        val text: String,
    )

    /**
     * [words] punya default null supaya file cache lama yang belum punya field
     * ini tetap bisa dibaca, bukannya gagal parse lalu dibuang.
     *
     * `id` sengaja TIDAK disimpan: nilainya murni turunan posisi, jadi
     * dihitung ulang saat load lewat [withPositionalIds].
     */
    @JsonClass(generateAdapter = false)
    internal data class SyncedLineDto(
        val timeMs: Long,
        val text: String,
        val words: List<SyncedWordDto>? = null,
    )

    @JsonClass(generateAdapter = false)
    internal data class CachedEntry(
        val synced: List<SyncedLineDto>?,
        val plain: String?,
        val hasContent: Boolean,
        val cachedAt: Long,
    ) {
        fun toLyricsResult(trackId: String): LyricsResult = LyricsResult(
            trackId = trackId,
            synced = synced
                ?.map { dto ->
                    SyncedLine(
                        id = 0,
                        timeMs = dto.timeMs,
                        text = dto.text,
                        words = dto.words.orEmpty().map { SyncedWord(it.timeMs, it.text) },
                    )
                }
                ?.withPositionalIds(),
            plain = plain,
        )
    }

    companion object {
        private const val TAG = "LyricsCache"
        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private const val POSITIVE_TTL_MS = 30L * DAY_MS
        private const val NEGATIVE_TTL_MS = 1L * DAY_MS

        /**
         * Cukup untuk beberapa playlist besar tanpa membuang yang masih mungkin
         * dipakai lagi. File lirik biasanya 2 sampai 10 KB.
         */
        private const val MAX_ENTRIES = 500

        /** Jaring terhadap satu entry patologis, bukan batas utama. */
        private const val MAX_BYTES = 8L * 1024L * 1024L

        /** Sapu tiap sekian penulisan, bukan tiap penulisan. */
        private const val SWEEP_EVERY = 25
    }
}
