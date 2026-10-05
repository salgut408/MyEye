package com.salg.myeye

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.salg.myeye.ui.eye.EyeBehavior
import com.salg.myeye.ui.eye.LivingEye
import com.salg.myeye.ui.theme.MyEyeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // An artwork, not a tool: hide the system bars; a swipe from the edge brings them back.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        setContent {
            MyEyeTheme {
                KeepScreenOn()
                FingerFollowScreen()
            }
        }
    }
}

/** The eye follows a finger. Placeholder input until the face pipeline is wired in. */
@Composable
private fun FingerFollowScreen() {
    var touch by remember { mutableStateOf<Offset?>(null) }
    LivingEye(
        behavior = touch?.let { EyeBehavior.Tracking(it) } ?: EyeBehavior.Idle,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                awaitEachGesture {
                    touch = awaitFirstDown().position.normalizedIn(size)
                    do {
                        val event = awaitPointerEvent()
                        event.changes.firstOrNull()?.let { touch = it.position.normalizedIn(size) }
                    } while (event.changes.any { it.pressed })
                    touch = null
                }
            },
    )
}

/** Screen position → [-1, 1] on both axes, center = 0. */
private fun Offset.normalizedIn(size: IntSize) = Offset(
    (x - size.width / 2f) / (size.width / 2f),
    (y - size.height / 2f) / (size.height / 2f),
)

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
