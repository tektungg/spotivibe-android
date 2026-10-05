package com.tglabs.spotivibe.data

import com.tglabs.spotivibe.domain.LyricsResult
import com.tglabs.spotivibe.domain.SyncedLine
import com.tglabs.spotivibe.domain.cacheFileName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * [LyricsCache] sungguhan di atas folder sementara dan jam palsu.
 *
 * Dulu kelas ini tidak teruji sama sekali karena konstruktornya butuh
 * `Context`. Konstruktor internal berbasis [File] membuat perilaku yang paling
 * penting untuk mode offline bisa diuji di JVM: entri basi TIDAK hilang saat
 * dibaca, dan isi lokasi lama di `cacheDir` pindah utuh.
 */
class LyricsCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val hari = 24L * 60 * 60 * 1000
    private var sekarang = 1_700_000_000_000L

    private fun cache(dir: File = tmp.root.resolve("baru"), legacy: File? = null) =
        LyricsCache(dir, legacy, nowMs = { sekarang })

    private fun lirik(id: String = "spotify:track:a") = LyricsResult(
        trackId = id,
        synced = listOf(SyncedLine(id = 0, timeMs = 1_000, text = "halo")),
        plain = null,
    )

    private fun kosong(id: String = "spotify:track:a") =
        LyricsResult(trackId = id, synced = null, plain = null)

    // ── TTL vs cadangan basi ─────────────────────────────────────

    @Test
    fun `entri dalam masa TTL terbaca lewat get`() {
        val c = cache()
        c.put(lirik())
        assertEquals("halo", c.get("spotify:track:a")?.synced?.single()?.text)
    }

    /** REGRESI: dulu get() menghapus berkas basi, jadi getStale tidak punya apa-apa. */
    @Test
    fun `entri basi tidak terbaca lewat get tapi berkasnya tetap ada`() {
        val dir = tmp.root.resolve("baru")
        val c = cache(dir)
        c.put(lirik())
        sekarang += 31 * hari
        assertNull(c.get("spotify:track:a"))
        assertTrue(File(dir, cacheFileName("spotify:track:a")).isFile)
    }

    @Test
    fun `entri basi tersedia lewat getStale`() {
        val c = cache()
        c.put(lirik())
        sekarang += 90 * hari
        assertEquals("halo", c.getStale("spotify:track:a")?.synced?.single()?.text)
    }

    @Test
    fun `entri negatif tidak pernah jadi cadangan`() {
        val c = cache()
        c.put(kosong())
        assertNull(c.getStale("spotify:track:a"))
        sekarang += 2 * hari
        assertNull(c.getStale("spotify:track:a"))
    }

    @Test
    fun `put menimpa entri basi dengan yang segar`() {
        val c = cache()
        c.put(lirik())
        sekarang += 31 * hari
        c.put(lirik().copy(synced = listOf(SyncedLine(id = 0, timeMs = 0, text = "baru"))))
        assertEquals("baru", c.get("spotify:track:a")?.synced?.single()?.text)
    }

    @Test
    fun `berkas rusak dibuang dan dianggap miss`() {
        val dir = tmp.root.resolve("baru")
        val c = cache(dir)
        val f = File(dir, cacheFileName("spotify:track:a")).apply { writeText("{bukan json") }
        assertNull(c.getStale("spotify:track:a"))
        assertFalse(f.exists())
    }

    // ── Migrasi cacheDir ke filesDir ─────────────────────────────

    /** REGRESI: tanpa migrasi, update app menghapus semua lirik offline user. */
    @Test
    fun `lirik di lokasi lama tetap terbaca setelah pindah lokasi`() {
        val lama = tmp.root.resolve("lama")
        cache(dir = lama).put(lirik())

        val baru = tmp.root.resolve("baru")
        val c = cache(dir = baru, legacy = lama)
        assertNotNull(c.get("spotify:track:a"))
        assertFalse("folder lama harus dihapus", lama.exists())
        assertTrue(File(baru, cacheFileName("spotify:track:a")).isFile)
    }

    @Test
    fun `migrasi memindahkan semua berkas dan menghitungnya`() {
        val lama = tmp.newFolder("m_lama")
        repeat(3) { File(lama, "f$it.json").writeText("{}") }
        val baru = tmp.root.resolve("m_baru")
        assertEquals(3, migrateLegacyLyricsCache(lama, baru))
        assertEquals(3, baru.listFiles()!!.size)
        assertFalse(lama.exists())
    }

    /** Berkas di lokasi baru ditulis versi app ini, jadi lebih baru: ia menang. */
    @Test
    fun `berkas yang sudah ada di lokasi baru tidak ditimpa`() {
        val lama = tmp.newFolder("k_lama")
        val baru = tmp.newFolder("k_baru")
        File(lama, "a.json").writeText("lama")
        File(baru, "a.json").writeText("baru")
        assertEquals(0, migrateLegacyLyricsCache(lama, baru))
        assertEquals("baru", File(baru, "a.json").readText())
    }

    @Test
    fun `migrasi tanpa folder lama tidak melakukan apa pun`() {
        val baru = tmp.root.resolve("x_baru")
        assertEquals(0, migrateLegacyLyricsCache(tmp.root.resolve("tidak_ada"), baru))
    }

    @Test
    fun `migrasi idempoten`() {
        val lama = tmp.newFolder("i_lama")
        File(lama, "a.json").writeText("{}")
        val baru = tmp.root.resolve("i_baru")
        assertEquals(1, migrateLegacyLyricsCache(lama, baru))
        assertEquals(0, migrateLegacyLyricsCache(lama, baru))
        assertEquals(1, baru.listFiles()!!.size)
    }
}
