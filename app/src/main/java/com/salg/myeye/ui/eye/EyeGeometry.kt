package com.salg.myeye.ui.eye

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.util.lerp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Everything the cartoon renderer needs, in canvas pixels. Pure math so it can be unit-tested
 * without drawing anything.
 */
data class EyeGeometry(
    val center: Offset,
    /** Horizontal and vertical sclera radii. */
    val radiusX: Float,
    val radiusY: Float,
    val irisCenter: Offset,
    val irisRadius: Float,
    val pupilRadius: Float,
    /** Scale (≤ 1) applied to the iris along [squashAngleDegrees] to fake a round eyeball. */
    val squash: Float,
    val squashAngleDegrees: Float,
    /** Y of the upper/lower lid curve at the eye's vertical center line. */
    val upperLidY: Float,
    val lowerLidY: Float,
    val highlightLarge: Offset,
    val highlightLargeRadius: Float,
    val highlightSmall: Offset,
    val highlightSmallRadius: Float,
    val outlineWidth: Float,
) {
    /** Lid curves meet the oval's left/right extremes. */
    val leftCorner get() = Offset(center.x - radiusX * CORNER_OVERSHOOT, center.y)
    val rightCorner get() = Offset(center.x + radiusX * CORNER_OVERSHOOT, center.y)

    /** Quadratic control point whose curve between the corners peaks at [apexY]. */
    fun lidControl(apexY: Float) = Offset(center.x, 2f * apexY - center.y)

    companion object {
        const val CORNER_OVERSHOOT = 1.05f
    }
}

/** One floating "Z" of the sleeping eye, in canvas pixels. */
data class ZGlyph(val center: Offset, val size: Float, val alpha: Float)

/**
 * The three Zs drifting up from the sleeping eye's upper right. Anchored to the sclera (never the
 * iris), staggered a third of a cycle apart, each growing as it rises and fading in and out.
 * Empty when [sleep] is 0.
 */
fun zzzGlyphs(geo: EyeGeometry, sleep: Float, phase: Float): List<ZGlyph> {
    if (sleep <= 0f) return emptyList()
    return (0 until 3).map { i ->
        val p = (phase + i / 3f) % 1f
        val sway = sin(p * 2f * PI.toFloat()) * 0.08f * geo.radiusX
        ZGlyph(
            center = Offset(
                geo.center.x + lerp(0.55f, 0.9f, p) * geo.radiusX + sway,
                geo.center.y - lerp(0.75f, 2.1f, p) * geo.radiusY,
            ),
            size = lerp(0.12f, 0.3f, p) * geo.radiusX,
            alpha = sleep.coerceIn(0f, 1f) * sin(p * PI.toFloat()),
        )
    }
}

/** Clamps [gaze] into the unit disk. */
fun clampGaze(gaze: Offset): Offset {
    val d = gaze.getDistance()
    return if (d > 1f) gaze / d else gaze
}

fun eyeGeometry(size: Size, gaze: Offset, expression: EyeExpression, style: EyeStyle): EyeGeometry {
    val center = size.center
    // Fit the oval to the width, but never let it outgrow a short canvas.
    val rx = min(size.width * style.widthFraction / 2f, size.height * 0.45f * style.aspect / 2f)
    val ry = rx / style.aspect

    val g = clampGaze(gaze)
    val reach = g.getDistance()
    val irisCenter = Offset(
        center.x + g.x * style.maxTravel * rx,
        center.y + g.y * style.maxTravel * ry,
    )
    val irisRadius = style.irisFraction * rx
    val pupilFraction = lerp(0.3f, 0.7f, expression.pupil.coerceIn(0f, 1f))

    // Lids: closed means both curves meet on a line just below center.
    val lidOpen = expression.lidOpen.coerceIn(0f, 1f)
    val squint = expression.squint.coerceIn(0f, 1f)
    val closedY = center.y + 0.1f * ry
    var upper = lerp(closedY, center.y - LID_RETRACT * ry, lidOpen) + squint * 0.35f * ry
    var lower = lerp(closedY, center.y + LID_RETRACT * ry, sqrt(lidOpen)) - squint * 0.5f * ry
    if (lower < upper) {
        val mid = (upper + lower) / 2f
        upper = mid
        lower = mid
    }

    return EyeGeometry(
        center = center,
        radiusX = rx,
        radiusY = ry,
        irisCenter = irisCenter,
        irisRadius = irisRadius,
        pupilRadius = irisRadius * pupilFraction,
        squash = 1f - style.maxSquash * reach * reach,
        squashAngleDegrees = Math.toDegrees(atan2(g.y, g.x).toDouble()).toFloat(),
        upperLidY = upper,
        lowerLidY = lower,
        // Highlights are anchored to the sclera, never the iris: that is what makes it look wet.
        highlightLarge = Offset(center.x - 0.3f * rx, center.y - 0.38f * ry),
        highlightLargeRadius = 0.1f * rx,
        highlightSmall = Offset(center.x - 0.46f * rx, center.y - 0.14f * ry),
        highlightSmallRadius = 0.045f * rx,
        outlineWidth = style.outlineFraction * rx,
    )
}

/** How far past the oval a fully retracted lid sits, as a fraction of the vertical radius. */
private const val LID_RETRACT = 1.15f
