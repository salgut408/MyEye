package com.salg.myeye

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salg.myeye.camera.CameraFaceSource
import com.salg.myeye.ui.WatcherScreen
import com.salg.myeye.ui.rememberCameraPermission
import com.salg.myeye.ui.theme.MyEyeTheme
import com.salg.myeye.watch.Sight

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
                val viewModel: WatcherViewModel = viewModel(
                    factory = WatcherViewModel.factory(
                        cameraSource = CameraFaceSource(applicationContext),
                        initialSource = SourceMode.Camera,
                    ),
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val askForCamera = rememberCameraPermission(viewModel::onCameraPermission)
                WatcherScreen(
                    state = state,
                    onTap = {
                        if (state.source == SourceMode.Camera && state.watcher.sight == Sight.NO_PERMISSION) askForCamera()
                    },
                    onSelectSource = viewModel::useSource,
                    showSourcePicker = BuildConfig.DEBUG,
                )
            }
        }
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
