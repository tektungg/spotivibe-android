package com.tglabs.spotivibe.data

import com.tglabs.spotivibe.domain.STALE_RETENTION_MS
import com.tglabs.spotivibe.domain.cacheFileName
import com.tglabs.spotivibe.domain.LYRICS_SELECTION_VERSION
import com.tglabs.spotivibe.domain.isCacheExpired
import com.tglabs.spotivibe.domain.isSelectionOutdated
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
 * Antarmuka kecil untuk lapis disk, supaya [LyricsRepository] bisa diuji tanpa
 * `Context`. Urutan resolusi cache adalah wiring, dan wiring yang tidak diuji
 * berulang kali jadi tempat bug bersembunyi di project ini.
 */
interface LyricsDiskCache {
    /** Entri yang masih dalam masa TTL. Null kalau tidak ada atau sudah basi. */
    fun get(trackId: String): LyricsResult?
    fun put(result: LyricsResult)

    /**
     * Entri BERISI berapa pun umurnya, untuk cadangan saat jaringan tidak bisa
     * menjawab. Default tidak punya cadangan supaya implementasi lama tetap sah.
     */
    fun getStale(trackId: String): LyricsResult? = null
}

/**
 * JSON file-based cache untuk hasil lookup LRCLIB.
 *
 * Disimpan di `context.filesDir/lyrics-cache/<sanitized-trackId>.json`.
 * Dulu di `cacheDir`, yang boleh dikosongkan Android kapan saja saat storage
 * sesak, dan itu persis saat yang salah untuk kehilangan lirik offline. Isi
 * lokasi lama dipindahkan sekali, malas, di akses pertama (lihat
 * [migrateLegacyLyricsCache]).
 *
 * TTL (kapan lirik ditanyakan ulang ke LRCLIB):
 * - Positive (track ketemu, [LyricsResult.hasContent] true) → 30 hari.
 * - Negative (kosong, biar nggak spam LRCLIB tiap kali user replay track yang
 *   memang tidak punya lirik) → 1 hari.
 *
 * Entri yang lewat TTL TIDAK dihapus saat dibaca. [get] menganggapnya miss,
 * tapi [getStale] masih bisa menyajikannya sebagai cadangan offline. Berkas
 * baru dibuang oleh [sweep] (500 entry / 8 MB, LRU, atau tidak dipakai lebih
 * dari [STALE_RETENTION_MS]) atau tertimpa [put] berikutnya.
 *
 * Implementasi sengaja sederhana — satu file per track, no index, no lock.
 * Race condition write-write paling-paling overwrite dengan data identik.
 */
