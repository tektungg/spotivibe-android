package com.tglabs.spotivibe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationTextTest {

    private val baris = listOf("baris nol", "baris satu", "baris dua", "baris tiga")

    private fun teks(
        title: String? = "Judul",
        artist: String? = "Artis",
        window: LyricWindow = LyricWindow("sebelum", "aktif", "sesudah"),
        reconnecting: Boolean = false,
    ) = notificationText(title, artist, window, reconnecting)

    // ── Jendela tiga baris ───────────────────────────────────────

    @Test
    fun `jendela mengambil baris di sekitar index`() {
        val w = lyricWindow(baris, 2)
        assertEquals("baris satu", w.prev)
        assertEquals("baris dua", w.current)
        assertEquals("baris tiga", w.next)
    }

    /** Baris pertama tidak punya pendahulu. */
    @Test
    fun `tepi awal tidak punya baris sebelumnya`() {
        val w = lyricWindow(baris, 0)
        assertNull(w.prev)
        assertEquals("baris nol", w.current)
        assertEquals("baris satu", w.next)
    }

    /** Baris terakhir tidak punya penerus. */
    @Test
    fun `tepi akhir tidak punya baris sesudahnya`() {
        val w = lyricWindow(baris, baris.lastIndex)
        assertEquals("baris dua", w.prev)
        assertEquals("baris tiga", w.current)
        assertNull(w.next)
    }

    /**
     * Index -1 berarti lagu masih di intro. Belum ada baris aktif, tapi baris
     * pertama sudah pantas ditampilkan sebagai yang akan datang.
     */
    @Test
    fun `intro belum punya baris aktif tapi sudah punya baris berikutnya`() {
        val w = lyricWindow(baris, -1)
        assertNull(w.prev)
        assertNull(w.current)
        assertEquals("baris nol", w.next)
    }

    @Test
    fun `index jauh di luar rentang tidak melempar exception`() {
        listOf(99, -99, Int.MAX_VALUE, Int.MIN_VALUE).forEach { i ->
            val w = lyricWindow(baris, i)
            assertNull("index=$i", w.current)
        }
    }

    @Test
    fun `daftar null atau kosong menghasilkan jendela kosong`() {
        listOf(lyricWindow(null, 0), lyricWindow(emptyList(), 0)).forEach { w ->
            assertNull(w.prev)
            assertNull(w.current)
            assertNull(w.next)
        }
    }

    @Test
    fun `daftar satu baris hanya punya baris aktif`() {
        val w = lyricWindow(listOf("sendirian"), 0)
        assertNull(w.prev)
        assertEquals("sendirian", w.current)
        assertNull(w.next)
    }

    // ── Judul dan artis ──────────────────────────────────────────

    @Test
    fun `judul dan artis dipakai apa adanya`() {
        val t = teks()
        assertEquals("Judul", t.title)
        assertEquals("Artis", t.subtitle)
    }

    @Test
    fun `judul kosong atau null diganti nama app`() {
        assertEquals(NOTIF_FALLBACK_TITLE, teks(title = null).title)
        assertEquals(NOTIF_FALLBACK_TITLE, teks(title = "").title)
        assertEquals(NOTIF_FALLBACK_TITLE, teks(title = "   ").title)
    }

    /**
     * Artis null berarti belum ada lagu sama sekali; artis kosong berarti
     * lagunya ada tapi metadatanya tidak lengkap. Hanya yang pertama yang layak
     * disebut "Loading", karena yang kedua tidak sedang memuat apa pun.
     */
    @Test
    fun `artis null berarti memuat, artis kosong tidak`() {
        assertEquals(NOTIF_FALLBACK_SUBTITLE, teks(artist = null).subtitle)
        assertEquals("", teks(artist = "").subtitle)
    }

    // ── Baris aktif ──────────────────────────────────────────────

    @Test
    fun `baris aktif diberi penanda`() {
        assertEquals("${NOTIF_ACTIVE_MARK}aktif", teks().current)
    }

    /**
     * Bagian instrumental tidak punya teks. Membiarkannya kosong membuat tinggi
     * notifikasi berubah-ubah dan barisnya melompat tiap kali masuk jeda.
     */
    @Test
    fun `baris kosong diganti not balok supaya tingginya tetap`() {
        listOf(null, "", "   ").forEach { isi ->
            val t = teks(window = LyricWindow("a", isi, "b"))
            assertEquals("isi=$isi", NOTIF_ACTIVE_MARK + NOTIF_BLANK_LINE, t.current)
        }
    }

    /**
     * REGRESI: saat menyambung ulang, lirik berhenti bergerak. Tanpa keterangan
     * ini user menatap lirik basi dan itu terlihat seperti app yang hang.
     */
    @Test
    fun `sedang menyambung ulang menimpa baris aktif`() {
        val t = teks(reconnecting = true)
        assertEquals(NOTIF_RECONNECTING, t.current)
        assertFalse("lirik basi tidak boleh ikut tampil", t.current.contains("aktif"))
    }

    @Test
    fun `menyambung ulang tidak menghapus judul dan artis`() {
        val t = teks(reconnecting = true)
        assertEquals("Judul", t.title)
        assertEquals("Artis", t.subtitle)
    }

    // ── Baris pinggir ────────────────────────────────────────────

    @Test
    fun `baris pinggir tampil apa adanya`() {
        val t = teks()
        assertEquals("sebelum", t.prev)
        assertEquals("sesudah", t.next)
    }

    /** Baris pinggir yang kosong dibiarkan kosong, tidak diberi not balok. */
    @Test
    fun `baris pinggir kosong tidak diberi penanda`() {
        val t = teks(window = LyricWindow(null, "aktif", null))
        assertEquals("", t.prev)
        assertEquals("", t.next)
    }

    @Test
    fun `baris pinggir tidak pernah diberi penanda baris aktif`() {
        val t = teks()
        assertFalse(t.prev.startsWith(NOTIF_ACTIVE_MARK))
        assertFalse(t.next.startsWith(NOTIF_ACTIVE_MARK))
        assertTrue(t.current.startsWith(NOTIF_ACTIVE_MARK))
    }

    // ── Ketahanan ────────────────────────────────────────────────

    @Test
    fun `tanpa lagu sama sekali tetap menghasilkan teks yang layak`() {
        val t = notificationText(null, null, lyricWindow(null, -1), false)
        assertEquals(NOTIF_FALLBACK_TITLE, t.title)
        assertEquals(NOTIF_FALLBACK_SUBTITLE, t.subtitle)
        assertEquals(NOTIF_ACTIVE_MARK + NOTIF_BLANK_LINE, t.current)
        assertEquals("", t.prev)
        assertEquals("", t.next)
    }

    @Test
    fun `deterministik`() {
        repeat(5) { assertEquals(teks(), teks()) }
    }
}
