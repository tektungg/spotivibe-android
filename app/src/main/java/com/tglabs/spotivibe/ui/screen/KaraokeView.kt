package com.tglabs.spotivibe.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tglabs.spotivibe.domain.LyricsResult
import com.tglabs.spotivibe.ui.component.LyricsList

/**
 * Karaoke fullscreen — lyrics fill seluruh layar dengan font 2x lebih besar.
 * Hide system bars (status + navigation) selama mode aktif, restore saat exit.
 * Exit via back gesture atau tap X button di pojok kanan atas.
 */
@Composable
fun KaraokeView(
    lyrics: LyricsResult?,
    progressMs: Long,
    accent: Color,
    romaji: Map<Long, String?>,
    fontSize: Int,
    onExit: () -> Unit,
) {
    val view = LocalView.current

    // Hide system bars saat composable hidup. Auto-restored saat exit (composable
    // leaves composition → SideEffect doesn't run, controller resets di parent).
    SideEffect {
        if (!view.isInEditMode) {
            val window = (view.context as android.app.Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    BackHandler(onBack = onExit)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Lyrics — font 1.7x lebih besar dari setting normal. Accent
        // diread dari LocalSvColors (provided by SpotivibeTheme caller).
        LyricsList(
            lyrics = lyrics,
            progressMs = progressMs,
            romaji = romaji,
            fontSize = (fontSize * 1.7f).toInt().coerceAtMost(56),
            modifier = Modifier.fillMaxSize(),
        )

        // Exit button — fixed top-right, minimal & subtle
        IconButton(
            onClick = onExit,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 24.dp, end = 16.dp)
                .size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.FullscreenExit,
                contentDescription = "Exit karaoke",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.size(28.dp),
            )
        }
    }
}
