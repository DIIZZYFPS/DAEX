package com.daex.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.daex.android.ui.theme.DaexAppTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/**
 * Snapshot/screenshot test: renders the composable to a real bitmap on the JVM (no device) via
 * Roborazzi and diffs it against a committed baseline in src/test/screenshots/. `PAUSED` looper
 * mode freezes the clock so the infinite "breathing" animations in these composables don't make
 * the captured frame non-deterministic across runs - the screenshot always captures each
 * animation's initial value.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class SuggestedPromptsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `suggested prompts empty-state screenshot`() {
        composeRule.setContent {
            DaexAppTheme {
                Box(modifier = Modifier.size(360.dp, 640.dp)) {
                    SuggestedPrompts(
                        prompts = listOf(
                            "Explain quantum entanglement simply",
                            "Write a haiku about midnight code",
                            "Plan a 3-day trip to Lisbon"
                        ),
                        onSelectPrompt = {}
                    )
                }
            }
        }

        composeRule.onRoot().captureRoboImage("src/test/screenshots/suggested_prompts_default.png")
    }
}
