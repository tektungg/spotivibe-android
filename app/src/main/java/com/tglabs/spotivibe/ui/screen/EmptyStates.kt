package com.tglabs.spotivibe.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.tglabs.spotivibe.ui.component.AccentCtaButton
import com.tglabs.spotivibe.ui.component.HairlineRule
import com.tglabs.spotivibe.ui.component.MonoEyebrow
import com.tglabs.spotivibe.ui.theme.LocalSvColors
import com.tglabs.spotivibe.ui.theme.SvIcons
import com.tglabs.spotivibe.ui.theme.SvSpace
import com.tglabs.spotivibe.ui.theme.SvType
import java.util.Calendar

/**
 * Editorial empty / error state covers. Setiap state = magazine page:
 * - Top: mono accent eyebrow + hairline rule
 * - Center: massive Instrument Serif display headline (multi-line) +
 *   italic Newsreader body paragraph
 * - Bottom: optional accent CTA
 * - Footer: mono error code (NP-002 / 404 / 403 / 429) + brand line
 *
 * Reference: claude-design-handoff/screens-settings.jsx EmptyState().
 */

enum class EmptyKind {
    /** Idle — connected but no track playing. */
    Idle,
    /** LRCLIB lookup returned nothing. */
    NotFound,
    /** Free Spotify tier — can't control playback. */
    Premium,
    /** Rate limit / reconnecting Spotify session. */
    RateLimit,
}

@Composable
fun EmptyState(
    kind: EmptyKind,
    onCta: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val sv = LocalSvColors.current
    val data = stateData(kind)
    val year = Calendar.getInstance().get(Calendar.YEAR)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(sv.bg0)
            .padding(horizontal = SvSpace.s5),
    ) {
        Spacer(modifier = Modifier.height(SvSpace.s10))

        // Top eyebrow + hairline
        MonoEyebrow(text = data.eyebrow, color = sv.accent)
        Spacer(modifier = Modifier.height(SvSpace.s2))
        HairlineRule(soft = true)

        // Hero — multi-line display
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = data.headline,
                style = SvType.Display.copy(
                    fontSize = 72.sp,
                    lineHeight = 68.sp,
                    letterSpacing = (-2.2).sp,
                ),
                color = sv.ink1,
            )
            Spacer(modifier = Modifier.height(SvSpace.s4))
            Text(
                text = data.body,
                style = SvType.BodyItalic.copy(fontSize = 17.sp, lineHeight = 24.sp),
                color = sv.ink3,
                modifier = Modifier.padding(end = SvSpace.s10),
            )

            if (data.cta.isNotBlank() && onCta != null) {
                Spacer(modifier = Modifier.height(SvSpace.s8))
                AccentCtaButton(
                    label = data.cta,
                    onClick = onCta,
                    trailingIcon = SvIcons.Next,
                )
            }
        }

        // Footer — mono error code + brand
        HairlineRule(soft = true)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = SvSpace.s3),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MonoEyebrow(text = data.code)
            MonoEyebrow(text = "SPOTIVIBE · $year")
        }
        Spacer(modifier = Modifier.height(SvSpace.s6))
    }
}

private data class EmptyStateData(
    val eyebrow: String,
    val headline: String,
    val body: String,
    val cta: String,
    val code: String,
)

private fun stateData(kind: EmptyKind): EmptyStateData = when (kind) {
    EmptyKind.Idle -> EmptyStateData(
        eyebrow  = "STATUS · IDLE",
        headline = "Nothing\nplaying.",
        body     = "Open Spotify and play anything. Spotivibe pulls the lyrics automatically.",
        cta      = "",
        code     = "NP-002",
    )
    EmptyKind.NotFound -> EmptyStateData(
        eyebrow  = "ERR · NO LYRICS",
        headline = "Silent\nedition.",
        body     = "LRCLIB has no lyrics for this track yet. You can request it or contribute the timing.",
        cta      = "",
        code     = "404",
    )
    EmptyKind.Premium -> EmptyStateData(
        eyebrow  = "AUTH · UPGRADE",
        headline = "Free tier\ncan only watch.",
        body     = "Spotify Premium is required for playback control. Lyrics still show while a Premium device is playing.",
        cta      = "GO TO SPOTIFY",
        code     = "403",
    )
    EmptyKind.RateLimit -> EmptyStateData(
        eyebrow  = "WAIT · REFRESHING",
        headline = "A short\nintermission.",
        body     = "Reconnecting ke Spotify session. Cuma sebentar.",
        cta      = "",
        code     = "429",
    )
}

@Suppress("unused")
private val _textAlignSentinel = TextAlign.Start
