package com.salg.myeye.watch

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sign

enum class Sight { SEEING, DARK, NO_PERMISSION, CAMERA_ERROR }

/** Whether the eye is awake at all. Asleep overrides everything else, including panic. */
enum class Alertness { AWAKE, DROWSY, ASLEEP }

enum class SleepReason { ALONE, EXHAUSTED }

/** The obsessive policy. Only meaningful while [Sight.SEEING]. */
sealed interface Watch {
    data object Idle : Watch

    /** @param desperate started shortly after being blind: commits faster and brings relief. */
    data class Acquiring(val id: Int, val since: Long, val face: SeenFace, val desperate: Boolean) : Watch

    data class Tracking(val id: Int, val last: SeenFace) : Watch

    data class Lost(val id: Int, val last: SeenFace, val since: Long) : Watch
}

data class WatcherState(
    val sight: Sight = Sight.SEEING,
    val watch: Watch = Watch.Idle,
    /** Non-null exactly when the eye can't see: ramps 0 → 1 the longer it stays blind. */
    val franticIntensity: Float? = null,
    /** Where the eye should look, or null when it's minding its own business (Idle/Frantic). */
    val gaze: Gaze? = null,
    /** 0 = far … 1 = close, for the tracked face. */
    val closeness: Float = 0f,
    /** The tracked person's eyes are closed (level). */
    val mirrorBlink: Boolean = false,
    /**
     * One-shot: the tracked person just closed their eyes, so the eye blinks once with them.
     * Fires on the closing edge only, so someone looking down at the phone (eyes classified
     * as closed for a long time) gets one blink, not a shut eye.
     */
    val mirrorBlinkStart: Boolean = false,
    /** One-shot: true only on the update where the eye finds someone right after being blind. */
    val relief: Boolean = false,
    val alertness: Alertness = Alertness.AWAKE,
    /** 0 = alert … 1 = about to fall asleep (1 while asleep). Drives the drooping lids. */
    val drowsiness: Float = 0f,
    /** Why it's asleep, or null when awake. */
    val sleepReason: SleepReason? = null,
    /** One-shot: a face just woke the eye up (it startles). */
    val wake: Boolean = false,
    /** Latest camera frame, kept for the debug overlay. */
    val lastFrame: Perception.Frame? = null,
    // Bookkeeping, exposed for debugging and tests.
    val darkSince: Long? = null,
    val blindSince: Long? = null,
    val regainedSightAt: Long? = null,
    val lastMirrorBlinkAt: Long? = null,
    /** Since when it has been seeing an empty room (null while anyone is around or it's blind). */
    val aloneSince: Long? = null,
) {
    val isFrantic get() = franticIntensity != null
    val isAsleep get() = alertness == Alertness.ASLEEP
    val targetId: Int?
        get() = when (val w = watch) {
            Watch.Idle -> null
            is Watch.Acquiring -> w.id
            is Watch.Tracking -> w.id
            is Watch.Lost -> w.id
        }
}

/**
 * The eye's personality as a pure state machine: no Android, no wall clock. Time is always passed
 * in, so every behavior is reproducible in tests.
 *
 * Feed it every [Perception] via [onPerception], and call [onTick] periodically so purely
 * time-based changes (panic escalating, giving up on someone who left, falling asleep) happen even
 * when no new perception arrives.
 *
 * Alertness sits on top of Sight and Watch: alone for [WatcherConfig.sleepAfterMs] (drooping during
 * the last [WatcherConfig.drowsyLeadMs]) or exhausted by a long panic, the eye falls asleep. Asleep,
 * it ignores everything (darkness included) until any face appears, which startles it awake.
 */
class ObsessiveWatcher(private val config: WatcherConfig = WatcherConfig()) {

    var state: WatcherState = WatcherState()
        private set

