package com.salg.myeye.camera

import com.salg.myeye.watch.Alertness
import com.salg.myeye.watch.ObsessiveWatcher
import com.salg.myeye.watch.Perception
import com.salg.myeye.watch.Sight
import com.salg.myeye.watch.SleepReason
import com.salg.myeye.watch.Watch
import com.salg.myeye.watch.WatcherState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Each scripted scenario must actually provoke the behavior it is named after. */
class FakeScenariosTest {

    private class Replay(val states: List<Pair<Long, WatcherState>>) {
        fun at(ms: Long) = states.last { it.first <= ms }.second
        fun between(from: Long, to: Long) = states.filter { it.first in from..to }.map { it.second }
    }

    private fun replay(scenario: FakeScenario, untilMs: Long = scenario.durationMs - 1, stepMs: Long = 66): Replay {
        val watcher = ObsessiveWatcher()
        val out = mutableListOf<Pair<Long, WatcherState>>()
        var t = 0L
        while (t <= untilMs) {
            val scene = scenario.sceneAt(t % scenario.durationMs, (t / scenario.durationMs).toInt())
            out += t to watcher.onPerception(Perception.Frame(scene.faces, scene.luma, t), t)
            t += stepMs
        }
        return Replay(out)
    }

    @Test
    fun `walk across - the gaze follows the person from left to right`() {
        val r = replay(FakeScenarios.WalkAcross)
        val xs = r.between(1_000, 5_000).map { it.gaze!!.x }
        assertTrue(r.between(1_000, 5_000).all { it.watch is Watch.Tracking })
        assertEquals(xs.sorted(), xs)
        assertTrue(r.at(6_500).watch is Watch.Lost)
    }

    @Test
    fun `stranger steps closer - the eye never switches`() {
        val r = replay(FakeScenarios.StrangerStepsCloser)
        val during = r.between(2_500, 8_400)
        assertTrue(during.all { it.targetId == 1 && it.watch is Watch.Tracking })
        assertTrue("stranger really is bigger", r.at(7_000).lastFrame!!.faces.maxBy { it.size }.id == 2)
    }

    @Test
    fun `leaves and returns - looks after, re-acquires the new id, then gives up`() {
        val r = replay(FakeScenarios.LeavesAndReturns)
        val lost = r.at(5_000)
        assertTrue(lost.watch is Watch.Lost)
        assertEquals(1f, lost.gaze!!.x, 0.001f) // looking after them, past the right edge

        // Reappears with a new id near where they left: straight back to Tracking, no Acquiring.
        assertTrue(r.between(6_000, 8_000).none { it.watch is Watch.Acquiring })
        assertEquals(Watch.Tracking::class, r.at(6_100).watch::class)
        assertEquals(2, r.at(6_100).targetId)

        assertTrue(r.at(13_000).watch is Watch.Lost)
        assertEquals(Watch.Idle, r.at(14_100).watch)
    }

    @Test
    fun `lights off - panics in the dark, relief when they're back`() {
        val r = replay(FakeScenarios.LightsOff)
        assertEquals(Sight.DARK, r.at(5_000).sight)
        assertTrue(r.at(5_000).isFrantic)
        assertTrue(r.at(7_900).franticIntensity!! > r.at(5_000).franticIntensity!!)

        val after = r.between(8_000, 11_000)
        assertEquals(1, after.count { it.relief })
        assertTrue(r.at(8_300).watch is Watch.Tracking)
    }

    @Test
    fun `blinker - mirror blink follows the person's eyes`() {
        val r = replay(FakeScenarios.Blinker)
        assertFalse(r.at(800).mirrorBlink)
        assertTrue(r.at(1_100).mirrorBlink)
        assertFalse(r.at(1_400).mirrorBlink)
        assertTrue(r.at(5_500).mirrorBlink)
    }

    @Test
    fun `tour plays every scenario back to back with fresh ids`() {
        val tour = FakeScenarios.Tour
        assertEquals(FakeScenarios.all.sumOf { it.durationMs }, tour.durationMs)
        val ids = (0 until tour.durationMs step 500).flatMap { t -> tour.sceneAt(t, 0).faces.map { it.id } }.toSet()
        val perScenario = FakeScenarios.all.indices.map { i -> ids.filter { it / 10 == i } }
        assertTrue("every scenario contributes its own ids", perScenario.all { it.isNotEmpty() })
    }

    @Test
    fun `nobody home - drowsy, asleep, then startled awake by the visitor`() {
        val r = replay(FakeScenarios.NobodyHome)
        assertEquals(Alertness.AWAKE, r.at(40_000).alertness)
        assertEquals(Alertness.DROWSY, r.at(50_000).alertness)
        assertTrue(r.at(61_000).isAsleep)
        assertEquals(1, r.between(74_000, 84_000).count { it.wake })
        assertTrue(r.at(76_000).watch is Watch.Tracking)
    }

    @Test
    fun `covered for long - panic, exhausted sleep, startle not relief`() {
        val r = replay(FakeScenarios.CoveredForLong)
        assertTrue(r.at(30_000).isFrantic)
        assertEquals(SleepReason.EXHAUSTED, r.at(75_000).sleepReason)
        assertFalse(r.at(75_000).isFrantic)
        val after = r.between(81_000, 91_000)
        assertEquals(1, after.count { it.wake })
        assertTrue(after.none { it.relief })
    }

    @Test
    fun `fake source slows to one frame per second while in low power`() = runTest {
        val source = FakeFaceSource(FakeScenarios.WalkAcross)
        val normal = source.perceptions(MutableStateFlow(false)).take(3).toList()
        assertEquals(listOf(0L, 66L, 132L), normal.map { (it as Perception.Frame).timestampMs })
        val asleep = source.perceptions(MutableStateFlow(true)).take(3).toList()
        assertEquals(listOf(0L, 1_000L, 2_000L), asleep.map { (it as Perception.Frame).timestampMs })
    }
}
