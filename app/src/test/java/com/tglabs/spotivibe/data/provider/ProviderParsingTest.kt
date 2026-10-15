package com.tglabs.spotivibe.data.provider

import com.tglabs.spotivibe.domain.LrclibProbe
import com.tglabs.spotivibe.domain.LyricsResult
import com.tglabs.spotivibe.domain.ProbeSource
import com.tglabs.spotivibe.domain.ProviderProbe
import com.tglabs.spotivibe.domain.SyncedLine
import com.tglabs.spotivibe.domain.isInterlude
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsing respons tiap sumber. Fixture di `src/test/resources/providers/`
 * meniru bentuk respons asli yang direkam 2026-10-15, dengan teks buatan.
 */
class ProviderParsingTest {

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/providers/$name")) { "fixture $name tidak ada" }.readText()

    private val query = TrackQuery(
        trackId = "spotify:track:0VjIjW4GlUZAMYd2vXMi3b",
        title = "Blinding Lights",
        artist = "The Weeknd",
        album = "After Hours",
        durationMs = 200_000,
    )

    // ── AMLL ─────────────────────────────────────────────────────

    /** REGRESI desain: pencarian AMLL fuzzy, item pertamanya lagu lain. */
    @Test
    fun `amll hanya menerima item yang mencantumkan ID Spotify lagunya`() {
        assertEquals(8269588720193923L, pickAmllId(fixture("amll_search.json"), query.spotifyId))
        assertNull(pickAmllId(fixture("amll_search.json"), "idYangTidakAda"))
        assertNull(pickAmllId(fixture("amll_search.json"), ""))
        assertNull(pickAmllId("bukan json", query.spotifyId))
    }

    @Test
    fun `amll get jadi lirik per kata dengan durasi dari TTML`() {
        val probe = amllProbe(fixture("amll_get.json"), query.trackId) as ProviderProbe.Content
        assertEquals(ProbeSource.Amll, probe.probe)
        assertEquals(200.0, probe.durationSec!!, 0.001)
        assertEquals("Baris satu", probe.result.synced?.single()?.text)
        assertEquals(query.trackId, probe.result.trackId)
    }

    @Test
    fun `amll tanpa lirik atau TTML rusak berarti tidak ada`() {
        assertEquals(ProviderProbe.Absent, amllProbe("""{"status":200,"data":{}}""", query.trackId))
        assertEquals(ProviderProbe.Absent, amllProbe("""{"data":{"lyrics":"<tt><p>"}}""", query.trackId))
    }

    // ── lrc.red ──────────────────────────────────────────────────

    @Test
    fun `lrc red memilih versi per kata yang judul artis dan durasinya cocok`() {
        val pick = pickLrcRedResult(fixture("lrcred_search.json"), query)
        assertEquals("https://lrc.red/s/WORD.ttml", pick?.lyricsUrl)
    }

    @Test
    fun `lrc red tanpa durasi lagu tetap menyaring judul dan artis`() {
        val pick = pickLrcRedResult(fixture("lrcred_search.json"), query.copy(durationMs = 0))
        assertTrue(pick?.lyricsUrl in setOf("https://lrc.red/s/WORD.ttml", "https://lrc.red/s/FAR.ttml"))
    }

    @Test
    fun `lrc red tanpa hasil cocok null`() {
        assertNull(pickLrcRedResult("""{"results":[],"source":"MISS-LRC-RED","total":0}""", query))
        assertNull(pickLrcRedResult(fixture("lrcred_search.json"), query.copy(title = "Save Your Tears")))
    }

    @Test
    fun `url lirik lrc red hanya https milik lrc red`() {
        assertEquals("lrc.red", allowedLrcRedUrl("https://lrc.red/s/X.ttml")?.host)
        assertNull(allowedLrcRedUrl("http://lrc.red/s/X.ttml"))
        assertNull(allowedLrcRedUrl("https://evil.example/s/X.ttml"))
        assertNull(allowedLrcRedUrl("https://lrc.red.evil.example/x"))
        assertNull(allowedLrcRedUrl("bukan url"))
    }

    // ── LRCMux ───────────────────────────────────────────────────

    @Test
    fun `lrcmux jadi baris per kata dengan penanda jeda`() {
        val probe = lrcMuxProbe(fixture("lrcmux.json"), query.trackId) as ProviderProbe.Content
        val lines = probe.result.synced!!
        assertEquals(ProbeSource.LrcMux, probe.probe)
        assertEquals(200.0, probe.durationSec!!, 0.001)
        assertEquals(listOf("Yeah", "", "I've been tryna call", "Tanpa kata"), lines.map { it.text })
        assertTrue(lines[1].isInterlude)
        assertEquals(listOf("I've ", "been ", "tryna ", "call"), lines[2].words.map { it.text })
        assertEquals(28_824L, lines[2].words.last().endMs)
        assertTrue(lines[3].words.isEmpty())
        assertEquals(lines.indices.toList(), lines.map { it.id })
    }

    @Test
    fun `lrcmux kosong atau rusak berarti tidak ada`() {
        assertEquals(ProviderProbe.Absent, lrcMuxProbe("""{"lines":[]}""", query.trackId))
        assertEquals(ProviderProbe.Absent, lrcMuxProbe("""{"title":"Not Found","status":404}""", query.trackId))
        assertEquals(ProviderProbe.Absent, lrcMuxProbe("<html>", query.trackId))
    }

    // ── LRCLIB ───────────────────────────────────────────────────

    private fun synced(teks: String) = LyricsResult("t", listOf(SyncedLine(0, 1_000, teks)), null)

    @Test
    fun `lrclib membawa durasi versi yang menang`() {
        val probe = toProviderProbe(
            get = LrclibProbe.Content(LyricsResult("t", null, "plain"), 200.0),
            search = LrclibProbe.Content(synced("dari search"), 201.0),
            targetSec = 200.0,
        ) as ProviderProbe.Content
        assertEquals(ProbeSource.Search, probe.probe)
        assertEquals(201.0, probe.durationSec!!, 0.001)
    }

    @Test
    fun `lrclib tidak ada dan gagal diterjemahkan`() {
        assertEquals(ProviderProbe.Absent, toProviderProbe(LrclibProbe.Absent, LrclibProbe.Absent, 200.0))
        assertEquals(
            ProviderProbe.Failed("http_503"),
            toProviderProbe(LrclibProbe.Failed("http_503"), LrclibProbe.Absent, 200.0),
        )
    }
}
