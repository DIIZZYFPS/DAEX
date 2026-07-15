package com.daex.android.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.daex.android.ui.theme.DaexAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-device counterpart to [SuggestedPromptsTest], which runs the same composable under
 * Robolectric on the JVM. This exercises actual on-device rendering and touch dispatch rather
 * than Robolectric's simulation - real device/emulator only (`:app:connectedDebugAndroidTest`),
 * cannot run in this environment which has no adb/device access - reviewed for correctness only.
 */
@RunWith(AndroidJUnit4::class)
class SuggestedPromptsInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun clickingAPromptReportsExactlyThatPromptsText() {
        var selected: String? = null
        composeRule.setContent {
            DaexAppTheme {
                SuggestedPrompts(
                    prompts = listOf("Plan a 3-day trip to Lisbon", "Draft a birthday message"),
                    onSelectPrompt = { selected = it }
                )
            }
        }

        composeRule.onNodeWithText("Draft a birthday message").performClick()

        assertEquals("Draft a birthday message", selected)
    }
}
