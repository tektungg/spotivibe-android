package com.tglabs.spotivibe.ui.component

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.tglabs.spotivibe.domain.CoverSwipe
import com.tglabs.spotivibe.domain.PlayerControls
import com.tglabs.spotivibe.domain.allowedSwipe
import com.tglabs.spotivibe.domain.swipeAction

/** Aksi gestur cover, plus apa yang diizinkan player. */
data class CoverActions(
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onTogglePlayPause: () -> Unit,
    val controls: PlayerControls,
)

/** Geseran sejauh ini baru dihitung pindah lagu. */
private val SWIPE_THRESHOLD = 48.dp

/**
 * Geser horizontal untuk pindah lagu, ketuk dua kali untuk play/pause. Tanpa
 * [actions] modifier ini tidak melakukan apa-apa. Gerakan yang tidak diizinkan
 * player (lihat [PlayerControls]) diabaikan, sama seperti tombolnya yang redup.
 */
fun Modifier.coverGestures(actions: CoverActions?): Modifier {
    if (actions == null) return this
    return this
        .pointerInput(actions) {
            var total = 0f
            detectHorizontalDragGestures(
                onDragStart = { total = 0f },
                onDragEnd = {
                    when (allowedSwipe(swipeAction(total, SWIPE_THRESHOLD.toPx()), actions.controls)) {
                        CoverSwipe.Next -> actions.onNext()
                        CoverSwipe.Previous -> actions.onPrevious()
                        null -> Unit
                    }
                },
                onHorizontalDrag = { change, dx ->
                    change.consume()
                    total += dx
                },
            )
        }
        .pointerInput(actions) {
            detectTapGestures(
                onDoubleTap = { if (actions.controls.canPlayPause) actions.onTogglePlayPause() },
            )
        }
}
