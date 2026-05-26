package com.tglabs.spotivibe.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tglabs.spotivibe.BuildConfig
import com.tglabs.spotivibe.domain.UiState

@Composable
fun SettingsScreen(
    state: UiState,
    onBack: () -> Unit,
    onToggleDarkMode: () -> Unit,
    onSetFontSize: (Int) -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val playing = state as? UiState.Playing
    val darkMode = playing?.darkMode ?: true
    val fontSize = playing?.lyricsFontSize ?: 17

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // ── Top app bar ──────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 32.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // ── Scrollable content ──────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SectionHeader(title = "Appearance", icon = Icons.Filled.Palette)
            Spacer(modifier = Modifier.height(8.dp))

            // Theme toggle row
            SettingRow(
                title = "Dark theme",
                subtitle = if (darkMode) "Tampilan gelap (default)" else "Tampilan terang (untuk outdoor)",
                trailing = {
                    Switch(
                        checked = darkMode,
                        onCheckedChange = { onToggleDarkMode() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        ),
                    )
                },
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                modifier = Modifier.padding(vertical = 8.dp),
            )

            // Font size slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.padding(end = 16.dp)) {
                        Text(
                            text = "Lyrics font size",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "${fontSize}sp — ${getFontDescription(fontSize)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "$fontSize",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Slider(
                    value = fontSize.toFloat(),
                    onValueChange = { onSetFontSize(it.toInt()) },
                    valueRange = 12f..56f,
                    steps = (56 - 12) / 2 - 1,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader(title = "Spotify", icon = Icons.Filled.MusicNote)
            Spacer(modifier = Modifier.height(8.dp))

            // Connection status
            val statusText = when {
                playing == null -> "Tidak terhubung"
                playing.isPremium -> "Terhubung sebagai Premium"
                else -> "Terhubung sebagai Free"
            }
            val statusColor = when {
                playing == null -> MaterialTheme.colorScheme.onSurfaceVariant
                playing.isPremium -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            SettingRow(
                title = "Status",
                subtitle = statusText,
                trailing = {
                    if (playing != null) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Logout button
            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(text = "Logout Spotify")
            }

            // Revoke link
            Spacer(modifier = Modifier.height(12.dp))
            LinkRow(
                text = "Revoke akses sepenuhnya di Spotify Account",
                subtitle = "spotify.com/account/apps — opens browser",
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.spotify.com/account/apps"),
                    )
                    runCatching { context.startActivity(intent) }
                },
            )

            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader(title = "About", icon = Icons.Filled.Info)
            Spacer(modifier = Modifier.height(8.dp))

            SettingRow(
                title = "Version",
                subtitle = BuildConfig.VERSION_NAME,
                trailing = {},
            )

            Spacer(modifier = Modifier.height(8.dp))

            LinkRow(
                text = "Source code on GitHub",
                subtitle = "github.com/tektungg/spotivibe-android",
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/tektungg/spotivibe-android"),
                    )
                    runCatching { context.startActivity(intent) }
                },
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Spotivibe — Code. Vibe. Sing along.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier
            .weight(1f)
            .padding(end = 16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing()
    }
}

@Composable
private fun LinkRow(
    text: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun getFontDescription(size: Int): String = when {
    size <= 14 -> "Kecil"
    size <= 18 -> "Normal"
    size <= 24 -> "Besar"
    size <= 36 -> "Sangat besar"
    else -> "Karaoke"
}
