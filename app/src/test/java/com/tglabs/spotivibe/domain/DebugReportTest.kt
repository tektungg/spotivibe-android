package com.tglabs.spotivibe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugReportTest {

    // ── Jejak ────────────────────────────────────────────────────

    @Test
    fun `jejak menyimpan kejadian terakhir dan membuang yang terlama`() {
        val trace = SyncTrace(capacity = 3)
        (1..5).forEach { trace.record(it * 100L, "e$it") }
        assertEquals(listOf("e3", "e4", "e5"), trace.snapshot().map { it.message })
    }

    @Test
    fun `snapshot jejak adalah salinan`() {
        val trace = SyncTrace()
        trace.record(0, "a")
        val snap = trace.snapshot()
        trace.record(1, "b")
        assertEquals(1, snap.size)
    }

    // ── Isi report ───────────────────────────────────────────────

    private val base = DebugInfo(
        appVersion = "0.9.1 (25)",
        variant = "full release",
        androidVersion = "14 (API 34)",
        device = "Xiaomi 2412DPC0AG",
    )

    @Test
    fun `report tanpa player tetap lengkap`() {
        val text = buildDebugReport(base)
        assertTrue(text.contains("App: 0.9.1 (25) (full release)"))
        assertTrue(text.contains("none followed"))
        assertTrue(text.contains("State: ?"))
        assertTrue(text.contains("Recent events"))
    }

    @Test
    fun `report memuat player lagu sumber dan jejak`() {
        val report = SourceReport(
            trackId = "spotify:track:abc",
            entries = listOf(
                SourceEntry(ProviderId.Amll, SourceOutcome.Found(TimingTier.Word), durationSec = 200.0),
                SourceEntry(ProviderId.LrcRed, SourceOutcome.Failed("http_503")),
                SourceEntry(ProviderId.LrcMux, SourceOutcome.NotAsked),
                SourceEntry(ProviderId.Lrclib, SourceOutcome.NotFound),
            ),
            winner = ProviderId.Amll,
        )
        val text = buildDebugReport(
            base.copy(
                playerPackage = "com.spotify.music",
                playerLabel = "Spotify",
                playerVersion = "9.0.60",
                track = NowPlaying("spotify:track:abc", "Judul", "Artis", "Album", null, 200_000, 12_000, false),
                controls = PlayerControls(canPlayPause = true, canSeek = false),
                offsets = SyncOffsets(globalMs = 100, deviceMs = 250, songMs = -50, outputLabel = "Toyota"),
                lyricsState = LyricsState.NotFound,
                report = report,
                trace = listOf(TraceEntry(1_000, "follow com.spotify.music"), TraceEntry(4_500, "seek refused")),
                nowMs = 5_000,
            ),
        )
        assertTrue(text.contains("Spotify · com.spotify.music · 9.0.60"))
        assertTrue(text.contains("Track: Judul — Artis (Album) · 200000 ms · spotify:track:abc"))
        assertTrue(text.contains("seek=false"))
        assertTrue(text.contains("total 300 ms"))
        assertTrue(text.contains("Winner: AMLL"))
        assertTrue(text.contains("AMLL: found · word · 200.0 s"))
        assertTrue(text.contains("lrc.red: failed · server error · http_503"))
        assertTrue(text.contains("LRCMux: not asked"))
        assertTrue(text.contains("-4.0s follow com.spotify.music"))
        assertTrue(text.contains("-0.5s seek refused"))
    }

    @Test
    fun `lagu yang dijawab cache disebut, bukan dikosongkan`() {
        val text = buildDebugReport(base.copy(lyricsState = LyricsState.NotFound))
        assertTrue(text.contains("no network lookup for this track"))
    }

    // ── Redaksi ──────────────────────────────────────────────────

    @Test
    fun `query string URL dan nilai rahasia dibuang`() {
        val bersih = redact("gagal https://api.example.com/get?track=x&key=abc123 lalu token=zzz dan api_key=k1")
        assertFalse(bersih.contains("abc123"))
        assertFalse(bersih.contains("zzz"))
        assertFalse(bersih.contains("k1"))
        assertFalse(bersih.contains("track=x"))
        assertTrue(bersih.contains("https://api.example.com/get?…"))
    }

    @Test
    fun `alasan gagal berisi URL ikut disensor di report`() {
        val report = SourceReport(
            "t",
            listOf(SourceEntry(ProviderId.LrcRed, SourceOutcome.Failed("timeout https://lrc.red/api/v1?track=rahasia"))),
            winner = null,
        )
        val text = buildDebugReport(base.copy(report = report))
        assertFalse(text.contains("rahasia"))
    }

    @Test
    fun `teks biasa tidak berubah`() {
        assertEquals("Spotify · com.spotify.music", redact("Spotify · com.spotify.music"))
    }
}
