package com.salg.myeye.ui.eye

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview
import com.salg.myeye.ui.theme.MyEyeTheme

// Every state drawn from fixed inputs (the static renderer), so previews are deterministic.

@Composable
private fun EyePreview(gaze: Offset, expression: EyeExpression, style: EyeStyle = EyeStyle()) {
    MyEyeTheme {
        Eye(
            gaze = gaze,
            expression = expression,
            style = style,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        )
    }
}

private const val W = 360
private const val H = 640

@Preview(name = "Idle", widthDp = W, heightDp = H)
@Composable
private fun IdlePreview() = EyePreview(Offset(0.1f, -0.05f), EyeExpression.Idle)

@Preview(name = "Acquiring", widthDp = W, heightDp = H)
@Composable
private fun AcquiringPreview() = EyePreview(Offset(-0.5f, 0.1f), EyeExpression.Acquiring)

@Preview(name = "Tracking (intense stare)", widthDp = W, heightDp = H)
@Composable
private fun TrackingPreview() = EyePreview(Offset(0.45f, 0.2f), EyeBehavior.Tracking(Offset(0.45f, 0.2f)).expression())

@Preview(name = "Tracking, very close (dilated)", widthDp = W, heightDp = H)
@Composable
private fun TrackingClosePreview() =
    EyePreview(Offset.Zero, EyeBehavior.Tracking(Offset.Zero, closeness = 1f).expression())

@Preview(name = "Lost (looking after them)", widthDp = W, heightDp = H)
@Composable
private fun LostPreview() = EyePreview(Offset(0.95f, 0f), EyeExpression.Lost)

@Preview(name = "Frantic", widthDp = W, heightDp = H)
@Composable
private fun FranticPreview() = EyePreview(Offset(-0.8f, -0.55f), EyeExpression.Frantic)

@Preview(name = "Relief (mid slow blink)", widthDp = W, heightDp = H)
@Composable
private fun ReliefPreview() =
    EyePreview(Offset.Zero, EyeExpression.Tracking.copy(lidOpen = 0.3f, pupil = EyeExpression.RELIEF_PUPIL))

@Preview(name = "Mirror blink (closed)", widthDp = W, heightDp = H)
@Composable
private fun BlinkPreview() = EyePreview(Offset(0.2f, 0f), EyeExpression.Tracking.copy(lidOpen = 0f))

@Preview(name = "Foreshortening at the edge", widthDp = W, heightDp = H)
@Composable
private fun ForeshortenPreview() = EyePreview(Offset(0f, 1f), EyeExpression.Frantic)

@Preview(name = "Skin-colored lids", widthDp = W, heightDp = H)
@Composable
private fun SkinLidsPreview() = EyePreview(
    Offset(-0.3f, 0.1f),
    EyeExpression.Tracking,
    EyeStyle(lid = androidx.compose.ui.graphics.Color(0xFFC98E6B)),
)
