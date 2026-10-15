package com.tglabs.spotivibe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRaceTest {

    private val target = 200.0

    private fun lines(withWords: Boolean) = listOf(
        SyncedLine(
            id = 0,
            timeMs = 1_000,
            text = "halo",
            words = if (withWords) listOf(SyncedWord(1_000, "halo")) else emptyList(),
        ),
    )

    private fun word(id: ProviderId, durasi: Double? = 200.0, probe: ProbeSource = ProbeSource.Amll) =
        ProviderAnswer(id, ProviderProbe.Content(LyricsResult("t", lines(true), null), durasi, probe))

    private fun line(id: ProviderId, durasi: Double? = 200.0, probe: ProbeSource = ProbeSource.Get) =
        ProviderAnswer(id, ProviderProbe.Content(LyricsResult("t", lines(false), null), durasi, probe))

    private fun plain(id: ProviderId) =
        ProviderAnswer(id, ProviderProbe.Content(LyricsResult("t", null, "teks"), 200.0, ProbeSource.Get))

    private fun absent(id: ProviderId) = ProviderAnswer(id, ProviderProbe.Absent)
    private fun failed(id: ProviderId) = ProviderAnswer(id, ProviderProbe.Failed("http_503"))

    // ── Tier ─────────────────────────────────────────────────────

    @Test
    fun `tier timing dari isi lirik`() {
        assertEquals(TimingTier.Word, timingTier(LyricsResult("t", lines(true), null)))
        assertEquals(TimingTier.Line, timingTier(LyricsResult("t", lines(false), null)))
        assertEquals(TimingTier.Plain, timingTier(LyricsResult("t", null, "teks")))
    }

    // ── Pemenang ─────────────────────────────────────────────────

    @Test
    fun `per kata mengalahkan per baris walau peringkatnya lebih rendah`() {
        val menang = pickProviderWinner(listOf(line(ProviderId.Amll), word(ProviderId.LrcMux)), target)
        assertEquals(ProviderId.LrcMux, menang?.id)
    }

    @Test
    fun `timing setara dimenangkan peringkat`() {
        val menang = pickProviderWinner(listOf(word(ProviderId.LrcMux), word(ProviderId.Amll)), target)
        assertEquals(ProviderId.Amll, menang?.id)
    }

    @Test
    fun `versi di luar toleransi kalah dari per baris yang pas`() {
        val menang = pickProviderWinner(
            listOf(word(ProviderId.LrcMux, durasi = 230.0), line(ProviderId.Lrclib, durasi = 200.0)),
            target,
        )
        assertEquals(ProviderId.Lrclib, menang?.id)
    }

    @Test
    fun `durasi tidak dilaporkan dianggap cocok`() {
        val menang = pickProviderWinner(listOf(word(ProviderId.Amll, durasi = null), line(ProviderId.Lrclib)), target)
        assertEquals(ProviderId.Amll, menang?.id)
    }

    @Test
    fun `plain kalah dari synced`() {
        val menang = pickProviderWinner(listOf(plain(ProviderId.Amll), line(ProviderId.Lrclib)), target)
        assertEquals(ProviderId.Lrclib, menang?.id)
    }

    @Test
    fun `tanpa konten tidak ada pemenang`() {
        assertNull(pickProviderWinner(listOf(absent(ProviderId.Amll), failed(ProviderId.Lrclib)), target))
    }

    // ── Kapan berhenti ───────────────────────────────────────────

    @Test
    fun `per kata dari peringkat teratas langsung selesai`() {
        val pending = setOf(ProviderId.LrcRed, ProviderId.LrcMux, ProviderId.Lrclib)
        assertTrue(isRaceSettled(listOf(word(ProviderId.Amll)), pending, target))
    }

    @Test
    fun `per kata menunggu sumber berperingkat lebih tinggi`() {
        assertFalse(isRaceSettled(listOf(word(ProviderId.LrcMux)), setOf(ProviderId.Amll), target))
        assertTrue(isRaceSettled(listOf(word(ProviderId.LrcMux)), setOf(ProviderId.Lrclib), target))
    }

    @Test
    fun `per baris tidak menghentikan perlombaan`() {
        assertFalse(isRaceSettled(listOf(line(ProviderId.Lrclib)), setOf(ProviderId.LrcMux), target))
    }

    @Test
    fun `per kata di luar toleransi tidak menghentikan perlombaan`() {
        assertFalse(isRaceSettled(listOf(word(ProviderId.Amll, durasi = 260.0)), setOf(ProviderId.Lrclib), target))
    }

    @Test
    fun `semua sudah menjawab berarti selesai`() {
        assertTrue(isRaceSettled(listOf(absent(ProviderId.Amll)), emptySet(), target))
    }

    // ── Keputusan akhir ──────────────────────────────────────────

    @Test
    fun `konten jadi Found dengan sumber statistiknya`() {
        val hasil = combineProviderAnswers(listOf(word(ProviderId.LrcRed, probe = ProbeSource.LrcRed)), target)
        assertEquals(ProbeSource.LrcRed, (hasil as LyricsLookup.Found).probe)
    }

    /**
     * Sumber komunitas sering tidak terjangkau. Kalau kegagalan mereka
     * membatalkan "tidak ada" dari LRCLIB, lagu tanpa lirik tidak pernah
     * di-cache negatif dan ditanyakan ulang setiap kali diputar.
     */
    @Test
    fun `LRCLIB tidak ada dan komunitas gagal tetap NotFound`() {
        val hasil = combineProviderAnswers(listOf(failed(ProviderId.Amll), absent(ProviderId.Lrclib)), target)
        assertEquals(LyricsLookup.NotFound, hasil)
    }

    @Test
    fun `LRCLIB gagal tanpa konten jadi Unavailable`() {
        val hasil = combineProviderAnswers(listOf(absent(ProviderId.Amll), failed(ProviderId.Lrclib)), target)
        assertEquals(LyricsLookup.Unavailable("http_503"), hasil)
    }

    @Test
    fun `tanpa LRCLIB NotFound hanya kalau semua tidak ada`() {
        assertEquals(LyricsLookup.NotFound, combineProviderAnswers(listOf(absent(ProviderId.Amll)), target))
        assertTrue(
            combineProviderAnswers(listOf(absent(ProviderId.Amll), failed(ProviderId.LrcMux)), target)
                is LyricsLookup.Unavailable,
        )
        assertTrue(combineProviderAnswers(emptyList(), target) is LyricsLookup.Unavailable)
    }
}
