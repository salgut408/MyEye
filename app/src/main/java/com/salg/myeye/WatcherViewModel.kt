package com.salg.myeye

import android.os.SystemClock
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.salg.myeye.camera.FaceSource
import com.salg.myeye.camera.FakeFaceSource
import com.salg.myeye.camera.FakeScenario
import com.salg.myeye.ui.eye.EyeBehavior
import com.salg.myeye.watch.Clock
import com.salg.myeye.watch.Gaze
import com.salg.myeye.watch.ObsessiveWatcher
import com.salg.myeye.watch.Perception
import com.salg.myeye.watch.Watch
import com.salg.myeye.watch.WatcherConfig
import com.salg.myeye.watch.WatcherState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Where perceptions come from. */
sealed interface SourceMode {
    data object Camera : SourceMode
    data class Fake(val scenario: FakeScenario) : SourceMode
}

data class EyeUiState(
    val behavior: EyeBehavior = EyeBehavior.Idle,
    /** Increments once per relief moment; the eye plays it when this changes. */
    val reliefKey: Int = 0,
    /** Increments each time the watched person blinks; the eye blinks with them. */
    val blinkKey: Int = 0,
    /** Full watcher state, for the debug overlay. */
    val watcher: WatcherState = WatcherState(),
    val analysisFps: Float = 0f,
    val source: SourceMode = SourceMode.Camera,
)

/**
 * Owns the watcher and everything it remembers. Data flows one way:
 * [FaceSource] → [ObsessiveWatcher] → [EyeUiState] → Compose.
 *
 * Perceiving only happens while the UI collects [uiState] (i.e. while the app is visible), so the
 * camera is released as soon as the app leaves the screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatcherViewModel(
    private val cameraSource: FaceSource,
    private val clock: Clock,
    config: WatcherConfig = WatcherConfig(),
    initialSource: SourceMode = SourceMode.Camera,
    private val fakeSource: (FakeScenario) -> FaceSource = ::FakeFaceSource,
) : ViewModel() {

    private val watcher = ObsessiveWatcher(config)
    private val cameraPermission = MutableStateFlow<Boolean?>(null) // null = not known yet
    private val source = MutableStateFlow(initialSource)
    private var reliefKey = 0
    private var blinkKey = 0
    private val fps = FpsMeter()

    fun onCameraPermission(granted: Boolean) {
        cameraPermission.value = granted
    }

    fun useSource(mode: SourceMode) {
        source.value = mode
    }

    val uiState: StateFlow<EyeUiState> = channelFlow {
        suspend fun publish(s: WatcherState) {
            if (s.relief) reliefKey++
            if (s.mirrorBlinkStart) blinkKey++
            send(EyeUiState(s.toBehavior(), reliefKey, blinkKey, s, fps.value, source.value))
        }
        launch {
            while (true) {
                delay(TICK_MS)
                publish(watcher.onTick(clock.nowMs()))
            }
        }
        perceptions().collect { p ->
            val now = clock.nowMs()
            if (p is Perception.Frame) fps.onFrame(now)
            publish(watcher.onPerception(p, now))
        }
    }.stateIn(
        viewModelScope,
        // Stop perceiving the moment nobody is looking at the eye: the camera is released at once.
        SharingStarted.WhileSubscribed(stopTimeoutMillis = 0),
        EyeUiState(source = initialSource),
    )

    private fun perceptions(): Flow<Perception> = source.flatMapLatest { mode ->
        when (mode) {
            is SourceMode.Fake -> fakeSource(mode.scenario).perceptions()
            SourceMode.Camera -> cameraPermission.flatMapLatest { granted ->
                when (granted) {
                    null -> emptyFlow()
                    false -> flowOf(Perception.NoPermission)
                    true -> cameraSource.perceptions()
                }
            }
        }
    }

    companion object {
        const val TICK_MS = 100L

        fun factory(cameraSource: FaceSource, initialSource: SourceMode) = viewModelFactory {
            initializer {
                WatcherViewModel(
                    cameraSource = cameraSource,
                    clock = Clock { SystemClock.elapsedRealtime() },
                    initialSource = initialSource,
                )
            }
        }
    }
}

fun WatcherState.toBehavior(): EyeBehavior {
    franticIntensity?.let { return EyeBehavior.Frantic(it) }
    val target = gaze?.toOffset() ?: Offset.Zero
    return when (watch) {
        Watch.Idle -> EyeBehavior.Idle
        is Watch.Acquiring -> EyeBehavior.Acquiring(target)
        is Watch.Tracking -> EyeBehavior.Tracking(target, closeness)
        is Watch.Lost -> EyeBehavior.Lost(target)
    }
}

private fun Gaze.toOffset() = Offset(x, y)

/** Smoothed frames per second from frame arrival times. */
private class FpsMeter {
    private var last: Long? = null
    var value = 0f
        private set

    fun onFrame(nowMs: Long) {
        val prev = last
        last = nowMs
        if (prev == null || nowMs <= prev) return
        val instant = 1000f / (nowMs - prev)
        value = if (value == 0f) instant else value * 0.9f + instant * 0.1f
    }
}
