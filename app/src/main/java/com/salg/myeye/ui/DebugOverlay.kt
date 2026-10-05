package com.salg.myeye.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salg.myeye.EyeUiState
import com.salg.myeye.SourceMode
import com.salg.myeye.camera.FakeScenarios
import com.salg.myeye.ui.theme.DebugGreen
import com.salg.myeye.watch.Watch
import java.util.Locale

/**
 * Tuning overlay (long-press the eye). Shows what the eye perceives and decides, and in debug
 * builds lets you swap the camera for a scripted fake scenario.
 */
@Composable
fun DebugOverlay(
    state: EyeUiState,
    showSourcePicker: Boolean,
    onSelectSource: (SourceMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val w = state.watcher
    val frame = w.lastFrame
    Box(modifier.fillMaxSize()) {
        // Face boxes in the eye's normalized space (x, y ∈ [-1, 1], half-size = face size).
        Canvas(Modifier.fillMaxSize()) {
            frame?.faces?.forEach { face ->
                val color = if (face.id == w.targetId) DebugGreen else Color.Gray
                val left = (face.x - face.size + 1f) / 2f * size.width
                val top = (face.y - face.size + 1f) / 2f * size.height
                drawRect(
                    color,
                    topLeft = Offset(left, top),
                    size = Size(face.size * size.width, face.size * size.height),
                    style = Stroke(3.dp.toPx()),
                )
            }
        }

        val lines = buildList {
            add("sight   ${w.sight}")
            add("watch   ${w.watch.label}")
            add("target  ${w.targetId ?: "–"}")
            add("faces   ${frame?.faces?.joinToString { "#${it.id} size ${"%.2f".fmt(it.size)}" } ?: "–"}")
            add("eyes    ${frame?.faces?.joinToString { f -> f.eyesOpen?.let { "%.2f".fmt(it) } ?: "?" } ?: "–"}")
            add("luma    ${frame?.meanLuma ?: "–"}")
            add("fps     ${"%.1f".fmt(state.analysisFps)}")
            w.franticIntensity?.let { add("panic   ${"%.2f".fmt(it)}") }
            add("source  ${state.source.label}")
        }
        Text(
            text = lines.joinToString("\n"),
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = DebugGreen),
            modifier = Modifier
                .align(Alignment.TopStart)
                .safeDrawingPadding()
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                .padding(8.dp),
        )

        if (showSourcePicker) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding()
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp),
            ) {
                val options = listOf(SourceMode.Camera) +
                    (FakeScenarios.all + FakeScenarios.Tour).map { SourceMode.Fake(it) }
                options.forEach { option ->
                    FilterChip(
                        selected = option == state.source,
                        onClick = { onSelectSource(option) },
                        label = { Text(option.label) },
                    )
                }
            }
        }
    }
}

private val SourceMode.label
    get() = when (this) {
        SourceMode.Camera -> "Camera"
        is SourceMode.Fake -> "Fake: ${scenario.name}"
    }

private val Watch.label: String
    get() = when (this) {
        Watch.Idle -> "Idle"
        is Watch.Acquiring -> "Acquiring #$id" + if (desperate) " (desperate)" else ""
        is Watch.Tracking -> "Tracking #$id"
        is Watch.Lost -> "Lost #$id"
    }

private fun String.fmt(value: Float) = String.format(Locale.US, this, value)
