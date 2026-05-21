package com.tglabs.spotivibe.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.tglabs.spotivibe.ui.theme.BorderHairline
import com.tglabs.spotivibe.ui.theme.SurfaceFrost

/**
 * Reusable frosted glass card — pakai semi-transparent fill + hairline border.
 * Real backdrop-blur (RenderEffect.createBlurEffect) butuh Android 12+ dan
 * material yang lebih kompleks; untuk MVP cukup translucent fill + border.
 */
@Composable
fun FrostedCard(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 20,
    contentPadding: Int = 20,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(SurfaceFrost)
            .padding(contentPadding.dp),
    ) {
        Surface(
            color = androidx.compose.ui.graphics.Color.Transparent,
            border = BorderStroke(1.dp, BorderHairline),
            shape = shape,
            modifier = Modifier.matchParentSize(),
            content = {},
        )
        content()
    }
}
