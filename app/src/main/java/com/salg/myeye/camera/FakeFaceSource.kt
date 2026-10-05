package com.salg.myeye.camera

import com.salg.myeye.watch.Perception
import com.salg.myeye.watch.SeenFace
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Replays a scripted [FakeScenario] in a loop at a camera-like frame rate. No camera involved. */
class FakeFaceSource(
    private val scenario: FakeScenario,
    private val frameIntervalMs: Long = 66, // ≈ 15 fps, like low-res ImageAnalysis on a mid phone
) : FaceSource {
    override fun perceptions(lowPower: StateFlow<Boolean>): Flow<Perception> = flow {
        var elapsed = 0L
        while (true) {
            val loop = (elapsed / scenario.durationMs).toInt()
            val scene = scenario.sceneAt(elapsed % scenario.durationMs, loop)
            emit(Perception.Frame(scene.faces, scene.luma, elapsed))
            // Asleep: only ~1 frame per second, like the camera source. Scenario time keeps flowing.
            val interval = if (lowPower.value) FaceSource.LOW_POWER_INTERVAL_MS else frameIntervalMs
            delay(interval)
            elapsed += interval
        }
    }
}

/** What the camera "sees" at one instant. */
data class FakeScene(val faces: List<SeenFace>, val luma: Int = BRIGHT)

/**
 * A scripted situation. [sceneAt] gets time within the loop and the loop index; use the loop index
 * to vary tracking ids between loops, as ML Kit would.
 */
class FakeScenario(
    val name: String,
    val durationMs: Long,
    val sceneAt: (tMs: Long, loop: Int) -> FakeScene,
)

private const val BRIGHT = 120
private const val DARK = 12

private fun progress(t: Long, from: Long, to: Long) = ((t - from).toFloat() / (to - from)).coerceIn(0f, 1f)
private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f
private fun sway(t: Long, periodMs: Long, amp: Float) = amp * sin(2 * PI * t / periodMs).toFloat()

private fun person(id: Int, x: Float, y: Float = -0.1f, size: Float = 0.2f, eyesOpen: Float? = 0.95f) =
    SeenFace(id, x, y, size, eyesOpen)

object FakeScenarios {

    /** One person crosses the frame from the eye's left to its right, then the room is empty. */
    val WalkAcross = FakeScenario("One person walks left to right", 9_000) { t, loop ->
        val x = lerp(-1.1f, 1.1f, progress(t, 0, 6_000))
        FakeScene(if (t < 6_000 && abs(x) <= 1f) listOf(person(loop * 10 + 1, x, y = sway(t, 1_100, 0.04f))) else emptyList())
    }

    /** The eye's person stands still; a stranger walks up and gets much closer. The eye must not switch. */
    val StrangerStepsCloser = FakeScenario("Stranger steps closer", 11_000) { t, loop ->
        val me = person(loop * 10 + 1, x = -0.35f + sway(t, 3_000, 0.08f), size = 0.18f)
        val stranger = person(
            loop * 10 + 2,
            x = 0.45f + sway(t, 2_000, 0.05f),
            y = 0f,
            size = lerp(0.12f, 0.55f, progress(t, 2_500, 6_500)),
        )
        FakeScene(if (t in 2_500 until 8_500) listOf(me, stranger) else listOf(me))
    }

    /**
     * The person walks off to the right edge, is gone 2 s (the eye looks after them), comes back
     * near where they left with a new tracking id (heuristic re-acquire), then leaves for good
     * (Lost → Idle after 4 s).
     */
    val LeavesAndReturns = FakeScenario("Person leaves and returns", 16_000) { t, loop ->
        val first = loop * 10 + 1
        val second = loop * 10 + 2
        val faces = when {
            t < 2_500 -> listOf(person(first, 0f))
            t < 4_000 -> listOf(person(first, lerp(0f, 0.95f, progress(t, 2_500, 4_000))))
            t < 6_000 -> emptyList()
            t < 8_000 -> listOf(person(second, lerp(0.85f, 0f, progress(t, 6_000, 8_000))))
            t < 10_000 -> listOf(person(second, sway(t, 2_000, 0.1f)))
            else -> emptyList()
        }
        FakeScene(faces)
    }

    /** Someone is watched, then the lights go off for 5 s; when they come back the eye is relieved. */
    val LightsOff = FakeScenario("Lights go off", 12_000) { t, loop ->
        when {
            t < 3_000 -> FakeScene(listOf(person(loop * 10 + 1, sway(t, 2_500, 0.25f))))
            t < 8_000 -> FakeScene(emptyList(), luma = DARK)
            // Back in the light: ML Kit hands out a fresh tracking id.
            else -> FakeScene(listOf(person(loop * 10 + 2, sway(t, 2_500, 0.25f))))
        }
    }

    /** A person who blinks now and then, including one long, slow blink. Exercises mirror blink. */
    val Blinker = FakeScenario("Person blinks", 8_000) { t, loop ->
        val closed = t in 1_000..1_250 || t in 3_000..3_150 || t in 5_000..5_700
        FakeScene(listOf(person(loop * 10 + 1, 0.2f + sway(t, 4_000, 0.1f), eyesOpen = if (closed) 0.05f else 0.95f)))
    }

    /** An empty room: drowsy at 45 s, asleep at 60 s, then someone walks in and startles it. */
    val NobodyHome = FakeScenario("Nobody home (sleeps, then startled)", 85_000) { t, loop ->
        FakeScene(if (t < 75_000) emptyList() else listOf(person(loop * 10 + 1, sway(t, 3_000, 0.2f))))
    }

    /** The lens stays covered: panic, then exhausted sleep; uncovered, a face startles it awake. */
    val CoveredForLong = FakeScenario("Covered for a long time (exhausted)", 92_000) { t, loop ->
        when {
            t < 2_000 -> FakeScene(listOf(person(loop * 10 + 1, 0f)))
            t < 82_000 -> FakeScene(emptyList(), luma = 5)
            else -> FakeScene(listOf(person(loop * 10 + 2, sway(t, 2_500, 0.2f))))
        }
    }

    val all = listOf(WalkAcross, StrangerStepsCloser, LeavesAndReturns, LightsOff, Blinker)

    /** Slow scenarios for sleep; kept out of [Tour] because each takes well over a minute. */
    val sleepy = listOf(NobodyHome, CoveredForLong)

    /** Every scenario back to back, so the eye can be watched going through all of its moods. */
    val Tour: FakeScenario = run {
        val total = all.sumOf { it.durationMs }
        FakeScenario("Tour (all scenarios)", total) { t, loop ->
            var start = 0L
            for ((index, s) in all.withIndex()) {
                if (t < start + s.durationMs) return@FakeScenario s.sceneAt(t - start, loop * all.size + index)
                start += s.durationMs
            }
            FakeScene(emptyList())
        }
    }
}
