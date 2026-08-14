package com.tglabs.spotivibe.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tglabs.spotivibe.domain.LyricsResult
import com.tglabs.spotivibe.domain.LyricsState
import com.tglabs.spotivibe.domain.SyncedLine
import com.tglabs.spotivibe.ui.component.LyricsList
import com.tglabs.spotivibe.ui.theme.SpotivibeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test instrumentasi untuk `LyricsList`.
 *
 * Berjalan di emulator, BUKAN di lane gate cepat. Yang diuji di sini adalah hal
 * yang memang tidak bisa dijangkau unit test: apakah komposabelnya benar-benar
 * menggambar sesuatu, dan apakah ketukan benar-benar sampai ke callback.
 *
 * Perbedaan tiga keadaan kosong adalah yang paling penting. Ketiganya sama-sama
 * "tidak ada lirik di layar" tapi artinya berbeda bagi user: sedang mencari,
 * lagunya memang tidak berlirik, atau jaringannya mati. Yang satu tinggal
 * ditunggu, yang satu permanen, dan yang satu perlu diperbaiki sendiri.
 */
@RunWith(AndroidJUnit4::class)
class LyricsListUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun baris(vararg teks: String): List<SyncedLine> =
        teks.mapIndexed { i, t ->
            SyncedLine(id = i, timeMs = i * 1000L, text = t, words = emptyList())
        }

    private fun pasang(
        state: LyricsState,
        activeIndex: Int = 0,
        onSeekToLine: ((SyncedLine) -> Unit)? = null,
    ) {
        compose.setContent {
            SpotivibeTheme {
                LyricsList(
                    lyricsState = state,
                    activeIndex = activeIndex,
                    progressMs = 0L,
                    onSeekToLine = onSeekToLine,
                )
            }
        }
    }

    private fun siap(vararg teks: String) = LyricsState.Ready(
        LyricsResult(trackId = "spotify:track:uji", synced = baris(*teks), plain = null),
    )

    // ── Tiga keadaan kosong yang berbeda artinya ─────────────────

    @Test
    fun sedangMencariMenampilkanKeteranganMenunggu() {
        pasang(LyricsState.Loading)
        compose.onNodeWithText("Finding lyrics…").assertIsDisplayed()
    }

    @Test
    fun tidakBerlirikBedaDenganJaringanMati() {
        pasang(LyricsState.NotFound)
        compose.onNodeWithText("No lyrics found for this track").assertIsDisplayed()
    }

    @Test
    fun jaringanMatiMemberiTahuCaraMencobaLagi() {
        pasang(LyricsState.Unavailable(reason = "jaringan mati"))
        compose.onNodeWithText(
            "Couldn't load lyrics. Check your connection, then replay the song.",
        ).assertIsDisplayed()
    }

    // ── Menggambar lirik ─────────────────────────────────────────

    @Test
    fun barisLirikBenarBenarDigambar() {
        pasang(siap("baris pertama", "baris kedua", "baris ketiga"))
        compose.onNodeWithText("baris pertama").assertIsDisplayed()
        compose.onNodeWithText("baris kedua").assertIsDisplayed()
    }

    /**
     * Bagian instrumental tidak punya teks. Membiarkannya benar-benar kosong
     * membuat tinggi barisnya runtuh dan daftar melompat saat masuk jeda.
     */
    @Test
    fun barisKosongTetapMemakanRuang() {
        pasang(siap("ada isinya", ""))
        compose.onNodeWithText("♪").assertIsDisplayed()
    }

    // ── Ketukan sampai ke callback ───────────────────────────────

    @Test
    fun mengetukBarisMengirimBarisItuKeCallback() {
        var ditekan: SyncedLine? = null
        pasang(siap("baris pertama", "baris kedua"), onSeekToLine = { ditekan = it })

        compose.onNodeWithText("baris kedua").performClick()

        assertEquals("baris kedua", ditekan?.text)
        assertEquals("id-nya harus ikut, bukan cuma teksnya", 1, ditekan?.id)
    }

    /**
     * Tanpa callback, barisnya tetap harus tergambar dan tidak boleh membuat
     * apa pun jatuh saat diketuk. Overlay memakai bentuk ini.
     */
    @Test
    fun tanpaCallbackTetapAmanDiketuk() {
        pasang(siap("baris pertama"), onSeekToLine = null)
        compose.onNodeWithText("baris pertama").performClick()
        compose.onNodeWithText("baris pertama").assertIsDisplayed()
    }
}
