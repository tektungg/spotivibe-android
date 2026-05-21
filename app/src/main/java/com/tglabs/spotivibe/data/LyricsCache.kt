package com.tglabs.spotivibe.data

import android.content.Context
import android.util.Log
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.tglabs.spotivibe.domain.LyricsResult
import com.tglabs.spotivibe.domain.SyncedLine
import java.io.File

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

    /** Ambil cache kalau ada dan belum expired. Return null kalau miss/expired/corrupt. */
    fun get(trackId: String): LyricsResult? {
        val file = fileFor(trackId)
        if (!file.exists()) return null
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
                synced = result.synced?.map { SyncedLineDto(it.timeMs, it.text) },
                plain = result.plain,
                hasContent = result.hasContent,
                cachedAt = System.currentTimeMillis(),
            )
            fileFor(result.trackId).writeText(adapter.toJson(entry))
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to write cache for ${result.trackId}: ${t.message}")
        }
    }

    /** Sanitize trackId untuk pakai sebagai nama file — `:` → `_`. */
    private fun fileFor(trackId: String): File {
        val safe = trackId.replace(':', '_')
        return File(dir, "$safe.json")
    }

    @JsonClass(generateAdapter = false)
    internal data class SyncedLineDto(
        val timeMs: Long,
        val text: String,
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
            synced = synced?.map { SyncedLine(it.timeMs, it.text) },
            plain = plain,
        )
    }

    companion object {
        private const val TAG = "LyricsCache"
        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private const val POSITIVE_TTL_MS = 30L * DAY_MS
        private const val NEGATIVE_TTL_MS = 1L * DAY_MS
    }
}
