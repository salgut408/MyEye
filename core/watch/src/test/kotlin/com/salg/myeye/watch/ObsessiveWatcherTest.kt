package com.salg.myeye.watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObsessiveWatcherTest {

    private val config = WatcherConfig()
    private val watcher = ObsessiveWatcher(config)
    private var now = 0L

    private fun face(id: Int, x: Float = 0f, y: Float = 0f, size: Float = 0.2f, eyesOpen: Float? = 0.9f) =
        SeenFace(id, x, y, size, eyesOpen)

    /** Feeds one frame at the current time, then advances time. */
    private fun frame(vararg faces: SeenFace, luma: Int = 120, stepMs: Long = 0): WatcherState {
        val s = watcher.onPerception(Perception.Frame(faces.toList(), luma, now), now)
        now += stepMs
        return s
    }

    /** Feeds the same faces at ~30 fps for [durationMs]; returns the last state. */
    private fun frames(durationMs: Long, vararg faces: SeenFace, luma: Int = 120): WatcherState {
        val end = now + durationMs
        var s = watcher.state
        while (now <= end) s = frame(*faces, luma = luma, stepMs = 33)
        return s
    }

    private fun trackFace(f: SeenFace): WatcherState {
        val s = frames(config.acquireMs + 50, f)
        assertEquals(Watch.Tracking(f.id, f), s.watch)
        return s
    }

    // --- Watch policy ---------------------------------------------------------------------------

    @Test
    fun `idle picks the largest face as the candidate`() {
        val s = frame(face(1, size = 0.1f), face(2, size = 0.3f), face(3, size = 0.2f))
        assertEquals(2, (s.watch as Watch.Acquiring).id)
    }

    @Test
    fun `acquiring commits after 400 ms of continuous sighting`() {
        val f = face(1, x = 0.2f)
        frame(f)
        now = 399
        assertTrue(frame(f).watch is Watch.Acquiring)
        now = 400
        assertEquals(Watch.Tracking(1, f), frame(f).watch)
    }

    @Test
    fun `acquiring is cancelled if the candidate disappears before 400 ms`() {
        frame(face(1), stepMs = 200)
        val s = frame()
        assertEquals(Watch.Idle, s.watch)
        assertNull(s.gaze)
    }

    @Test
    fun `ignores a closer stranger while tracking`() {
        val mine = face(1, x = -0.3f, size = 0.15f)
        trackFace(mine)

        val stranger = face(2, x = 0.4f, size = 0.5f) // much closer, much bigger
        val s = frames(3_000, mine, stranger)

        assertEquals(Watch.Tracking(1, mine), s.watch)
        assertEquals(Gaze(-0.3f, 0f), s.gaze)
    }

    @Test
    fun `ignores a newer face while tracking`() {
        trackFace(face(1))
        val s = frames(1_000, face(1), face(99, size = 0.9f))
        assertEquals(1, s.targetId)
    }

    @Test
    fun `losing the tracked face goes to Lost and holds the last position`() {
        trackFace(face(1, x = 0.3f, y = -0.2f))
        val s = frame(face(2, x = -0.9f)) // only a far-away stranger remains
        val lost = s.watch as Watch.Lost
        assertEquals(1, lost.id)
        assertEquals(Gaze(0.3f, -0.2f), s.gaze)
    }

    @Test
    fun `stays Lost until 4000 ms, then goes Idle`() {
        trackFace(face(1))
        val lostAt = now
        frame()
        now = lostAt + 3_999
        assertTrue(frame().watch is Watch.Lost)
        now = lostAt + 4_000
        assertEquals(Watch.Idle, frame().watch)
    }

    @Test
    fun `Lost times out on ticks alone when no frames arrive`() {
        trackFace(face(1))
        val lostAt = now
        frame()
        assertTrue(watcher.onTick(lostAt + 3_000).watch is Watch.Lost)
        assertEquals(Watch.Idle, watcher.onTick(lostAt + 4_000).watch)
    }

    @Test
    fun `same id coming back during Lost resumes Tracking`() {
        trackFace(face(1, x = 0.8f))
        frames(1_000)
        val back = face(1, x = -0.5f) // far away, but it's the same id
        assertEquals(Watch.Tracking(1, back), frame(back).watch)
    }

    @Test
    fun `re-acquires a new tracking id that appears near the last position during Lost`() {
        trackFace(face(1, x = 0.2f, y = 0.1f))
        frames(1_500) // occluded
        val reborn = face(7, x = 0.4f, y = 0.2f) // distance ≈ 0.22 < 0.35
        val s = frame(reborn, face(8, x = -0.8f, size = 0.6f))
        assertEquals(Watch.Tracking(7, reborn), s.watch)
    }

    @Test
    fun `re-acquire picks the face nearest the last position`() {
        trackFace(face(1, x = 0f))
        frame()
        val s = frame(face(5, x = 0.3f), face(6, x = 0.1f))
        assertEquals(6, s.targetId)
    }

    @Test
    fun `does not re-acquire a face that appears far away during Lost`() {
        trackFace(face(1, x = -0.5f))
        frame()
        val s = frames(2_000, face(9, x = 0.5f, size = 0.6f)) // distance 1.0
        assertTrue(s.watch is Watch.Lost)
        assertEquals(1, s.targetId)
    }

    @Test
    fun `Lost looks after someone who left past the edge`() {
        trackFace(face(1, x = 0.8f, y = 0.1f))
        val s = frame()
        assertEquals(Gaze(1f, 0.1f), s.gaze) // 0.8 + 0.25, clamped to 1

        assertEquals(Gaze(-0.9f, 0f), lookAfter(face(2, x = -0.65f), config))
        assertEquals(Gaze(0.5f, 0f), lookAfter(face(3, x = 0.5f), config)) // not near an edge
    }

    @Test
    fun `closeness grows with face size`() {
        assertEquals(0f, trackFace(face(1, size = 0.1f)).closeness, 0.001f)
        assertEquals(1f, frame(face(1, size = 0.5f)).closeness, 0.001f)
        assertEquals(0.5f, frame(face(1, size = (config.farSize + config.nearSize) / 2)).closeness, 0.001f)
    }

    // --- Mirror blink ---------------------------------------------------------------------------

    @Test
    fun `mirror blink triggers when the tracked face's eyes are closed`() {
        trackFace(face(1, eyesOpen = 0.9f))
        assertFalse(frame(face(1, eyesOpen = 0.9f)).mirrorBlink)
        assertTrue(frame(face(1, eyesOpen = 0.1f)).mirrorBlink)
        assertFalse(frame(face(1, eyesOpen = 0.3f)).mirrorBlink) // threshold is strict
        assertFalse(frame(face(1, eyesOpen = null)).mirrorBlink) // unknown = don't blink
    }

    @Test
    fun `mirror blink fires once when their eyes close, not for as long as they stay closed`() {
        trackFace(face(1, eyesOpen = 0.9f))
        assertFalse(frame(face(1, eyesOpen = 0.9f), stepMs = 33).mirrorBlinkStart)

        assertTrue(frame(face(1, eyesOpen = 0.1f), stepMs = 33).mirrorBlinkStart)
        // Looking down at the phone: classified closed for seconds. Still just the one blink.
        val longClosed = (1..90).map { frame(face(1, eyesOpen = 0.1f), stepMs = 33) }
        assertTrue(longClosed.none { it.mirrorBlinkStart })
        assertTrue(longClosed.all { it.mirrorBlink })
        assertFalse(watcher.onTick(now).mirrorBlinkStart)

        // They open and blink again: another blink.
        frame(face(1, eyesOpen = 0.9f), stepMs = 33)
        assertTrue(frame(face(1, eyesOpen = 0.05f), stepMs = 33).mirrorBlinkStart)
    }

    @Test
    fun `mirror blinks are rate-limited against flickering classification`() {
        trackFace(face(1))
        assertTrue(frame(face(1, eyesOpen = 0.1f), stepMs = 100).mirrorBlinkStart)
        frame(face(1, eyesOpen = 0.9f), stepMs = 100)
        assertFalse("within 600 ms", frame(face(1, eyesOpen = 0.1f), stepMs = 100).mirrorBlinkStart)
        frame(face(1, eyesOpen = 0.9f), stepMs = 400)
        assertTrue("after 600 ms", frame(face(1, eyesOpen = 0.1f)).mirrorBlinkStart)
    }

    @Test
    fun `mirror blink ignores a stranger closing their eyes`() {
        trackFace(face(1))
        val s = frame(face(1), face(2, eyesOpen = 0f))
        assertFalse(s.mirrorBlink)
        assertFalse(s.mirrorBlinkStart)
    }

    // --- Sight ----------------------------------------------------------------------------------

    @Test
    fun `DARK only after at least 1000 ms below the threshold`() {
        frame(luma = 20)
        now = 999
        assertEquals(Sight.SEEING, frame(luma = 20).sight)
        now = 1_000
        assertEquals(Sight.DARK, frame(luma = 20).sight)
    }

    @Test
    fun `a single bright frame resets the dark timer`() {
        frame(luma = 20)
        now = 800
        frame(luma = 100)
        now = 1_200
        assertEquals(Sight.SEEING, frame(luma = 20).sight)
        now = 2_200
        assertEquals(Sight.DARK, frame(luma = 20).sight)
    }

    @Test
    fun `dark hysteresis - 38 does not flip it back, 46 does`() {
        frames(1_100, luma = 10)
        assertEquals(Sight.DARK, watcher.state.sight)
        assertEquals(Sight.DARK, frames(2_000, luma = 38).sight)
        assertEquals(Sight.DARK, frame(luma = 45).sight)
        assertEquals(Sight.SEEING, frame(luma = 46).sight)
    }

    @Test
    fun `going dark forgets the tracked person and turns frantic`() {
        trackFace(face(1))
        val s = frames(1_100, face(1), luma = 10)
        assertEquals(Sight.DARK, s.sight)
        assertEquals(Watch.Idle, s.watch)
        assertTrue(s.isFrantic)
        assertNull(s.gaze)
    }

    @Test
    fun `camera error is frantic`() {
        val s = watcher.onPerception(Perception.CameraError(RuntimeException("boom")), 0)
        assertEquals(Sight.CAMERA_ERROR, s.sight)
        assertEquals(0f, s.franticIntensity)
    }

    // --- Frantic --------------------------------------------------------------------------------

    @Test
    fun `NoPermission is frantic and intensity ramps from 0 to 1 over 10 s`() {
        val start = 5_000L
        val s = watcher.onPerception(Perception.NoPermission, start)
        assertEquals(Sight.NO_PERMISSION, s.sight)
        assertEquals(0f, s.franticIntensity)
        assertEquals(0.25f, watcher.onTick(start + 2_500).franticIntensity!!, 0.0001f)
        assertEquals(0.5f, watcher.onTick(start + 5_000).franticIntensity!!, 0.0001f)
        assertEquals(1f, watcher.onTick(start + 10_000).franticIntensity!!, 0.0001f)
        assertEquals(1f, watcher.onTick(start + 60_000).franticIntensity!!, 0.0001f)
    }

    @Test
    fun `repeated blind perceptions don't restart the ramp`() {
        watcher.onPerception(Perception.NoPermission, 0)
        watcher.onPerception(Perception.NoPermission, 3_000)
        assertEquals(0.6f, watcher.onPerception(Perception.NoPermission, 6_000).franticIntensity!!, 0.0001f)
    }

    @Test
    fun `seeing again stops the panic`() {
        watcher.onPerception(Perception.NoPermission, 0)
        now = 4_000
        val s = frame()
        assertEquals(Sight.SEEING, s.sight)
        assertNull(s.franticIntensity)
    }

    // --- Relief ---------------------------------------------------------------------------------

    @Test
    fun `from Frantic, seeing a face reaches Tracking within 150 ms and emits Relief once`() {
        watcher.onPerception(Perception.NoPermission, 0)
        now = 8_000
        val f = face(1, x = 0.1f)

        val first = frame(f)
        assertTrue((first.watch as Watch.Acquiring).desperate)
        assertFalse(first.relief)

        now = 8_100
        assertFalse(frame(f).relief)
        now = 8_150
        val found = frame(f)
        assertEquals(Watch.Tracking(1, f), found.watch)
        assertTrue(found.relief)

        // One-shot: never again for the same recovery, by frame or by tick.
        assertFalse(frames(2_000, f).relief)
        assertFalse(watcher.onTick(now).relief)

        // Losing and re-finding them is not relief either.
        frame()
        assertFalse(frame(face(1)).relief)
    }

    @Test
    fun `without blindness, acquisition takes the normal 400 ms and brings no relief`() {
        val f = face(1)
        frame(f)
        now = 150
        assertTrue(frame(f).watch is Watch.Acquiring)
        now = 400
        val s = frame(f)
        assertTrue(s.watch is Watch.Tracking)
        assertFalse(s.relief)
    }

    @Test
    fun `relief window expires if nobody shows up`() {
        watcher.onPerception(Perception.NoPermission, 0)
        now = 1_000
        frames(config.reliefWindowMs + 100) // seeing, but nobody there
        val f = face(1)
        frame(f)
        now += config.reliefAcquireMs
        assertTrue(frame(f).watch is Watch.Acquiring) // back to the patient 400 ms
    }

    // --- Sleep & wake ---------------------------------------------------------------------------

    /** Empty-room frames at 10 fps, then ticks fill the gaps like the ViewModel would. */
    private fun alone(durationMs: Long, luma: Int = 120): WatcherState = frames(durationMs, luma = luma)

    @Test
    fun `never drowsy while it has someone to watch`() {
        trackFace(face(1))
        val s = frames(config.sleepAfterMs + 5_000, face(1))
        assertEquals(Alertness.AWAKE, s.alertness)
        assertEquals(0f, s.drowsiness)
        assertTrue(s.watch is Watch.Tracking)
    }

    @Test
    fun `waiting for someone who left is not being alone`() {
        trackFace(face(1))
        frame()
        assertNull(watcher.state.aloneSince)
        assertTrue(watcher.state.watch is Watch.Lost)
    }

    @Test
    fun `a stranger passing by resets the alone timer`() {
        alone(40_000)
        frame(face(9)) // anyone at all
        val s = alone(40_000)
        assertEquals(Alertness.AWAKE, s.alertness)
    }

    @Test
    fun `gets drowsy for the last 15 s, then falls asleep at 60 s alone`() {
        val start = now
        frame()
        assertEquals(Alertness.AWAKE, watcher.onTick(start + 44_999).alertness)
        val halfway = watcher.onTick(start + 52_500)
        assertEquals(Alertness.DROWSY, halfway.alertness)
        assertEquals(0.5f, halfway.drowsiness, 0.001f)

        // Ticks alone are enough: no frames needed for time to pass.
        val asleep = watcher.onTick(start + 60_000)
        assertEquals(Alertness.ASLEEP, asleep.alertness)
        assertEquals(SleepReason.ALONE, asleep.sleepReason)
        assertNull(asleep.gaze)
    }

    @Test
    fun `a face while drowsy just perks it up, without a startle`() {
        alone(50_000)
        assertEquals(Alertness.DROWSY, watcher.state.alertness)
        val s = frame(face(1))
        assertEquals(Alertness.AWAKE, s.alertness)
        assertEquals(0f, s.drowsiness)
        assertFalse(s.wake)
        assertTrue(s.watch is Watch.Acquiring)
    }

    @Test
    fun `a face while asleep startles it awake once and it goes for the biggest face`() {
        alone(config.sleepAfterMs + 1_000)
        assertTrue(watcher.state.isAsleep)

        val s = frame(face(1, size = 0.1f), face(2, size = 0.3f), stepMs = 33)
        assertTrue(s.wake)
        assertEquals(Alertness.AWAKE, s.alertness)
        assertEquals(2, (s.watch as Watch.Acquiring).id)
        assertFalse((s.watch as Watch.Acquiring).desperate)

        assertFalse(frame(face(2, size = 0.3f), stepMs = 33).wake)
        assertFalse(watcher.onTick(now).wake)
    }

    @Test
    fun `asleep, darkness doesn't make it panic`() {
        alone(config.sleepAfterMs + 1_000)
        val s = frames(20_000, luma = 5)
        assertTrue(s.isAsleep)
        assertEquals(Sight.DARK, s.sight) // still measured, for the overlay
        assertNull(s.franticIntensity)
        assertFalse(s.isFrantic)
    }

    @Test
    fun `a long panic exhausts it into sleep`() {
        watcher.onPerception(Perception.NoPermission, 0)
        val maxPanic = watcher.onTick(config.franticRampMs + config.exhaustAfterMs - 1)
        assertEquals(1f, maxPanic.franticIntensity)
        assertEquals(Alertness.AWAKE, maxPanic.alertness)

        val exhausted = watcher.onTick(config.franticRampMs + config.exhaustAfterMs)
        assertTrue(exhausted.isAsleep)
        assertEquals(SleepReason.EXHAUSTED, exhausted.sleepReason)
        assertNull(exhausted.franticIntensity)

        // Still no permission: stays asleep rather than panicking again.
        assertTrue(watcher.onPerception(Perception.NoPermission, 100_000).isAsleep)
    }

    @Test
    fun `waking from an exhausted sleep is a startle, not relief`() {
        // Dark only counts as blind after darkAfterMs.
        frames(config.darkAfterMs + config.franticRampMs + config.exhaustAfterMs + 500, luma = 5)
        assertEquals(SleepReason.EXHAUSTED, watcher.state.sleepReason)

        val f = face(1)
        val woke = frame(f, stepMs = 50)
        assertTrue(woke.wake)
        val all = (1..20).map { frame(f, stepMs = 50) }
        assertTrue(all.any { it.watch is Watch.Tracking })
        assertTrue(all.none { it.relief })
    }

    @Test
    fun `sleepNow puts it to sleep immediately`() {
        trackFace(face(1))
        val s = watcher.sleepNow(now)
        assertTrue(s.isAsleep)
        assertEquals(Watch.Idle, s.watch)
        assertTrue(frame(face(1)).wake)
    }
}
