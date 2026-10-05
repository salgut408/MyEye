package com.salg.myeye.ui.eye

import androidx.compose.runtime.Immutable

/**
 * What the face around the eye is doing.
 *
 * @param lidOpen 0 = closed, 1 = wide open.
 * @param squint 0 = relaxed, 1 = hard squint (upper lid lowers, lower lid rises).
 * @param pupil 0 = pinprick, 1 = fully dilated; mapped to 30–70% of the iris by the renderer.
 */
@Immutable
data class EyeExpression(
    val lidOpen: Float,
    val squint: Float = 0f,
    val pupil: Float = 0.5f,
) {
    companion object {
        val Idle = EyeExpression(lidOpen = 0.8f, squint = 0f, pupil = 0.5f)
        val Acquiring = EyeExpression(lidOpen = 0.9f, squint = 0f, pupil = 0.45f)
        val Tracking = EyeExpression(lidOpen = 0.75f, squint = 0.25f, pupil = 0.4f)
        val Lost = EyeExpression(lidOpen = 0.9f, squint = 0f, pupil = 0.5f)
        val Frantic = EyeExpression(lidOpen = 1f, squint = 0f, pupil = 0.8f)
        const val RELIEF_PUPIL = 0.35f
    }
}
