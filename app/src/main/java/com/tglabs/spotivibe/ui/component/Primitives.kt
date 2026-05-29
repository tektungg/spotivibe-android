package com.tglabs.spotivibe.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tglabs.spotivibe.ui.theme.LocalSvColors
import com.tglabs.spotivibe.ui.theme.SvMotion
import com.tglabs.spotivibe.ui.theme.SvRadius
import com.tglabs.spotivibe.ui.theme.SvSpace
import com.tglabs.spotivibe.ui.theme.SvType
import kotlin.math.roundToInt

/**
 * Editorial Magazine layout primitives. Mengganti Material3 chrome
 * (Switch, Slider, FilterChip, pill Button) dengan hairline-bordered,
 * mono-labeled, square-corner alternatives.
 *
 * Semua membaca dari LocalSvColors.current — accent dinamis per-track
 * tetap propagate.
 */

// ─── HairlineRule ──────────────────────────────────────────
/**
 * 1dp horizontal divider. `soft = true` pakai RuleSoft (lebih dim).
 * Pakai untuk separator section, antara seek bar dan transport, dll.
 */
@Composable
fun HairlineRule(
    modifier: Modifier = Modifier,
    soft: Boolean = false,
) {
    val sv = LocalSvColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(if (soft) sv.ruleSoft else sv.rule),
    )
}

// ─── MonoEyebrow ───────────────────────────────────────────
/**
 * Uppercase mono micro-label (11sp, 0.12em tracking). Pakai sebagai
 * eyebrow di atas section title, status indicator, error code, dll.
 *
 * `color` default = InkDark3 (mid-gray) tapi sering di-override ke
 * accent saat ingin highlight (e.g. "STATUS · IDLE").
 */
@Composable
fun MonoEyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    val sv = LocalSvColors.current
    Text(
        text = text.uppercase(),
        style = SvType.MonoUp,
        color = color ?: sv.ink3,
        maxLines = maxLines,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

// ─── SegControl ────────────────────────────────────────────
/**
 * Hairline-bordered segmented control. Active segment = accent fill
 * dengan accentInk text. Inactive = transparent dengan ink2 text.
 * Pakai untuk theme toggle (Dark/Light), scroll mode (Smooth/Snap), dll.
 */
@Composable
fun SegControl(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sv = LocalSvColors.current
    Row(
        modifier = modifier
            // clip DULU sebelum border supaya background accent segment aktif
            // ter-clip ke rounded shape — tanpa ini fill rectangular bocor
            // keluar di sudut rounded border.
            .clip(RoundedCornerShape(SvRadius.r1))
            .border(1.dp, sv.rule, RoundedCornerShape(SvRadius.r1)),
    ) {
        options.forEachIndexed { idx, opt ->
            val isActive = opt == selected
            val bg by animateColorAsState(
                targetValue = if (isActive) sv.accent else Color.Transparent,
                animationSpec = tween(SvMotion.DurFast, easing = SvMotion.EaseOut),
                label = "segBg",
            )
            val fg by animateColorAsState(
                targetValue = if (isActive) sv.accentInk else sv.ink2,
                animationSpec = tween(SvMotion.DurFast, easing = SvMotion.EaseOut),
                label = "segFg",
            )
            if (idx > 0) {
                Box(modifier = Modifier
                    .width(1.dp)
                    .height(36.dp)
                    .background(sv.rule),
                )
            }
            Box(
                modifier = Modifier
                    .background(bg)
                    .clickable(onClick = { onSelect(opt) })
                    .padding(horizontal = SvSpace.s4, vertical = SvSpace.s2),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = opt.uppercase(),
                    style = SvType.MonoUp,
                    color = fg,
                )
            }
        }
    }
}

// ─── Toggle (custom, no Material elevation) ─────────────────
/**
 * Track 44×22, thumb 18×18. Accent fill saat ON, hairline border saat
 * OFF. Tidak ada elevation / drop shadow — strict editorial.
 */
