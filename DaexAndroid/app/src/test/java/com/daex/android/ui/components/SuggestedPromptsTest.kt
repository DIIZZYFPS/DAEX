package com.daex.android.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.daex.android.ui.theme.DaexAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SuggestedPromptsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders every suggested prompt`() {
        val prompts = listOf("Explain quantum entanglement simply", "Write a haiku about midnight code")
        composeRule.setContent {
            DaexAppTheme {
                SuggestedPrompts(prompts = prompts, onSelectPrompt = {})
            }
        }

        prompts.forEach { composeRule.onNodeWithText(it).assertExists() }
    }

    @Test
    fun `clicking a prompt reports exactly that prompt's text`() {
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

    @Test
    fun `shows the welcome copy even with no prompts`() {
        composeRule.setContent {
            DaexAppTheme {
                SuggestedPrompts(prompts = emptyList(), onSelectPrompt = {})
            }
        }

        composeRule.onNodeWithText("Welcome to D A E X").assertExists()
    }
}
