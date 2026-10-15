package com.tglabs.spotivibe.data

import com.tglabs.spotivibe.data.provider.LyricsProvider
import com.tglabs.spotivibe.data.provider.TrackQuery
import com.tglabs.spotivibe.domain.LyricsLookupEvent
import com.tglabs.spotivibe.domain.LyricsResult
import com.tglabs.spotivibe.domain.LyricsState
import com.tglabs.spotivibe.domain.ProbeSource
import com.tglabs.spotivibe.domain.ProviderCooldown
import com.tglabs.spotivibe.domain.ProviderId
import com.tglabs.spotivibe.domain.ProviderProbe
import com.tglabs.spotivibe.domain.SourceOutcome
import com.tglabs.spotivibe.domain.TimingTier
import com.tglabs.spotivibe.domain.SyncedLine
import com.tglabs.spotivibe.domain.SyncedWord
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.util.concurrent.atomic.AtomicInteger

/**
 * Perlombaan sumber lewat [LyricsRepository] sungguhan: LRCLIB palsu plus
 * sumber komunitas palsu yang menjawab dengan jeda berbeda.
 *
 * Memakai `runBlocking` dan jeda puluhan milidetik, bukan waktu virtual:
 * repository pindah ke `Dispatchers.IO`, yang tidak dikendalikan scheduler
 * test. Tenggat konten diperpendek lewat `contentGraceMs` supaya test tetap
 * di bawah satu detik.
 */
class LyricsRaceRepositoryTest {

    private class NoDisk : LyricsDiskCache {
        var put: LyricsResult? = null
        override fun get(trackId: String): LyricsResult? = null
        override fun put(result: LyricsResult) {
            put = result
        }
    }

    /** LRCLIB palsu: `/get` 404, `/search` mengembalikan [search] (kosong = tidak ada). */
    private class FakeLrclib(private val search: List<LrclibDto>, private val failing: Boolean = false) : LrclibApi {
        override suspend fun get(
            trackName: String,
            artistName: String,
            albumName: String,
            duration: Int,
        ): Response<LrclibDto> = Response.error(if (failing) 503 else 404, "".toResponseBody(null))

        override suspend fun search(trackName: String, artistName: String): Response<List<LrclibDto>> =
            if (failing) Response.error(503, "".toResponseBody(null)) else Response.success(search)
    }

    /** Sumber palsu yang menjawab [answer] setelah [delayMs], atau menggantung selamanya. */
    private class FakeProvider(
        override val id: ProviderId,
        private val delayMs: Long,
        private val answer: ProviderProbe?,
    ) : LyricsProvider {
        val calls = AtomicInteger(0)
        @Volatile var cancelled = false

        override suspend fun lookup(query: TrackQuery): ProviderProbe {
            calls.incrementAndGet()
            try {
                delay(delayMs)
                return answer ?: awaitCancellation()
            } catch (e: kotlinx.coroutines.CancellationException) {
                cancelled = true
                throw e
            }
        }
    }

    private val lrclibLine = LrclibDto(trackName = "Lagu", duration = 200.0, syncedLyrics = "[00:01.00] dari lrclib")

    private fun wordContent(teks: String, probe: ProbeSource) = ProviderProbe.Content(
        LyricsResult(
            trackId = "t1",
            synced = listOf(SyncedLine(0, 1_000, teks, words = listOf(SyncedWord(1_000, teks)))),
            plain = null,
        ),
        durationSec = 200.0,
        probe = probe,
    )

    private val dicatat = mutableListOf<LyricsLookupEvent>()

    private fun repo(
        providers: List<LyricsProvider>,
        lrclib: LrclibApi = FakeLrclib(listOf(lrclibLine)),
        community: Boolean = true,
        cooldown: ProviderCooldown = ProviderCooldown(),
        disk: NoDisk = NoDisk(),
    ) = LyricsRepository(
        diskCache = disk,
        statsRecorder = { dicatat += it },
        lrclibApi = lrclib,
        providers = providers,
        communitySources = { community },
        cooldown = cooldown,
        contentGraceMs = 150,
    )

    private fun LyricsRepository.ambil(): LyricsState = runBlocking {
        fetchLyrics("t1", "Lagu", "Artis", "Album", 200_000, allowRetry = false)
    }

    private fun teks(state: LyricsState): String? =
        (state as? LyricsState.Ready)?.result?.synced?.firstOrNull()?.text

    @Test
    fun `per kata dari komunitas mengalahkan per baris dari LRCLIB`() {
        val amll = FakeProvider(ProviderId.Amll, 20, wordContent("dari amll", ProbeSource.Amll))
        val state = repo(listOf(amll)).ambil()
        assertEquals("dari amll", teks(state))
        assertEquals(ProbeSource.Amll, dicatat.single().probe)
    }

    @Test
    fun `pemenang per kata teratas membatalkan sumber yang masih jalan`() {
        val amll = FakeProvider(ProviderId.Amll, 10, wordContent("dari amll", ProbeSource.Amll))
        val lambat = FakeProvider(ProviderId.LrcMux, 5_000, wordContent("dari lrcmux", ProbeSource.LrcMux))
        val mulai = System.nanoTime()
        val state = repo(listOf(amll, lambat)).ambil()
        val lamaMs = (System.nanoTime() - mulai) / 1_000_000
        assertEquals("dari amll", teks(state))
        assertTrue("sumber lambat harus dibatalkan", lambat.cancelled)
        assertTrue("tidak boleh menunggu sumber lambat, $lamaMs ms", lamaMs < 2_000)
    }

