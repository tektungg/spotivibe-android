package com.tglabs.spotivibe.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.tglabs.spotivibe.domain.LyricsState
import com.tglabs.spotivibe.ui.component.LyricsList
import com.tglabs.spotivibe.ui.component.MonoEyebrow
import com.tglabs.spotivibe.ui.theme.LocalSvColors
import com.tglabs.spotivibe.ui.theme.SvIcons
import com.tglabs.spotivibe.ui.theme.SvSpace
import com.tglabs.spotivibe.ui.theme.SvType

/**
 * Karaoke fullscreen — minimal chrome, lyrics fill screen.
 * - Top-left: mono "FULLSCREEN · {lang}" eyebrow + artist below.
 * - Top-right: 36dp hairline circle close X (backdrop tint).
 * - Center: oversized lyrics 1.7× user setting (cap 56sp).
 * - Bottom: 1px hairline progress + mono tabular elapsed/duration +
 *   subtle "tap × to exit" hint.
 *
 * Hides system bars via WindowCompat / WindowInsetsControllerCompat.
 * Restores on dispose (exit via X tap or hardware back).
 */
@Composable
fun KaraokeView(
    lyricsState: LyricsState,
    activeIndex: Int,
    progressMs: Long,
    accent: Color,
    romaji: Map<Long, String?>,
    fontSize: Int,
    onExit: () -> Unit,
    durationMs: Long = 0L,
    artist: String = "",
    lang: String = "",
) {
    val sv = LocalSvColors.current
    val view = LocalView.current

    // Hide system bars on entry, restore on exit
    DisposableEffect(view) {
        val window = (view.context as android.app.Activity).window
        val insets = WindowCompat.getInsetsController(window, view)
        insets.hide(WindowInsetsCompat.Type.systemBars())
        insets.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            insets.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    BackHandler(onBack = onExit)

    val karaokeFontSize = (fontSize * 1.7f).toInt().coerceAtMost(56)
    val safeDuration = durationMs.coerceAtLeast(1L)
    val ratio = (progressMs.toFloat() / safeDuration).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(sv.bg0),
    ) {
        // Lyrics fill the whole screen
        LyricsList(
            lyricsState = lyricsState,
            activeIndex = activeIndex,
            progressMs = progressMs,
            romaji = romaji,
            fontSize = karaokeFontSize,
            modifier = Modifier.fillMaxSize(),
        )

        // ── Top-left meta block ──
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = SvSpace.s12, start = SvSpace.s5),
        ) {
            MonoEyebrow(
                text = if (lang.isNotBlank()) "FULLSCREEN · $lang" else "FULLSCREEN",
                color = sv.ink3,
            )
            if (artist.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = artist,
                    style = SvType.H3.copy(fontSize = 14.sp),
                    color = sv.ink2,
                )
            }
        }

        // ── Top-right close X ──
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = SvSpace.s12, end = SvSpace.s5)
                .size(36.dp)
                .clip(CircleShape)
                .background(sv.bg2.copy(alpha = 0.6f))
                .border(1.dp, sv.rule, CircleShape)
                .clickable(onClick = onExit),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = SvIcons.Close,
                contentDescription = "Exit fullscreen",
                tint = sv.ink1,
                modifier = Modifier.size(16.dp),
            )
        }

        // ── Bottom: hairline progress + meta ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = SvSpace.s5, end = SvSpace.s5, bottom = SvSpace.s8),
        ) {
            if (durationMs > 0L) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatTime(progressMs),
                        style = SvType.MonoTabular.copy(fontSize = 11.sp),
                        color = sv.ink2,
                    )
                    Spacer(modifier = Modifier.padding(start = SvSpace.s3))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(sv.rule),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(ratio)
                                .height(1.dp)
                                .background(sv.accent),
                        )
                    }
                    Spacer(modifier = Modifier.padding(start = SvSpace.s3))
                    Text(
                        text = formatTime(durationMs),
                        style = SvType.MonoTabular.copy(fontSize = 11.sp),
                        color = sv.ink3,
                    )
                }
                Spacer(modifier = Modifier.height(SvSpace.s2))
            }
            Text(
                text = "tap × to exit fullscreen",
                style = SvType.BodyItalic.copy(fontSize = 13.sp),
                color = sv.ink3,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
