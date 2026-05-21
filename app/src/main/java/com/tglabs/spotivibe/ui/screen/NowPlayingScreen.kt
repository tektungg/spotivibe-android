package com.tglabs.spotivibe.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tglabs.spotivibe.domain.UiState
import com.tglabs.spotivibe.ui.component.LyricsList
import com.tglabs.spotivibe.ui.theme.AccentDefault
import com.tglabs.spotivibe.ui.theme.BorderHairline
import com.tglabs.spotivibe.ui.theme.SurfaceFrost
import com.tglabs.spotivibe.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun NowPlayingScreen(
    state: UiState.Playing,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleRomanization: () -> Unit,
    onToggleOverlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.track
    val targetAccent = state.accentColor ?: AccentDefault
    val accent by animateColorAsState(
        targetValue = targetAccent,
        animationSpec = tween(durationMillis = 500),
        label = "accent",
    )

    val displayProgressMs by produceState(initialValue = track.progressMs, track) {
        value = track.progressMs
        if (!track.isPaused) {
            val baseline = track.progressMs
            val baselineTime = System.currentTimeMillis()
            while (isActive) {
                delay(200)
                val elapsed = System.currentTimeMillis() - baselineTime
                value = (baseline + elapsed).coerceAtMost(track.durationMs)
            }
        }
    }

    var draggingValue by remember(track.id) { mutableLongStateOf(-1L) }
    val effectiveProgressMs = if (draggingValue >= 0L) draggingValue else displayProgressMs

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 32.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HeaderArea(
            bitmap = state.albumBitmap,
            title = track.title,
            artist = track.artist,
            accent = accent,
            showRomajiToggle = state.canRomanize,
            romajiEnabled = state.romanizationEnabled,
            overlayEnabled = state.overlayEnabled,
            onToggleRomanization = onToggleRomanization,
            onToggleOverlay = onToggleOverlay,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.weight(1f, fill = true).fillMaxWidth()) {
            LyricsList(
                lyrics = state.lyrics,
                progressMs = effectiveProgressMs,
                accent = accent,
                romaji = state.romaji,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        ControlsBar(
            progressMs = effectiveProgressMs,
            durationMs = track.durationMs,
            isPaused = track.isPaused,
            accent = accent,
            onDrag = { draggingValue = it },
            onSeek = { positionMs ->
                draggingValue = -1L
                onSeek(positionMs)
            },
            onTogglePlayPause = onTogglePlayPause,
            onNext = onNext,
            onPrevious = onPrevious,
        )
    }
}

@Composable
private fun HeaderArea(
    bitmap: android.graphics.Bitmap?,
    title: String,
    artist: String,
    accent: Color,
    showRomajiToggle: Boolean,
    romajiEnabled: Boolean,
    overlayEnabled: Boolean,
    onToggleRomanization: () -> Unit,
    onToggleOverlay: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Cover crossfade — bitmap berubah → 400ms cross-fade smooth
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceFrost),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(targetState = bitmap, animationSpec = tween(400), label = "cover") { bmp ->
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Album cover",
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.High,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "♪", style = MaterialTheme.typography.headlineMedium, color = accent)
                    }
                }
            }
        }

        // Title slide-in + fade saat track ganti
        Column(modifier = Modifier.weight(1f)) {
            AnimatedContent(
                targetState = title.ifBlank { "Untitled" },
                transitionSpec = {
                    (slideInVertically(tween(300)) { it / 3 } + fadeIn(tween(300)))
                        .togetherWith(slideOutVertically(tween(200)) { -it / 3 } + fadeOut(tween(200)))
                },
                label = "title",
            ) { t ->
                Text(
                    text = t,
                    style = MaterialTheme.typography.titleLarge,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            AnimatedContent(
                targetState = artist.ifBlank { "Unknown artist" },
                transitionSpec = {
                    (fadeIn(tween(400, delayMillis = 100)))
                        .togetherWith(fadeOut(tween(200)))
                },
                label = "artist",
            ) { a ->
                Text(
                    text = a,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Translate icon — cuma muncul kalau lirik mengandung non-Latin script
        if (showRomajiToggle) {
            IconToggleButton(
                enabled = romajiEnabled,
                accent = accent,
                onClick = onToggleRomanization,
                icon = Icons.Filled.Translate,
                description = "Toggle romanization",
            )
        }

        // Overlay icon — selalu muncul. State ON = lirik melayang di atas app lain
        IconToggleButton(
            enabled = overlayEnabled,
            accent = accent,
            onClick = onToggleOverlay,
            icon = Icons.Filled.PictureInPictureAlt,
            description = "Toggle floating overlay",
        )
    }
}

@Composable
private fun IconToggleButton(
    enabled: Boolean,
    accent: Color,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
) {
    val bgColor by animateColorAsState(
        targetValue = if (enabled) accent else Color.Transparent,
        animationSpec = tween(durationMillis = 250),
        label = "toggleBg",
    )
    val iconColor by animateColorAsState(
        targetValue = if (enabled) Color.Black else MaterialTheme.colorScheme.onBackground,
        animationSpec = tween(durationMillis = 250),
        label = "toggleIcon",
    )
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bgColor)
            .border(1.dp, if (enabled) accent else BorderHairline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = iconColor,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ControlsBar(
    progressMs: Long,
    durationMs: Long,
    isPaused: Boolean,
    accent: Color,
    onDrag: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val safeDuration = durationMs.coerceAtLeast(1L)
        val ratio = (progressMs.toFloat() / safeDuration).coerceIn(0f, 1f)
        Slider(
            value = ratio,
            onValueChange = { newRatio ->
                onDrag((newRatio * safeDuration).toLong())
            },
            onValueChangeFinished = {
                onSeek((ratio * safeDuration).toLong())
            },
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.15f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatMs(progressMs),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
            )
            Text(
                text = formatMs(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "Previous",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(modifier = Modifier.size(8.dp))
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(64.dp)) {
                    Icon(
                        imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (isPaused) "Play" else "Pause",
                        tint = Color.Black,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.size(8.dp))
            IconButton(onClick = onNext) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