    @Test
    fun `sumber yang menggantung tidak menahan lirik setelah tenggat`() {
        val gantung = FakeProvider(ProviderId.Amll, 0, null)
        val state = repo(listOf(gantung)).ambil()
        assertEquals("dari lrclib", teks(state))
        assertTrue(gantung.cancelled)
    }

    @Test
    fun `komunitas gagal dan LRCLIB tidak ada tetap NotFound dan di-cache`() {
        val disk = NoDisk()
        val gagal = FakeProvider(ProviderId.LrcRed, 5, ProviderProbe.Failed("http_503"))
        val state = repo(listOf(gagal), lrclib = FakeLrclib(emptyList()), disk = disk).ambil()
        assertEquals(LyricsState.NotFound, state)
        assertEquals(false, disk.put?.hasContent)
    }

    @Test
    fun `LRCLIB gagal tapi komunitas punya lirik tetap Ready`() {
        val mux = FakeProvider(ProviderId.LrcMux, 5, wordContent("dari lrcmux", ProbeSource.LrcMux))
        val state = repo(listOf(mux), lrclib = FakeLrclib(emptyList(), failing = true)).ambil()
        assertEquals("dari lrcmux", teks(state))
    }

    @Test
    fun `setelan komunitas mati berarti hanya LRCLIB`() {
        val amll = FakeProvider(ProviderId.Amll, 5, wordContent("dari amll", ProbeSource.Amll))
        val state = repo(listOf(amll), community = false).ambil()
        assertEquals("dari lrclib", teks(state))
        assertEquals(0, amll.calls.get())
    }

    @Test
    fun `sumber yang sedang dijeda tidak ditanya`() {
        val cooldown = ProviderCooldown()
        repeat(2) { cooldown.record(ProviderId.Amll, ProviderProbe.Failed("http_503")) }
        val amll = FakeProvider(ProviderId.Amll, 5, wordContent("dari amll", ProbeSource.Amll))
        val state = repo(listOf(amll), cooldown = cooldown).ambil()
        assertEquals("dari lrclib", teks(state))
        assertEquals(0, amll.calls.get())
    }

    @Test
    fun `kegagalan berturut sumber komunitas mulai menjedanya`() {
        val cooldown = ProviderCooldown()
        val gagal = FakeProvider(ProviderId.LrcRed, 0, ProviderProbe.Failed("http_503"))
        // Dua repository berbagi satu cooldown: repository kedua tidak punya
        // mem cache dari yang pertama, jadi benar-benar berlomba lagi.
        repo(listOf(gagal), cooldown = cooldown).ambil()
        repo(listOf(gagal), cooldown = cooldown).ambil()
        assertTrue(cooldown.isCoolingDown(ProviderId.LrcRed))
    }

    // ── Report sumber ────────────────────────────────────────────

    private fun LyricsRepository.reportT1() = reports.value["t1"]

    @Test
    fun `report mencatat pemenang dan sumber yang dibatalkan`() {
        val amll = FakeProvider(ProviderId.Amll, 10, wordContent("dari amll", ProbeSource.Amll))
        val lambat = FakeProvider(ProviderId.LrcMux, 5_000, wordContent("dari lrcmux", ProbeSource.LrcMux))
        val r = repo(listOf(amll, lambat))
        r.ambil()
        val report = r.reportT1()!!
        assertEquals(ProviderId.Amll, report.winner)
        val byId = report.entries.associateBy { it.id }
        assertEquals(SourceOutcome.Found(TimingTier.Word), byId.getValue(ProviderId.Amll).outcome)
        assertEquals(SourceOutcome.NotAsked, byId.getValue(ProviderId.LrcMux).outcome)
    }

    @Test
    fun `report mencatat sumber yang dijeda dan yang gagal`() {
        val cooldown = ProviderCooldown()
        repeat(2) { cooldown.record(ProviderId.Amll, ProviderProbe.Failed("http_503")) }
        val amll = FakeProvider(ProviderId.Amll, 5, wordContent("dari amll", ProbeSource.Amll))
        val gagal = FakeProvider(ProviderId.LrcRed, 5, ProviderProbe.Failed("http_502"))
        val r = repo(listOf(amll, gagal), cooldown = cooldown)
        r.ambil()
        val byId = r.reportT1()!!.entries.associateBy { it.id }
        assertEquals(SourceOutcome.Skipped, byId.getValue(ProviderId.Amll).outcome)
        assertEquals(SourceOutcome.Failed("http_502"), byId.getValue(ProviderId.LrcRed).outcome)
        assertEquals(ProviderId.Lrclib, r.reportT1()!!.winner)
    }

    @Test
    fun `report disimpan per lagu dan dibatasi`() {
        val r = repo(emptyList())
        (1..10).forEach { i -> runBlocking { r.fetchLyrics("t$i", "Lagu", "Artis", "Album", 200_000, allowRetry = false) } }
        assertEquals(8, r.reports.value.size)
        assertEquals((3..10).map { "t$it" }, r.reports.value.keys.toList())
    }
}
