package com.tglabs.spotivibe.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tglabs.spotivibe.ui.component.HeaderOverflowMenu
import com.tglabs.spotivibe.ui.theme.SpotivibeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test instrumentasi untuk menu overflow.
 *
 * Menu ini pernah punya bug yang khas UI: saat pencarian lirik pertama
 * dipasang, layar tablet tertinggal dan pintunya tidak ada sama sekali, karena
 * menunya ditulis langsung di header portrait yang tidak dipakai di sana.
 * Unit test tidak bisa melihat hal semacam itu.
 */
@RunWith(AndroidJUnit4::class)
class HeaderOverflowMenuUiTest {

    @get:Rule
    val compose = createComposeRule()

    private var cariLirik = 0
    private var bukaSettings = 0
    private var lupakan = 0

    private fun pasang(showForgetOverride: Boolean = false) {
        compose.setContent {
            SpotivibeTheme {
                HeaderOverflowMenu(
                    onSearchLyrics = { cariLirik++ },
                    onOpenSettings = { bukaSettings++ },
                    showForgetOverride = showForgetOverride,
                    onForgetOverride = { lupakan++ },
                )
            }
        }
    }

    private fun buka() = compose.onNodeWithContentDescription("Menu").performClick()

    // ── Pintunya ada ─────────────────────────────────────────────

    @Test
    fun tombolMenuTergambar() {
        pasang()
        compose.onNodeWithContentDescription("Menu").assertIsDisplayed()
    }

    @Test
    fun menuTertutupSampaiDitekan() {
        pasang()
        compose.onNodeWithText("Search lyrics").assertDoesNotExist()
    }

    @Test
    fun menekanTombolMembukaMenunya() {
        pasang()
        buka()
        compose.onNodeWithText("Search lyrics").assertIsDisplayed()
        compose.onNodeWithText("Settings").assertIsDisplayed()
    }

    // ── Item bersyarat ───────────────────────────────────────────

    /**
     * "Forget saved lyrics" cuma masuk akal kalau lagu ini memang punya lirik
     * tersimpan. Menampilkannya selalu berarti menawarkan tindakan yang tidak
     * melakukan apa pun di kebanyakan lagu.
     */
    @Test
    fun lupakanLirikTersembunyiKalauTidakAdaYangTersimpan() {
        pasang(showForgetOverride = false)
        buka()
        compose.onNodeWithText("Search lyrics").assertIsDisplayed()
        compose.onNodeWithText("Forget saved lyrics").assertDoesNotExist()
    }

    @Test
    fun lupakanLirikMunculKalauAdaYangTersimpan() {
        pasang(showForgetOverride = true)
        buka()
        compose.onNodeWithText("Forget saved lyrics").assertIsDisplayed()
    }

    // ── Ketukan sampai ke callback ───────────────────────────────

    @Test
    fun mengetukCariLirikMemanggilCallbackNya() {
        pasang()
        buka()
        compose.onNodeWithText("Search lyrics").performClick()
        assertEquals(1, cariLirik)
        assertEquals(0, bukaSettings)
    }

    @Test
    fun mengetukSettingsMemanggilCallbackNya() {
        pasang()
        buka()
        compose.onNodeWithText("Settings").performClick()
        assertEquals(1, bukaSettings)
        assertEquals(0, cariLirik)
    }

    @Test
    fun mengetukLupakanLirikMemanggilCallbackNya() {
        pasang(showForgetOverride = true)
        buka()
        compose.onNodeWithText("Forget saved lyrics").performClick()
        assertEquals(1, lupakan)
    }

    /** Menu harus menutup sendiri setelah dipilih, bukan menggantung terbuka. */
    @Test
    fun menuMenutupSetelahMemilih() {
        pasang()
        buka()
        compose.onNodeWithText("Search lyrics").performClick()
        compose.onNodeWithText("Search lyrics").assertDoesNotExist()
    }
}
