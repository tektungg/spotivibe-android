package com.tglabs.spotivibe.data.provider

import com.tglabs.spotivibe.data.HttpClients
import com.tglabs.spotivibe.domain.ProviderProbe
import com.tglabs.spotivibe.domain.TimingTier
import com.tglabs.spotivibe.domain.timingTier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Lane periodik: memanggil API sumber komunitas SUNGGUHAN.
 *
 * Tidak jalan di gate test (butuh jaringan, dan API pihak ketiga bisa berubah
 * atau mati kapan saja). Jalankan sebelum rilis dan saat curiga sebuah sumber
 * berubah bentuk:
 *
 * ```
 * SPOTIVIBE_LIVE=1 ./gradlew testFullDebugUnitTest --tests "*LiveProvidersTest"
 * ```
 *
 * Lagu acuannya "Blinding Lights" karena per 2026-10-15 ketiga sumber punya
 * versi per kata untuk lagu itu. Kalau test ini gagal, yang pertama dicek
 * adalah bentuk respons API-nya, bukan kode app.
 */
class LiveProvidersTest {

    private val query = TrackQuery(
        trackId = "spotify:track:0VjIjW4GlUZAMYd2vXMi3b",
        title = "Blinding Lights",
        artist = "The Weeknd",
        album = "After Hours",
        durationMs = 200_040,
    )

    @Before
    fun hanyaKalauDiminta() {
        assumeTrue("set SPOTIVIBE_LIVE=1 untuk memanggil API sungguhan", System.getenv("SPOTIVIBE_LIVE") == "1")
    }

    private fun assertWordSynced(provider: LyricsProvider) {
        val probe = runBlocking { provider.lookup(query) }
        val content = probe as? ProviderProbe.Content
            ?: throw AssertionError("${provider.id} tidak mengembalikan lirik: $probe")
        assertEquals("${provider.id} harus per kata", TimingTier.Word, timingTier(content.result))
    }

    @Test
    fun amll() = assertWordSynced(AmllProvider(HttpClients.lyrics))

    @Test
    fun lrcRed() = assertWordSynced(LrcRedProvider(HttpClients.lyrics))

    @Test
    fun lrcMux() = assertWordSynced(LrcMuxProvider(HttpClients.lyrics))
}
