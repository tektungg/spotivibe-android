package com.tglabs.spotivibe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMatchTest {

    @Test
    fun `judul dinormalisasi tanpa kurung akhiran dan aksen`() {
        assertEquals("blinding lights", normalizeTitle("Blinding Lights (Mixed)"))
        assertEquals("blinding lights", normalizeTitle("Blinding Lights - Remastered 2020"))
        assertEquals("cafe", normalizeTitle("Café"))
        assertEquals("夜に駆ける", normalizeTitle("夜に駆ける"))
    }

    @Test
    fun `judul sama cocok`() {
        assertTrue(titlesMatch("Blinding Lights", "BLINDING LIGHTS"))
        assertTrue(titlesMatch("Blinding Lights", "Blinding Lights (Mixed)"))
    }

    @Test
    fun `remix live dan acoustic ditolak kalau lagunya bukan versi itu`() {
        assertFalse(titlesMatch("Blinding Lights", "Blinding Lights [Major Lazer Remix]"))
        assertFalse(titlesMatch("Blinding Lights", "Blinding Lights - Live"))
        assertTrue(titlesMatch("Blinding Lights - Live", "Blinding Lights (Live)"))
    }

    @Test
    fun `judul berbeda tidak cocok`() {
        assertFalse(titlesMatch("Blinding Lights", "Save Your Tears"))
    }

    @Test
    fun `artis utama cukup untuk kolaborasi`() {
        assertTrue(artistsMatch("The Weeknd", "The Weeknd"))
        assertTrue(artistsMatch("Daft Punk, Pharrell Williams", "Daft Punk feat. Pharrell Williams"))
        assertTrue(artistsMatch("Beyoncé", "Beyonce & JAY-Z"))
    }

    @Test
    fun `artis lain tidak cocok`() {
        assertFalse(artistsMatch("The Weeknd", "All Hail The Queen"))
        assertFalse(artistsMatch("", "The Weeknd"))
    }
}
