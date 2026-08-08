package com.tglabs.spotivibe.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import com.tglabs.spotivibe.domain.PlaybackCapability
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tglabs.spotivibe.BuildConfig
import com.tglabs.spotivibe.domain.UiState
import com.tglabs.spotivibe.ui.component.HairlineIconButton
import com.tglabs.spotivibe.ui.component.HairlineRule
import com.tglabs.spotivibe.ui.component.MonoEyebrow
import com.tglabs.spotivibe.ui.component.SegControl
import com.tglabs.spotivibe.ui.component.TickSlider
import com.tglabs.spotivibe.ui.component.Toggle
import com.tglabs.spotivibe.ui.theme.LocalSvColors
import com.tglabs.spotivibe.ui.theme.SvIcons
import com.tglabs.spotivibe.ui.theme.SvRadius
import com.tglabs.spotivibe.ui.theme.SvSpace
import com.tglabs.spotivibe.ui.theme.SvType

/**
 * Editorial Settings — numbered sections, hairline dividers, custom
 * primitives (SegControl / TickSlider / Toggle) replacing Material chrome.
 *
 * Structure (per screens-settings.jsx):
 *   ── Header: back + mono SPOTIVIBE/v0.x.x + big "Settings." display ──
 *   01 — APPEARANCE  · Theme & type
 *   02 — LYRICS      · Sync, spacing, feedback
 *   03 — SPOTIFY     · Connection
 *   04 — COLOPHON    · About this app
 */
