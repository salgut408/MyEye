package com.salg.myeye.ui.eye

import androidx.compose.runtime.Immutable

/**
 * What the face around the eye is doing.
 *
 * @param lidOpen 0 = closed, 1 = wide open.
 * @param squint 0 = relaxed, 1 = hard squint (upper lid lowers, lower lid rises).
 * @param pupil 0 = pinprick, 1 = fully dilated; mapped to 30–70% of the iris by the renderer.
 * @param strain 0 = calm, 1 = exhausted from panic (the sclera turns bloodshot).
 * @param sleep 0 = awake … 1 = sound asleep; fades in the floating "Z z z".
 * @param zPhase 0..1, loops: where the floating Zs are in their rise.
 */
@Immutable
data class EyeExpression(
    val lidOpen: Float,
    val squint: Float = 0f,
    val pupil: Float = 0.5f,
    val strain: Float = 0f,
    val sleep: Float = 0f,
    val zPhase: Float = 0f,
) {
    companion object {
        val Idle = EyeExpression(lidOpen = 0.8f, squint = 0f, pupil = 0.5f)
        val Acquiring = EyeExpression(lidOpen = 0.9f, squint = 0f, pupil = 0.45f)
        val Tracking = EyeExpression(lidOpen = 0.75f, squint = 0.25f, pupil = 0.4f)
        val Lost = EyeExpression(lidOpen = 0.9f, squint = 0f, pupil = 0.5f)
        val Frantic = EyeExpression(lidOpen = 1f, squint = 0f, pupil = 0.8f)
        val Asleep = EyeExpression(lidOpen = 0f, squint = 0f, pupil = 0.6f)
        const val STARTLE_PUPIL = 0.3f
        const val RELIEF_PUPIL = 0.35f
    }
}
