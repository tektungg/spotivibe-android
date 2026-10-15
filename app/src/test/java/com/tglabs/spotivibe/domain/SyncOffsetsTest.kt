package com.tglabs.spotivibe.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncOffsetsTest {

    // ── Penjumlahan ──────────────────────────────────────────────

    @Test
    fun `ketiga lapis dijumlahkan`() {
        assertEquals(450, combinedOffsetMs(globalMs = 100, deviceMs = 250, songMs = 100))
        assertEquals(-300, combinedOffsetMs(globalMs = 0, deviceMs = -200, songMs = -100))
    }

    @Test
    fun `tiap lapis dijepit dulu sebelum dijumlahkan`() {
        // Nilai rusak 9000 di satu lapis tidak boleh menyumbang lebih dari 2000.
        assertEquals(2000, combinedOffsetMs(globalMs = 0, deviceMs = 9000, songMs = 0))
        assertEquals(-2000, combinedOffsetMs(globalMs = 0, deviceMs = 0, songMs = -9000))
    }

    @Test
    fun `jumlah dijepit di batas total`() {
        assertEquals(TOTAL_OFFSET_LIMIT_MS, combinedOffsetMs(2000, 2000, 2000))
        assertEquals(-TOTAL_OFFSET_LIMIT_MS, combinedOffsetMs(-2000, -2000, -2000))
    }

    @Test
    fun `totalMs SyncOffsets sama dengan combinedOffsetMs`() {
        val o = SyncOffsets(globalMs = 200, deviceMs = 300, songMs = -100, outputLabel = "Mobil")
        assertEquals(400, o.totalMs)
    }

    // ── Pemilihan perangkat ──────────────────────────────────────

    private fun device(kind: OutputKind, name: String = kind.name, type: Int = kind.priority) =
        OutputDevice(kind = kind, typeCode = type, name = name)

    @Test
    fun `bluetooth menang atas speaker internal dan kabel`() {
        val bt = device(OutputKind.Bluetooth, "Toyota")
        val picked = pickMediaOutput(
            listOf(device(OutputKind.BuiltIn), device(OutputKind.Wired), bt),
        )
        assertEquals(bt, picked)
    }

    @Test
    fun `tanpa perangkat eksternal speaker internal yang dipakai`() {
        val speaker = device(OutputKind.BuiltIn)
        assertEquals(speaker, pickMediaOutput(listOf(speaker)))
    }

    @Test
    fun `daftar kosong tidak memilih apa pun`() {
        assertNull(pickMediaOutput(emptyList()))
    }

    @Test
    fun `dua perangkat segolongan mempertahankan urutan sistem`() {
        val pertama = device(OutputKind.Bluetooth, "Earbud")
        val kedua = device(OutputKind.Bluetooth, "Mobil")
        assertEquals(pertama, pickMediaOutput(listOf(pertama, kedua)))
    }

    // ── Kunci perangkat ──────────────────────────────────────────

    @Test
    fun `kunci stabil untuk perangkat yang sama`() {
        val a = device(OutputKind.Bluetooth, "Toyota", type = 8)
        assertEquals(outputKey(a), outputKey(a.copy()))
        assertEquals(16, outputKey(a).length)
    }

    @Test
    fun `kunci beda untuk nama berbeda dan tipe berbeda`() {
        val mobil = device(OutputKind.Bluetooth, "Toyota", type = 8)
        assertNotEquals(outputKey(mobil), outputKey(mobil.copy(name = "Earbud")))
        assertNotEquals(outputKey(mobil), outputKey(mobil.copy(typeCode = 26)))
    }

    @Test
    fun `kunci tidak memuat nama perangkat mentah`() {
        val key = outputKey(device(OutputKind.Bluetooth, "toyota"))
        assert(!key.contains("toyota")) { "nama perangkat bocor ke kunci: $key" }
    }

    @Test
    fun `nama kosong diberi label golongan`() {
        assertEquals("Phone speaker", outputLabel(device(OutputKind.BuiltIn, name = "  ")))
        assertEquals("Toyota", outputLabel(device(OutputKind.Bluetooth, name = " Toyota ")))
    }

    // ── Aliran ───────────────────────────────────────────────────

    private class FakeStore {
        val device = mutableMapOf<String, MutableStateFlow<Int>>()
        val song = mutableMapOf<String, MutableStateFlow<Int>>()
        fun deviceFlow(key: String): Flow<Int> = device.getOrPut(key) { MutableStateFlow(0) }
        fun songFlow(id: String): Flow<Int> = song.getOrPut(id) { MutableStateFlow(0) }
    }

    @Test
    fun `offset perangkat dan lagu ikut berganti saat perangkat dan lagu berganti`() = runTest {
        val store = FakeStore()
        val mobil = AudioOutput(key = "mobil", label = "Toyota")
        store.device["mobil"] = MutableStateFlow(250)
        store.song["spotify:track:a"] = MutableStateFlow(-100)
        val output = MutableStateFlow<AudioOutput?>(null)
        val track = MutableStateFlow<String?>("spotify:track:a")
        val flow = syncOffsetsFlow(
            globalMs = flowOf(50),
            output = output,
            trackId = track,
            deviceOffsetMs = store::deviceFlow,
            songOffsetMs = store::songFlow,
        )

        assertEquals(SyncOffsets(50, 0, -100, null), flow.first())

        output.value = mobil
        assertEquals(SyncOffsets(50, 250, -100, "Toyota"), flow.first())

        track.value = "spotify:track:b"
        assertEquals(SyncOffsets(50, 250, 0, "Toyota"), flow.first())
    }

    @Test
    fun `lagu tanpa id menyumbang nol`() = runTest {
        val store = FakeStore()
        val flow = syncOffsetsFlow(
            globalMs = flowOf(0),
            output = flowOf(null),
            trackId = flowOf(""),
            deviceOffsetMs = store::deviceFlow,
            songOffsetMs = { error("lagu tanpa id tidak boleh dibaca") },
        )
        assertEquals(0, flow.first().songMs)
    }
}
