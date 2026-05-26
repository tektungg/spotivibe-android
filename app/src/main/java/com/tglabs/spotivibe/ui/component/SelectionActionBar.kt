package com.tglabs.spotivibe.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tglabs.spotivibe.ui.theme.LocalSvColors
import com.tglabs.spotivibe.ui.theme.SvIcons
import com.tglabs.spotivibe.ui.theme.SvRadius
import com.tglabs.spotivibe.ui.theme.SvSpace
import com.tglabs.spotivibe.ui.theme.SvType

/**
 * Editorial selection mode UI. Dua composables:
 *
 * 1. [SelectionHeader] — replaces NPHeader saat selection mode aktif.
 *    Mono "SELECTION MODE" eyebrow + "N of 5 lines" serif + horizontal
 *    dot pip counter + close X.
 *
 * 2. [SelectionActionBar] — replaces Transport saat selection mode aktif.
 *    Cancel (transparent + hairline) | Share as card (accent fill, mono
 *    uppercase, share icon).
 */

@Composable
fun SelectionHeader(
    count: Int,
    max: Int = 5,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sv = LocalSvColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = SvSpace.s4, end = SvSpace.s4, top = SvSpace.s2, bottom = SvSpace.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SvSpace.s3),
    ) {
        // Close button
        HairlineIconButton(
            onClick = onCancel,
            icon = SvIcons.Close,
            contentDescription = "Cancel selection",
            size = 36.dp,
            iconSize = 18.dp,
        )

        // Title block
        Column(modifier = Modifier.weight(1f)) {
            MonoEyebrow(text = "SELECTION MODE", color = sv.ink3, modifier = Modifier.padding(bottom = 2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$count",
                    style = SvType.H3.copy(fontSize = androidx.compose.ui.unit.TextUnit(20f, androidx.compose.ui.unit.TextUnitType.Sp)),
                    color = sv.ink1,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "of $max lines",
                    style = SvType.BodyItalic.copy(fontSize = androidx.compose.ui.unit.TextUnit(14f, androidx.compose.ui.unit.TextUnitType.Sp)),
                    color = sv.ink3,
                )
            }
        }

        // Dot pip counter — 5 dots, fill = accent if filled, rule otherwise
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(max) { i ->
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (i < count) sv.accent else sv.rule),
                )
            }
        }
    }
}

@Composable
fun SelectionActionBar(
    count: Int,
    onShare: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sv = LocalSvColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        HairlineRule(soft = true)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SvSpace.s4),
            horizontalArrangement = Arrangement.spacedBy(SvSpace.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Cancel button — flex 1, transparent + hairline
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(SvRadius.r1))
                    .background(Color.Transparent)
                    .border(1.dp, sv.rule, RoundedCornerShape(SvRadius.r1))
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "CANCEL",
                    style = SvType.MonoUp.copy(fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp)),
                    color = sv.ink2,
                )
            }

            // Share button — flex 2, accent fill
            val enabled = count > 0
            Box(
                modifier = Modifier
                    .weight(2f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(SvRadius.r1))
                    .background(if (enabled) sv.accent else sv.accent.copy(alpha = 0.3f))
                    .clickable(enabled = enabled, onClick = onShare),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SvSpace.s2),
                ) {
                    Icon(
                        imageVector = SvIcons.Share,
                        contentDescription = null,
                        tint = sv.accentInk,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "SHARE AS CARD",
                        style = SvType.MonoUp.copy(fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp)),
                        color = sv.accentInk,
                    )
                }
            }
        }
    }
}
