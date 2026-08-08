package com.tglabs.spotivibe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsSyncEngineTest {

    /** Jam palsu yang bisa dimajukan manual. */
    private class Clock(var nowMs: Long = 1_000_000L) {
        fun advance(ms: Long) { nowMs += ms }
    }

    private fun lines(vararg timesMs: Long): List<SyncedLine> =
        timesMs.mapIndexed { i, t -> SyncedLine(id = i, timeMs = t, text = "baris $i") }

    private fun engine(clock: Clock) = LyricsSyncEngine(nowMs = { clock.nowMs })

    /** Kondisi awal yang lazim: lagu jalan dari posisi 0, lirik sudah ada. */
    private fun playing(clock: Clock, vararg timesMs: Long): LyricsSyncEngine =
        engine(clock).apply {
            onLyrics(lines(*timesMs))
            onPlayerState(progressMs = 0, capturedAtMs = clock.nowMs, isPaused = false)
        }

    // ── Extrapolasi ──────────────────────────────────────────────

    @Test
    fun `tanpa lirik index minus satu`() {
        val clock = Clock()
        val e = engine(clock)
        e.onPlayerState(5_000, clock.nowMs, isPaused = false)
        assertEquals(-1, e.activeIndex())
        assertFalse(e.hasLines)
    }

    @Test
    fun `saat jalan posisi maju mengikuti jam`() {
        val clock = Clock()
        val e = playing(clock, 1_000, 2_000, 3_000)

        assertEquals(-1, e.activeIndex())
        clock.advance(1_000)
        assertEquals(0, e.activeIndex())
        clock.advance(1_000)
        assertEquals(1, e.activeIndex())
        clock.advance(5_000)
        assertEquals(2, e.activeIndex())
    }

    @Test
    fun `saat pause posisi membeku walau jam jalan`() {
        val clock = Clock()
        val e = engine(clock)
        e.onLyrics(lines(1_000, 2_000))
        e.onPlayerState(progressMs = 1_200, capturedAtMs = clock.nowMs, isPaused = true)

        assertEquals(0, e.activeIndex())
        clock.advance(60_000)
        assertEquals("pause tidak boleh memajukan lirik", 0, e.activeIndex())
        assertEquals(1_200L, e.rawPositionMs())
    }

    @Test
    fun `baseline pakai capturedAtMs bukan waktu pemanggilan`() {
        val clock = Clock()
        val e = engine(clock)
        e.onLyrics(lines(1_000, 2_000))
        // Snapshot diambil 800 ms lalu, collector baru sampai sekarang.
        e.onPlayerState(progressMs = 1_500, capturedAtMs = clock.nowMs - 800, isPaused = false)

        assertEquals(2_300L, e.rawPositionMs())
        assertEquals(1, e.activeIndex())
    }

    // ── Offset ───────────────────────────────────────────────────

    /**
     * REGRESI: inti dari perbaikan ini. Mengubah offset harus langsung
     * menggeser baris aktif TANPA event player baru, karena index inilah yang
     * dibaca notification dan overlay. Dulu offset cuma diterapkan di dua
     * Composable layar, jadi dua permukaan lain tidak pernah ikut bergeser.
     */
    @Test
    fun `ubah offset langsung menggeser baris aktif tanpa event player`() {
        val clock = Clock()
        val e = engine(clock)
        e.onLyrics(lines(1_000, 2_000, 3_000))
        e.onPlayerState(progressMs = 1_900, capturedAtMs = clock.nowMs, isPaused = true)

        assertEquals(0, e.activeIndex())

        e.onOffset(200)
        assertEquals("offset positif memajukan lirik", 1, e.activeIndex())

        e.onOffset(-1_000)
        assertEquals("offset negatif memundurkan lirik", -1, e.activeIndex())

        e.onOffset(0)
        assertEquals(0, e.activeIndex())
    }

    @Test
    fun `offset tidak mengubah posisi playback sebenarnya`() {
        val clock = Clock()
        val e = engine(clock)
        e.onPlayerState(progressMs = 5_000, capturedAtMs = clock.nowMs, isPaused = true)
        e.onOffset(750)

        assertEquals("slider transport harus tetap menunjuk posisi asli", 5_000L, e.rawPositionMs())
        assertEquals(5_750L, e.lyricsPositionMs())
    }

    @Test
    fun `posisi lirik tidak pernah negatif`() {
        val clock = Clock()
        val e = engine(clock)
        e.onPlayerState(progressMs = 100, capturedAtMs = clock.nowMs, isPaused = true)
        e.onOffset(-2_000)
        assertEquals(0L, e.lyricsPositionMs())
    }

    // ── Ganti lagu dan seek ──────────────────────────────────────

    /**
     * REGRESI: saat ganti lagu, controller mengosongkan lirik sebelum fetch
     * selesai. Kalau engine masih memegang baris lagu lama, ticker sempat
     * menerbitkan index dari lagu yang salah.
     */
    @Test
    fun `mengosongkan lirik mereset index walau posisi jauh di dalam lagu`() {
        val clock = Clock()
        val e = engine(clock)
        e.onLyrics(lines(1_000, 2_000))
        e.onPlayerState(progressMs = 5_000, capturedAtMs = clock.nowMs, isPaused = true)
        assertEquals(1, e.activeIndex())

        e.onLyrics(null)

        assertEquals(-1, e.activeIndex())
        assertFalse(e.hasLines)
    }

    @Test
    fun `seek langsung tercermin di baris aktif`() {
        val clock = Clock()
        val e = playing(clock, 1_000, 2_000, 3_000)
        clock.advance(1_100)
        assertEquals(0, e.activeIndex())

        // User seek ke 2.9 detik; Spotify mengirim snapshot baru.
        e.onPlayerState(progressMs = 2_900, capturedAtMs = clock.nowMs, isPaused = false)
        assertEquals(1, e.activeIndex())
    }

    @Test
    fun `lirik yang datang dari cache langsung dapat baris yang benar`() {
        val clock = Clock()
        val e = engine(clock)
        // Lagu sudah jalan 45 detik sebelum lirik selesai di-fetch.
        e.onPlayerState(progressMs = 45_000, capturedAtMs = clock.nowMs, isPaused = false)
        assertEquals(-1, e.activeIndex())

        e.onLyrics(lines(0, 10_000, 40_000, 80_000))
        assertEquals("tidak boleh mulai dari baris pertama", 2, e.activeIndex())
    }

    // ── Penjadwalan tick ─────────────────────────────────────────

    @Test
    fun `tanpa lirik tidur di interval idle`() {
        val clock = Clock()
        val e = engine(clock)
        e.onPlayerState(0, clock.nowMs, isPaused = false)
        assertEquals(500L, e.nextDelayMs(idleMs = 500L))
    }

    @Test
    fun `tidur tepat sampai baris berikutnya`() {
        val clock = Clock()
        val e = engine(clock)
        e.onLyrics(lines(1_000, 1_120))
        e.onPlayerState(progressMs = 1_000, capturedAtMs = clock.nowMs, isPaused = false)
        assertEquals(120L, e.nextDelayMs())
    }

    @Test
    fun `saat pause tidak bangun berulang tanpa perlu`() {
        val clock = Clock()
        val e = engine(clock)
        e.onLyrics(lines(1_000, 1_010))
        e.onPlayerState(progressMs = 1_000, capturedAtMs = clock.nowMs, isPaused = true)
        assertEquals(250L, e.nextDelayMs())
    }

    @Test
    fun `delay selalu di rentang aman sepanjang lagu`() {
        val clock = Clock()
        val e = playing(clock, 0, 800, 1_600, 2_400, 20_000)
        repeat(200) {
            val d = e.nextDelayMs()
            assertTrue("delay=$d", d in 16L..250L)
            clock.advance(d)
        }
    }

    /**
     * Ticker adaptif harus menyalakan SETIAP baris, tidak ada yang terlewat.
     * Ini yang membedakannya dari interval tetap: dengan tick 500 ms, dua baris
     * yang berjarak kurang dari itu bisa terlompati.
     */
    @Test
    fun `tidak ada baris yang terlewat walau jaraknya rapat`() {
        val clock = Clock()
        val rapat = longArrayOf(0, 120, 240, 360, 480, 600, 900, 1_500)
        val e = playing(clock, *rapat)

        val terlihat = LinkedHashSet<Int>()
        repeat(500) {
            terlihat.add(e.activeIndex())
            clock.advance(e.nextDelayMs())
        }

        val diharapkan = (0 until rapat.size).toSet()
        assertTrue(
            "baris terlewat: ${diharapkan - terlihat}",
            terlihat.containsAll(diharapkan),
        )
    }
}
