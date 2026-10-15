package com.tglabs.spotivibe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoverGestureTest {

    @Test
    fun `geser ke kiri lewat ambang berarti lagu berikutnya`() {
        assertEquals(CoverSwipe.Next, swipeAction(totalDx = -60f, thresholdPx = 50f))
        assertEquals(CoverSwipe.Next, swipeAction(totalDx = -50f, thresholdPx = 50f))
    }

    @Test
    fun `geser ke kanan lewat ambang berarti lagu sebelumnya`() {
        assertEquals(CoverSwipe.Previous, swipeAction(totalDx = 80f, thresholdPx = 50f))
    }

    /** Sentuhan tidak sengaja dan geseran kecil saat menggulir tidak memindah lagu. */
    @Test
    fun `geseran di bawah ambang diabaikan`() {
        assertNull(swipeAction(totalDx = 49f, thresholdPx = 50f))
        assertNull(swipeAction(totalDx = -10f, thresholdPx = 50f))
        assertNull(swipeAction(totalDx = 0f, thresholdPx = 50f))
    }

    @Test
    fun `geseran yang tidak diizinkan player diabaikan`() {
        val hanyaNext = PlayerControls(canSkipNext = true)
        assertEquals(CoverSwipe.Next, allowedSwipe(CoverSwipe.Next, hanyaNext))
        assertNull(allowedSwipe(CoverSwipe.Previous, hanyaNext))
        assertNull(allowedSwipe(CoverSwipe.Next, PlayerControls()))
        assertNull(allowedSwipe(null, PlayerControls.Optimistic))
    }
}
