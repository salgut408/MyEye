package com.salg.myeye.ui.eye

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.util.lerp

/**
 * What the eye should be doing right now, in UI terms. Gaze targets are normalized:
 * x, y in [-1, 1], center = 0, +y = down.
 */
@Immutable
sealed interface EyeBehavior {
    data object Idle : EyeBehavior

    data class Acquiring(val target: Offset) : EyeBehavior

    /** @param closeness 0 = far, 1 = very close; dilates the pupil. */
    data class Tracking(val target: Offset, val closeness: Float = 0f) : EyeBehavior

    data class Lost(val holdAt: Offset) : EyeBehavior

    /** @param intensity 0 = just went blind, 1 = blind for a long time. */
    data class Frantic(val intensity: Float) : EyeBehavior

    /** @param droop 0 = just getting sleepy … 1 = about to fall asleep. */
    data class Drowsy(val droop: Float) : EyeBehavior

    data object Asleep : EyeBehavior
}

/** Resting expression for a behavior, before blinks and the relief moment are layered on. */
fun EyeBehavior.expression(): EyeExpression = when (this) {
    EyeBehavior.Idle -> EyeExpression.Idle
    is EyeBehavior.Acquiring -> EyeExpression.Acquiring
    is EyeBehavior.Tracking -> EyeExpression.Tracking.copy(
        pupil = EyeExpression.Tracking.pupil + 0.25f * closeness.coerceIn(0f, 1f),
    )
    is EyeBehavior.Lost -> EyeExpression.Lost
    is EyeBehavior.Frantic -> EyeExpression.Frantic
    is EyeBehavior.Drowsy -> EyeExpression.Idle.copy(
        lidOpen = lerp(EyeExpression.Idle.lidOpen, 0.2f, droop.coerceIn(0f, 1f)),
        pupil = 0.55f,
    )
    EyeBehavior.Asleep -> EyeExpression.Asleep
}
