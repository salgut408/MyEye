package com.salg.myeye.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.salg.myeye.EyeUiState
import com.salg.myeye.SourceMode
import com.salg.myeye.ui.eye.LivingEye

/**
 * The whole app: one eye. Tap = [onTap] (used to ask for the camera again), long-press = toggle
 * the debug overlay.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WatcherScreen(
    state: EyeUiState,
    onTap: () -> Unit,
    onSelectSource: (SourceMode) -> Unit,
    showSourcePicker: Boolean,
    modifier: Modifier = Modifier,
    onNap: () -> Unit = {},
) {
    var debugVisible by rememberSaveable { mutableStateOf(false) }
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .semantics { contentDescription = "The Watcher" }
            .combinedClickable(
                interactionSource = null,
                indication = null,
                onClickLabel = "Let the eye see",
                onLongClickLabel = "Toggle debug overlay",
                onLongClick = { debugVisible = !debugVisible },
                onClick = onTap,
            ),
    ) {
        LivingEye(
            behavior = state.behavior,
            reliefKey = state.reliefKey,
            blinkKey = state.blinkKey,
            wakeKey = state.wakeKey,
            modifier = Modifier.fillMaxSize(),
        )
        if (debugVisible) {
            DebugOverlay(state, showSourcePicker, onSelectSource, onNap)
        }
    }
}