@Composable
fun SettingsScreen(
    state: UiState,
    onBack: () -> Unit,
    onToggleDarkMode: () -> Unit,
    onSetFontSize: (Int) -> Unit,
    onLogout: () -> Unit,
    onSetLyricsOffsetMs: (Int) -> Unit = {},
    onSetLineSpacing: (Int) -> Unit = {},
    onToggleHighContrast: () -> Unit = {},
    onToggleSmoothScroll: () -> Unit = {},
    onToggleHaptic: () -> Unit = {},
) {
    val sv = LocalSvColors.current
    val context = LocalContext.current
    val playing = state as? UiState.Playing

    val darkMode = playing?.darkMode ?: true
    val fontSize = playing?.lyricsFontSize ?: 17
    val lyricsOffsetMs = playing?.lyricsOffsetMs ?: 0
    val lineSpacing = playing?.lineSpacing ?: 7
    val highContrast = playing?.highContrast ?: false
    val smoothScroll = playing?.smoothScroll ?: true
    val hapticEnabled = playing?.hapticEnabled ?: true

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(sv.bg0),
    ) {
        // ── Header ─────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = SvSpace.s5, end = SvSpace.s5, top = SvSpace.s8, bottom = SvSpace.s4),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HairlineIconButton(
                    onClick = onBack,
                    icon = SvIcons.Back,
                    contentDescription = "Back",
                )
                Spacer(modifier = Modifier.width(SvSpace.s3))
                MonoEyebrow(text = "SPOTIVIBE / V${BuildConfig.VERSION_NAME}")
            }
            Spacer(modifier = Modifier.height(SvSpace.s4))
            Text(
                text = "Settings.",
                style = SvType.Display.copy(fontSize = 56.sp, lineHeight = 56.sp, letterSpacing = (-1.4).sp),
                color = sv.ink1,
            )
        }
        HairlineRule()

        // ── Scrollable content ─────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // ── 01 APPEARANCE ──
            SectionHeader(number = "01", title = "Appearance", hint = "Theme & type")
            SettingsRow(
                label = "Theme",
                trail = if (darkMode) "DARK" else "LIGHT",
                content = {
                    SegControl(
                        options = listOf("Dark", "Light"),
                        selected = if (darkMode) "Dark" else "Light",
                        onSelect = { sel ->
                            if ((sel == "Dark") != darkMode) onToggleDarkMode()
                        },
                    )
                },
            )
            SettingsRow(
                label = "Lyrics font size",
                trail = "${fontSize}sp · ${fontSizeDescription(fontSize)}",
                content = {
                    TickSlider(
                        value = ((fontSize - 12).coerceIn(0, 44)).toFloat() / 44f,
                        onValueChange = { v ->
                            val new = (12 + (v * 44).toInt()).coerceIn(12, 56)
                            onSetFontSize(new)
                        },
                    )
                    SliderUnitLabel(text = "12 → 56")
                },
            )

            // ── 02 LYRICS ──
            SectionHeader(number = "02", title = "Lyrics", hint = "Sync, spacing, feedback")
            SettingsRow(
                label = "Sync offset",
                trail = "${if (lyricsOffsetMs >= 0) "+" else ""}${lyricsOffsetMs} ms",
                content = {
                    TickSlider(
                        value = ((lyricsOffsetMs + 2000).coerceIn(0, 4000)).toFloat() / 4000f,
                        twoSided = true,
                        onValueChange = { v ->
                            val new = ((v - 0.5f) * 4000f).toInt().coerceIn(-2000, 2000)
                            onSetLyricsOffsetMs(new)
                        },
                    )
                    SliderUnitLabel(text = "−2000 / 0 / +2000")
                },
            )
            SettingsRow(
                label = "Line spacing",
                trail = "${lineSpacing} dp",
                content = {
                    TickSlider(
                        value = (lineSpacing.coerceIn(0, 20)).toFloat() / 20f,
                        onValueChange = { v -> onSetLineSpacing((v * 20).toInt().coerceIn(0, 20)) },
                    )
                    SliderUnitLabel(text = "0 → 20")
                },
            )
            SettingsRow(
                label = "High contrast",
                trail = "for outdoor use",
                content = {
                    Toggle(on = highContrast, onChange = { onToggleHighContrast() })
                },
                inlineTrailing = true,
            )
            SettingsRow(
                label = "Scroll",
                trail = if (smoothScroll) "SMOOTH" else "SNAP",
                content = {
                    SegControl(
                        options = listOf("Smooth", "Snap"),
                        selected = if (smoothScroll) "Smooth" else "Snap",
                        onSelect = { sel ->
                            if ((sel == "Smooth") != smoothScroll) onToggleSmoothScroll()
                        },
                    )
                },
            )
            SettingsRow(
                label = "Haptic feedback",
                trail = if (hapticEnabled) "ON" else "OFF",
                content = {
                    Toggle(on = hapticEnabled, onChange = { onToggleHaptic() })
                },
                inlineTrailing = true,
            )

            // ── 03 SPOTIFY ──
            SectionHeader(number = "03", title = "Spotify", hint = "Connection")
            Column(
                modifier = Modifier.padding(horizontal = SvSpace.s5, vertical = SvSpace.s3),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SvSpace.s3),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when (playing?.capability) {
                                    null -> sv.ink4
                                    PlaybackCapability.Full -> sv.accent
                                    PlaybackCapability.Unknown -> sv.ink2
                                    PlaybackCapability.Restricted -> sv.ink3
                                }
                            ),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (playing?.capability) {
                                null -> "Tidak terhubung"
                                PlaybackCapability.Full -> "Premium · Terhubung"
                                PlaybackCapability.Unknown -> "Terhubung"
                                PlaybackCapability.Restricted -> "Terhubung · kontrol dibatasi"
                            },
                            style = SvType.Body,
                            color = sv.ink1,
                        )
                        Text(
                            text = when (playing?.capability) {
                                null -> "tap Connect untuk mulai"
                                PlaybackCapability.Full -> "full playback control"
                                // Unknown bukan kegagalan: kontrol tetap jalan
                                // lewat App Remote, hanya label akunnya yang
                                // belum terkonfirmasi lewat Web API.
                                PlaybackCapability.Unknown -> "tipe akun belum terkonfirmasi"
                                PlaybackCapability.Restricted -> "view-only · butuh Premium untuk control"
                            },
                            style = SvType.BodyItalic.copy(fontSize = 13.sp),
                            color = sv.ink3,
                        )
                    }
                    if (playing != null) {
                        val restricted = playing.capability.showsUpgradeNotice
                        MonoEyebrow(
                            text = if (restricted) "● VIEW" else "● ACTIVE",
                            color = if (restricted) sv.ink3 else sv.accent,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(SvSpace.s3))
                Row(horizontalArrangement = Arrangement.spacedBy(SvSpace.s2)) {
                    // Logout button
                    OutlinedActionButton(
                        label = "LOGOUT",
                        icon = SvIcons.Logout,
                        onClick = onLogout,
                        modifier = Modifier.weight(1f),
                    )
                    // Revoke link
                    OutlinedActionButton(
                        label = "REVOKE",
                        icon = SvIcons.External,
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.spotify.com/account/apps"))
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // ── 04 COLOPHON ──
            SectionHeader(number = "04", title = "Colophon", hint = "About this app")
            Column(modifier = Modifier.padding(horizontal = SvSpace.s5, vertical = SvSpace.s3)) {
                Row(horizontalArrangement = Arrangement.spacedBy(SvSpace.s8)) {
                    MetricColumn(label = "VERSION", value = BuildConfig.VERSION_NAME)
                    MetricColumn(label = "BUILD", value = currentBuildDate())
                    MetricColumn(label = "LRCLIB", value = "v3")
                }
                Spacer(modifier = Modifier.height(SvSpace.s4))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(SvRadius.r1))
                        .border(1.dp, sv.rule, RoundedCornerShape(SvRadius.r1))
                        .clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/tektungg/spotivibe-android"))
                                )
                            }
                        }
                        .padding(SvSpace.s4),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(SvSpace.s3),
                        ) {
                            Icon(
                                imageVector = SvIcons.Github,
                                contentDescription = null,
                                tint = sv.ink2,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "github.com/tektungg/spotivibe-android",
                                style = SvType.Mono.copy(fontSize = 13.sp),
                                color = sv.ink1,
                            )
                        }
                        Icon(
                            imageVector = SvIcons.External,
                            contentDescription = null,
                            tint = sv.ink2,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(SvSpace.s5))
                Text(
                    text = "Code. Vibe. Sing along.",
                    style = SvType.BodyItalic,
                    color = sv.ink3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(SvSpace.s8))
            }
        }
    }
}

