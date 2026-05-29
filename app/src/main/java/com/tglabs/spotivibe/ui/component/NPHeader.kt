package com.tglabs.spotivibe.ui.component

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tglabs.spotivibe.ui.theme.LocalSvColors
import com.tglabs.spotivibe.ui.theme.SvIcons
import com.tglabs.spotivibe.ui.theme.SvMotion
import com.tglabs.spotivibe.ui.theme.SvRadius
import com.tglabs.spotivibe.ui.theme.SvSpace
import com.tglabs.spotivibe.ui.theme.SvType

/**
 * Magazine-style NowPlaying header.
 *
 * Grid 3-col: [44dp album thumb] [title block weight 1] [hairline icon row].
 * Title block: mono "ALBUM · YEAR" eyebrow + Instrument Serif title 18sp +
 * italic Newsreader artist 12sp.
 *
 * `albumLabel` opsional override untuk mono eyebrow (mis. "EXCERPT · 2025"
 * di selection mode). Default: derive dari album field.
 */
@Composable
fun NPHeader(
    albumBitmap: Bitmap?,
    title: String,
    artist: String,
    album: String,
    onRomajiClick: () -> Unit,
    onOverlayClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    showRomajiIcon: Boolean = true,
    romajiEnabled: Boolean = false,
    overlayEnabled: Boolean = false,
) {
    val sv = LocalSvColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = SvSpace.s4, end = SvSpace.s4, top = SvSpace.s2, bottom = SvSpace.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SvSpace.s3),
    ) {
        // ── Album thumb 44dp ──
        AlbumThumb(bitmap = albumBitmap, size = 44.dp)

        // ── Title block ──
        Column(modifier = Modifier.weight(1f)) {
            MonoEyebrow(
                // Album sebagai eyebrow. Release year tidak tersedia dari
                // Spotify App Remote PlayerState (cuma name/artist/album/uri/
                // duration), jadi tidak ditampilkan daripada hardcode salah.
                text = album.ifBlank { "NOW PLAYING" },
                color = sv.ink3,
                modifier = Modifier.padding(bottom = 2.dp),
            )
            // Track-change spec: 540ms total. Old fades + slides -20dp over
            // 0-220ms. New crossfades at delayMillis=180ms, slides +10dp→0
            // over 360ms. Easing = SvMotion.EaseOut (cubic-bezier 0.16,1,0.3,1).
            AnimatedContent(
                targetState = title.ifBlank { "Untitled" },
                transitionSpec = {
                    (slideInVertically(
                        tween(360, delayMillis = 180, easing = SvMotion.EaseOut),
                    ) { it / 3 } + fadeIn(
                        tween(360, delayMillis = 180, easing = SvMotion.EaseOut),
                    ))
                        .togetherWith(
                            slideOutVertically(tween(220, easing = SvMotion.EaseOut)) { -it / 3 } +
                                fadeOut(tween(220, easing = SvMotion.EaseOut))
                        )
                },
                label = "title",
            ) { t ->
                Text(
                    text = t,
                    style = SvType.H3.copy(fontSize = 18.sp, lineHeight = 22.sp),
                    color = sv.ink1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AnimatedContent(
                targetState = artist.ifBlank { "Unknown artist" },
                transitionSpec = {
                    fadeIn(tween(360, delayMillis = 180, easing = SvMotion.EaseOut))
                        .togetherWith(fadeOut(tween(220, easing = SvMotion.EaseOut)))
                },
                label = "artist",
            ) { a ->
                Text(
                    text = a,
                    style = SvType.BodyItalic.copy(fontSize = 12.sp),
                    color = sv.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // ── Icon row ──
        Row(
            horizontalArrangement = Arrangement.spacedBy(SvSpace.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showRomajiIcon) {
                HeaderToggleIcon(
                    icon = SvIcons.Check,            // placeholder — Rm icon belum dibuat
                    description = "Toggle romanization",
                    enabled = romajiEnabled,
                    onClick = onRomajiClick,
                )
            }
            HeaderToggleIcon(
                icon = SvIcons.Pip,
                description = "Toggle floating overlay",
                enabled = overlayEnabled,
                onClick = onOverlayClick,
            )
            HairlineIconButton(
                onClick = onMoreClick,
                icon = SvIcons.Kebab,
                contentDescription = "More",
                size = 36.dp,
                iconSize = 16.dp,
            )
        }
    }
}

@Composable
fun AlbumThumb(
    bitmap: Bitmap?,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val sv = LocalSvColors.current
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(SvRadius.r1))
            .background(sv.bg2),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(text = "♪", style = SvType.H3, color = sv.ink3)
        }
    }
}

@Composable
private fun HeaderToggleIcon(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val sv = LocalSvColors.current
    HairlineIconButton(
        onClick = onClick,
        icon = icon,
        contentDescription = description,
        size = 36.dp,
        iconSize = 16.dp,
        tint = if (enabled) sv.accent else sv.ink2,
    )
}