    fun onPerception(p: Perception, nowMs: Long): WatcherState {
        val prev = state
        if (prev.isAsleep) {
            state = whileAsleep(prev, p, nowMs)
            return state
        }
        val (sight, darkSince) = measureSight(prev, p, nowMs)
        val seeing = sight == Sight.SEEING
        val regainedSightAt = when {
            !seeing -> null
            prev.sight != Sight.SEEING -> nowMs
            else -> prev.regainedSightAt
        }

        var relief = false
        val watch = when {
            !seeing -> Watch.Idle // forget everyone; it will have to find them again
            p is Perception.Frame -> {
                val desperate = regainedSightAt != null && nowMs - regainedSightAt <= config.reliefWindowMs
                val next = step(timeout(prev.watch, nowMs), p.faces, nowMs, desperate)
                relief = prev.watch is Watch.Acquiring && prev.watch.desperate && next is Watch.Tracking
                next
            }
            else -> prev.watch
        }

        val next = derive(
            prev.copy(
                sight = sight,
                watch = watch,
                relief = relief,
                lastFrame = (p as? Perception.Frame) ?: prev.lastFrame,
                darkSince = darkSince,
                blindSince = if (seeing) null else prev.blindSince ?: nowMs,
                // Relief is spent once it has been delivered.
                regainedSightAt = if (relief) null else regainedSightAt,
            ),
            nowMs,
        )
        val wasClosed = prev.mirrorBlink && prev.targetId == next.targetId
        val rested = prev.lastMirrorBlinkAt?.let { nowMs - it >= config.mirrorBlinkRefractoryMs } ?: true
        val blink = next.mirrorBlink && !wasClosed && rested
        // Alone = seeing, minding its own business, and nobody at all in the frame.
        val alone = seeing && next.watch == Watch.Idle && p is Perception.Frame && p.faces.isEmpty()
        state = settle(
            next.copy(
                mirrorBlinkStart = blink,
                lastMirrorBlinkAt = if (blink) nowMs else prev.lastMirrorBlinkAt,
                aloneSince = if (alone) prev.aloneSince ?: nowMs else null,
                wake = false,
            ),
            nowMs,
        )
        return state
    }

    fun onTick(nowMs: Long): WatcherState {
        val s = state.copy(relief = false, mirrorBlinkStart = false, wake = false)
        state = if (s.isAsleep) s else settle(derive(s.copy(watch = timeout(s.watch, nowMs)), nowMs), nowMs)
        return state
    }

    /** Puts the eye to sleep right away (debug "Nap now"). */
    fun sleepNow(nowMs: Long): WatcherState {
        state = fallAsleep(state, SleepReason.ALONE, nowMs)
        return state
    }

    private fun measureSight(prev: WatcherState, p: Perception, nowMs: Long): Pair<Sight, Long?> = when (p) {
        Perception.NoPermission -> Sight.NO_PERMISSION to prev.darkSince
        is Perception.CameraError -> Sight.CAMERA_ERROR to prev.darkSince
        is Perception.Frame -> {
            val darkSince = if (p.meanLuma < config.darkEnterLuma) prev.darkSince ?: nowMs else null
            val sight = when {
                prev.sight == Sight.DARK && p.meanLuma <= config.darkExitLuma -> Sight.DARK
                darkSince != null && nowMs - darkSince >= config.darkAfterMs -> Sight.DARK
                else -> Sight.SEEING
            }
            sight to darkSince
        }
    }

    /** Decides how awake the eye is from how long it has been alone or panicking. */
    private fun settle(s: WatcherState, nowMs: Long): WatcherState {
        val blindSince = s.blindSince
        if (blindSince != null && nowMs - blindSince >= config.franticRampMs + config.exhaustAfterMs) {
            return fallAsleep(s, SleepReason.EXHAUSTED, nowMs)
        }
        val aloneSince = s.aloneSince
            ?: return s.copy(alertness = Alertness.AWAKE, drowsiness = 0f, sleepReason = null)
        val alone = nowMs - aloneSince
        if (alone >= config.sleepAfterMs) return fallAsleep(s, SleepReason.ALONE, nowMs)
        val drowsiness = ((alone - (config.sleepAfterMs - config.drowsyLeadMs)).toFloat() / config.drowsyLeadMs)
            .coerceIn(0f, 1f)
        return s.copy(
            alertness = if (drowsiness > 0f) Alertness.DROWSY else Alertness.AWAKE,
            drowsiness = drowsiness,
            sleepReason = null,
        )
    }

    private fun fallAsleep(s: WatcherState, reason: SleepReason, nowMs: Long) = derive(
        s.copy(
            alertness = Alertness.ASLEEP,
            sleepReason = reason,
            drowsiness = 1f,
            watch = Watch.Idle,
            blindSince = null, // panic is over; sleep overrides it
            aloneSince = null,
            regainedSightAt = null,
            relief = false,
            mirrorBlinkStart = false,
            wake = false,
        ),
        nowMs,
    )

