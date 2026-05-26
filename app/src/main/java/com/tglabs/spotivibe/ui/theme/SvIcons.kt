package com.tglabs.spotivibe.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Hairline / monoline icon set. Stroke width 1 (or 1.5 untuk emphasis),
 * round line caps + joins. Replace Material filled icons di seluruh UI.
 *
 * SVG paths di-port dari claude-design-handoff/screens-shared.jsx Icon().
 * ViewBox 24×24, defaultSize 24×24, di-resize via Modifier.size().
 *
 * Convention:
 * - Stroke icons (close, back, gear, etc) → stroke = current color, fill = none.
 * - Filled icons (play, pause, prev, next dots, signal) → fill = current color.
 *   "Mono filled" → tetap minimalis tapi solid shape karena hairline play
 *   ambiguous saat kecil.
 */
object SvIcons {

    val Play: ImageVector by lazy {
        builder("Play")
            .filledPath { moveTo(7f, 4f); lineTo(7f, 20f); lineTo(20f, 12f); close() }
            .build()
    }

    val Pause: ImageVector by lazy {
        builder("Pause")
            .filledPath { moveTo(6f, 5f); horizontalLineToRelative(3.5f); verticalLineToRelative(14f); horizontalLineToRelative(-3.5f); close() }
            .filledPath { moveTo(14.5f, 5f); horizontalLineToRelative(3.5f); verticalLineToRelative(14f); horizontalLineToRelative(-3.5f); close() }
            .build()
    }

    val Prev: ImageVector by lazy {
        builder("Prev")
            .filledPath { moveTo(19f, 4f); lineTo(8f, 12f); lineTo(19f, 20f); close() }
            .strokePath(strokeWidth = 1.5f) { moveTo(6f, 4f); verticalLineToRelative(16f) }
            .build()
    }

    val Next: ImageVector by lazy {
        builder("Next")
            .filledPath { moveTo(5f, 4f); lineTo(16f, 12f); lineTo(5f, 20f); close() }
            .strokePath(strokeWidth = 1.5f) { moveTo(18f, 4f); verticalLineToRelative(16f) }
            .build()
    }

    val Close: ImageVector by lazy {
        builder("Close")
            .strokePath { moveTo(5f, 5f); lineTo(19f, 19f) }
            .strokePath { moveTo(19f, 5f); lineTo(5f, 19f) }
            .build()
    }

    val Back: ImageVector by lazy {
        builder("Back")
            .strokePath { moveTo(14f, 5f); lineTo(7f, 12f); lineTo(14f, 19f) }
            .strokePath { moveTo(7f, 12f); lineTo(20f, 12f) }
            .build()
    }

    val Fullscreen: ImageVector by lazy {
        builder("Fullscreen")
            .strokePath { moveTo(4f, 9f); verticalLineTo(4f); horizontalLineToRelative(5f) }
            .strokePath { moveTo(20f, 9f); verticalLineTo(4f); horizontalLineToRelative(-5f) }
            .strokePath { moveTo(4f, 15f); verticalLineTo(20f); horizontalLineToRelative(5f) }
            .strokePath { moveTo(20f, 15f); verticalLineTo(20f); horizontalLineToRelative(-5f) }
            .build()
    }

    val Gear: ImageVector by lazy {
        builder("Gear")
            .strokePath {
                // approx circle r=3
                moveTo(15f, 12f)
                arcTo(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, 9f, 12f)
                arcTo(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, 15f, 12f)
            }
            .strokePath { moveTo(12f, 2f); verticalLineToRelative(3f) }
            .strokePath { moveTo(12f, 19f); verticalLineToRelative(3f) }
            .strokePath { moveTo(2f, 12f); horizontalLineToRelative(3f) }
            .strokePath { moveTo(19f, 12f); horizontalLineToRelative(3f) }
            .build()
    }

    val Sun: ImageVector by lazy {
        builder("Sun")
            .strokePath {
                moveTo(16f, 12f)
                arcTo(4f, 4f, 0f, isMoreThanHalf = true, isPositiveArc = true, 8f, 12f)
                arcTo(4f, 4f, 0f, isMoreThanHalf = true, isPositiveArc = true, 16f, 12f)
            }
            .strokePath { moveTo(12f, 2f); verticalLineToRelative(2f) }
            .strokePath { moveTo(12f, 20f); verticalLineToRelative(2f) }
            .strokePath { moveTo(2f, 12f); horizontalLineToRelative(2f) }
            .strokePath { moveTo(20f, 12f); horizontalLineToRelative(2f) }
            .build()
    }

    val Moon: ImageVector by lazy {
        builder("Moon")
            .strokePath { moveTo(20f, 14.5f); arcToRelative(8f, 8f, 0f, isMoreThanHalf = true, isPositiveArc = true, -10.5f, -10.5f); arcToRelative(6.5f, 6.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 10.5f, 10.5f); close() }
            .build()
    }

    val Logout: ImageVector by lazy {
        builder("Logout")
            .strokePath { moveTo(9f, 4f); horizontalLineTo(4f); verticalLineTo(20f); horizontalLineTo(9f) }
            .strokePath { moveTo(16f, 16f); lineTo(20f, 12f); lineTo(16f, 8f) }
            .strokePath { moveTo(9f, 12f); horizontalLineTo(20f) }
            .build()
    }

