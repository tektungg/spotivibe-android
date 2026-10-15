package com.tglabs.spotivibe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourcePickerTest {

    private fun lirik(teks: String) = LyricsResult("t", listOf(SyncedLine(0, 1_000, teks)), null)

    private val amll = SourceEntry(ProviderId.Amll, SourceOutcome.Found(TimingTier.Word), lirik("amll"), 200.0)
    private val lrclib = SourceEntry(ProviderId.Lrclib, SourceOutcome.Found(TimingTier.Line), lirik("lrclib"), 199.0)
    private val report = SourceReport(
        trackId = "t",
        entries = listOf(amll, SourceEntry(ProviderId.LrcRed, SourceOutcome.NotFound), lrclib),
        winner = ProviderId.Amll,
    )

    @Test
    fun `semua sumber aktif tampil, yang belum ditanya sebagai NotAsked`() {
        val m = sourcePickerModel(report, "t", communitySources = true, asking = null, showing = null)
        assertEquals(listOf(ProviderId.Amll, ProviderId.LrcRed, ProviderId.LrcMux, ProviderId.Lrclib), m.entries.map { it.id })
        assertEquals(SourceOutcome.NotAsked, m.entries[2].outcome)
    }

    @Test
    fun `sumber komunitas mati hanya menyisakan LRCLIB`() {
        val m = sourcePickerModel(report, "t", communitySources = false, asking = null, showing = null)
        assertEquals(listOf(ProviderId.Lrclib), m.entries.map { it.id })
    }

    /** Lagu yang dijawab cache tidak punya report, tapi sumbernya tetap bisa ditanya. */
    @Test
    fun `tanpa report atau report lagu lain semua sumber belum ditanya`() {
        val tanpa = sourcePickerModel(null, "t", communitySources = true, asking = null, showing = null)
        assertEquals(4, tanpa.entries.count { it.outcome == SourceOutcome.NotAsked })
        val lain = sourcePickerModel(report, "lagu-lain", communitySources = true, asking = null, showing = null)
        assertEquals(4, lain.entries.count { it.outcome == SourceOutcome.NotAsked })
    }

    /** REGRESI desain: setelah user memilih LRCLIB, penanda harus pindah dari pemenang perlombaan. */
    @Test
    fun `yang sedang dipakai dicocokkan dengan lirik yang tampil`() {
        val tampilLrclib = lirik("lrclib").copy(trackId = "media:override")
        val m = sourcePickerModel(report, "t", communitySources = true, asking = null, showing = tampilLrclib)
        assertEquals(ProviderId.Lrclib, m.inUse)
        assertNull(sourcePickerModel(report, "t", true, null, showing = lirik("lain")).inUse)
    }

    @Test
    fun `aksi per keadaan`() {
        assertEquals(SourceAction.InUse, sourceAction(amll, inUse = ProviderId.Amll))
        assertEquals(SourceAction.Use, sourceAction(lrclib, inUse = ProviderId.Amll))
        assertEquals(SourceAction.None, sourceAction(SourceEntry(ProviderId.LrcRed, SourceOutcome.NotFound), null))
        assertEquals(SourceAction.Ask, sourceAction(SourceEntry(ProviderId.LrcMux, SourceOutcome.NotAsked), null))
        assertEquals(SourceAction.Ask, sourceAction(SourceEntry(ProviderId.LrcMux, SourceOutcome.Failed("timeout")), null))
        assertEquals(SourceAction.Ask, sourceAction(SourceEntry(ProviderId.Amll, SourceOutcome.Skipped), null))
    }

    @Test
    fun `label status`() {
        assertEquals("Word-synced · 3:20", sourceStatusLabel(amll))
        assertEquals("Line-synced · 3:19", sourceStatusLabel(lrclib))
        assertEquals("No lyrics", sourceStatusLabel(SourceEntry(ProviderId.LrcRed, SourceOutcome.NotFound)))
        assertEquals("Failed · rate limited", sourceStatusLabel(SourceEntry(ProviderId.LrcRed, SourceOutcome.Failed("http_429"))))
        assertEquals("Paused after errors", sourceStatusLabel(SourceEntry(ProviderId.Amll, SourceOutcome.Skipped)))
        assertEquals("Not asked yet", sourceStatusLabel(SourceEntry(ProviderId.Amll, SourceOutcome.NotAsked)))
    }
}
