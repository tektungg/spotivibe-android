package com.tglabs.spotivibe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InterludeTest {

    private fun lines(vararg pairs: Pair<Long, String>): List<SyncedLine> =
        pairs.mapIndexed { i, (t, text) -> SyncedLine(id = i, timeMs = t, text = text) }

    // ── Frame titik ──────────────────────────────────────────────

    @Test
    fun `awal jeda semua titik redup dan ukuran penuh`() {
        val f = interludeFrame(elapsedMs = 0, gapMs = 6_550)
        assertEquals(listOf(0f, 0f, 0f), f.dotLevels)
        assertEquals(1f, f.scale)
    }

    @Test
    fun `titik menyala berurutan dari kiri`() {
        // gap 6550: jatah per titik (6550 - 550) / 3 = 2000 ms.
        val f = interludeFrame(elapsedMs = 3_000, gapMs = 6_550)
        assertEquals(1f, f.dotLevels[0], 0.001f)
        assertEquals(0.5f, f.dotLevels[1], 0.001f)
        assertEquals(0f, f.dotLevels[2], 0.001f)
    }

    @Test
    fun `titik ketiga penuh sebelum jeda habis`() {
        val f = interludeFrame(elapsedMs = 6_000, gapMs = 6_550)
        assertEquals(listOf(1f, 1f, 1f), f.dotLevels)
        assertEquals(1f, f.scale)
    }

    @Test
    fun `menyusut di lima ratus ms terakhir`() {
        assertEquals(0.5f, interludeFrame(elapsedMs = 6_300, gapMs = 6_550).scale, 0.001f)
        assertEquals(0f, interludeFrame(elapsedMs = 6_550, gapMs = 6_550).scale, 0.001f)
    }

    @Test
    fun `posisi di luar jeda dijepit`() {
        assertEquals(interludeFrame(0, 6_550), interludeFrame(-500, 6_550))
        assertEquals(interludeFrame(6_550, 6_550), interludeFrame(99_000, 6_550))
    }

    @Test
    fun `jeda pendek tidak dianimasikan`() {
        assertEquals(InterludeFrame.Idle, interludeFrame(elapsedMs = 1_000, gapMs = 2_999))
    }

    @Test
    fun `tingkat nyala selalu di antara nol dan satu`() {
        for (t in 0L..10_000L step 137L) {
            val f = interludeFrame(t, 10_000)
            assertTrue(f.dotLevels.all { it in 0f..1f })
            assertTrue(f.scale in 0f..1f)
        }
    }

    // ── Deteksi jeda ─────────────────────────────────────────────

    @Test
    fun `baris kosong dengan jeda panjang adalah interlude`() {
        val l = lines(1_000L to "a", 5_000L to "", 12_000L to "b")
        assertEquals(7_000L, interludeGapMs(l, 1))
    }

    @Test
    fun `baris berisi bukan interlude`() {
        val l = lines(1_000L to "a", 12_000L to "b")
        assertNull(interludeGapMs(l, 0))
    }

    @Test
    fun `baris kosong terakhir tidak punya jeda yang diketahui`() {
        val l = lines(1_000L to "a", 5_000L to " ")
        assertNull(interludeGapMs(l, 1))
    }

    @Test
    fun `baris kosong dengan jeda pendek tidak dianimasikan`() {
        val l = lines(1_000L to "", 2_500L to "b")
        assertNull(interludeGapMs(l, 0))
    }

    @Test
    fun `index di luar daftar aman`() {
        assertNull(interludeGapMs(emptyList(), 0))
        assertNull(interludeGapMs(lines(0L to ""), 5))
    }

    @Test
    fun `intro panjang memberi jeda sampai baris pertama`() {
        assertEquals(15_000L, introGapMs(lines(15_000L to "a", 18_000L to "b")))
    }

    @Test
    fun `intro pendek dan lirik kosong tidak memberi jeda`() {
        assertNull(introGapMs(lines(2_000L to "a")))
        assertNull(introGapMs(emptyList()))
    }

    @Test
    fun `intro yang sudah ditandai baris kosong tidak digandakan`() {
        // Baris kosong pertama sudah menampilkan titiknya sendiri.
        assertNull(introGapMs(lines(0L to "", 15_000L to "a")))
    }
}
