package com.salg.myeye.ui.eye

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
 * @param blinkKey bump this to blink once (mirroring the watched person's blink).
 * @param wakeKey bump this to play the startle of being woken up.
 */
@Composable
fun LivingEye(
    behavior: EyeBehavior,
    modifier: Modifier = Modifier,
    reliefKey: Int = 0,
    blinkKey: Int = 0,
    wakeKey: Int = 0,
    style: EyeStyle = EyeStyle(),
    renderer: EyeRenderer = CartoonEyeRenderer,
) {
    val gazeX = remember { Animatable(0f) }
    val gazeY = remember { Animatable(0f) }
    val lid = remember { Animatable(EyeExpression.Idle.lidOpen) }
    val squint = remember { Animatable(EyeExpression.Idle.squint) }
    val pupil = remember { Animatable(EyeExpression.Idle.pupil) }
    val blink = remember { Animatable(0f) } // 1 = fully closed, multiplied over lidOpen
    val strain = remember { Animatable(0f) }
    val zzz = remember { Animatable(0f) } // opacity of the sleeping Zs
    var tremor by remember { mutableStateOf(Offset.Zero) }
    // True while a one-shot moment (relief, startle) owns the lids and pupil.
    var oneShot by remember { mutableStateOf(false) }
    val current by rememberUpdatedState(behavior)

    suspend fun lookAt(target: Offset, spec: AnimationSpec<Float>) = coroutineScope {
        launch { gazeX.animateTo(target.x, spec) }
        launch { gazeY.animateTo(target.y, spec) }
    }

    // Resting expression for the current behavior. Paused while a one-shot moment owns the face.
    val resting = behavior.expression()
    val asleep = behavior == EyeBehavior.Asleep
    LaunchedEffect(resting, oneShot) {
        if (oneShot) return@LaunchedEffect
        // Falling asleep is a slow, heavy close; everything else is a quick spring.
        launch { lid.animateTo(resting.lidOpen, if (asleep) FallAsleepTween else LidSpring) }
        launch { squint.animateTo(resting.squint, LidSpring) }
        launch { pupil.animateTo(resting.pupil, PupilSpring) }
    }

    // The longer it's blind, the more strained (bloodshot) the eye gets; it recovers slowly.
    val targetStrain = (behavior as? EyeBehavior.Frantic)?.intensity?.coerceIn(0f, 1f)?.let { it * it } ?: 0f
    LaunchedEffect(targetStrain) {
        strain.animateTo(targetStrain, if (targetStrain > 0f) StrainSpring else tween(2500))
    }

    // Asleep: the Zs fade in slowly, and vanish at once when it wakes.
    LaunchedEffect(asleep) {
        zzz.animateTo(if (asleep) 1f else 0f, tween(if (asleep) 1500 else 150))
    }

    // Idle life: the lids breathe and the pupil hunts slightly (hippus), always.
    val life = rememberInfiniteTransition(label = "life")
    val breath by life.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    val hippus by life.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "hippus",
    )
    val zPhase by life.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)),
        label = "zPhase",
    )

    // Gaze motion. Keyed on the kind of motion, so target updates don't restart the loops and
    // leaving a state cancels its loop automatically.
    val motion = behavior.motion()
    LaunchedEffect(motion) {
        when (motion) {
            Motion.Wander -> while (true) {
                // Mostly small wandering; now and then a curious look far off to one side.
                val point = if (Random.nextFloat() < 0.15f) randomInDisk(0.75f, edgeBias = true) else randomInDisk(0.35f)
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

            // Sleepy: the gaze sinks and drifts slowly, heavier the closer it is to sleep.
            Motion.Doze -> while (true) {
                val droop = (current as? EyeBehavior.Drowsy)?.droop ?: 1f
                val point = Offset(Random.nextFloat() * 0.3f - 0.15f, 0.15f + 0.35f * droop)
                lookAt(point, tween(Random.nextInt(2500, 4000), easing = FastOutSlowInEasing))
            }

            // Asleep: the eyeball rolls down a little and rests.
            Motion.Sleep -> lookAt(Offset(0f, 0.3f), tween(1800, easing = FastOutSlowInEasing))

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

    suspend fun blinkOnce() {
        blink.animateTo(1f, tween(70))
        blink.animateTo(0f, tween(120))
    }

    // Spontaneous blinks while calm, sometimes a double blink; slow, heavy ones when drowsy.
    // Frantic eyes don't blink, and closed ones can't.
    LaunchedEffect(motion) {
        when (motion) {
            Motion.Frantic, Motion.Sleep -> return@LaunchedEffect
            Motion.Doze -> while (true) {
                delay(Random.nextLong(2000, 4000))
                if (oneShot) continue
                blink.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
                delay(Random.nextLong(100, 400))
                blink.animateTo(0f, tween(450, easing = FastOutSlowInEasing))
            }
            else -> while (true) {
                delay(Random.nextLong(3000, 7000))
                if (oneShot) continue
                blinkOnce()
                if (Random.nextFloat() < 0.15f) {
                    delay(90)
                    blinkOnce()
                }
            }
        }
    }

    // Mirror blink: the watched person blinked, so the eye blinks with them.
    val initialBlinkKey = remember { blinkKey }
    LaunchedEffect(blinkKey) {
        if (blinkKey != initialBlinkKey && !oneShot) blinkOnce()
    }

    // Startle: woken up by a face. Lids fly open past wide, the pupil snaps small, a jolt.
    val initialWakeKey = remember { wakeKey }
    LaunchedEffect(wakeKey) {
        if (wakeKey == initialWakeKey) return@LaunchedEffect
        oneShot = true
        try {
            blink.snapTo(0f)
            launch { lid.animateTo(1f, StartleSpring) }
            launch { pupil.animateTo(EyeExpression.STARTLE_PUPIL, tween(120)) }
            val jolt = launch {
                try {
                    val until = withFrameNanos { it } + 300_000_000L
                    while (withFrameNanos { it } < until) {
                        tremor = Offset(Random.nextFloat() * 0.06f - 0.03f, Random.nextFloat() * 0.06f - 0.03f)
                    }
                } finally {
                    tremor = Offset.Zero
                }
            }
            delay(700)
            jolt.cancel()
        } finally {
            oneShot = false
        }
    }

    // Relief: the first time it sees again after being blind. Skip the key we started with so a
    // recomposition from scratch never replays an old relief.
    val initialReliefKey = remember { reliefKey }
    LaunchedEffect(reliefKey) {
        if (reliefKey == initialReliefKey) return@LaunchedEffect
        oneShot = true
        try {
            launch { pupil.animateTo(EyeExpression.RELIEF_PUPIL, tween(350)) }
            blink.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
            delay(120)
            blink.animateTo(0f, tween(520, easing = FastOutSlowInEasing))
            delay(600)
        } finally {
            oneShot = false
        }
    }

    // Animated values are read only in the draw phase, so motion never triggers recomposition.
    Canvas(modifier) {
        val expression = EyeExpression(
            // Breathing never cracks a sleeping eye open.
            lidOpen = (lid.value + 0.02f * breath * (1f - zzz.value)).coerceIn(0f, 1f) * (1f - blink.value),
            squint = squint.value,
            pupil = (pupil.value + 0.025f * hippus).coerceIn(0f, 1f),
            strain = strain.value,
            sleep = zzz.value,
            zPhase = zPhase,
        )
        with(renderer) { drawEye(Offset(gazeX.value, gazeY.value) + tremor, expression, style) }
    }
}

private enum class Motion { Wander, Follow, Search, Frantic, Doze, Sleep }

private fun EyeBehavior.motion() = when (this) {
    EyeBehavior.Idle -> Motion.Wander
    is EyeBehavior.Acquiring, is EyeBehavior.Tracking -> Motion.Follow
    is EyeBehavior.Lost -> Motion.Search
    is EyeBehavior.Frantic -> Motion.Frantic
    is EyeBehavior.Drowsy -> Motion.Doze
    EyeBehavior.Asleep -> Motion.Sleep
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
private val StrainSpring = spring<Float>(dampingRatio = 1f, stiffness = 20f)
private val StartleSpring = spring<Float>(dampingRatio = 0.4f, stiffness = 1500f)
private val FallAsleepTween = tween<Float>(1800, easing = FastOutSlowInEasing)