class LyricsCache internal constructor(
    private val dir: File,
    /** Lokasi lama di `cacheDir`, dipindahkan sekali. Null = tidak ada migrasi. */
    private val legacyDir: File?,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : LyricsDiskCache {

    constructor(context: Context) : this(
        dir = File(context.filesDir, DIR_NAME),
        legacyDir = File(context.cacheDir, DIR_NAME),
    )

    init {
        if (!dir.exists()) dir.mkdirs()
    }

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(CachedEntry::class.java)

    /** Mulai dari 0 supaya penulisan PERTAMA sesi ini langsung memicu sapuan. */
    private val writesSinceSweep = AtomicInteger(0)

    /**
     * Migrasi dijalankan di akses pertama, bukan di konstruktor. Konstruktor
     * terpanggil dari `by lazy` di thread utama; [get] dan [put] selalu
     * dipanggil dari `Dispatchers.IO` oleh [LyricsRepository].
     */
    @Volatile private var migrated = legacyDir == null

    private fun ensureMigrated() {
        if (migrated) return
        synchronized(this) {
            if (migrated) return
            val legacy = legacyDir ?: return
            val n = migrateLegacyLyricsCache(legacy, dir)
            if (n > 0) Log.d(TAG, "Migrasi cache lirik: $n file dari cacheDir ke filesDir")
            migrated = true
        }
    }

    /** Ambil cache kalau ada dan belum lewat TTL. Return null kalau miss/basi/corrupt. */
    override fun get(trackId: String): LyricsResult? {
        val entry = read(trackId) ?: return null
        if (isCacheExpired(entry.cachedAt, nowMs(), entry.hasContent)) {
            // Sengaja TIDAK dihapus: berkas ini cadangan untuk getStale() saat
            // offline. Dulu dihapus di sini, dan lagu yang liriknya sudah lama
            // tersimpan justru kehilangan liriknya saat sinyal hilang.
            Log.d(TAG, "Cache basi untuk $trackId (disimpan sebagai cadangan)")
            return null
        }
        if (isSelectionOutdated(entry.selectionVersion)) {
            // Dipilih dengan aturan lama. Sama seperti basi: tidak dipakai saat
            // online supaya dipilih ulang, tapi tetap ada untuk getStale().
            Log.d(TAG, "Cache versi seleksi ${entry.selectionVersion} untuk $trackId, pilih ulang")
            return null
        }
        return entry.toLyricsResult(trackId)
    }

    /** Entri berisi, berapa pun umurnya. Entri negatif tidak pernah jadi cadangan. */
    override fun getStale(trackId: String): LyricsResult? {
        val entry = read(trackId) ?: return null
        if (!entry.hasContent) return null
        return entry.toLyricsResult(trackId)
    }

    private fun read(trackId: String): CachedEntry? {
        ensureMigrated()
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
        runCatching { file.setLastModified(nowMs()) }
        return try {
            adapter.fromJson(file.readText())
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to read cache for $trackId: ${t.message}")
            runCatching { file.delete() }
            null
        }
    }

    /** Simpan hasil ke disk. Tidak throw — log saja kalau gagal. */
    override fun put(result: LyricsResult) {
        ensureMigrated()
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
                cachedAt = nowMs(),
                selectionVersion = LYRICS_SELECTION_VERSION,
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
            nowMs = nowMs(),
            maxAgeMs = STALE_RETENTION_MS,
            maxEntries = MAX_ENTRIES,
            maxBytes = MAX_BYTES,
        )
        if (buang.isEmpty()) return
        val perNama = files.associateBy { it.name }
        buang.forEach { perNama[it]?.delete() }
        Log.d(TAG, "Sweep cache: ${buang.size} dari ${files.size} file dibuang")
    }

    private fun fileFor(trackId: String): File = File(dir, cacheFileName(trackId))

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
        /**
         * Versi aturan pemilihan saat entri ini dipilih. Default 0 supaya
         * berkas lama tetap terbaca, lalu diperlakukan basi oleh [get].
         */
        val selectionVersion: Int = 0,
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
        private const val DIR_NAME = "lyrics-cache"

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

/**
 * Pindahkan berkas cache lirik dari [legacy] ke [target], lalu hapus [legacy].
 *
 * Idempoten: aman dipanggil berulang, dan kalau dihentikan di tengah jalan
 * (proses dibunuh) sisanya dipindahkan di panggilan berikutnya. Berkas yang
 * sudah ada di [target] menang, karena ia ditulis lebih baru oleh versi app
 * ini. `renameTo` gagal kalau dua lokasi beda volume; saat itu jatuh ke copy
 * lalu delete. Semua kegagalan ditelan: kehilangan cache hanya berarti lirik
 * diambil ulang dari jaringan, bukan crash.
 *
 * @return jumlah berkas yang berhasil dipindahkan.
 */
internal fun migrateLegacyLyricsCache(legacy: File, target: File): Int {
    if (!legacy.isDirectory) return 0
    if (!target.exists()) target.mkdirs()
    var moved = 0
    legacy.listFiles()?.filter { it.isFile }?.forEach { f ->
        runCatching {
            val dest = File(target, f.name)
            when {
                dest.exists() -> f.delete()
                f.renameTo(dest) -> moved++
                else -> {
                    f.copyTo(dest, overwrite = false)
                    // Stempel waktu dipertahankan supaya urutan LRU tidak
                    // teracak oleh migrasi.
                    dest.setLastModified(f.lastModified())
                    f.delete()
                    moved++
                }
            }
        }
    }
    runCatching { legacy.delete() }
    return moved
}
