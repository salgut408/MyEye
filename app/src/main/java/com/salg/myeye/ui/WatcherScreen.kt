package com.salg.myeye.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.salg.myeye.EyeUiState
import com.salg.myeye.ui.eye.LivingEye

@Composable
fun WatcherScreen(state: EyeUiState, modifier: Modifier = Modifier) {
    LivingEye(
        behavior = state.behavior,
        reliefKey = state.reliefKey,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    )
}
