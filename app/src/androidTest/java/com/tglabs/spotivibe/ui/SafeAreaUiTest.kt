package com.tglabs.spotivibe.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tglabs.spotivibe.domain.LyricsState
import com.tglabs.spotivibe.domain.NowPlaying
import com.tglabs.spotivibe.domain.UiState
import com.tglabs.spotivibe.ui.screen.NowPlayingScreen
import com.tglabs.spotivibe.ui.screen.NowPlayingTabletScreen
import com.tglabs.spotivibe.ui.screen.SettingsScreen
import com.tglabs.spotivibe.ui.theme.LocalSvSafeInsets
import com.tglabs.spotivibe.ui.theme.SpotivibeTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Safe area dengan insets palsu yang meniru head unit mobil: bar tebal di
 * atas, navigation bar di KIRI, dan bar di bawah.
 *
 * Insets disuntikkan lewat [LocalSvSafeInsets]. `createComposeRule` tidak
 * menjalankan edge-to-edge, jadi insets sistemnya biasanya 0 dan tidak akan
 * membuktikan apa pun. `ResponsiveGuardTest` menjamin semua layar membaca
 * insets lewat seam ini, jadi yang lolos di sini juga berlaku di perangkat.
 */
@RunWith(AndroidJUnit4::class)
class SafeAreaUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val atas = 64.dp
    private val kiri = 80.dp
    private val bawah = 48.dp
    private val insetsHeadUnit = WindowInsets(left = kiri, top = atas, right = 0.dp, bottom = bawah)

    private val playing = UiState.Playing(
        track = NowPlaying(
            id = "spotify:track:safe",
            title = "Judul Lagu Uji",
            artist = "Artis Uji",
            album = "Album Uji",
            imageUri = null,
            durationMs = 200_000,
            progressMs = 10_000,
            isPaused = false,
        ),
        lyricsState = LyricsState.NotFound,
    )

    private fun DpRect.assertDiDalamAreaAman(nama: String, tinggiLayar: Dp) {
        assertTrue("$nama tertimpa bar atas: top=$top < $atas", top >= atas)
        assertTrue("$nama tertimpa bar kiri: left=$left < $kiri", left >= kiri)
        assertTrue(
            "$nama tertimpa bar bawah: bottom=$bottom > ${tinggiLayar - bawah}",
            bottom <= tinggiLayar - bawah,
        )
    }

    private fun tinggiLayar(): Dp = compose.onRoot().getUnclippedBoundsInRoot().bottom

    /** REGRESI: laporan asli. Header tertimpa navigasi di head unit. */
    @Test
    fun headerDanTransportPortraitDiDalamAreaAman() {
        compose.setContent {
            SpotivibeTheme {
                CompositionLocalProvider(LocalSvSafeInsets provides insetsHeadUnit) {
                    NowPlayingScreen(
                        state = playing,
                        onTogglePlayPause = {}, onNext = {}, onPrevious = {}, onSeek = {},
                        onToggleRomanization = {}, onToggleOverlay = {},
                        onToggleDarkMode = {}, onBumpFontSize = {}, onLogout = {},
                        onOpenSettings = {},
                    )
                }
            }
        }
        val h = tinggiLayar()
        compose.onAllNodesWithText("Judul Lagu Uji")[0].getUnclippedBoundsInRoot()
            .assertDiDalamAreaAman("judul", h)
        compose.onNodeWithContentDescription("Previous").getUnclippedBoundsInRoot()
            .assertDiDalamAreaAman("tombol previous", h)
        compose.onNodeWithContentDescription("Pause").getUnclippedBoundsInRoot()
            .assertDiDalamAreaAman("tombol pause", h)
    }

    /** Head unit hampir selalu landscape, dan navigasinya sering di kiri. */
    @Test
    fun panelLandscapeDiDalamAreaAman() {
        compose.setContent {
            SpotivibeTheme {
                CompositionLocalProvider(LocalSvSafeInsets provides insetsHeadUnit) {
                    NowPlayingTabletScreen(
                        state = playing,
                        onTogglePlayPause = {}, onNext = {}, onPrevious = {}, onSeek = {},
                        onToggleRomanization = {}, onToggleOverlay = {}, onOpenSettings = {},
                    )
                }
            }
        }
        val h = tinggiLayar()
        compose.onAllNodesWithText("Judul Lagu Uji")[0].getUnclippedBoundsInRoot()
            .assertDiDalamAreaAman("judul", h)
        compose.onNodeWithContentDescription("Previous").getUnclippedBoundsInRoot()
            .assertDiDalamAreaAman("tombol previous", h)
    }

    @Test
    fun headerSettingsDiDalamAreaAman() {
        compose.setContent {
            SpotivibeTheme {
                CompositionLocalProvider(LocalSvSafeInsets provides insetsHeadUnit) {
                    SettingsScreen(
                        state = playing,
                        onBack = {}, onToggleDarkMode = {}, onSetFontSize = {}, onLogout = {},
                    )
                }
            }
        }
        compose.onNodeWithText("Settings.").getUnclippedBoundsInRoot()
            .assertDiDalamAreaAman("judul settings", tinggiLayar())
    }
}
