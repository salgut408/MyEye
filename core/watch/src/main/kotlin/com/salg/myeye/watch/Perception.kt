package com.salg.myeye.watch

/**
 * One face as the eye perceives it.
 *
 * @param id ML Kit tracking id; stable while the face stays in view, lost on occlusion.
 * @param x horizontal position in [-1, 1], center = 0, already mirrored so +x = the eye's right.
 * @param y vertical position in [-1, 1], center = 0, +y = down.
 * @param size face box width / image width; a proxy for distance (bigger = closer).
 * @param eyesOpen lower of the two eye-open probabilities, or null if unknown.
 */
data class SeenFace(val id: Int, val x: Float, val y: Float, val size: Float, val eyesOpen: Float?)

sealed interface Perception {
    data class Frame(val faces: List<SeenFace>, val meanLuma: Int, val timestampMs: Long) : Perception
    data object NoPermission : Perception
    data class CameraError(val cause: Throwable) : Perception
}

/** Normalized gaze target, same space as [SeenFace.x]/[SeenFace.y]. */
data class Gaze(val x: Float, val y: Float)

/** Time source, injected so the watcher and ViewModel can be driven by tests. */
fun interface Clock {
    fun nowMs(): Long
}