@Composable
fun Toggle(
    on: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sv = LocalSvColors.current
    val trackBg by animateColorAsState(
        targetValue = if (on) sv.accent else sv.bg3,
        animationSpec = tween(SvMotion.DurFast, easing = SvMotion.EaseOut),
        label = "toggleTrack",
    )
    val thumbColor by animateColorAsState(
        targetValue = if (on) sv.accentInk else sv.ink1,
        animationSpec = tween(SvMotion.DurFast, easing = SvMotion.EaseOut),
        label = "toggleThumb",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (on) 22.dp else 1.dp,
        animationSpec = tween(SvMotion.DurFast, easing = SvMotion.EaseOut),
        label = "toggleOffset",
    )
    Box(
        modifier = modifier
            .size(width = 44.dp, height = 22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(trackBg)
            .border(
                width = if (on) 0.dp else 1.dp,
                color = if (on) Color.Transparent else sv.rule,
                shape = RoundedCornerShape(11.dp),
            )
            .clickable(onClick = { onChange(!on) }),
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset, y = 2.dp)
                .size(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(thumbColor),
        )
    }
}

// ─── TickSlider ────────────────────────────────────────────
/**
 * 21 vertical tick marks. Filled portion = accent. Vertical 2×18 dp handle
 * bar. `twoSided = true` untuk slider yang centered at 0 (mis. sync offset
 * ±2000ms) — filled bar grows outward dari tengah.
 *
 * `value` 0.0-1.0 (normalized). Caller bertanggung jawab mapping ke
 * range domain (12-56sp font, ±2000ms offset, dll).
 */
@Composable
fun TickSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    twoSided: Boolean = false,
    tickCount: Int = 21,
) {
    val sv = LocalSvColors.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(twoSided, tickCount) {
                detectHorizontalDragGestures(
                    onDragStart = { /* tap-anywhere start */ },
                    onHorizontalDrag = { change, _ ->
                        val rel = (change.position.x / size.width).coerceIn(0f, 1f)
                        onValueChange(rel)
                    },
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { /* swallow click — drag handles it */ },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // hairline base track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(sv.rule),
        )

        // Tick marks
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val centerIdx = (tickCount - 1) / 2
            repeat(tickCount) { i ->
                val filled = if (twoSided) {
                    if (value >= 0.5f) i in centerIdx..(centerIdx + ((value - 0.5f) * (tickCount - 1)).roundToInt())
                    else i in (centerIdx - ((0.5f - value) * (tickCount - 1)).roundToInt())..centerIdx
                } else {
                    i.toFloat() / (tickCount - 1) <= value
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(if (filled) 8.dp else 4.dp)
                        .background(if (filled) sv.accent else sv.rule),
                )
            }
        }

        // Vertical handle bar (2×18dp) — positioned via Box layout offset
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.fillMaxWidth(value.coerceIn(0f, 0.99f)))
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(18.dp)
                    .background(sv.ink1),
            )
        }
    }
}

// ─── AccentCtaButton ───────────────────────────────────────
/**
 * Full-width square (r1 = 2dp) button dengan accent fill + accentInk
 * mono uppercase label. Optional trailing icon. Tidak ada elevation,
 * tidak ada Material ripple — fade alpha saat pressed.
 */
@Composable
fun AccentCtaButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val sv = LocalSvColors.current
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.5f,
        animationSpec = tween(SvMotion.DurFast),
        label = "ctaAlpha",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SvRadius.r1))
            .background(sv.accent.copy(alpha = alpha))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = SvSpace.s5, vertical = SvSpace.s5),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label.uppercase(),
            style = SvType.MonoUp.copy(fontSize = 12.sp),
            color = sv.accentInk,
        )
        if (trailingIcon != null) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = sv.accentInk,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// ─── HairlineIconButton ────────────────────────────────────
/**
 * Square 36dp hit area, hairline border opsional. Pakai untuk Rm / pip /
 * kebab / close icons di header — fokus minimal chrome.
 */
@Composable
fun HairlineIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    bordered: Boolean = false,
    size: Dp = 36.dp,
    iconSize: Dp = 18.dp,
    tint: Color? = null,
) {
    val sv = LocalSvColors.current
    Box(
        modifier = modifier
            .size(size)
            .then(
                if (bordered) Modifier.border(1.dp, sv.rule, RoundedCornerShape(size / 2))
                else Modifier
            )
            .clip(RoundedCornerShape(size / 2))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint ?: sv.ink2,
            modifier = Modifier.size(iconSize),
        )
    }
}
