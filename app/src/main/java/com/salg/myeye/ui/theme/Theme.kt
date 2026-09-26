package com.salg.myeye.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// The app is an artwork on a black stage: always dark, no dynamic color.
private val WatcherColorScheme = darkColorScheme(
    primary = IrisTeal,
    background = Void,
    surface = Void,
    onBackground = Sclera,
    onSurface = Sclera,
)

@Composable
fun MyEyeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WatcherColorScheme,
        typography = Typography,
        content = content
    )
}