    val External: ImageVector by lazy {
        builder("External")
            .strokePath { moveTo(14f, 4f); horizontalLineTo(20f); verticalLineToRelative(6f) }
            .strokePath { moveTo(20f, 4f); lineTo(11f, 13f) }
            .strokePath { moveTo(18f, 14f); verticalLineTo(19f); horizontalLineTo(5f); verticalLineTo(6f); horizontalLineTo(10f) }
            .build()
    }

    val Check: ImageVector by lazy {
        builder("Check")
            .strokePath(strokeWidth = 1.6f) { moveTo(4f, 12f); lineTo(9f, 17f); lineTo(20f, 6f) }
            .build()
    }

    val Share: ImageVector by lazy {
        builder("Share")
            .strokePath { moveTo(8f, 12f); lineTo(16f, 6f) }
            .strokePath { moveTo(8f, 12f); lineTo(16f, 18f) }
            .strokePath { moveTo(5f, 12f); arcToRelative(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, -6f, 0f); arcToRelative(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, 6f, 0f); close() }
            .strokePath { moveTo(22f, 6f); arcToRelative(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, -6f, 0f); arcToRelative(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, 6f, 0f); close() }
            .strokePath { moveTo(22f, 18f); arcToRelative(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, -6f, 0f); arcToRelative(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, 6f, 0f); close() }
            .build()
    }

    val Github: ImageVector by lazy {
        builder("Github")
            .strokePath(strokeWidth = 1.4f) {
                moveTo(9f, 19f); curveTo(5f, 20.5f, 5f, 17f, 3f, 16.5f)
            }
            .strokePath(strokeWidth = 1.4f) {
                moveTo(15f, 21f); verticalLineTo(17.5f)
                curveTo(15f, 16.6f, 14.6f, 15.7f, 14.1f, 15.2f)
                curveTo(17.07f, 14.9f, 20.1f, 13.7f, 20.1f, 8.7f)
                curveTo(20.1f, 7.4f, 19.6f, 6.2f, 18.8f, 5.4f)
                curveTo(19f, 4.4f, 19f, 3.3f, 18.7f, 2.2f)
                curveTo(18.7f, 2.2f, 17.7f, 1.9f, 15.3f, 3.5f)
                curveTo(13.4f, 3f, 11.4f, 3f, 9.5f, 3.5f)
                curveTo(7.1f, 1.9f, 6.1f, 2.2f, 6.1f, 2.2f)
                curveTo(5.8f, 3.3f, 5.8f, 4.4f, 6f, 5.4f)
                curveTo(5.2f, 6.2f, 4.7f, 7.4f, 4.7f, 8.7f)
                curveTo(4.7f, 13.7f, 7.7f, 14.9f, 10.7f, 15.2f)
                curveTo(10.2f, 15.7f, 9.8f, 16.6f, 9.8f, 17.5f)
                verticalLineTo(21f)
            }
            .build()
    }

    val Kebab: ImageVector by lazy {
        builder("Kebab")
            .filledPath { dotAt(12f, 5f) }
            .filledPath { dotAt(12f, 12f) }
            .filledPath { dotAt(12f, 19f) }
            .build()
    }

    val Drag: ImageVector by lazy {
        builder("Drag")
            .filledPath { dotAt(9f, 6f) }
            .filledPath { dotAt(15f, 6f) }
            .filledPath { dotAt(9f, 12f) }
            .filledPath { dotAt(15f, 12f) }
            .filledPath { dotAt(9f, 18f) }
            .filledPath { dotAt(15f, 18f) }
            .build()
    }

    val Pip: ImageVector by lazy {
        builder("PiP")
            .strokePath {
                moveTo(3f, 4f); horizontalLineTo(21f); verticalLineTo(20f); horizontalLineTo(3f); close()
            }
            .filledPath {
                moveTo(11f, 11f); horizontalLineTo(20f); verticalLineTo(18f); horizontalLineTo(11f); close()
            }
            .build()
    }

    // ─── helpers ───────────────────────────────────────────────
    private fun builder(name: String) = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )

    private fun ImageVector.Builder.strokePath(
        strokeWidth: Float = 1f,
        block: PathBuilder.() -> Unit,
    ): ImageVector.Builder = path(
        pathFillType = PathFillType.NonZero,
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = strokeWidth,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        strokeLineMiter = 4f,
        pathBuilder = block,
    )

    private fun ImageVector.Builder.filledPath(
        block: PathBuilder.() -> Unit,
    ): ImageVector.Builder = path(
        pathFillType = PathFillType.NonZero,
        fill = SolidColor(Color.Black),
        stroke = null,
        strokeLineWidth = 0f,
        pathBuilder = block,
    )

    private fun PathBuilder.dotAt(cx: Float, cy: Float, r: Float = 1.2f) {
        moveTo(cx - r, cy)
        arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, 2 * r, 0f)
        arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, -2 * r, 0f)
        close()
    }
}
