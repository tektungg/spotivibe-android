package com.tglabs.spotivibe.data

import com.tglabs.spotivibe.domain.LyricsOverride
import com.tglabs.spotivibe.domain.LyricsOverrideStore
import com.tglabs.spotivibe.domain.LyricsResult
import com.tglabs.spotivibe.domain.SyncedLine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsOverrideRepositoryTest {

    private class FakeStore(
        var awal: MutableList<LyricsOverride> = mutableListOf(),
        /** Untuk menguji bahwa disk bermasalah tidak menjatuhkan fitur. */
        var gagalSave: Boolean = false,
        var gagalLoad: Boolean = false,
    ) : LyricsOverrideStore {
        val tersimpan = mutableMapOf<String, LyricsOverride>()
        var jumlahSave = 0
        var jumlahDelete = 0

        init {
            awal.forEach { tersimpan[it.trackId] = it }
        }

        override suspend fun loadAll(): List<LyricsOverride> {
            if (gagalLoad) error("disk bermasalah")
            return tersimpan.values.toList()
        }

        override suspend fun save(override: LyricsOverride) {
            jumlahSave++
            if (gagalSave) error("disk penuh")
            tersimpan[override.trackId] = override
        }

        override suspend fun delete(trackId: String) {
            jumlahDelete++
            tersimpan.remove(trackId)
        }
    }

    private fun lirik(teks: String = "halo") = LyricsResult(
        trackId = "t1",
        synced = listOf(SyncedLine(id = 0, timeMs = 0, text = teks)),
        plain = null,
    )

    private fun repo(store: FakeStore) =
        LyricsOverrideRepository(store, nowMs = { 1_700_000_000_000L })

    // ── Sesi versus permanen ─────────────────────────────────────

    /**
     * REGRESI KONSEPTUAL: memilih hasil TIDAK boleh otomatis menyimpan. User
     * mungkin mencoba beberapa hasil sebelum ketemu yang benar, dan tiap
     * percobaan tidak seharusnya jadi komitmen.
     */
    @Test
    fun `tanpa centang tidak menyentuh disk sama sekali`() = runTest {
        val store = FakeStore()
        val r = repo(store)

        r.apply("t1", lirik(), lrclibId = 42, remember = false)

        assertNotNull("harus tetap berlaku di sesi ini", r.overrideFor("t1"))
        assertEquals("disk tidak boleh disentuh", 0, store.jumlahSave)
        assertFalse(r.isRemembered("t1"))
    }

    @Test
    fun `dengan centang menulis ke disk`() = runTest {
        val store = FakeStore()
        val r = repo(store)

        r.apply("t1", lirik(), lrclibId = 42, remember = true)

        assertEquals(1, store.jumlahSave)
        assertTrue(r.isRemembered("t1"))
        assertEquals(42L, store.tersimpan["t1"]?.lrclibId)
    }

    @Test
    fun `muat ulang mengembalikan yang permanen saja`() = runTest {
        val store = FakeStore()
        val pertama = repo(store)
        pertama.apply("permanen", lirik("disimpan"), 1, remember = true)
        pertama.apply("sesi", lirik("sementara"), 2, remember = false)

        // Proses baru: repository baru, store yang sama.
        val kedua = repo(store)
        kedua.restore()

        assertNotNull("yang permanen harus kembali", kedua.overrideFor("permanen"))
        assertNull("yang sesi harus hilang", kedua.overrideFor("sesi"))
        assertTrue(kedua.isRemembered("permanen"))
    }

    @Test
    fun `memilih ulang menimpa pilihan sebelumnya`() = runTest {
        val store = FakeStore()
        val r = repo(store)
        r.apply("t1", lirik("pertama"), 1, remember = false)
        r.apply("t1", lirik("kedua"), 2, remember = false)

        assertEquals("kedua", r.overrideFor("t1")?.synced?.get(0)?.text)
    }

    @Test
    fun `pilihan sesi bisa dinaikkan jadi permanen`() = runTest {
        val store = FakeStore()
        val r = repo(store)
        r.apply("t1", lirik(), 1, remember = false)
        assertFalse(r.isRemembered("t1"))

        r.apply("t1", lirik(), 1, remember = true)
        assertTrue(r.isRemembered("t1"))
    }

    // ── Lupakan ──────────────────────────────────────────────────

    @Test
    fun `lupakan menghapus dari memori DAN disk`() = runTest {
        val store = FakeStore()
        val r = repo(store)
        r.apply("t1", lirik(), 1, remember = true)

        r.forget("t1")

        assertNull(r.overrideFor("t1"))
        assertFalse(r.isRemembered("t1"))
        assertEquals(1, store.jumlahDelete)
        assertTrue(store.tersimpan.isEmpty())
    }

    @Test
    fun `lupakan pilihan sesi juga bekerja`() = runTest {
        val store = FakeStore()
        val r = repo(store)
        r.apply("t1", lirik(), 1, remember = false)
        r.forget("t1")
        assertNull(r.overrideFor("t1"))
    }

    @Test
    fun `lupakan track yang tidak punya override tidak meledak`() = runTest {
        val r = repo(FakeStore())
        r.forget("tidak-ada")
        assertNull(r.overrideFor("tidak-ada"))
    }

    // ── Disk bermasalah tidak menjatuhkan fitur ──────────────────

    /**
     * Prinsipnya sama dengan LyricsStatsRecorder: kegagalan menyimpan tidak
     * boleh menolak perintah user. Pilihannya tetap berlaku, cuma tidak
     * bertahan setelah app ditutup.
     */
    @Test
    fun `gagal menulis tetap menerapkan pilihan di memori`() = runTest {
        val store = FakeStore(gagalSave = true)
        val r = repo(store)

        r.apply("t1", lirik(), 1, remember = true)

        assertNotNull("pilihan harus tetap berlaku", r.overrideFor("t1"))
        assertFalse("tapi tidak boleh mengaku tersimpan", r.isRemembered("t1"))
    }

    @Test
    fun `gagal memuat tidak meledak saat startup`() = runTest {
        val r = repo(FakeStore(gagalLoad = true))
        r.restore()
        assertNull(r.overrideFor("apa pun"))
    }

    // ── Isolasi antar track ──────────────────────────────────────

    @Test
    fun `override satu track tidak bocor ke track lain`() = runTest {
        val r = repo(FakeStore())
        r.apply("t1", lirik("lagu satu"), 1, remember = false)

        assertNotNull(r.overrideFor("t1"))
        assertNull(r.overrideFor("t2"))
    }

    // ── Revisi untuk UI ──────────────────────────────────────────

    @Test
    fun `revisi naik tiap kali isi berubah`() = runTest {
        val r = repo(FakeStore())
        val awal = r.revision.value

        r.apply("t1", lirik(), 1, remember = false)
        val setelahApply = r.revision.value
        assertTrue(setelahApply > awal)

        r.forget("t1")
        assertTrue(r.revision.value > setelahApply)
    }
}
