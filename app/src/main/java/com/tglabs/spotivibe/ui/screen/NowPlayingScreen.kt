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
import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.ui.unit.sp
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

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = if (isLandscape) 16.dp else 32.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (isLandscape) {
            // ── Landscape: cover/meta kolom kiri + lyrics kolom kanan ──
            Row(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Left: vertical cover + meta + icons (fixed ~240dp wide)
                LandscapeSidebar(
                    bitmap = state.albumBitmap,
                    title = track.title,
                    artist = track.artist,
                    accent = accent,
                    showRomajiToggle = state.canRomanize,
                    romajiEnabled = state.romanizationEnabled,
                    overlayEnabled = state.overlayEnabled,
                    onToggleRomanization = onToggleRomanization,
                    onToggleOverlay = onToggleOverlay,
                    modifier = Modifier.width(180.dp).fillMaxHeight(),
                )

                // Right: lyrics fills remaining
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    LyricsList(
                        lyrics = state.lyrics,
                        progressMs = effectiveProgressMs,
                        accent = accent,
                        romaji = state.romaji,
                    )
                }
            }
        } else {
            // ── Portrait: header row di atas, lyrics di tengah ──
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
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ── Controls full-width di bawah (both orientations) ──
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

/**
 * Landscape sidebar: cover gede di atas, title + artist, toolbar icons.
 * Layout vertikal — content stack ke bawah. Width fixed 240dp dari parent.
 */
@Composable
private fun LandscapeSidebar(
    bitmap: android.graphics.Bitmap?,
    title: String,
    artist: String,
    accent: Color,
    showRomajiToggle: Boolean,
    romajiEnabled: Boolean,
    overlayEnabled: Boolean,
    onToggleRomanization: () -> Unit,
    onToggleOverlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Cover compact 140dp
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceFrost),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(targetState = bitmap, animationSpec = tween(400), label = "cover_landscape") { bmp ->
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
                        Text(text = "♪", style = MaterialTheme.typography.headlineSmall, color = accent)
                    }
                }
            }
        }

        // Title + artist compact (centered)
        AnimatedContent(
            targetState = title.ifBlank { "Untitled" },
            transitionSpec = {
                (slideInVertically(tween(300)) { it / 3 } + fadeIn(tween(300)))
                    .togetherWith(slideOutVertically(tween(200)) { -it / 3 } + fadeOut(tween(200)))
            },
            label = "title_landscape",
        ) { t ->
            Text(
                text = t,
                style = MaterialTheme.typography.titleMedium,
                fontSize = 15.sp,
                color = accent,
                fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AnimatedContent(
            targetState = artist.ifBlank { "Unknown artist" },
            transitionSpec = { fadeIn(tween(400, delayMillis = 100)).togetherWith(fadeOut(tween(200))) },
            label = "artist_landscape",
        ) { a ->
            Text(
                text = a,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Icons row — translate + overlay
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showRomajiToggle) {
                IconToggleButton(
                    enabled = romajiEnabled,
                    accent = accent,
                    onClick = onToggleRomanization,
                    icon = Icons.Filled.Translate,
                    description = "Toggle romanization",
                )
            }
            IconToggleButton(
                enabled = overlayEnabled,
                accent = accent,
                onClick = onToggleOverlay,
                icon = Icons.Filled.PictureInPictureAlt,
                description = "Toggle floating overlay",
            )
        }
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
        // Cover crossfade — compact 56dp
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(10.dp))
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
                        Text(text = "♪", style = MaterialTheme.typography.titleMedium, color = accent)
                    }
                }
            }
        }

        // Title slide-in + fade saat track ganti — compact titleMedium 15sp
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
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 15.sp,
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val safeDuration = durationMs.coerceAtLeast(1L)
        val ratio = (progressMs.toFloat() / safeDuration).coerceIn(0f, 1f)

        // ── Row 1: time-left | slider flex | time-right (compact inline) ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = formatMs(progressMs),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier.width(38.dp),
            )
            Slider(
                value = ratio,
                onValueChange = { newRatio -> onDrag((newRatio * safeDuration).toLong()) },
                onValueChangeFinished = { onSeek((ratio * safeDuration).toLong()) },
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                ),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatMs(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier.width(38.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }

        // ── Row 2: buttons (smaller: 24dp skip, 44dp play) ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "Previous",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(modifier = Modifier.size(12.dp))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (isPaused) "Play" else "Pause",
                        tint = Color.Black,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.size(12.dp))
            IconButton(onClick = onNext, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(26.dp),
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
