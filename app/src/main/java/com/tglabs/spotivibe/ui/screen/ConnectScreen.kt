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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
 * Editorial Connect cover. Magazine masthead — VOL/ISSUE/YEAR strip,
 * hairline rule, massive "Sing along." display in two lines, italic
 * subtitle, full-width accent CTA, bottom colophon.
 *
 * Reference: screens-settings.jsx Connect().
 */
@Composable
fun ConnectScreen(
    isConnecting: Boolean,
    errorMessage: String? = null,
    onConnect: () -> Unit,
) {
    val sv = LocalSvColors.current
    val year = Calendar.getInstance().get(Calendar.YEAR)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(sv.bg0)
            .padding(horizontal = SvSpace.s5),
    ) {
        Spacer(modifier = Modifier.height(SvSpace.s8))

        // ── Masthead ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            MonoEyebrow(text = "VOL.01 · ISSUE 02")
            MonoEyebrow(text = "$year")
        }
        Spacer(modifier = Modifier.height(SvSpace.s3))
        HairlineRule(soft = true)

        // ── Hero ──
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Sing\nalong.",
                style = SvType.Display.copy(
                    fontSize = 84.sp,
                    lineHeight = 80.sp,
                    letterSpacing = (-2.5).sp,
                ),
                color = sv.ink1,
            )
            Spacer(modifier = Modifier.height(SvSpace.s2))
            Text(
                text = "Synced lyrics with romanization for K-pop, J-pop, and Mandopop — over your Spotify session.",
                style = SvType.BodyItalic.copy(fontSize = 18.sp, lineHeight = 24.sp),
                color = sv.ink3,
                modifier = Modifier.padding(end = SvSpace.s12),
            )

            Spacer(modifier = Modifier.height(SvSpace.s8))

            if (isConnecting) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SvSpace.s3),
                ) {
                    CircularProgressIndicator(
                        color = sv.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.padding(2.dp),
                    )
                    Text(
                        text = "Connecting…",
                        style = SvType.MonoUp,
                        color = sv.ink3,
                    )
                }
            } else {
                AccentCtaButton(
                    label = "CONNECT SPOTIFY",
                    onClick = onConnect,
                    trailingIcon = SvIcons.Next,
                )
                Spacer(modifier = Modifier.height(SvSpace.s3))
                Text(
                    text = "Make sure the Spotify app is installed and signed in with a Premium account.",
                    style = SvType.BodyItalic.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = sv.ink3,
                )
            }

            errorMessage?.let { msg ->
                Spacer(modifier = Modifier.height(SvSpace.s5))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = SvSpace.s2),
                ) {
                    Column {
                        MonoEyebrow(text = "ERR · CONNECTION", color = sv.accent)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = msg,
                            style = SvType.BodyItalic.copy(fontSize = 14.sp),
                            color = sv.ink2,
                        )
                    }
                }
            }
        }

        // ── Colophon ──
        Spacer(modifier = Modifier.height(SvSpace.s4))
        HairlineRule(soft = true)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = SvSpace.s3, horizontal = 0.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MonoEyebrow(text = "SPOTIVIBE")
            MonoEyebrow(text = "CODE · VIBE · SING")
        }
        Spacer(modifier = Modifier.height(SvSpace.s6))
    }
}

// Compile sanity: ensures TextAlign import is consumed if needed later.
@Suppress("unused")
private val _textAlignSentinel = TextAlign.Start
