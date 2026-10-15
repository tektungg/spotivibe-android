package com.tglabs.spotivibe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceReportTest {

    private val word = LyricsResult(
        trackId = "t",
        synced = listOf(SyncedLine(0, 1_000, "a", words = listOf(SyncedWord(1_000, "a")))),
        plain = null,
    )

    private fun content(id: ProviderId) = ProviderAnswer(id, ProviderProbe.Content(word, 200.0, ProbeSource.Amll))
    private fun failed(id: ProviderId, reason: String) = ProviderAnswer(id, ProviderProbe.Failed(reason))

    // ── Menyusun report ──────────────────────────────────────────

    @Test
    fun `setiap sumber muncul sekali dan urut peringkat`() {
        val r = sourceReportOf(
            trackId = "t",
            answers = listOf(failed(ProviderId.Lrclib, "http_503"), content(ProviderId.LrcMux)),
            raced = listOf(ProviderId.Lrclib, ProviderId.LrcMux, ProviderId.LrcRed),
            skipped = listOf(ProviderId.Amll),
            winner = ProviderId.LrcMux,
        )
        assertEquals(listOf(ProviderId.Amll, ProviderId.LrcRed, ProviderId.LrcMux, ProviderId.Lrclib), r.entries.map { it.id })
        assertEquals(SourceOutcome.Skipped, r.entries[0].outcome)
        assertEquals(SourceOutcome.NotAsked, r.entries[1].outcome)
        assertEquals(SourceOutcome.Found(TimingTier.Word), r.entries[2].outcome)
        assertEquals(word, r.entries[2].result)
        assertEquals(200.0, r.entries[2].durationSec!!, 0.0)
        assertEquals(SourceOutcome.Failed("http_503"), r.entries[3].outcome)
        assertEquals(ProviderId.LrcMux, r.winner)
    }

    @Test
    fun `memperbarui satu sumber menjaga urutan`() {
        val r = sourceReportOf("t", emptyList(), listOf(ProviderId.Amll, ProviderId.Lrclib), emptyList(), null)
        val baru = r.with(SourceEntry(ProviderId.Amll, SourceOutcome.NotFound))
        assertEquals(listOf(ProviderId.Amll, ProviderId.Lrclib), baru.entries.map { it.id })
        assertEquals(SourceOutcome.NotFound, baru.entries[0].outcome)
    }

    // ── Alasan gagal ─────────────────────────────────────────────

    @Test
    fun `alasan gagal digolongkan`() {
        assertEquals("timed out", describeFailure("lrcmux: SocketTimeoutException timeout"))
        assertEquals("timed out", describeFailure("connect timed out"))
        assertEquals("rate limited", describeFailure("http_429"))
        assertEquals("server error", describeFailure("http_503"))
        assertEquals("refused the request", describeFailure("http_403"))
        assertEquals("no connection", describeFailure("Unable to resolve host lrclib.net"))
        assertEquals("unreachable", describeFailure("aneh"))
    }

    // ── Pesan ────────────────────────────────────────────────────

    private fun reportWith(vararg failures: Pair<ProviderId, String>) = SourceReport(
        trackId = "t",
        entries = failures.map { (id, why) -> SourceEntry(id, SourceOutcome.Failed(why)) } +
            SourceEntry(ProviderId.Lrclib, SourceOutcome.NotFound),
        winner = null,
    )

    @Test
    fun `tidak ada lirik menyebut sumber yang gagal`() {
        val notice = lyricsNotice(LyricsState.NotFound, reportWith(ProviderId.Amll to "timeout", ProviderId.LrcMux to "http_502"))
        assertEquals("No lyrics found. Couldn't reach AMLL (timed out) and LRCMux (server error).", notice)
    }

    @Test
    fun `gagal memuat menyebut sumber dan alasannya`() {
        val notice = lyricsNotice(LyricsState.Unavailable("http_503"), reportWith(ProviderId.Lrclib to "http_503"))
        assertTrue(notice!!.startsWith("Couldn't load lyrics: LRCLIB (server error)"))
    }

    @Test
    fun `tanpa kegagalan atau tanpa report pakai pesan bawaan`() {
        assertNull(lyricsNotice(LyricsState.NotFound, reportWith()))
        assertNull(lyricsNotice(LyricsState.NotFound, null))
        assertNull(lyricsNotice(LyricsState.Offline, reportWith(ProviderId.Amll to "timeout")))
        assertNull(lyricsNotice(LyricsState.Loading, reportWith(ProviderId.Amll to "timeout")))
    }

    @Test
    fun `nama digabung seperti kalimat`() {
        assertEquals("", joinNames(emptyList()))
        assertEquals("A", joinNames(listOf("A")))
        assertEquals("A and B", joinNames(listOf("A", "B")))
        assertEquals("A, B and C", joinNames(listOf("A", "B", "C")))
    }
}
