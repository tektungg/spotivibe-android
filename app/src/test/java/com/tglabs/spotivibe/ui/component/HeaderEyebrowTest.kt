package com.tglabs.spotivibe.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeaderEyebrowTest {

    @Test
    fun `online menampilkan album apa adanya`() {
        assertEquals("Map of the Soul", headerEyebrowText("Map of the Soul", offline = false))
    }

    @Test
    fun `album kosong jatuh ke NOW PLAYING`() {
        assertEquals("NOW PLAYING", headerEyebrowText("", offline = false))
    }

    /**
     * Eyebrow dipotong ellipsis di satu baris. Penanda di BELAKANG album
     * panjang akan terpotong dan user tidak pernah tahu dirinya offline.
     */
    @Test
    fun `penanda offline selalu di depan supaya tidak terpotong`() {
        val panjang = "A".repeat(200)
        assertTrue(headerEyebrowText(panjang, offline = true).startsWith("OFFLINE · "))
    }

    @Test
    fun `offline dengan album kosong tetap terbaca`() {
        assertEquals("OFFLINE · NOW PLAYING", headerEyebrowText("", offline = true))
    }
}
