package com.tglabs.spotivibe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsSyncTest {

    private fun lines(vararg timesMs: Long): List<SyncedLine> =
        timesMs.mapIndexed { i, t -> SyncedLine(timeMs = t, text = "baris $i") }

    // ── findActiveLineIndex ──────────────────────────────────────

    @Test
    fun `daftar kosong mengembalikan minus satu`() {
        assertEquals(-1, findActiveLineIndex(emptyList(), 5_000))
    }

    @Test
    fun `sebelum baris pertama mengembalikan minus satu`() {
        assertEquals(-1, findActiveLineIndex(lines(1_000, 2_000), 999))
    }

    @Test
    fun `tepat di timestamp baris sudah dianggap aktif`() {
        assertEquals(0, findActiveLineIndex(lines(1_000, 2_000), 1_000))
        assertEquals(1, findActiveLineIndex(lines(1_000, 2_000), 2_000))
    }

    @Test
    fun `satu milidetik sebelum batas masih baris sebelumnya`() {
        assertEquals(0, findActiveLineIndex(lines(1_000, 2_000), 1_999))
    }

    @Test
    fun `di antara dua baris memilih yang sebelumnya`() {
        assertEquals(1, findActiveLineIndex(lines(1_000, 2_000, 3_000), 2_500))
    }

    @Test
    fun `setelah baris terakhir tetap di baris terakhir`() {
        assertEquals(2, findActiveLineIndex(lines(1_000, 2_000, 3_000), 999_999))
    }

    @Test
    fun `satu baris saja`() {
        assertEquals(-1, findActiveLineIndex(lines(5_000), 4_999))
        assertEquals(0, findActiveLineIndex(lines(5_000), 5_000))
    }

    /**
     * File LRC boleh punya beberapa baris dengan timestamp identik. Pilihannya
     * harus deterministik, dan yang terakhir cocok dengan urutan baca.
     */
    @Test
    fun `timestamp kembar memilih baris terakhir`() {
        assertEquals(2, findActiveLineIndex(lines(1_000, 2_000, 2_000, 3_000), 2_000))
    }

    @Test
    fun `hasil konsisten dengan pemindaian linear`() {
        val l = lines(0, 500, 1_200, 1_200, 4_000, 9_500, 9_501)
        for (ms in -100L..10_000L step 37) {
            val expected = l.indexOfLast { it.timeMs <= ms }
            assertEquals("ms=$ms", expected, findActiveLineIndex(l, ms))
        }
    }

    // ── lyricsProgressMs ─────────────────────────────────────────

    @Test
    fun `offset nol tidak mengubah apa-apa`() {
        assertEquals(10_000L, lyricsProgressMs(10_000, 0))
    }

    @Test
    fun `offset positif mempercepat lirik`() {
        assertEquals(10_500L, lyricsProgressMs(10_000, 500))
    }

    @Test
    fun `offset negatif memperlambat lirik`() {
        assertEquals(9_500L, lyricsProgressMs(10_000, -500))
    }

    @Test
    fun `hasil tidak pernah negatif`() {
        assertEquals(0L, lyricsProgressMs(100, -2_000))
    }

    /**
     * REGRESI: offset harus benar-benar menggeser baris yang aktif. Kalau tidak,
     * slider di Settings cuma dekorasi.
     */
    @Test
    fun `offset menggeser baris aktif`() {
        val l = lines(1_000, 2_000, 3_000)
        val raw = 1_900L
        val tanpaOffset = findActiveLineIndex(l, lyricsProgressMs(raw, 0))
        val denganOffset = findActiveLineIndex(l, lyricsProgressMs(raw, 200))
        assertEquals(0, tanpaOffset)
        assertEquals(1, denganOffset)
        assertNotEquals(tanpaOffset, denganOffset)
    }

    // ── seekTargetMs ─────────────────────────────────────────────

    /**
     * REGRESI: dulu tap baris mengirim `line.timeMs` mentah. Dengan offset
     * non-nol, seek ke situ membuat progress lirik jadi `timeMs + offset`, yang
     * bisa sudah melewati baris yang barusan ditap, jadi yang menyala malah
     * baris berikutnya.
     */
    @Test
    fun `tap baris mendarat tepat di baris itu walau ada offset`() {
        val l = lines(1_000, 2_000, 3_000)
        val offset = 400

        val target = seekTargetMs(l[1].timeMs, offset)
        val indexSetelahSeek = findActiveLineIndex(l, lyricsProgressMs(target, offset))

        assertEquals("harus mendarat di baris yang ditap", 1, indexSetelahSeek)
    }

    @Test
    fun `tanpa kompensasi offset tap baris meleset ke baris berikutnya`() {
        val l = lines(1_000, 2_000, 2_300)
        val offset = 400
        // Perilaku lama: seek langsung ke timeMs tanpa dikurangi offset.
        val indexSalah = findActiveLineIndex(l, lyricsProgressMs(l[1].timeMs, offset))
        assertEquals("ini bug-nya, terdokumentasi", 2, indexSalah)
    }

    @Test
    fun `seek target adalah kebalikan dari lyrics progress`() {
        for (t in longArrayOf(0, 1, 999, 5_000, 123_456)) {
            for (offset in intArrayOf(-2_000, -500, 0, 500, 2_000)) {
                if (t - offset < 0) continue // di-clamp, invarian tidak berlaku
                assertEquals(
                    "t=$t offset=$offset",
                    t,
                    lyricsProgressMs(seekTargetMs(t, offset), offset),
                )
            }
        }
    }

    @Test
    fun `seek target tidak pernah negatif`() {
        assertEquals(0L, seekTargetMs(100, 2_000))
    }

    // ── nextTickDelayMs ──────────────────────────────────────────

    @Test
    fun `saat pause tidur selama mungkin`() {
        assertEquals(250L, nextTickDelayMs(nextLineTimeMs = 1_000, lyricsNowMs = 0, isPaused = true))
    }

    @Test
    fun `di baris terakhir tidur selama mungkin`() {
        assertEquals(250L, nextTickDelayMs(nextLineTimeMs = null, lyricsNowMs = 0, isPaused = false))
    }

    @Test
    fun `tidur tepat sampai batas baris berikutnya`() {
        assertEquals(
            120L,
            nextTickDelayMs(nextLineTimeMs = 5_120, lyricsNowMs = 5_000, isPaused = false),
        )
    }

    @Test
    fun `batas jauh dipotong supaya tetap responsif terhadap seek`() {
        assertEquals(
            250L,
            nextTickDelayMs(nextLineTimeMs = 60_000, lyricsNowMs = 0, isPaused = false),
        )
    }

    @Test
    fun `batas yang sudah lewat tidak jadi busy loop`() {
        assertEquals(
            16L,
            nextTickDelayMs(nextLineTimeMs = 1_000, lyricsNowMs = 5_000, isPaused = false),
        )
    }

    @Test
    fun `delay selalu di dalam rentang aman`() {
        for (next in longArrayOf(-5_000, 0, 17, 250, 1_000, 999_999)) {
            for (now in longArrayOf(0, 500, 10_000)) {
                val d = nextTickDelayMs(next, now, isPaused = false)
                assertTrue("next=$next now=$now d=$d", d in 16L..250L)
            }
        }
    }
}
