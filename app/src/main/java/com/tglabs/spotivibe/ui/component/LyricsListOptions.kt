package com.tglabs.spotivibe.ui.component

import com.tglabs.spotivibe.domain.UiState

/**
 * [LyricsListOptions] dari setelan user di [UiState.Playing]. Ukuran font dan
 * jarak baris tetap dari pemanggil, karena portrait dan landscape menskalakan
 * keduanya berbeda.
 */
fun UiState.Playing.lyricsOptions(fontSize: Int, lineSpacing: Int): LyricsListOptions = LyricsListOptions(
    fontSize = fontSize,
    lineSpacing = lineSpacing,
    highContrast = highContrast,
    smoothScroll = smoothScroll,
    hapticEnabled = hapticEnabled,
    isPlaying = !track.isPaused,
    lyricEffects = lyricEffects,
    notice = lyricsNotice,
)
