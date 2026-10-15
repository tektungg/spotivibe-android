package com.tglabs.spotivibe.ui.component

import androidx.compose.runtime.Composable
import com.tglabs.spotivibe.domain.DEVICE_OFFSET_MAX_MS
import com.tglabs.spotivibe.domain.DEVICE_OFFSET_MIN_MS
import com.tglabs.spotivibe.domain.LYRICS_OFFSET_MAX_MS
import com.tglabs.spotivibe.domain.LYRICS_OFFSET_MIN_MS
import com.tglabs.spotivibe.domain.LYRICS_OFFSET_STEP_MS
import com.tglabs.spotivibe.domain.SyncOffsets
import com.tglabs.spotivibe.domain.tickCountFor
import com.tglabs.spotivibe.domain.tickIndexOf
import com.tglabs.spotivibe.domain.tickValueOf
import com.tglabs.spotivibe.ui.screen.SettingsRow
import com.tglabs.spotivibe.ui.screen.SliderUnitLabel

/**
 * Slider offset global dan offset perangkat output aktif.
 *
 * Offset per lagu SENGAJA tidak di sini: itu disetel sambil mendengarkan
 * lagunya, lewat "Sync this song" di menu layar utama. Settings bukan tempat
 * user berada saat ia menyadari satu lagu meleset.
 *
 * Slider perangkat hanya muncul kalau perangkatnya diketahui. Slider tanpa
 * perangkat akan menyimpan nilai yang tidak pernah dipakai.
 */
@Composable
internal fun SyncOffsetRows(
    offsets: SyncOffsets,
    onSetGlobalMs: (Int) -> Unit,
    onSetDeviceMs: (Int) -> Unit,
) {
    OffsetSliderRow(
        label = "Sync offset",
        trail = signedMs(offsets.globalMs),
        valueMs = offsets.globalMs,
        range = LYRICS_OFFSET_MIN_MS..LYRICS_OFFSET_MAX_MS,
        onChange = onSetGlobalMs,
    )
    val label = offsets.outputLabel ?: return
    OffsetSliderRow(
        label = "This output",
        trail = "$label · ${signedMs(offsets.deviceMs)}",
        valueMs = offsets.deviceMs,
        range = DEVICE_OFFSET_MIN_MS..DEVICE_OFFSET_MAX_MS,
        onChange = onSetDeviceMs,
    )
}

@Composable
private fun OffsetSliderRow(
    label: String,
    trail: String,
    valueMs: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
) {
    val minMs = range.first
    val maxMs = range.last
    SettingsRow(
        label = label,
        trail = trail,
        content = {
            val garis = tickCountFor(minMs, maxMs, LYRICS_OFFSET_STEP_MS)
            TickSlider(
                index = tickIndexOf(valueMs, minMs, maxMs, garis),
                tickCount = garis,
                twoSided = true,
                onIndexChange = { i -> onChange(tickValueOf(i, minMs, maxMs, garis)) },
            )
            SliderUnitLabel(text = "−${-minMs} / 0 / +$maxMs")
        },
    )
}

/** `+120 ms`, `-80 ms`, `+0 ms`. */
internal fun signedMs(ms: Int): String = "${if (ms >= 0) "+" else ""}$ms ms"
