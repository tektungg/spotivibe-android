package com.tglabs.spotivibe.data

import com.tglabs.spotivibe.domain.LyricsOverride
import com.tglabs.spotivibe.domain.LyricsResult
import com.tglabs.spotivibe.domain.SyncedLine
import com.tglabs.spotivibe.domain.SyncedWord
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Test berkas sungguhan di direktori sementara. Tidak butuh perangkat maupun
 * Robolectric karena direktorinya di-inject.
 *
 * Isi simpanan ini adalah keputusan user, yaitu lirik yang ia pilih sendiri
 * setelah menemukan lirik otomatisnya salah. Tidak ada sumber lain untuk
 * memulihkannya kalau hilang.
 */
class FileLyricsOverrideStoreTest {

    private lateinit var dir: File
    private lateinit var store: FileLyricsOverrideStore

    @Before
    fun siapkan() {
        dir = File.createTempFile("override-test", "").let {
            it.delete()
            File(it.absolutePath + "-dir")
        }
        store = FileLyricsOverrideStore(dir)
    }

    @After
    fun bersihkan() {
        dir.deleteRecursively()
    }

    private fun override(
        trackId: String = "spotify:track:abc",
        lrclibId: Long = 42L,
        savedAtMs: Long = 1_700_000_000_000L,
        synced: List<SyncedLine>? = listOf(
            SyncedLine(id = 0, timeMs = 1000, text = "baris satu", words = emptyList()),
            SyncedLine(id = 1, timeMs = 2000, text = "baris dua", words = emptyList()),
        ),
        plain: String? = null,
    ) = LyricsOverride(
        trackId = trackId,
        lrclibId = lrclibId,
        savedAtMs = savedAtMs,
        result = LyricsResult(trackId = trackId, synced = synced, plain = plain),
    )

    // ── Simpan dan muat ──────────────────────────────────────────

    @Test
    fun `direktori dibuat otomatis`() {
        assertTrue("direktori harus ada setelah konstruksi", dir.isDirectory)
    }

    @Test
    fun `yang disimpan bisa dimuat lagi utuh`() = runBlocking {
        store.save(override())
        val dimuat = store.loadAll()
        assertEquals(1, dimuat.size)
        val o = dimuat.first()
        assertEquals("spotify:track:abc", o.trackId)
        assertEquals(42L, o.lrclibId)
        assertEquals(1_700_000_000_000L, o.savedAtMs)
        assertEquals(2, o.result.synced?.size)
        assertEquals("baris satu", o.result.synced?.get(0)?.text)
        assertEquals(2000L, o.result.synced?.get(1)?.timeMs)
    }

    @Test
    fun `simpanan kosong menghasilkan daftar kosong`() = runBlocking {
        assertTrue(store.loadAll().isEmpty())
    }

    @Test
    fun `beberapa override hidup berdampingan`() = runBlocking {
        store.save(override(trackId = "spotify:track:a"))
        store.save(override(trackId = "spotify:track:b"))
        store.save(override(trackId = "spotify:track:c"))
        assertEquals(3, store.loadAll().size)
    }

    @Test
    fun `menyimpan track yang sama menimpa, bukan menggandakan`() = runBlocking {
        store.save(override(lrclibId = 1L))
        store.save(override(lrclibId = 2L))
        val dimuat = store.loadAll()
        assertEquals("harus satu, bukan dua", 1, dimuat.size)
        assertEquals(2L, dimuat.first().lrclibId)
    }

    // ── Hapus ────────────────────────────────────────────────────

    @Test
    fun `hapus membuang override itu saja`() = runBlocking {
        store.save(override(trackId = "spotify:track:a"))
        store.save(override(trackId = "spotify:track:b"))
        store.delete("spotify:track:a")
        val sisa = store.loadAll()
        assertEquals(1, sisa.size)
        assertEquals("spotify:track:b", sisa.first().trackId)
    }

    @Test
    fun `hapus yang tidak ada tidak melempar exception`() = runBlocking {
        store.delete("spotify:track:tidak-pernah-ada")
        assertTrue(store.loadAll().isEmpty())
    }

    // ── Ketahanan ────────────────────────────────────────────────

