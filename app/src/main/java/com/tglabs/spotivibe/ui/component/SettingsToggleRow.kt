package com.tglabs.spotivibe.ui.component

import androidx.compose.runtime.Composable
import com.tglabs.spotivibe.ui.screen.SettingsRow

/** Baris Settings berisi satu toggle di kanan label. */
@Composable
internal fun SettingsToggleRow(label: String, on: Boolean, onToggle: () -> Unit) {
    SettingsRow(
        label = label,
        content = { Toggle(on = on, onChange = { onToggle() }) },
        inlineTrailing = true,
    )
}
