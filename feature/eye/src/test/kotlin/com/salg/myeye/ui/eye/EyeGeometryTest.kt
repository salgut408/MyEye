package com.salg.myeye.ui.eye

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EyeGeometryTest {
    private val size = Size(1000f, 2000f)
    private val style = EyeStyle()

    private fun geo(gaze: Offset = Offset.Zero, expression: EyeExpression = EyeExpression.Idle) =
        eyeGeometry(size, gaze, expression, style)

    @Test
    fun `sclera is a slightly wide oval centered on the canvas`() {
        val g = geo()
        assertEquals(Offset(500f, 1000f), g.center)
        assertEquals(430f, g.radiusX, 0.01f)
        assertTrue(g.radiusX > g.radiusY)
    }

    @Test
    fun `iris travel is capped at maxTravel of the sclera radius`() {
        val g = geo(gaze = Offset(5f, 0f)) // way out of range
        assertEquals(style.maxTravel * g.radiusX, g.irisCenter.x - g.center.x, 0.01f)
        assertEquals(g.center.y, g.irisCenter.y, 0.01f)
    }

    @Test
    fun `iris is 45 percent of sclera width`() {
        val g = geo()
        assertEquals(0.45f * 2f * g.radiusX, 2f * g.irisRadius, 0.01f)
    }

    @Test
    fun `highlights do not move with the iris`() {
        val centered = geo(gaze = Offset.Zero)
        val looking = geo(gaze = Offset(-0.9f, 0.7f))
        assertEquals(centered.highlightLarge, looking.highlightLarge)
        assertEquals(centered.highlightSmall, looking.highlightSmall)
        assertTrue("highlight sits upper-left", centered.highlightLarge.x < centered.center.x && centered.highlightLarge.y < centered.center.y)
    }

    @Test
    fun `iris foreshortens up to 30 percent along its travel direction`() {
        assertEquals(1f, geo(gaze = Offset.Zero).squash, 0.0001f)
        val edge = geo(gaze = Offset(0f, 1f))
        assertEquals(0.7f, edge.squash, 0.0001f)
        assertEquals(90f, edge.squashAngleDegrees, 0.01f)
    }

    @Test
    fun `pupil follows expression and stays inside the iris`() {
        val small = geo(expression = EyeExpression.Idle.copy(pupil = 0.35f))
        val big = geo(expression = EyeExpression.Frantic)
        assertTrue(big.pupilRadius > small.pupilRadius)
        assertTrue(big.pupilRadius < big.irisRadius)
    }

    @Test
    fun `closed lids meet and squint narrows the opening`() {
        val closed = geo(expression = EyeExpression(lidOpen = 0f))
        assertEquals(closed.upperLidY, closed.lowerLidY, 0.01f)

        val relaxed = geo(expression = EyeExpression(lidOpen = 0.75f, squint = 0f))
        val squinting = geo(expression = EyeExpression(lidOpen = 0.75f, squint = 0.25f))
        assertTrue(squinting.upperLidY > relaxed.upperLidY)
        assertTrue(squinting.lowerLidY < relaxed.lowerLidY)
    }

    @Test
    fun `wide open lids clear the whole sclera`() {
        val g = geo(expression = EyeExpression.Frantic)
        assertTrue(g.upperLidY < g.center.y - g.radiusY)
        assertTrue(g.lowerLidY > g.center.y + g.radiusY)
    }

    @Test
    fun `no Zs while awake`() {
        assertTrue(zzzGlyphs(geo(), sleep = 0f, phase = 0.4f).isEmpty())
    }

    @Test
    fun `three Zs float above the upper right of the sclera, wherever the eye looks`() {
        val a = zzzGlyphs(geo(gaze = Offset.Zero), sleep = 1f, phase = 0.3f)
        val b = zzzGlyphs(geo(gaze = Offset(-1f, 1f)), sleep = 1f, phase = 0.3f)
        assertEquals(3, a.size)
        assertEquals(a, b)
        val g = geo()
        assertTrue(a.all { it.center.x > g.center.x && it.center.y < g.center.y - 0.5f * g.radiusY })
    }

    @Test
    fun `a Z rises, grows and fades in then out over its cycle`() {
        val g = geo()
        val early = zzzGlyphs(g, 1f, phase = 0.1f).first()
        val mid = zzzGlyphs(g, 1f, phase = 0.5f).first()
        val late = zzzGlyphs(g, 1f, phase = 0.9f).first()
        assertTrue(mid.center.y < early.center.y && late.center.y < mid.center.y)
        assertTrue(late.size > early.size)
        assertTrue(mid.alpha > early.alpha && mid.alpha > late.alpha)
        assertEquals(0f, zzzGlyphs(g, 1f, phase = 0f).first().alpha, 0.0001f)
    }

    @Test
    fun `Z opacity scales with how asleep it is`() {
        val half = zzzGlyphs(geo(), sleep = 0.5f, phase = 0.5f).first().alpha
        val full = zzzGlyphs(geo(), sleep = 1f, phase = 0.5f).first().alpha
        assertEquals(full / 2f, half, 0.0001f)
    }
}
