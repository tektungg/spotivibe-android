package com.tglabs.spotivibe.data

import android.content.Context
import android.util.Log
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.tglabs.spotivibe.domain.LyricsOverride
import com.tglabs.spotivibe.domain.cacheFileName
import com.tglabs.spotivibe.domain.LyricsOverrideStore
import com.tglabs.spotivibe.domain.LyricsResult
import com.tglabs.spotivibe.domain.SyncedLine
import com.tglabs.spotivibe.domain.SyncedWord
import com.tglabs.spotivibe.domain.withPositionalIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Override disimpan sebagai satu file JSON per track di
 * `filesDir/lyrics-overrides/`.
 *
 * **`filesDir`, bukan `cacheDir`.** Override adalah keputusan user, bukan cache.
 * `cacheDir` boleh dihapus sistem kapan saja saat penyimpanan sesak, dan
 * kehilangan keputusan user karena itu tidak bisa diterima. Ini juga membuatnya
 * kebal dari pembatasan LRU cache lirik.
 *
 * Tidak ada batas ukuran maupun kedaluwarsa di sini, dan itu disengaja. Jumlah
 * override dibatasi oleh seberapa sering user menemukan lirik salah, bukan oleh
 * berapa banyak lagu yang diputar. Cache tumbuh sendiri, override tidak.
 */
class FileLyricsOverrideStore(private val dir: File) : LyricsOverrideStore {

    /**
     * Direktori di-inject, bukan diturunkan dari Context di dalam.
     *
     * Tanpa ini seluruh kelas hanya bisa dijalankan di perangkat, padahal
     * isinya adalah keputusan user yang tidak boleh hilang: pilihan lirik
     * manual, yang tidak punya sumber lain untuk dipulihkan.
     */
    constructor(context: Context) : this(File(context.filesDir, OVERRIDE_DIR))

    init {
        if (!dir.exists()) dir.mkdirs()
    }

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(StoredOverride::class.java)

    override suspend fun loadAll(): List<LyricsOverride> = withContext(Dispatchers.IO) {
        val files = dir.listFiles()?.filter { it.isFile } ?: return@withContext emptyList()
        files.mapNotNull { file ->
            try {
                adapter.fromJson(file.readText())?.toDomain()
            } catch (t: Throwable) {
                // File rusak dibuang, bukan bikin seluruh pemuatan gagal. Satu
                // override yang korup tidak boleh menghilangkan yang lain.
                Log.w(TAG, "Override rusak, dihapus: ${file.name} (${t.message})")
                runCatching { file.delete() }
                null
            }
        }
    }

    override suspend fun save(override: LyricsOverride) = withContext(Dispatchers.IO) {
        fileFor(override.trackId).writeText(adapter.toJson(override.toStored()))
    }

    override suspend fun delete(trackId: String) = withContext(Dispatchers.IO) {
        fileFor(trackId).delete()
        Unit
    }

    /** Sanitize trackId untuk nama file, aturan yang sama dengan cache lirik. */
    private fun fileFor(trackId: String): File = File(dir, cacheFileName(trackId))

    // ── Bentuk tersimpan ─────────────────────────────────────────
    //
    // trackId ikut disimpan di dalam isi, tidak cuma jadi nama file, supaya
    // pemulihan tidak bergantung pada sanitasi nama file yang bisa berubah.

    @JsonClass(generateAdapter = false)
    internal data class StoredWord(val timeMs: Long, val text: String)

    @JsonClass(generateAdapter = false)
    internal data class StoredLine(
        val timeMs: Long,
        val text: String,
        val words: List<StoredWord>? = null,
    )

    @JsonClass(generateAdapter = false)
    internal data class StoredOverride(
        val trackId: String,
        val lrclibId: Long,
        val savedAtMs: Long,
        val synced: List<StoredLine>?,
        val plain: String?,
    ) {
        fun toDomain(): LyricsOverride = LyricsOverride(
            trackId = trackId,
            lrclibId = lrclibId,
            savedAtMs = savedAtMs,
            result = LyricsResult(
                trackId = trackId,
                synced = synced
                    ?.map { l ->
                        SyncedLine(
                            id = 0,
                            timeMs = l.timeMs,
                            text = l.text,
                            words = l.words.orEmpty().map { SyncedWord(it.timeMs, it.text) },
                        )
                    }
                    ?.withPositionalIds(),
                plain = plain,
            ),
        )
    }

    private fun LyricsOverride.toStored(): StoredOverride = StoredOverride(
        trackId = trackId,
        lrclibId = lrclibId,
        savedAtMs = savedAtMs,
        synced = result.synced?.map { l ->
            StoredLine(
                timeMs = l.timeMs,
                text = l.text,
                words = l.words.takeIf { it.isNotEmpty() }
                    ?.map { StoredWord(it.timeMs, it.text) },
            )
        },
        plain = result.plain,
    )

    companion object {
        private const val TAG = "LyricsOverrideStore"

        /** filesDir, BUKAN cacheDir. Keputusan user tidak boleh dihapus sistem. */
        const val OVERRIDE_DIR = "lyrics-overrides"
    }
}
