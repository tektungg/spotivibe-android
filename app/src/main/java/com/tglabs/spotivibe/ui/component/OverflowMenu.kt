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
import androidx.compose.ui.unit.dp
import com.tglabs.spotivibe.ui.theme.LocalSvColors
import com.tglabs.spotivibe.ui.theme.SvIcons
import com.tglabs.spotivibe.ui.theme.SvType

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
    onSearchLyrics: () -> Unit,
    onOpenSettings: () -> Unit,
    /** Hanya true kalau lagu ini punya lirik tersimpan permanen. */
    showForgetOverride: Boolean = false,
    onForgetOverride: () -> Unit = {},
    buttonSize: androidx.compose.ui.unit.Dp = 36.dp,
    iconSize: androidx.compose.ui.unit.Dp = 16.dp,
) {
    val sv = LocalSvColors.current
    var terbuka by remember { mutableStateOf(false) }

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
            DropdownMenuItem(
                text = { Text("Search lyrics", style = SvType.Body, color = sv.ink1) },
                onClick = {
                    terbuka = false
                    onSearchLyrics()
                },
            )
            if (showForgetOverride) {
                DropdownMenuItem(
                    text = { Text("Forget saved lyrics", style = SvType.Body, color = sv.accent) },
                    onClick = {
                        terbuka = false
                        onForgetOverride()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Settings", style = SvType.Body, color = sv.ink1) },
                onClick = {
                    terbuka = false
                    onOpenSettings()
                },
            )
        }
    }
}
