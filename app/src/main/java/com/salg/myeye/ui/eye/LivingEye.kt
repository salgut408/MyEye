package com.salg.myeye.ui.eye

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The animated eye. Turns a (possibly rapidly changing) [behavior] into smooth motion: springs for
 * gaze, lids and pupil, plus the eye's own life (saccades, blinks, frantic searching, tremor).
 *
 * @param reliefKey bump this to play the one-shot relief moment (slow blink + pupil constriction).
 */
@Composable
fun LivingEye(
    behavior: EyeBehavior,
    modifier: Modifier = Modifier,
    reliefKey: Int = 0,
    style: EyeStyle = EyeStyle(),
    renderer: EyeRenderer = CartoonEyeRenderer,
) {
    val gazeX = remember { Animatable(0f) }
    val gazeY = remember { Animatable(0f) }
    val lid = remember { Animatable(EyeExpression.Idle.lidOpen) }
    val squint = remember { Animatable(EyeExpression.Idle.squint) }
    val pupil = remember { Animatable(EyeExpression.Idle.pupil) }
    val blink = remember { Animatable(0f) } // 1 = fully closed, multiplied over lidOpen
    var tremor by remember { mutableStateOf(Offset.Zero) }
    var relieving by remember { mutableStateOf(false) }
    val current by rememberUpdatedState(behavior)

    suspend fun lookAt(target: Offset, spec: AnimationSpec<Float>) = coroutineScope {
        launch { gazeX.animateTo(target.x, spec) }
        launch { gazeY.animateTo(target.y, spec) }
    }

    // Resting expression for the current behavior. Paused while the relief moment owns the face.
    val resting = behavior.expression()
    LaunchedEffect(resting, relieving) {
        if (relieving) return@LaunchedEffect
        launch { lid.animateTo(resting.lidOpen, LidSpring) }
        launch { squint.animateTo(resting.squint, LidSpring) }
        launch { pupil.animateTo(resting.pupil, PupilSpring) }
    }

    // Gaze motion. Keyed on the kind of motion, so target updates don't restart the loops and
    // leaving a state cancels its loop automatically.
    val motion = behavior.motion()
    LaunchedEffect(motion) {
        when (motion) {
            Motion.Wander -> while (true) {
                val point = randomInDisk(0.35f)
                lookAt(point, SaccadeSpring)
                val dwell = Random.nextInt(1500, 4000)
                lookAt(point + randomInDisk(0.06f), tween(dwell, easing = LinearOutSlowInEasing))
            }

            Motion.Follow -> {
                var anchor: Offset? = null
                // Each new target launches a fresh spring; Animatable interrupts the previous one
                // and carries its velocity over, so motion stays continuous.
                snapshotFlow { current }.collect { b ->
                    when (b) {
                        is EyeBehavior.Acquiring -> {
                            anchor = b.target
                            launch { lookAt(b.target, SnapSpring) }
                        }
                        is EyeBehavior.Tracking -> {
                            // Dead zone: hold perfectly still for tiny movements; that reads as a stare.
                            val last = anchor
                            if (last != null && (b.target - last).getDistance() < FOLLOW_DEAD_ZONE) return@collect
                            anchor = b.target
                            launch { lookAt(b.target, FollowSpring) }
                        }
                        else -> Unit
                    }
                }
            }

            Motion.Search -> {
                val hold = (current as? EyeBehavior.Lost)?.holdAt ?: Offset.Zero
                lookAt(hold, HoldSpring)
                delay(1200)
                while (true) {
                    val glance = hold + Offset(Random.nextFloat() * 0.4f - 0.2f, Random.nextFloat() * 0.2f - 0.1f)
                    lookAt(glance, SaccadeSpring)
                    delay(Random.nextLong(500, 1100))
                }
            }

            Motion.Frantic -> coroutineScope {
                val tremorJob = launch {
                    try {
                        while (true) {
                            withFrameNanos { }
                            val amp = 0.008f + 0.02f * franticIntensity(current)
                            tremor = Offset(Random.nextFloat() * 2 * amp - amp, Random.nextFloat() * 2 * amp - amp)
                        }
                    } finally {
                        tremor = Offset.Zero
                    }
                }
                try {
                    while (true) {
                        val i = franticIntensity(current)
                        val target = randomInDisk(0.4f + 0.6f * i, edgeBias = true)
                        val spec = spring<Float>(dampingRatio = 0.55f, stiffness = 1500f + 3000f * i)
                        // Fire and forget: the next saccade interrupts this one mid-flight.
                        launch { lookAt(target, spec) }
                        delay((lerp(600f, 80f, i) * (0.7f + 0.6f * Random.nextFloat())).toLong())
                    }
                } finally {
                    tremorJob.cancel()
                }
            }
        }
    }

    // Spontaneous blinks while calm. Frantic eyes don't blink.
    LaunchedEffect(motion) {
        if (motion == Motion.Frantic) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(3000, 7000))
            val mirroring = (current as? EyeBehavior.Tracking)?.mirrorBlink == true
            if (!mirroring && !relieving) {
                blink.animateTo(1f, tween(70))
                blink.animateTo(0f, tween(120))
            }
        }
    }

    // Mirror blink: when the watched person closes their eyes, so does the eye.
    val mirrorBlink = (behavior as? EyeBehavior.Tracking)?.mirrorBlink == true
    LaunchedEffect(mirrorBlink) {
        if (mirrorBlink) blink.animateTo(1f, tween(70))
        else if (blink.value > 0f) blink.animateTo(0f, tween(120))
    }

    // Relief: the first time it sees again after being blind. Skip the key we started with so a
    // recomposition from scratch never replays an old relief.
    val initialReliefKey = remember { reliefKey }
    LaunchedEffect(reliefKey) {
        if (reliefKey == initialReliefKey) return@LaunchedEffect
        relieving = true
        try {
            launch { pupil.animateTo(EyeExpression.RELIEF_PUPIL, tween(350)) }
            blink.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
            delay(120)
            blink.animateTo(0f, tween(520, easing = FastOutSlowInEasing))
            delay(600)
        } finally {
            relieving = false
        }
    }

    // Animated values are read only in the draw phase, so motion never triggers recomposition.
    Canvas(modifier) {
        val expression = EyeExpression(
            lidOpen = lid.value * (1f - blink.value),
            squint = squint.value,
            pupil = pupil.value,
        )
        with(renderer) { drawEye(Offset(gazeX.value, gazeY.value) + tremor, expression, style) }
    }
}

