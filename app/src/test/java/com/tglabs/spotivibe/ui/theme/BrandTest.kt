package com.tglabs.spotivibe.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BrandTest {

    @Test
    fun `eyebrow is the tagline in mono-up form`() {
        assertEquals("EVERY PLAYER · EVERY LINE", Brand.TAGLINE_EYEBROW)
    }

    @Test
    fun `eyebrowOf splits sentences with a middle dot and drops the final period`() {
        assertEquals("ONE · TWO · THREE", Brand.eyebrowOf("One. Two. Three."))
        assertEquals("SINGLE LINE", Brand.eyebrowOf("  Single line.  "))
    }

    @Test
    fun `tagline no longer carries the VS Code era wording`() {
        assertFalse(Brand.TAGLINE.contains("Code", ignoreCase = true))
    }
}
