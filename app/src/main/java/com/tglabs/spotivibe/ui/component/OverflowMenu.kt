package com.tglabs.spotivibe.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tglabs.spotivibe.ui.theme.LocalSvColors
import com.tglabs.spotivibe.ui.theme.SvIcons
import com.tglabs.spotivibe.ui.theme.SvType

/**
 * Isi menu kebab. Dibungkus satu objek supaya portrait dan landscape menyusun
 * menu yang sama dari satu tempat, dan item baru tidak perlu menambah parameter
 * di setiap lapis pemanggil.
 */
data class HeaderMenuActions(
    val onSearchLyrics: () -> Unit,
    val onOpenSettings: () -> Unit,
    /** Null = tidak ada lagu untuk disinkronkan, item disembunyikan. */
    val onSyncSong: (() -> Unit)? = null,
    /** Hanya true kalau lagu ini punya lirik tersimpan permanen. */
    val showForgetOverride: Boolean = false,
    val onForgetOverride: () -> Unit = {},
)

/**
 * Menu kebab yang dipakai portrait maupun landscape.
 *
 * Ditarik jadi satu komponen karena kedua layar punya baris ikonnya sendiri.
 * Kalau menunya ditulis dua kali, keduanya akan melenceng: pertama kali fitur
 * pencarian dipasang, landscape memang tertinggal dan pintunya tidak ada sama
 * sekali.
 *
 * Kebab dipilih ketimbang menambah ikon ke-5 di baris header supaya baris itu
 * tidak sesak di layar kecil, apalagi saat ikon romaji ikut muncul. Settings
 * jarang dibuka, sementara "Search lyrics" dipakai justru saat ada masalah.
 */
@Composable
fun HeaderOverflowMenu(
    actions: HeaderMenuActions,
    buttonSize: Dp = 36.dp,
    iconSize: Dp = 16.dp,
) {
    val sv = LocalSvColors.current
    var terbuka by remember { mutableStateOf(false) }
    // Setiap item menutup menu dulu, baru menjalankan aksinya.
    val pilih: (() -> Unit) -> Unit = { aksi ->
        terbuka = false
        aksi()
    }

    Box {
        HairlineIconButton(
            onClick = { terbuka = true },
            icon = SvIcons.Kebab,
            contentDescription = "Menu",
            size = buttonSize,
            iconSize = iconSize,
        )
        DropdownMenu(
            expanded = terbuka,
            onDismissRequest = { terbuka = false },
            containerColor = sv.bg2,
        ) {
            MenuItem("Search lyrics", sv.ink1) { pilih(actions.onSearchLyrics) }
            actions.onSyncSong?.let { sync -> MenuItem("Sync this song", sv.ink1) { pilih(sync) } }
            if (actions.showForgetOverride) {
                MenuItem("Forget saved lyrics", sv.accent) { pilih(actions.onForgetOverride) }
            }
            MenuItem("Settings", sv.ink1) { pilih(actions.onOpenSettings) }
        }
    }
}

@Composable
private fun MenuItem(label: String, color: Color, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = SvType.Body, color = color) },
        onClick = onClick,
    )
}
