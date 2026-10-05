package com.salg.myeye.camera

import com.salg.myeye.watch.SeenFace
import java.nio.ByteBuffer

/**
 * Maps a detected face box to the eye's normalized space.
 *
 * ML Kit reports boxes in the *upright* image (after applying [rotationDegrees]), so the frame's
 * width and height swap for 90/270. The front camera's frames are not mirrored, so a person standing
 * to the screen's right shows up on the image's left; with [mirror] the eye looks back at them the
 * way a person facing them would.
 *
 * @param centerX box center in upright-image pixels
 * @param boxWidth box width in upright-image pixels
 * @param imageWidth raw (sensor-oriented) frame width
 * @param imageHeight raw (sensor-oriented) frame height
 */
fun mapFace(
    id: Int,
    centerX: Float,
    centerY: Float,
    boxWidth: Float,
    imageWidth: Int,
    imageHeight: Int,
    rotationDegrees: Int,
    mirror: Boolean,
    eyesOpen: Float?,
): SeenFace {
    val sideways = rotationDegrees == 90 || rotationDegrees == 270
    val w = (if (sideways) imageHeight else imageWidth).toFloat()
    val h = (if (sideways) imageWidth else imageHeight).toFloat()
    val x = if (mirror) 1f - 2f * centerX / w else 2f * centerX / w - 1f
    val y = 2f * centerY / h - 1f
    return SeenFace(
        id = id,
        x = x.coerceIn(-1f, 1f),
        y = y.coerceIn(-1f, 1f),
        size = boxWidth / w,
        eyesOpen = eyesOpen,
    )
}

/**
 * Low-power throttle: while [lowPower], analyze at most one frame per
 * [FaceSource.LOW_POWER_INTERVAL_MS]. The first frame is always analyzed.
 */
fun shouldSkipFrame(lowPower: Boolean, timestampMs: Long, lastAnalyzedMs: Long?): Boolean =
    lowPower && lastAnalyzedMs != null && timestampMs - lastAnalyzedMs < FaceSource.LOW_POWER_INTERVAL_MS

/** The lower of two eye-open probabilities (either may be unknown); one closed eye counts as closed. */
fun eyesOpen(left: Float?, right: Float?): Float? = listOfNotNull(left, right).minOrNull()

/**
 * Mean brightness (0–255) of a Y (luma) plane, sampling every [step]th byte. Uses absolute reads
 * so the buffer's position is untouched. Must run before the frame is closed.
 */
fun meanLuma(yPlane: ByteBuffer, step: Int = 16): Int {
    val limit = yPlane.limit()
    if (limit == 0) return 0
    var sum = 0L
    var count = 0
    var i = 0
    while (i < limit) {
        sum += yPlane.get(i).toInt() and 0xFF
        count++
        i += step
    }
    return (sum / count).toInt()
}
