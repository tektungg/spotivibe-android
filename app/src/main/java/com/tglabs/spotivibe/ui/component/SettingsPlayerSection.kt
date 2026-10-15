package com.tglabs.spotivibe.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tglabs.spotivibe.domain.PlayerControls
import com.tglabs.spotivibe.domain.UiState
import com.tglabs.spotivibe.ui.screen.OutlinedActionButton
import com.tglabs.spotivibe.ui.theme.LocalSvColors
import com.tglabs.spotivibe.ui.theme.SvIcons
import com.tglabs.spotivibe.ui.theme.SvSpace
import com.tglabs.spotivibe.ui.theme.SvType

/**
 * Isi bagian "Player" di Settings: app yang sedang diikuti, apa yang
 * diizinkannya, dan jalan ke layar izin sistem.
 *
 * Tidak ada akun, jadi tidak ada logout. Satu-satunya hal yang bisa diatur
 * adalah izin notification access, dan mencabutnya di sana menghentikan
 * Spotivibe membaca player mana pun.
 */
@Composable
internal fun PlayerStatusSection(playing: UiState.Playing?, onOpenAccess: () -> Unit) {
    val sv = LocalSvColors.current
    Column(modifier = Modifier.padding(horizontal = SvSpace.s5, vertical = SvSpace.s3)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SvSpace.s3),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (playing != null) sv.accent else sv.ink4),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = playerTitle(playing), style = SvType.Body, color = sv.ink1)
                Text(
                    text = playerSubtitle(playing?.controls),
                    style = SvType.BodyItalic.copy(fontSize = 13.sp),
                    color = sv.ink3,
                )
            }
            if (playing != null) MonoEyebrow(text = "● ACTIVE", color = sv.accent)
        }
        Spacer(modifier = Modifier.height(SvSpace.s3))
        OutlinedActionButton(
            label = "NOTIFICATION ACCESS",
            icon = SvIcons.External,
            onClick = onOpenAccess,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun playerTitle(playing: UiState.Playing?): String = when {
    playing == null -> "No player right now"
    playing.playerLabel != null -> "Following ${playing.playerLabel}"
    else -> "Following a player"
}

/** Yang perlu diketahui user: kenapa sebuah tombol mati, bukan daftar bit. */
private fun playerSubtitle(controls: PlayerControls?): String = when {
    controls == null -> "play something in any music app"
    !controls.hasAny -> "this player doesn't accept controls from other apps"
    !controls.canSeek -> "this player doesn't allow seeking from other apps"
    else -> "play, pause, skip, and seek from here"
}
