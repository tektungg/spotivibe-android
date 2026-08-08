package com.tglabs.spotivibe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsSearchTest {

    private fun entry(
        id: Long = 1,
        judul: String = "Bohemian Rhapsody",
        artis: String = "Queen",
        album: String = "A Night at the Opera",
        durasi: Int = 355,
        instrumental: Boolean = false,
        synced: String? = "[00:01.00]Is this the real life",
        plain: String? = "Is this the real life",
    ) = LrclibSearchEntry(id, judul, artis, album, durasi, instrumental, synced, plain)

    // ── Penyaringan ──────────────────────────────────────────────

    /**
     * Entry instrumental memang tidak berlirik. Menampilkannya cuma memancing
     * user memilih sesuatu yang menghasilkan layar kosong.
     */
    @Test
    fun `entry instrumental disaring`() {
        val hasil = toSearchResults(listOf(entry(instrumental = true)), "t")
        assertTrue(hasil.isEmpty())
    }

    /** LRCLIB kadang mengembalikan entry metadata tanpa isi sama sekali. */
    @Test
    fun `entry tanpa lirik sama sekali disaring`() {
        val hasil = toSearchResults(listOf(entry(synced = null, plain = null)), "t")
        assertTrue(hasil.isEmpty())
    }

    @Test
    fun `entry dengan lirik kosong juga disaring`() {
        val hasil = toSearchResults(listOf(entry(synced = "   ", plain = "")), "t")
        assertTrue(hasil.isEmpty())
    }

    @Test
    fun `entry yang hanya punya teks polos tetap ditampilkan`() {
        val hasil = toSearchResults(listOf(entry(synced = null)), "t")
        assertEquals(1, hasil.size)
        assertEquals(false, hasil[0].hasSynced)
    }

    @Test
    fun `synced yang tidak menghasilkan baris apa pun dianggap tidak synced`() {
        // LRC tanpa satu pun timestamp yang sah.
        val hasil = toSearchResults(listOf(entry(synced = "bukan lrc sama sekali")), "t")
        assertEquals(1, hasil.size)
        assertEquals(false, hasil[0].hasSynced)
    }

    @Test
    fun `yang lolos dan yang disaring bisa bercampur`() {
        val hasil = toSearchResults(
            listOf(
                entry(id = 1),
                entry(id = 2, instrumental = true),
                entry(id = 3, synced = null, plain = null),
                entry(id = 4),
            ),
            "t",
        )
        assertEquals(listOf(1L, 4L), hasil.map { it.lrclibId })
    }

    // ── Pemetaan ─────────────────────────────────────────────────

    @Test
    fun `field dipetakan apa adanya`() {
        val h = toSearchResults(listOf(entry()), "spotify:track:abc")[0]
        assertEquals(1L, h.lrclibId)
        assertEquals("Bohemian Rhapsody", h.trackName)
        assertEquals("Queen", h.artistName)
        assertEquals("A Night at the Opera", h.albumName)
        assertEquals(355, h.durationSec)
        assertTrue(h.hasSynced)
    }

    /**
     * Hasilnya dipasang ke track Spotify yang sedang diputar, BUKAN ke track
     * milik entry LRCLIB. Kalau tertukar, override tidak akan pernah ketemu
     * saat lagu itu diputar lagi.
     */
    @Test
    fun `trackId hasil memakai track Spotify yang sedang diputar`() {
        val h = toSearchResults(listOf(entry()), "spotify:track:abc")[0]
        assertEquals("spotify:track:abc", h.result.trackId)
    }

    @Test
    fun `lirik sudah ter-parse jadi memilihnya tidak perlu request lagi`() {
        val h = toSearchResults(
            listOf(entry(synced = "[00:01.00]baris satu\n[00:05.00]baris dua")),
            "t",
        )[0]
        assertEquals(2, h.result.synced?.size)
        assertEquals("baris satu", h.result.synced?.get(0)?.text)
    }

    @Test
    fun `judul dan artis kosong diberi penanda, bukan string kosong`() {
        val h = toSearchResults(listOf(entry(judul = "", artis = "")), "t")[0]
        assertTrue(h.trackName.isNotBlank())
        assertTrue(h.artistName.isNotBlank())
    }

    @Test
    fun `daftar kosong menghasilkan daftar kosong`() {
        assertTrue(toSearchResults(emptyList(), "t").isEmpty())
    }

    // ── formatDuration ───────────────────────────────────────────

    @Test
    fun `durasi diformat menit titik dua detik`() {
        assertEquals("5:55", formatDuration(355))
        assertEquals("1:00", formatDuration(60))
        assertEquals("0:07", formatDuration(7))
        assertEquals("10:05", formatDuration(605))
    }

    @Test
    fun `detik selalu dua digit`() {
        assertEquals("3:05", formatDuration(185))
        assertTrue(formatDuration(61).endsWith(":01"))
    }

    /**
     * Durasi tidak diketahui ditulis sebagai tanda hubung, bukan 0:00. Sama
     * prinsipnya dengan rate statistik: "belum tahu" tidak boleh tersamar jadi
     * nilai yang terlihat sah.
     */
    @Test
    fun `durasi nol atau negatif jadi tanda hubung`() {
        assertEquals("—", formatDuration(0))
        assertEquals("—", formatDuration(-5))
    }

    @Test
    fun `lagu sangat panjang tetap terbaca`() {
        assertEquals("62:03", formatDuration(3723))
    }
}
