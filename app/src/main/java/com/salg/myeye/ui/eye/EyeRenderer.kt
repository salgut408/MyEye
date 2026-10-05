package com.salg.myeye.ui.eye

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.lerp

/**
 * Draws one eye as a pure function of its inputs. Swap the implementation (e.g. for a realistic
 * shader eye) without touching the animation or state layers.
 */
interface EyeRenderer {
    fun DrawScope.drawEye(gaze: Offset, expression: EyeExpression, style: EyeStyle)
}

object CartoonEyeRenderer : EyeRenderer {
    override fun DrawScope.drawEye(gaze: Offset, expression: EyeExpression, style: EyeStyle) {
        val geo = eyeGeometry(size, gaze, expression, style)
        val oval = Path().apply {
            addOval(Rect(geo.center, geo.radiusX).copy(
                top = geo.center.y - geo.radiusY,
                bottom = geo.center.y + geo.radiusY,
            ))
        }
        val upperLid = lidCurve(geo, geo.upperLidY)
        val lowerLid = lidCurve(geo, geo.lowerLidY)
        val opening = Path().apply {
            moveTo(geo.leftCorner.x, geo.leftCorner.y)
            geo.lidControl(geo.upperLidY).let { quadraticTo(it.x, it.y, geo.rightCorner.x, geo.rightCorner.y) }
            geo.lidControl(geo.lowerLidY).let { quadraticTo(it.x, it.y, geo.leftCorner.x, geo.leftCorner.y) }
            close()
        }
        val aperture = Path.combine(PathOperation.Intersect, oval, opening)

        // Lids (only visible when the lid color differs from the background).
        drawPath(oval, style.lid)

        clipPath(aperture) {
            drawPath(oval, lerp(style.sclera, style.strainedSclera, expression.strain.coerceIn(0f, 1f)))
            // Soft shadow the upper lid casts on the eyeball.
            drawPath(upperLid, Color.Black.copy(alpha = 0.12f), style = Stroke(geo.radiusY * 0.22f))

            rotate(geo.squashAngleDegrees, pivot = geo.irisCenter) {
                scale(scaleX = geo.squash, scaleY = 1f, pivot = geo.irisCenter) {
                    drawCircle(style.iris, geo.irisRadius, geo.irisCenter)
                    drawCircle(
                        lerp(style.iris, Color.Black, 0.35f),
                        geo.irisRadius,
                        geo.irisCenter,
                        style = Stroke(geo.irisRadius * 0.12f),
                    )
                    drawCircle(style.pupil, geo.pupilRadius, geo.irisCenter)
                }
            }

            drawCircle(style.highlight, geo.highlightLargeRadius, geo.highlightLarge)
            drawCircle(style.highlight, geo.highlightSmallRadius, geo.highlightSmall)
        }

        val outline = Stroke(geo.outlineWidth, cap = StrokeCap.Round)
        drawPath(aperture, style.outline, style = outline)
        // The lid edges carry the outline when the eye is (nearly) closed and the aperture vanishes.
        clipPath(oval) {
            drawPath(upperLid, style.outline, style = outline)
            drawPath(lowerLid, style.outline, style = outline)
        }

        val zStroke = Stroke(geo.outlineWidth * 0.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        zzzGlyphs(geo, expression.sleep, expression.zPhase).forEach { z ->
            val h = z.size / 2f
            val glyph = Path().apply {
                moveTo(z.center.x - h, z.center.y - h)
                lineTo(z.center.x + h, z.center.y - h)
                lineTo(z.center.x - h, z.center.y + h)
                lineTo(z.center.x + h, z.center.y + h)
            }
            drawPath(glyph, style.sclera.copy(alpha = z.alpha), style = zStroke)
        }
    }

    private fun lidCurve(geo: EyeGeometry, apexY: Float) = Path().apply {
        val c = geo.lidControl(apexY)
        moveTo(geo.leftCorner.x, geo.leftCorner.y)
        quadraticTo(c.x, c.y, geo.rightCorner.x, geo.rightCorner.y)
    }
}

/** A static eye: draws exactly what it is given. Use [LivingEye] for the animated one. */
@Composable
fun Eye(
    gaze: Offset,
    expression: EyeExpression,
    modifier: Modifier = Modifier,
    style: EyeStyle = EyeStyle(),
    renderer: EyeRenderer = CartoonEyeRenderer,
) {
    Canvas(modifier) { with(renderer) { drawEye(gaze, expression, style) } }
}
