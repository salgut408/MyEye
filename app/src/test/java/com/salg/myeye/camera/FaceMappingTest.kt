package com.salg.myeye.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer

class FaceMappingTest {

    // Typical front-camera ImageAnalysis frame on a portrait phone: sensor-landscape 640×480,
    // rotation 270 → the upright image ML Kit reports boxes in is 480 wide × 640 tall.
    private fun front270(centerX: Float, centerY: Float = 320f, boxWidth: Float = 96f) = mapFace(
        id = 1, centerX = centerX, centerY = centerY, boxWidth = boxWidth,
        imageWidth = 640, imageHeight = 480, rotationDegrees = 270, mirror = true, eyesOpen = null,
    )

    @Test
    fun `rotation 270 + front mirroring maps a face on the image's left side to positive x`() {
        val face = front270(centerX = 48f)
        // Upright width is 480 (swapped), so x = 1 - 2·48/480 = 0.8. Using the unswapped 640 would give 0.85.
        assertEquals(0.8f, face.x, 0.0001f)
    }

    @Test
    fun `rotation 270 + front mirroring maps the image's right side to negative x and center to 0`() {
        assertEquals(-0.8f, front270(centerX = 432f).x, 0.0001f)
        assertEquals(0f, front270(centerX = 240f).x, 0.0001f)
    }

    @Test
    fun `y uses the swapped height and size is relative to the upright width`() {
        val face = front270(centerX = 240f, centerY = 160f, boxWidth = 96f)
        assertEquals(-0.5f, face.y, 0.0001f) // 2·160/640 - 1
        assertEquals(0.2f, face.size, 0.0001f) // 96/480
    }

    @Test
    fun `rotation 90 swaps too, rotation 0 does not`() {
        val r90 = mapFace(1, 48f, 320f, 96f, 640, 480, rotationDegrees = 90, mirror = true, eyesOpen = null)
        assertEquals(0.8f, r90.x, 0.0001f)
        val r0 = mapFace(1, 64f, 240f, 128f, 640, 480, rotationDegrees = 0, mirror = true, eyesOpen = null)
        assertEquals(0.8f, r0.x, 0.0001f) // 1 - 2·64/640
        assertEquals(0f, r0.y, 0.0001f)
        assertEquals(0.2f, r0.size, 0.0001f)
    }

    @Test
    fun `without mirroring the left side stays negative`() {
        val face = mapFace(1, 48f, 320f, 96f, 640, 480, rotationDegrees = 270, mirror = false, eyesOpen = null)
        assertEquals(-0.8f, face.x, 0.0001f)
    }

    @Test
    fun `boxes poking past the frame are clamped`() {
        assertEquals(1f, front270(centerX = -20f).x, 0.0001f)
    }

    @Test
    fun `eyes open is the lower probability and null only when both are unknown`() {
        assertEquals(0.1f, eyesOpen(0.9f, 0.1f))
        assertEquals(0.7f, eyesOpen(null, 0.7f))
        assertNull(eyesOpen(null, null))
    }

    @Test
    fun `mean luma samples every 16th byte without moving the buffer`() {
        val bytes = ByteArray(64) { i -> if (i % 16 == 0) 200.toByte() else 0 } // only sampled bytes are bright
        val buffer = ByteBuffer.wrap(bytes)
        assertEquals(200, meanLuma(buffer))
        assertEquals(0, buffer.position())
    }

    @Test
    fun `mean luma treats bytes as unsigned`() {
        assertEquals(255, meanLuma(ByteBuffer.wrap(ByteArray(32) { 0xFF.toByte() })))
        assertEquals(0, meanLuma(ByteBuffer.allocate(0)))
    }

    @Test
    fun `low power analyzes the first frame, then one per second`() {
        assertFalse(shouldSkipFrame(lowPower = true, timestampMs = 5_000, lastAnalyzedMs = null))
        assertTrue(shouldSkipFrame(lowPower = true, timestampMs = 5_500, lastAnalyzedMs = 5_000))
        assertFalse(shouldSkipFrame(lowPower = true, timestampMs = 6_000, lastAnalyzedMs = 5_000))
        // Sensor timestamps can be huge; no overflow tricks.
        assertFalse(shouldSkipFrame(lowPower = true, timestampMs = Long.MAX_VALUE / 2, lastAnalyzedMs = null))
    }

    @Test
    fun `awake, no frame is ever skipped`() {
        assertFalse(shouldSkipFrame(lowPower = false, timestampMs = 5_010, lastAnalyzedMs = 5_000))
    }
}
