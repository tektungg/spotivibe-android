package com.tglabs.spotivibe.data

import android.media.session.PlaybackState
import com.tglabs.spotivibe.domain.PlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Konstanta `PlaybackState` adalah `static final`, jadi nilainya tertanam
 * saat kompilasi dan test ini jalan di JVM tanpa stub Android.
 */
class MediaSessionMappingTest {

    @Test
    fun `state Android dipetakan ke PlayerState`() {
        assertEquals(PlayerState.Playing, playerStateOf(PlaybackState.STATE_PLAYING))
        assertEquals(PlayerState.Playing, playerStateOf(PlaybackState.STATE_FAST_FORWARDING))
        assertEquals(PlayerState.Paused, playerStateOf(PlaybackState.STATE_PAUSED))
        assertEquals(PlayerState.Stopped, playerStateOf(PlaybackState.STATE_STOPPED))
        assertEquals(PlayerState.Error, playerStateOf(PlaybackState.STATE_ERROR))
        assertEquals(PlayerState.None, playerStateOf(PlaybackState.STATE_NONE))
    }

    @Test
    fun `buffering dan pindah lagu adalah keadaan sementara`() {
        listOf(
            PlaybackState.STATE_BUFFERING,
            PlaybackState.STATE_CONNECTING,
            PlaybackState.STATE_SKIPPING_TO_NEXT,
            PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
            PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM,
        ).forEach { assertEquals(PlayerState.Transient, playerStateOf(it)) }
    }

    @Test
    fun `seek hanya kalau player mengiklankan SEEK_TO`() {
        assertTrue(controlsOf(PlaybackState.ACTION_SEEK_TO).canSeek)
        assertFalse(controlsOf(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT).canSeek)
    }

    /** Player berbeda mengiklankan tombol yang sama lewat bit berbeda. */
    @Test
    fun `play pause cukup salah satu bit`() {
        assertTrue(controlsOf(PlaybackState.ACTION_PLAY).canPlayPause)
        assertTrue(controlsOf(PlaybackState.ACTION_PAUSE).canPlayPause)
        assertTrue(controlsOf(PlaybackState.ACTION_PLAY_PAUSE).canPlayPause)
        assertFalse(controlsOf(0L).hasAny)
    }

    @Test
    fun `skip dibaca terpisah`() {
        val c = controlsOf(PlaybackState.ACTION_SKIP_TO_NEXT)
        assertTrue(c.canSkipNext)
        assertFalse(c.canSkipPrevious)
    }
}