private enum class Motion { Wander, Follow, Search, Frantic }

private fun EyeBehavior.motion() = when (this) {
    EyeBehavior.Idle -> Motion.Wander
    is EyeBehavior.Acquiring, is EyeBehavior.Tracking -> Motion.Follow
    is EyeBehavior.Lost -> Motion.Search
    is EyeBehavior.Frantic -> Motion.Frantic
}

private fun franticIntensity(b: EyeBehavior) = ((b as? EyeBehavior.Frantic)?.intensity ?: 1f).coerceIn(0f, 1f)

private fun randomInDisk(radius: Float, edgeBias: Boolean = false): Offset {
    val angle = Random.nextFloat() * 2f * PI.toFloat()
    val r = radius * if (edgeBias) 0.5f + 0.5f * Random.nextFloat() else sqrt(Random.nextFloat())
    return Offset(r * cos(angle), r * sin(angle))
}

private const val FOLLOW_DEAD_ZONE = 0.03f
private val FollowSpring = spring<Float>(dampingRatio = 0.6f, stiffness = 300f)
private val SnapSpring = spring<Float>(dampingRatio = 0.75f, stiffness = 1200f)
private val SaccadeSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 900f)
private val HoldSpring = spring<Float>(dampingRatio = 0.8f, stiffness = 500f)
private val LidSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 400f)
private val PupilSpring = spring<Float>(dampingRatio = 1f, stiffness = 120f)
