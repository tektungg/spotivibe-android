package com.tglabs.spotivibe.ui.screen

import com.tglabs.spotivibe.ui.theme.svSafeContent
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
import com.tglabs.spotivibe.domain.LyricsStats
import com.tglabs.spotivibe.domain.PlaybackCapability
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tglabs.spotivibe.BuildConfig
import com.tglabs.spotivibe.domain.UiState
import com.tglabs.spotivibe.ui.component.HairlineIconButton
import com.tglabs.spotivibe.ui.component.HairlineRule
import com.tglabs.spotivibe.ui.component.MonoEyebrow
import com.tglabs.spotivibe.ui.component.SegControl
import com.tglabs.spotivibe.domain.FONT_SIZE_MAX
import com.tglabs.spotivibe.domain.FONT_SIZE_MIN
import com.tglabs.spotivibe.domain.FONT_SIZE_STEP
import com.tglabs.spotivibe.domain.LINE_SPACING_MAX
import com.tglabs.spotivibe.domain.LINE_SPACING_MIN
import com.tglabs.spotivibe.domain.LINE_SPACING_STEP
import com.tglabs.spotivibe.domain.LYRICS_OFFSET_MAX_MS
import com.tglabs.spotivibe.domain.LYRICS_OFFSET_MIN_MS
import com.tglabs.spotivibe.domain.LYRICS_OFFSET_STEP_MS
import com.tglabs.spotivibe.domain.tickCountFor
import com.tglabs.spotivibe.domain.tickIndexOf
import com.tglabs.spotivibe.domain.tickValueOf
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
    lyricsStats: LyricsStats = LyricsStats(),
    onResetLyricsStats: () -> Unit = {},
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
            .background(sv.bg0)
            .svSafeContent(),
    ) {
        // ── Header ─────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = SvSpace.s5, end = SvSpace.s5, top = SvSpace.s3, bottom = SvSpace.s4),
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
                    val garisFont = tickCountFor(FONT_SIZE_MIN, FONT_SIZE_MAX, FONT_SIZE_STEP)
                    TickSlider(
                        index = tickIndexOf(fontSize, FONT_SIZE_MIN, FONT_SIZE_MAX, garisFont),
                        tickCount = garisFont,
                        onIndexChange = { i ->
                            onSetFontSize(tickValueOf(i, FONT_SIZE_MIN, FONT_SIZE_MAX, garisFont))
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
                    val garisOffset = tickCountFor(
                        LYRICS_OFFSET_MIN_MS, LYRICS_OFFSET_MAX_MS, LYRICS_OFFSET_STEP_MS,
                    )
                    TickSlider(
                        index = tickIndexOf(
                            lyricsOffsetMs, LYRICS_OFFSET_MIN_MS, LYRICS_OFFSET_MAX_MS, garisOffset,
                        ),
                        tickCount = garisOffset,
                        twoSided = true,
                        onIndexChange = { i ->
                            onSetLyricsOffsetMs(
                                tickValueOf(
                                    i, LYRICS_OFFSET_MIN_MS, LYRICS_OFFSET_MAX_MS, garisOffset,
                                ),
                            )
                        },
                    )
                    SliderUnitLabel(text = "−2000 / 0 / +2000")
                },
            )
            SettingsRow(
                label = "Line spacing",
                trail = "${lineSpacing} dp",
                content = {
                    val garisSpasi = tickCountFor(
                        LINE_SPACING_MIN, LINE_SPACING_MAX, LINE_SPACING_STEP,
                    )
                    TickSlider(
                        index = tickIndexOf(
                            lineSpacing, LINE_SPACING_MIN, LINE_SPACING_MAX, garisSpasi,
                        ),
                        tickCount = garisSpasi,
                        onIndexChange = { i ->
                            onSetLineSpacing(
                                tickValueOf(i, LINE_SPACING_MIN, LINE_SPACING_MAX, garisSpasi),
                            )
                        },
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
                                null -> "Not connected"
                                PlaybackCapability.Full -> "Premium · Connected"
                                PlaybackCapability.Unknown -> "Connected"
                                PlaybackCapability.Restricted -> "Connected · limited control"
                            },
                            style = SvType.Body,
                            color = sv.ink1,
                        )
                        Text(
                            text = when (playing?.capability) {
                                null -> "tap Connect to start"
                                PlaybackCapability.Full -> "full playback control"
                                // Unknown bukan kegagalan: kontrol tetap jalan
                                // lewat App Remote, hanya label akunnya yang
                                // belum terkonfirmasi lewat Web API.
                                PlaybackCapability.Unknown -> "account type not confirmed yet"
                                PlaybackCapability.Restricted -> "view-only · Premium required for control"
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

            // ── 04 STATISTIK LIRIK ──
            // Metrik inti produk ini. Ditampilkan, bukan cuma dicatat, karena
            // angka yang tidak bisa dilihat sama saja tidak ada.
            SectionHeader(number = "04", title = "Lyrics stats", hint = "Coverage")
            Column(modifier = Modifier.padding(horizontal = SvSpace.s5, vertical = SvSpace.s3)) {
                if (lyricsStats.total == 0) {
                    Text(
                        text = "No data yet. Play a few songs first.",
                        style = SvType.BodyItalic.copy(fontSize = 13.sp),
                        color = sv.ink3,
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(SvSpace.s8)) {
                        MetricColumn(
                            label = "SYNCED",
                            value = lyricsStats.syncedRate.asPercent(),
                        )
                        MetricColumn(
                            label = "ANY LYRICS",
                            value = lyricsStats.anyLyricsRate.asPercent(),
                        )
                        MetricColumn(
                            label = "LOOKUPS",
                            value = lyricsStats.total.toString(),
                        )
                    }
                    Spacer(modifier = Modifier.height(SvSpace.s3))
                    Row(horizontalArrangement = Arrangement.spacedBy(SvSpace.s8)) {
                        MetricColumn(
                            label = "FROM CACHE",
                            value = lyricsStats.cacheRate.asPercent(),
                        )
                        MetricColumn(
                            label = "REACHED",
                            value = lyricsStats.reachRate.asPercent(),
                        )
                        MetricColumn(
                            label = "SEARCH ONLY",
                            value = lyricsStats.searchOnlyRate.asPercent(),
                        )
                    }
                    Spacer(modifier = Modifier.height(SvSpace.s3))
                    Text(
                        // "Terjawab" sengaja dipisah dari total: lookup yang gagal
                        // dijangkau bukan lubang di database LRCLIB, jadi tidak
                        // ikut jadi penyebut coverage.
                        text = "${lyricsStats.synced} synced · " +
                            "${lyricsStats.plainOnly} plain text · " +
                            "${lyricsStats.notFound} not found · " +
                            "${lyricsStats.unavailable} unreachable",
                        style = SvType.BodyItalic.copy(fontSize = 13.sp),
                        color = sv.ink3,
                    )
                    Spacer(modifier = Modifier.height(SvSpace.s4))
                    OutlinedActionButton(
                        label = "RESET STATS",
                        icon = SvIcons.Close,
                        onClick = onResetLyricsStats,
                    )
                }
            }

            // ── 05 COLOPHON ──
            SectionHeader(number = "05", title = "Colophon", hint = "About this app")
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

/**
 * Persen untuk ditampilkan. null berarti belum ada data, dan itu ditulis
 * sebagai tanda hubung, bukan 0%, supaya "belum tahu" tidak tersamar jadi
 * "buruk".
 */
private fun Float?.asPercent(): String =
    if (this == null) "—" else "${(this * 100).toInt()}%"

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
