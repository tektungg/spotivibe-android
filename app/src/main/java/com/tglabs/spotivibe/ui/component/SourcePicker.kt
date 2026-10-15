package com.tglabs.spotivibe.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.tglabs.spotivibe.domain.SourceAction
import com.tglabs.spotivibe.domain.SourceEntry
import com.tglabs.spotivibe.domain.SourcePickerModel
import com.tglabs.spotivibe.domain.displayName
import com.tglabs.spotivibe.domain.sourceAction
import com.tglabs.spotivibe.domain.sourceStatusLabel
import com.tglabs.spotivibe.ui.theme.LocalSvColors
import com.tglabs.spotivibe.ui.theme.SvSpace
import com.tglabs.spotivibe.ui.theme.SvType

/**
 * "Sources for this song": satu baris per sumber lirik yang aktif, dengan apa
 * yang dimilikinya untuk lagu ini. Mengetuk baris memakai liriknya, atau
 * menanyakan sumber itu dulu kalau belum ada jawaban.
 */
@Composable
fun SourcePicker(model: SourcePickerModel, onPick: (SourceEntry) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        MonoEyebrow(text = "SOURCES FOR THIS SONG")
        Spacer(modifier = Modifier.height(SvSpace.s2))
        model.entries.forEach { entry ->
            SourceRow(
                entry = entry,
                action = sourceAction(entry, model.inUse),
                isAsking = model.asking == entry.id,
                // Satu pertanyaan pada satu waktu: hasil yang datang belakangan
                // tidak boleh menimpa pilihan yang sudah dibuat.
                enabled = model.asking == null,
                onClick = { onPick(entry) },
            )
            HairlineRule(soft = true)
        }
    }
}

@Composable
private fun SourceRow(
    entry: SourceEntry,
    action: SourceAction,
    isAsking: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val sv = LocalSvColors.current
    val tappable = enabled && (action == SourceAction.Use || action == SourceAction.Ask)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = tappable, onClick = onClick)
            .padding(vertical = SvSpace.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SvSpace.s3),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = entry.id.displayName, style = SvType.Body, color = sv.ink1)
            Text(
                text = sourceStatusLabel(entry),
                style = SvType.BodyItalic.copy(fontSize = 13.sp),
                color = sv.ink3,
            )
        }
        MonoEyebrow(
            text = actionLabel(action, isAsking),
            color = if (action == SourceAction.InUse) sv.accent else sv.ink2,
        )
    }
}

private fun actionLabel(action: SourceAction, isAsking: Boolean): String = when {
    isAsking -> "ASKING…"
    action == SourceAction.InUse -> "● IN USE"
    action == SourceAction.Use -> "USE"
    action == SourceAction.Ask -> "ASK"
    else -> ""
}