    /** Asleep: keep measuring the light (for the overlay) but only a face matters. */
    private fun whileAsleep(prev: WatcherState, p: Perception, nowMs: Long): WatcherState {
        val (sight, darkSince) = measureSight(prev, p, nowMs)
        val asleep = prev.copy(
            sight = sight,
            darkSince = darkSince,
            lastFrame = (p as? Perception.Frame) ?: prev.lastFrame,
            relief = false,
            mirrorBlinkStart = false,
            wake = false,
        )
        val face = (p as? Perception.Frame)?.faces?.maxByOrNull { it.size } ?: return asleep
        // Startled awake: straight to the face that woke it. Startle replaces relief.
        return derive(
            asleep.copy(
                alertness = Alertness.AWAKE,
                sleepReason = null,
                drowsiness = 0f,
                wake = true,
                watch = Watch.Acquiring(face.id, since = nowMs, face = face, desperate = false),
                blindSince = if (sight == Sight.SEEING) null else nowMs,
                regainedSightAt = null,
                aloneSince = null,
            ),
            nowMs,
        )
    }

    private fun timeout(watch: Watch, nowMs: Long): Watch =
        if (watch is Watch.Lost && nowMs - watch.since >= config.lostTimeoutMs) Watch.Idle else watch

    private fun step(watch: Watch, faces: List<SeenFace>, nowMs: Long, desperate: Boolean): Watch = when (watch) {
        Watch.Idle -> faces.maxByOrNull { it.size }
            ?.let { Watch.Acquiring(it.id, since = nowMs, face = it, desperate = desperate) }
            ?: Watch.Idle

        is Watch.Acquiring -> {
            val face = faces.firstOrNull { it.id == watch.id }
            val needed = if (watch.desperate) config.reliefAcquireMs else config.acquireMs
            when {
                face == null -> Watch.Idle
                nowMs - watch.since >= needed -> Watch.Tracking(face.id, face)
                else -> watch.copy(face = face)
            }
        }

        // Obsessive: only this id matters, however close or new anyone else is.
        is Watch.Tracking -> faces.firstOrNull { it.id == watch.id }
            ?.let { Watch.Tracking(it.id, it) }
            ?: Watch.Lost(watch.id, watch.last, since = nowMs)

        is Watch.Lost -> {
            // Tracking ids don't survive occlusion, so a face reappearing where they left is them.
            val face = faces.firstOrNull { it.id == watch.id }
                ?: faces
                    .filter { distance(it, watch.last) <= config.reacquireRadius }
                    .minByOrNull { distance(it, watch.last) }
            if (face != null) Watch.Tracking(face.id, face) else watch
        }
    }

    private fun derive(s: WatcherState, nowMs: Long): WatcherState {
        val tracked = (s.watch as? Watch.Tracking)?.last
        return s.copy(
            franticIntensity = s.blindSince?.let {
                min(1f, (nowMs - it).toFloat() / config.franticRampMs)
            },
            gaze = when (val w = s.watch) {
                Watch.Idle -> null
                is Watch.Acquiring -> Gaze(w.face.x, w.face.y)
                is Watch.Tracking -> Gaze(w.last.x, w.last.y)
                is Watch.Lost -> lookAfter(w.last, config)
            },
            closeness = tracked?.let {
                ((it.size - config.farSize) / (config.nearSize - config.farSize)).coerceIn(0f, 1f)
            } ?: 0f,
            mirrorBlink = tracked?.eyesOpen?.let { it < config.mirrorBlinkBelow } ?: false,
        )
    }
}

/** Where the eye holds its gaze after losing someone: toward the edge they left by. */
fun lookAfter(last: SeenFace, config: WatcherConfig): Gaze {
    val x = if (abs(last.x) > config.lookAfterEdge) {
        sign(last.x) * min(1f, abs(last.x) + config.lookAfterPush)
    } else {
        last.x
    }
    return Gaze(x, last.y)
}

private fun distance(a: SeenFace, b: SeenFace) = hypot(a.x - b.x, a.y - b.y)