    /**
     * REGRESI: satu berkas rusak tidak boleh menghilangkan yang lain. Kalau
     * seluruh pemuatan gagal, user kehilangan SEMUA pilihan liriknya gara-gara
     * satu berkas yang tulisannya terpotong, misalnya karena baterai habis di
     * tengah penyimpanan.
     */
    @Test
    fun `berkas rusak dibuang tanpa menjatuhkan yang lain`() = runBlocking {
        store.save(override(trackId = "spotify:track:sehat"))
        File(dir, "rusak.json").writeText("{ ini bukan json yang sah")

        val dimuat = store.loadAll()
        assertEquals(1, dimuat.size)
        assertEquals("spotify:track:sehat", dimuat.first().trackId)
        assertFalse("berkas rusak harus ikut dihapus", File(dir, "rusak.json").exists())
    }

    @Test
    fun `berkas kosong diperlakukan sebagai rusak`() = runBlocking {
        store.save(override(trackId = "spotify:track:sehat"))
        File(dir, "kosong.json").writeText("")
        assertEquals(1, store.loadAll().size)
    }

    /**
     * trackId ikut disimpan di dalam ISI, bukan hanya jadi nama berkas, supaya
     * pemulihan tidak bergantung pada aturan sanitasi nama yang bisa berubah.
     */
    @Test
    fun `trackId dipulihkan dari isi bukan dari nama berkas`() = runBlocking {
        val id = "spotify:track:titik:dua:banyak"
        store.save(override(trackId = id))
        assertEquals(id, store.loadAll().first().trackId)
    }

    @Test
    fun `nama berkas tidak mengandung titik dua`() = runBlocking {
        store.save(override(trackId = "spotify:track:abc"))
        val berkas = dir.listFiles()!!.first()
        assertFalse("nama berkas: ${berkas.name}", berkas.name.contains(":"))
    }

    // ── Bentuk data ──────────────────────────────────────────────

    @Test
    fun `timing per kata ikut tersimpan`() = runBlocking {
        store.save(
            override(
                synced = listOf(
                    SyncedLine(
                        id = 0,
                        timeMs = 1000,
                        text = "hello world",
                        words = listOf(SyncedWord(1000, "hello"), SyncedWord(1500, "world")),
                    ),
                ),
            ),
        )
        val kata = store.loadAll().first().result.synced?.first()?.words
        assertEquals(2, kata?.size)
        assertEquals("world", kata?.get(1)?.text)
        assertEquals(1500L, kata?.get(1)?.timeMs)
    }

    /**
     * `id` baris tidak disimpan karena nilainya murni turunan posisi. Ia harus
     * dihitung ulang saat dimuat, dan harus unik: dua baris dengan id sama
     * membuat Compose melempar exception saat menggambar daftar.
     */
    @Test
    fun `id baris dihitung ulang dan unik saat dimuat`() = runBlocking {
        store.save(
            override(
                synced = listOf(
                    SyncedLine(id = 99, timeMs = 1000, text = "a", words = emptyList()),
                    SyncedLine(id = 99, timeMs = 1000, text = "b", words = emptyList()),
                    SyncedLine(id = 99, timeMs = 2000, text = "c", words = emptyList()),
                ),
            ),
        )
        val id = store.loadAll().first().result.synced!!.map { it.id }
        assertEquals("id harus unik walau timestamp kembar", id.size, id.toSet().size)
    }

    @Test
    fun `lirik plain tanpa timing ikut tersimpan`() = runBlocking {
        store.save(override(synced = null, plain = "lirik tanpa timing"))
        val o = store.loadAll().first()
        assertEquals("lirik tanpa timing", o.result.plain)
        assertTrue(o.result.synced.isNullOrEmpty())
    }

    @Test
    fun `teks non-ASCII bertahan bolak-balik`() = runBlocking {
        val teks = "잠시라도 내 손 놓지 마, 日本語も"
        store.save(
            override(
                synced = listOf(SyncedLine(id = 0, timeMs = 0, text = teks, words = emptyList())),
            ),
        )
        assertEquals(teks, store.loadAll().first().result.synced?.first()?.text)
    }

    @Test
    fun `store kedua di direktori yang sama melihat isi yang sama`() = runBlocking {
        store.save(override())
        assertNotNull(FileLyricsOverrideStore(dir).loadAll().firstOrNull())
    }
}
