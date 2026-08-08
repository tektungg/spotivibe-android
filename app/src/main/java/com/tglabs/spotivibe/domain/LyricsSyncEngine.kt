package com.tglabs.spotivibe.domain

/**
 * State machine posisi lirik. Memegang baseline playback, offset user, dan
 * daftar baris, lalu menjawab satu pertanyaan: baris mana yang aktif sekarang.
 *
 * Diekstrak keluar dari `PlaybackController` supaya perilakunya bisa diuji.
 * Controller-nya sendiri terikat ke Spotify SDK, DataStore, dan Context, jadi
 * kalau logika ini tinggal di sana, yang bisa diuji cuma fungsi-fungsi murni
 * lepasannya, bukan hal yang sebenarnya penting: bahwa mengubah offset benar-
 * benar menggeser baris yang dipakai notification dan overlay.
 *
 * Jam di-inject lewat [nowMs] supaya test bisa memajukan waktu tanpa menunggu.
 *
 * TIDAK thread-safe. Dipakai hanya dari scope tunggal `PlaybackController`
 * (Main.immediate), dan hasilnya dipublikasikan lewat StateFlow.
 */
class LyricsSyncEngine(
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private var baselineProgressMs: Long = 0L
    private var baselineTimestampMs: Long = 0L
    private var paused: Boolean = true
    private var offsetMs: Int = 0
    private var lines: List<SyncedLine> = emptyList()

    val isPaused: Boolean get() = paused
    val hasLines: Boolean get() = lines.isNotEmpty()

    /**
     * Snapshot dari Spotify. [capturedAtMs] adalah wall clock saat snapshot
     * diambil di callback, bukan saat fungsi ini dipanggil, supaya
     * extrapolasi tetap akurat walau collector telat sedikit.
     */
    fun onPlayerState(progressMs: Long, capturedAtMs: Long, isPaused: Boolean) {
        baselineProgressMs = progressMs
        baselineTimestampMs = capturedAtMs
        paused = isPaused
    }

    fun onLyrics(newLines: List<SyncedLine>?) {
        lines = newLines ?: emptyList()
    }

    fun onOffset(ms: Int) {
        offsetMs = ms
    }

    /** Posisi playback sebenarnya, hasil extrapolasi dari baseline. */
    fun rawPositionMs(): Long =
        if (paused) baselineProgressMs
        else baselineProgressMs + (nowMs() - baselineTimestampMs)

    /** Posisi untuk mencocokkan lirik, sudah termasuk koreksi offset user. */
    fun lyricsPositionMs(): Long = lyricsProgressMs(rawPositionMs(), offsetMs)

    /** Baris aktif, atau -1 kalau belum ada lirik atau masih di intro. */
    fun activeIndex(): Int = findActiveLineIndex(lines, lyricsPositionMs())

    /**
     * Berapa lama boleh tidur sebelum perlu mengevaluasi ulang.
     * [idleMs] dipakai saat tidak ada lirik synced untuk diikuti.
     */
    fun nextDelayMs(idleMs: Long = 500L): Long {
        if (lines.isEmpty()) return idleMs
        return nextTickDelayMs(
            nextLineTimeMs = lines.getOrNull(activeIndex() + 1)?.timeMs,
            lyricsNowMs = lyricsPositionMs(),
            isPaused = paused,
        )
    }
}
