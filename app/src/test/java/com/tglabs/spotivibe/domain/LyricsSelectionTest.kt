package com.tglabs.spotivibe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsSelectionTest {

    private fun synced(teks: String) = LyricsResult(
        trackId = "t",
        synced = listOf(SyncedLine(id = 0, timeMs = 0, text = teks)),
        plain = null,
    )

    private fun plain(teks: String) = LyricsResult(trackId = "t", synced = null, plain = teks)

    private fun c(r: LyricsResult, durasi: Double?, sumber: ProbeSource = ProbeSource.Search) =
        LyricsCandidate(r, durasi, sumber)

    private fun pilih(target: Double?, vararg k: LyricsCandidate): String? =
        pickBestCandidate(k.toList(), target)?.result?.let { it.synced?.first()?.text ?: it.plain }

    // ── Synced dulu ──────────────────────────────────────────────

    @Test
    fun `synced menang atas plain dengan durasi sama`() {
        assertEquals("S", pilih(200.0, c(plain("P"), 200.0), c(synced("S"), 200.0)))
    }

    /**
     * REGRESI: permintaan aslinya. Dulu plain dari /get selalu menang, walau
     * /search punya versi synced untuk lagu yang sama.
     */
    @Test
    fun `synced dalam toleransi menang atas plain yang durasinya persis`() {
        assertEquals(
            "S",
            pilih(200.0, c(plain("P"), 200.0, ProbeSource.Get), c(synced("S"), 203.0)),
        )
    }

    // ── Durasi paling mirip ──────────────────────────────────────

    /** REGRESI: dulu yang diambil synced PERTAMA, tanpa melihat durasi. */
    @Test
    fun `di antara synced yang durasinya paling mirip menang`() {
        assertEquals(
            "dekat",
            pilih(200.0, c(synced("jauh"), 204.5), c(synced("dekat"), 200.4), c(synced("sedang"), 202.0)),
        )
    }

    @Test
    fun `selisih dihitung mutlak ke dua arah`() {
        assertEquals("pendek", pilih(200.0, c(synced("panjang"), 203.0), c(synced("pendek"), 198.5)))
    }

    // ── Toleransi ────────────────────────────────────────────────

    /**
     * Synced dari versi live/extended punya timestamp yang meleset di
     * sepanjang lagu. Plain yang durasinya pas lebih jujur.
     */
    @Test
    fun `plain dalam toleransi menang atas synced yang durasinya jauh`() {
        assertEquals("P", pilih(200.0, c(synced("live"), 245.0), c(plain("P"), 201.0)))
    }

    @Test
    fun `tepat di batas toleransi masih dianggap cocok`() {
        assertEquals(
            "S",
            pilih(200.0, c(plain("P"), 200.0), c(synced("S"), 200.0 + SYNC_DURATION_TOLERANCE_SEC)),
        )
    }

    @Test
    fun `sedikit di luar toleransi kalah dari plain yang cocok`() {
        assertEquals(
            "P",
            pilih(200.0, c(plain("P"), 200.0), c(synced("S"), 200.0 + SYNC_DURATION_TOLERANCE_SEC + 0.1)),
        )
    }

    /** Tidak ada yang cocok: synced tetap lebih berguna, yang terdekat dulu. */
    @Test
    fun `semua di luar toleransi tetap memilih synced terdekat`() {
        assertEquals(
            "S2",
            pilih(200.0, c(plain("P"), 230.0), c(synced("S1"), 260.0), c(synced("S2"), 240.0)),
        )
    }

    // ── Data tidak lengkap ───────────────────────────────────────

    @Test
    fun `kandidat tanpa durasi dianggap di luar toleransi`() {
        assertEquals("P", pilih(200.0, c(synced("tanpa durasi"), null), c(plain("P"), 200.0)))
    }

    /** Durasi lagu tidak diketahui: yang tersisa hanya synced lalu sumber. */
    @Test
    fun `durasi lagu tidak diketahui memilih synced lalu get`() {
        assertEquals("S", pilih(null, c(plain("P"), 200.0, ProbeSource.Get), c(synced("S"), 999.0)))
        assertEquals(
            "get",
            pilih(0.0, c(synced("search"), 200.0), c(synced("get"), 100.0, ProbeSource.Get)),
        )
    }

    @Test
    fun `seri dipecahkan oleh get karena juga dicocokkan dengan album`() {
        assertEquals(
            "get",
            pilih(200.0, c(synced("search"), 201.0), c(synced("get"), 201.0, ProbeSource.Get)),
        )
    }

    @Test
    fun `seri sempurna mempertahankan urutan asli`() {
        assertEquals("pertama", pilih(200.0, c(synced("pertama"), 201.0), c(synced("kedua"), 201.0)))
    }

    @Test
    fun `kandidat kosong tidak pernah dipilih`() {
        val kosong = LyricsResult(trackId = "t", synced = emptyList(), plain = " ")
        assertEquals("P", pilih(200.0, c(kosong, 200.0, ProbeSource.Get), c(plain("P"), 260.0)))
        assertNull(pickBestCandidate(listOf(c(kosong, 200.0)), 200.0))
    }

    @Test
    fun `daftar kosong tidak menghasilkan apa pun`() {
        assertNull(pickBestCandidate(emptyList(), 200.0))
    }

    // ── Jalur cepat ──────────────────────────────────────────────

    @Test
    fun `get synced dengan durasi nyaris persis tidak terkalahkan`() {
        assertTrue(isUnbeatable(c(synced("S"), 200.8, ProbeSource.Get), 200.0))
    }

    /** REGRESI: dulu SEMUA konten /get lewat jalur cepat, termasuk plain. */
    @Test
    fun `get plain tidak pernah lewat jalur cepat`() {
        assertFalse(isUnbeatable(c(plain("P"), 200.0, ProbeSource.Get), 200.0))
    }

    @Test
    fun `get synced dengan durasi agak beda menunggu search`() {
        assertFalse(isUnbeatable(c(synced("S"), 203.0, ProbeSource.Get), 200.0))
    }

    @Test
    fun `tanpa durasi tidak bisa dipastikan tak terkalahkan`() {
        assertFalse(isUnbeatable(c(synced("S"), null, ProbeSource.Get), 200.0))
        assertFalse(isUnbeatable(c(synced("S"), 200.0, ProbeSource.Get), null))
    }

    // ── Versi seleksi cache ──────────────────────────────────────

    @Test
    fun `entri tanpa versi seleksi dianggap usang`() {
        assertTrue(isSelectionOutdated(0))
        assertFalse(isSelectionOutdated(LYRICS_SELECTION_VERSION))
    }
}
