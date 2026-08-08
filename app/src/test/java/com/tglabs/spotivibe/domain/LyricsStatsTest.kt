package com.tglabs.spotivibe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsStatsTest {

    private fun ev(
        outcome: LyricsOutcome,
        source: LyricsSource = LyricsSource.Network,
        probe: ProbeSource? = null,
    ) = LyricsLookupEvent(source = source, outcome = outcome, probe = probe)

    private fun stats(vararg events: LyricsLookupEvent): LyricsStats =
        events.fold(LyricsStats()) { acc, e -> acc + e }

    // ── Akumulasi ────────────────────────────────────────────────

    @Test
    fun `stats kosong tidak mengklaim apa-apa`() {
        val s = LyricsStats()
        assertEquals(0, s.total)
        assertNull("belum ada data bukan berarti nol persen", s.syncedRate)
        assertNull(s.anyLyricsRate)
        assertNull(s.reachRate)
        assertNull(s.cacheRate)
        assertNull(s.searchOnlyRate)
    }

    @Test
    fun `satu peristiwa menaikkan penghitung yang tepat`() {
        val s = LyricsStats() + ev(LyricsOutcome.Synced, LyricsSource.Network, ProbeSource.Get)
        assertEquals(1, s.synced)
        assertEquals(1, s.fromNetwork)
        assertEquals(1, s.getWon)
        assertEquals(0, s.plainOnly)
        assertEquals(0, s.searchWon)
        assertEquals(1, s.total)
    }

    @Test
    fun `penghitung menjumlah lintas banyak peristiwa`() {
        val s = stats(
            ev(LyricsOutcome.Synced),
            ev(LyricsOutcome.Synced),
            ev(LyricsOutcome.NotFound),
            ev(LyricsOutcome.PlainOnly),
        )
        assertEquals(2, s.synced)
        assertEquals(1, s.notFound)
        assertEquals(1, s.plainOnly)
        assertEquals(4, s.total)
    }

    // ── Penyebut coverage ────────────────────────────────────────

    /**
     * REGRESI KONSEPTUAL, dan keputusan paling penting di file ini.
     *
     * Lookup yang gagal dijangkau BUKAN lubang di database LRCLIB, itu kegagalan
     * jaringan kita. Kalau ikut jadi penyebut coverage, angkanya turun setiap
     * kali sinyal jelek, dan kita akan menyimpulkan kualitas pencarian memburuk
     * padahal yang terjadi cuma naik lift.
     */
    @Test
    fun `gagal dijangkau tidak menurunkan angka coverage`() {
        val bersih = stats(
            ev(LyricsOutcome.Synced),
            ev(LyricsOutcome.Synced),
            ev(LyricsOutcome.NotFound),
        )
        val samaTapiSinyalJelek = stats(
            ev(LyricsOutcome.Synced),
            ev(LyricsOutcome.Synced),
            ev(LyricsOutcome.NotFound),
            ev(LyricsOutcome.Unavailable),
            ev(LyricsOutcome.Unavailable),
            ev(LyricsOutcome.Unavailable),
        )
        assertEquals(
            "coverage harus identik, cuma jangkauannya yang berbeda",
            bersih.syncedRate,
            samaTapiSinyalJelek.syncedRate,
        )
        assertEquals(3, bersih.answered)
        assertEquals(3, samaTapiSinyalJelek.answered)
        assertEquals(6, samaTapiSinyalJelek.total)
    }

    @Test
    fun `reachRate justru yang menangkap sinyal jelek`() {
        val s = stats(
            ev(LyricsOutcome.Synced),
            ev(LyricsOutcome.Unavailable),
            ev(LyricsOutcome.Unavailable),
            ev(LyricsOutcome.Unavailable),
        )
        assertEquals(0.25f, s.reachRate!!, 0.001f)
        assertEquals("coverage tetap sempurna dari yang terjawab", 1.0f, s.syncedRate!!, 0.001f)
    }

    @Test
    fun `semua gagal dijangkau berarti coverage belum diketahui`() {
        val s = stats(ev(LyricsOutcome.Unavailable), ev(LyricsOutcome.Unavailable))
        assertNull("tidak ada yang terjawab, jadi belum tahu", s.syncedRate)
        assertEquals(0f, s.reachRate!!, 0.001f)
    }

    // ── Angka-angka rate ─────────────────────────────────────────

    @Test
    fun `syncedRate menghitung dari yang terjawab`() {
        val s = stats(
            ev(LyricsOutcome.Synced),
            ev(LyricsOutcome.Synced),
            ev(LyricsOutcome.Synced),
            ev(LyricsOutcome.NotFound),
        )
        assertEquals(0.75f, s.syncedRate!!, 0.001f)
    }

    @Test
    fun `anyLyricsRate ikut menghitung teks polos`() {
        val s = stats(
            ev(LyricsOutcome.Synced),
            ev(LyricsOutcome.PlainOnly),
            ev(LyricsOutcome.NotFound),
            ev(LyricsOutcome.NotFound),
        )
        assertEquals(0.25f, s.syncedRate!!, 0.001f)
        assertEquals(0.5f, s.anyLyricsRate!!, 0.001f)
        assertTrue("any harus selalu >= synced", s.anyLyricsRate!! >= s.syncedRate!!)
    }

    @Test
    fun `cacheRate menghitung memory dan disk sebagai satu`() {
        val s = stats(
            ev(LyricsOutcome.Synced, LyricsSource.MemCache),
            ev(LyricsOutcome.Synced, LyricsSource.DiskCache),
            ev(LyricsOutcome.Synced, LyricsSource.Network),
            ev(LyricsOutcome.Synced, LyricsSource.Network),
        )
        assertEquals(0.5f, s.cacheRate!!, 0.001f)
        assertEquals(1, s.fromMem)
        assertEquals(1, s.fromDisk)
        assertEquals(2, s.fromNetwork)
    }

    // ── Angka yang menentukan pekerjaan berikutnya ───────────────

    /**
     * searchOnlyRate adalah alasan seluruh file ini ada. Sekarang /get dan
     * /search selalu ditembak paralel, menggandakan beban ke API komunitas
     * gratis. Angka ini yang menentukan apakah /search boleh diturunkan jadi
     * fallback.
     */
    @Test
    fun `searchOnlyRate mengukur seberapa sering search menyelamatkan`() {
        val s = stats(
            ev(LyricsOutcome.Synced, probe = ProbeSource.Get),
            ev(LyricsOutcome.Synced, probe = ProbeSource.Get),
            ev(LyricsOutcome.Synced, probe = ProbeSource.Get),
            ev(LyricsOutcome.Synced, probe = ProbeSource.Search),
        )
        assertEquals(0.25f, s.searchOnlyRate!!, 0.001f)
    }

    @Test
    fun `lookup dari cache tidak mengotori angka probe`() {
        val s = stats(
            ev(LyricsOutcome.Synced, LyricsSource.MemCache),
            ev(LyricsOutcome.Synced, LyricsSource.DiskCache),
            ev(LyricsOutcome.Synced, LyricsSource.Network, ProbeSource.Get),
        )
        assertEquals(1, s.getWon)
        assertEquals(0, s.searchWon)
        assertEquals(
            "penyebutnya cuma lookup jaringan yang berhasil",
            1.0f,
            1f - s.searchOnlyRate!!,
            0.001f,
        )
    }

    @Test
    fun `belum ada lookup jaringan berarti searchOnlyRate belum diketahui`() {
        val s = stats(ev(LyricsOutcome.Synced, LyricsSource.MemCache))
        assertNull(s.searchOnlyRate)
    }

    // ── outcomeOf ────────────────────────────────────────────────

    @Test
    fun `lirik ter-sync dinilai Synced`() {
        val state = LyricsState.Ready(
            LyricsResult(
                trackId = "t",
                synced = listOf(SyncedLine(id = 0, timeMs = 0, text = "halo")),
                plain = null,
            )
        )
        assertEquals(LyricsOutcome.Synced, outcomeOf(state))
    }

    @Test
    fun `hanya teks polos dinilai PlainOnly`() {
        val state = LyricsState.Ready(
            LyricsResult(trackId = "t", synced = null, plain = "halo dunia")
        )
        assertEquals(LyricsOutcome.PlainOnly, outcomeOf(state))
    }

    @Test
    fun `synced kosong turun jadi PlainOnly bukan Synced`() {
        val state = LyricsState.Ready(
            LyricsResult(trackId = "t", synced = emptyList(), plain = "halo")
        )
        assertEquals(LyricsOutcome.PlainOnly, outcomeOf(state))
    }

    @Test
    fun `NotFound dan Unavailable dipetakan terpisah`() {
        assertEquals(LyricsOutcome.NotFound, outcomeOf(LyricsState.NotFound))
        assertEquals(
            LyricsOutcome.Unavailable,
            outcomeOf(LyricsState.Unavailable("timeout")),
        )
    }
}
