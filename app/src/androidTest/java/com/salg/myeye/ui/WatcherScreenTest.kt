package com.salg.myeye.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salg.myeye.EyeUiState
import com.salg.myeye.toBehavior
import com.salg.myeye.ui.theme.MyEyeTheme
import com.salg.myeye.watch.ObsessiveWatcher
import com.salg.myeye.watch.Perception
import com.salg.myeye.watch.SeenFace
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Smoke test: the screen shows the eye, tap reaches the app, long-press toggles the overlay. */
@RunWith(AndroidJUnit4::class)
class WatcherScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val state = ObsessiveWatcher().run {
        onPerception(Perception.Frame(listOf(SeenFace(3, 0.2f, 0f, 0.2f, 0.9f)), meanLuma = 87, timestampMs = 0), 0)
        onPerception(Perception.Frame(listOf(SeenFace(3, 0.2f, 0f, 0.2f, 0.9f)), meanLuma = 87, timestampMs = 500), 500)
    }.let { EyeUiState(behavior = it.toBehavior(), watcher = it) }

    @Test
    fun longPressTogglesDebugOverlayAndTapIsForwarded() {
        var taps = 0
        rule.setContent {
            MyEyeTheme {
                WatcherScreen(state = state, onTap = { taps++ }, onSelectSource = {}, showSourcePicker = true)
            }
        }
        val eye = rule.onNodeWithContentDescription("The Watcher")
        eye.assertIsDisplayed()
        rule.onNodeWithText("Tracking #3", substring = true).assertDoesNotExist()

        eye.performTouchInput { longClick() }
        rule.onNodeWithText("Tracking #3", substring = true).assertIsDisplayed()
        rule.onNodeWithText("luma    87", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Fake: Stranger steps closer").assertIsDisplayed()

        eye.performTouchInput { longClick() }
        rule.onNodeWithText("Tracking #3", substring = true).assertDoesNotExist()

        eye.performClick()
        assertEquals(1, taps)
    }
}
