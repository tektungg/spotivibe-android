package com.tglabs.spotivibe.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tglabs.spotivibe.ui.component.Transport
import com.tglabs.spotivibe.ui.theme.SpotivibeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test instrumentasi untuk `Transport`.
 *
 * Baris tombol ini pernah terpotong habis di HP landscape tanpa ada yang
 * melaporkannya, karena yang hilang berada di bawah lipatan layar. Unit test
 * sudah menjaga perhitungan tingginya; yang di sini menjaga tombolnya benar-
 * benar ada dan benar-benar memanggil sesuatu saat ditekan.
 */
@RunWith(AndroidJUnit4::class)
class TransportUiTest {

    @get:Rule
    val compose = createComposeRule()

    private var togglePlay = 0
    private var next = 0
    private var previous = 0

    private fun pasang(
        isPaused: Boolean = false,
        progressMs: Long = 60_000L,
        durationMs: Long = 180_000L,
        compact: Boolean = false,
    ) {
        compose.setContent {
            SpotivibeTheme {
                Transport(
                    progressMs = progressMs,
                    durationMs = durationMs,
                    isPaused = isPaused,
                    onDrag = {},
                    onSeek = {},
                    onTogglePlayPause = { togglePlay++ },
                    onNext = { next++ },
                    onPrevious = { previous++ },
                    compact = compact,
                )
            }
        }
    }

    // ── Ketiga tombol ada ────────────────────────────────────────

    @Test
    fun ketigaTombolTergambar() {
        pasang()
        compose.onNodeWithContentDescription("Previous").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pause").assertIsDisplayed()
    }

    /**
     * Mode rapat cuma memangkas PADDING. Ukuran tombol sengaja tidak ikut
     * mengecil karena 44 dp sudah di bawah ambang target sentuh Material, jadi
     * ketiganya harus tetap ada dan tetap bisa ditekan.
     */
    @Test
    fun modeRapatTidakMenghilangkanTombol() {
        pasang(compact = true)
        compose.onNodeWithContentDescription("Previous").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pause").assertIsDisplayed()
    }

    // ── Ikon mengikuti keadaan ───────────────────────────────────

    @Test
    fun sedangJalanMenampilkanTombolJeda() {
        pasang(isPaused = false)
        compose.onNodeWithContentDescription("Pause").assertIsDisplayed()
    }

    @Test
    fun sedangJedaMenampilkanTombolPutar() {
        pasang(isPaused = true)
        compose.onNodeWithContentDescription("Play").assertIsDisplayed()
    }

    // ── Ketukan sampai ke callback ───────────────────────────────

    @Test
    fun setiapTombolMemanggilCallbackNya() {
        pasang()
        compose.onNodeWithContentDescription("Pause").performClick()
        compose.onNodeWithContentDescription("Next").performClick()
        compose.onNodeWithContentDescription("Previous").performClick()

        assertEquals(1, togglePlay)
        assertEquals(1, next)
        assertEquals(1, previous)
    }

    @Test
    fun tombolTidakSalingTertukar() {
        pasang()
        compose.onNodeWithContentDescription("Next").performClick()

        assertEquals(1, next)
        assertEquals("previous tidak boleh ikut terpanggil", 0, previous)
        assertEquals("play/pause tidak boleh ikut terpanggil", 0, togglePlay)
    }

    // ── Waktu ────────────────────────────────────────────────────

    @Test
    fun posisiDanDurasiTergambar() {
        pasang(progressMs = 74_000L, durationMs = 215_000L)
        compose.onNodeWithText("1:14").assertIsDisplayed()
        compose.onNodeWithText("3:35").assertIsDisplayed()
    }

    /**
     * Durasi nol pernah jadi sumber pembagian nol saat menghitung rasio slider.
     */
    @Test
    fun durasiNolTidakMenjatuhkanApaPun() {
        pasang(progressMs = 0L, durationMs = 0L)
        compose.onNodeWithContentDescription("Pause").assertIsDisplayed()
        assertTrue("berhasil digambar tanpa exception", true)
    }
}
