package com.salg.myeye

import app.cash.turbine.test
import com.salg.myeye.camera.FaceSource
import com.salg.myeye.camera.FakeScenarios
import com.salg.myeye.ui.eye.EyeBehavior
import com.salg.myeye.watch.Clock
import com.salg.myeye.watch.Perception
import com.salg.myeye.watch.SeenFace
import com.salg.myeye.watch.Sight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WatcherViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(
        camera: FaceSource = FaceSource { emptyFlow() },
        source: SourceMode = SourceMode.Camera,
    ) = WatcherViewModel(camera, Clock { testScheduler.currentTime }, initialSource = source)

    /** A camera that sees one face at ~30 fps. */
    private val cameraWithAFace = FaceSource {
        flow {
            while (true) {
                emit(Perception.Frame(listOf(SeenFace(1, 0.3f, 0f, 0.2f, 0.9f)), 120, 0))
                delay(33)
            }
        }
    }

    @Test
    fun `starts calm, not frantic, while permission is unknown`() = runTest {
        viewModel().uiState.test {
            assertEquals(EyeBehavior.Idle, awaitItem().behavior)
            advanceTimeBy(2_000)
            expectNoEvents()
        }
    }

    @Test
    fun `fake scenario drives the eye through the real watcher`() = runTest {
        val vm = viewModel(source = SourceMode.Fake(FakeScenarios.StrangerStepsCloser))
        vm.uiState.test {
            advanceTimeBy(7_000)
            val state = expectMostRecentItem()
            val tracking = state.behavior as EyeBehavior.Tracking
            assertTrue("still watching its person on the left, not the stranger", tracking.target.x < 0f)
            assertEquals(15f, state.analysisFps, 1f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `each blink of the watched person bumps blinkKey once`() = runTest {
        val vm = viewModel(source = SourceMode.Fake(FakeScenarios.Blinker))
        vm.uiState.test {
            advanceTimeBy(900)
            assertEquals(0, expectMostRecentItem().blinkKey)
            advanceTimeBy(1_000) // blink at 1.0–1.25 s
            assertEquals(1, expectMostRecentItem().blinkKey)
            advanceTimeBy(4_000) // blinks at 3.0 s and a long one at 5.0–5.7 s
            assertEquals(3, expectMostRecentItem().blinkKey)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `denied permission is frantic and escalates over time`() = runTest {
        val vm = viewModel()
        vm.onCameraPermission(false)
        vm.uiState.test {
            advanceTimeBy(150)
            val early = (expectMostRecentItem().behavior as EyeBehavior.Frantic).intensity
            advanceTimeBy(5_000)
            val later = expectMostRecentItem()
            assertEquals(Sight.NO_PERMISSION, later.watcher.sight)
            assertEquals(early + 0.5f, (later.behavior as EyeBehavior.Frantic).intensity, 0.02f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `granting permission after panic brings exactly one relief`() = runTest {
        val vm = viewModel(camera = cameraWithAFace)
        vm.onCameraPermission(false)
        vm.uiState.test {
            advanceTimeBy(3_000)
            assertTrue(expectMostRecentItem().behavior is EyeBehavior.Frantic)

            vm.onCameraPermission(true)
            advanceTimeBy(250)
            val relieved = expectMostRecentItem()
            assertTrue(relieved.behavior is EyeBehavior.Tracking)
            assertEquals(1, relieved.reliefKey)

            advanceTimeBy(5_000)
            assertEquals(1, vm.uiState.value.reliefKey)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the camera only runs while someone collects the UI state`() = runTest {
        var activeCameras = 0
        val camera = FaceSource {
            flow<Perception> {
                activeCameras++
                try {
                    awaitCancellation()
                } finally {
                    activeCameras--
                }
            }
        }
        val vm = viewModel(camera = camera)
        vm.onCameraPermission(true)

        vm.uiState.test {
            runCurrent()
            assertEquals(1, activeCameras)
            cancelAndIgnoreRemainingEvents()
        }
        runCurrent()
        assertEquals(0, activeCameras)
    }

    @Test
    fun `watcher memory survives the UI going away and coming back`() = runTest {
        val vm = viewModel(camera = cameraWithAFace)
        vm.onCameraPermission(true)
        vm.uiState.test {
            advanceTimeBy(1_000)
            assertTrue(expectMostRecentItem().behavior is EyeBehavior.Tracking)
            cancelAndIgnoreRemainingEvents()
        }
        vm.uiState.test {
            // Same face id on return: still the same person, no fresh 400 ms acquisition.
            advanceTimeBy(50)
            assertTrue(expectMostRecentItem().behavior is EyeBehavior.Tracking)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
