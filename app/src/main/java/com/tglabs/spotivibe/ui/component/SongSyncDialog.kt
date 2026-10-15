package com.tglabs.spotivibe.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.tglabs.spotivibe.domain.SONG_OFFSET_STEP_MS
import com.tglabs.spotivibe.domain.clampSongOffsetMs
import com.tglabs.spotivibe.ui.theme.LocalSvColors
import com.tglabs.spotivibe.ui.theme.SvRadius
import com.tglabs.spotivibe.ui.theme.SvSpace
import com.tglabs.spotivibe.ui.theme.SvType

/**
 * Koreksi sync untuk SATU lagu, disetel sambil lagunya diputar.
 *
 * Tombol langkah, bukan slider: yang dicari user di sini selisih kecil yang
 * terdengar ("liriknya sedikit telat"), dan tiap tekanan langsung terasa di
 * baris yang sedang menyala di belakang dialog. Nilainya disimpan per tekanan,
 * jadi menutup dialog dengan cara apa pun tidak membuang perubahan.
 */
@Composable
fun SongSyncDialog(
    title: String,
    songOffsetMs: Int,
    onChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sv = LocalSvColors.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(SvRadius.r1))
                .background(sv.bg2)
                .border(1.dp, sv.rule, RoundedCornerShape(SvRadius.r1))
                .padding(SvSpace.s5),
        ) {
            MonoEyebrow(text = "SYNC THIS SONG")
            Spacer(modifier = Modifier.height(SvSpace.s2))
            Text(text = title, style = SvType.H3, color = sv.ink1, maxLines = 2)
            Spacer(modifier = Modifier.height(SvSpace.s4))
            StepperRow(songOffsetMs = songOffsetMs, onChange = onChange)
            Spacer(modifier = Modifier.height(SvSpace.s2))
            Text(
                text = "Plus shows lyrics earlier. Applies to this song only.",
                style = SvType.BodyItalic.copy(fontSize = 13.sp),
                color = sv.ink3,
            )
            Spacer(modifier = Modifier.height(SvSpace.s4))
            Row(horizontalArrangement = Arrangement.spacedBy(SvSpace.s2)) {
                StepButton(label = "RESET", onClick = { onChange(0) }, modifier = Modifier.weight(1f))
                StepButton(label = "DONE", onClick = onDismiss, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StepperRow(songOffsetMs: Int, onChange: (Int) -> Unit) {
    val sv = LocalSvColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton(
            label = "−$SONG_OFFSET_STEP_MS",
            onClick = { onChange(clampSongOffsetMs(songOffsetMs - SONG_OFFSET_STEP_MS)) },
        )
        Text(
            text = "${if (songOffsetMs >= 0) "+" else ""}$songOffsetMs ms",
            style = SvType.MonoTabular.copy(fontSize = 18.sp),
            color = if (songOffsetMs == 0) sv.ink2 else sv.accent,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        StepButton(
            label = "+$SONG_OFFSET_STEP_MS",
            onClick = { onChange(clampSongOffsetMs(songOffsetMs + SONG_OFFSET_STEP_MS)) },
        )
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sv = LocalSvColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(SvRadius.r1))
            .border(1.dp, sv.rule, RoundedCornerShape(SvRadius.r1))
            .clickable(onClick = onClick)
            .padding(horizontal = SvSpace.s4, vertical = SvSpace.s3),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = SvType.MonoUp.copy(fontSize = 12.sp), color = sv.ink1)
    }
}
