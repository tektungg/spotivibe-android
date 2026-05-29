package com.tglabs.spotivibe.ui.component

import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.tglabs.spotivibe.ui.theme.LocalSvColors

/**
 * Editorial ambient background untuk NowPlayingScreen / KaraokeView.
 * Tiga layer stack:
 *
 * 1. Blurred album art (60dp blur + saturate 1.3 untuk dark, 80dp +
 *    sat 0.5 + brightness 1.6 untuk light). Skala 1.15x supaya edge
 *    blur tidak terlihat.
 * 2. Halftone dot screen — radial-gradient 1px dots dengan spacing 4px,
 *    multiply blend. Memberi visual quote "print magazine".
 * 3. Vertikal gradient — di dark mode: bawah lebih gelap (0.35 → 0.75
 *    black). Di light mode: warm-paper veil (0.55 → 0.85 paper alpha).
 *
 * `highContrast = true` bypass blur + halftone, return solid bg color —
 * untuk outdoor readability (max contrast).
 */
@Composable
fun AmbientBg(
    bitmap: Bitmap?,
    highContrast: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val sv = LocalSvColors.current
    val isDark = sv.isDark

    Box(modifier = modifier.fillMaxSize()) {
        // ── High contrast: solid bg, skip semua effect ──
        if (highContrast || bitmap == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(sv.bg0),
            )
            return
        }

        // ── Layer 1: blurred album art ──
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val blurRadius = if (isDark) 60f else 80f
                        renderEffect = RenderEffect
                            .createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
                            .asComposeRenderEffect()
                        scaleX = 1.15f
                        scaleY = 1.15f
                        alpha = if (isDark) 1f else 0.35f
                    },
            )
        } else {
            // Pre-S fallback: heavily darken without blur (still better than nothing)
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.18f },
            )
        }

        // ── Layer 2: halftone dot screen ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val dotColor = if (isDark) Color.Black.copy(alpha = 0.55f)
                                   else Color.Black.copy(alpha = 0.32f)
                    val spacingPx = 4.dp.toPx()
                    val dotRadiusPx = (if (isDark) 1f else 0.8f).dp.toPx()
                    onDrawBehind {
                        // Draw dots in a grid. Multiply blend simulated via semi-
                        // opaque black on top — close enough at this density.
                        var y = 0f
                        while (y < size.height) {
                            var x = 0f
                            while (x < size.width) {
                                drawCircle(
                                    color = dotColor,
                                    radius = dotRadiusPx,
                                    center = androidx.compose.ui.geometry.Offset(x, y),
                                )
                                x += spacingPx
                            }
                            y += spacingPx
                        }
                    }
                },
        )

        // ── Layer 3: vertical gradient veil ──
        // 4-stop: gelap di TOP (header area readability) + BOTTOM (transport)
        // dengan bagian tengah (lyrics) lebih terang supaya album art tetap
        // terlihat. Tanpa top-scrim ini, header susah dibaca saat album art
        // dominan warna terang.
        val gradientStops = if (isDark) {
            arrayOf(
                0.0f to Color.Black.copy(alpha = 0.62f),  // top — header
                0.16f to Color.Black.copy(alpha = 0.32f),
                0.55f to Color.Black.copy(alpha = 0.42f),
                1.0f to Color.Black.copy(alpha = 0.78f),  // bottom — transport
            )
        } else {
            arrayOf(
                0.0f to sv.bg0.copy(alpha = 0.78f),
                0.16f to sv.bg0.copy(alpha = 0.55f),
                0.55f to sv.bg0.copy(alpha = 0.62f),
                1.0f to sv.bg0.copy(alpha = 0.88f),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(colorStops = gradientStops)),
        )
    }
}