@Composable
private fun SectionHeader(number: String, title: String, hint: String? = null) {
    val sv = LocalSvColors.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = SvSpace.s5, end = SvSpace.s5, top = SvSpace.s5, bottom = SvSpace.s3),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(SvSpace.s3),
        ) {
            Text(
                text = number,
                style = SvType.MonoTabular.copy(fontSize = 13.sp),
                color = sv.ink3,
            )
            Text(
                text = title,
                style = SvType.H2.copy(fontSize = 32.sp, lineHeight = 32.sp),
                color = sv.ink1,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (hint != null) {
                Text(
                    text = hint,
                    style = SvType.BodyItalic.copy(fontSize = 12.sp),
                    color = sv.ink3,
                )
            }
        }
    }
}

@Composable
private fun SettingsRow(
    label: String,
    trail: String? = null,
    content: @Composable () -> Unit,
    inlineTrailing: Boolean = false,
) {
    val sv = LocalSvColors.current
    HairlineRule(soft = true)
    Column(
        modifier = Modifier.padding(horizontal = SvSpace.s5, vertical = SvSpace.s3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = SvType.H3.copy(fontSize = 18.sp, lineHeight = 22.sp),
                color = sv.ink1,
                modifier = Modifier.weight(1f),
            )
            if (inlineTrailing) {
                content()
            } else if (trail != null) {
                Text(
                    text = trail,
                    style = SvType.MonoTabular.copy(fontSize = 12.sp),
                    color = sv.ink3,
                )
            }
        }
        if (!inlineTrailing) {
            Spacer(modifier = Modifier.height(SvSpace.s2))
            content()
        }
    }
}

@Composable
private fun SliderUnitLabel(text: String) {
    val sv = LocalSvColors.current
    Text(
        text = text,
        style = SvType.Mono.copy(fontSize = 10.sp, letterSpacing = 1.sp),
        color = sv.ink4,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun OutlinedActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sv = LocalSvColors.current
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(SvRadius.r1))
            .background(Color.Transparent)
            .border(1.dp, sv.rule, RoundedCornerShape(SvRadius.r1))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SvSpace.s2),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = sv.ink2,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                style = SvType.MonoUp,
                color = sv.ink1,
            )
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String) {
    val sv = LocalSvColors.current
    Column {
        MonoEyebrow(text = label, color = sv.ink3)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = SvType.H2.copy(fontSize = 28.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp),
            color = sv.ink1,
        )
    }
}

private fun fontSizeDescription(size: Int): String = when {
    size <= 14 -> "small"
    size <= 18 -> "normal"
    size <= 24 -> "large"
    size <= 36 -> "huge"
    else       -> "karaoke"
}

private fun currentBuildDate(): String {
    val cal = java.util.Calendar.getInstance()
    val year = cal.get(java.util.Calendar.YEAR)
    val month = cal.get(java.util.Calendar.MONTH) + 1
    return "%d.%02d".format(year, month)
}
