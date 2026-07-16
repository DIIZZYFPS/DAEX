package com.daex.android.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.daex.android.framework.Message
import com.daex.android.ui.theme.DaexAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric-backed Compose UI test: renders the real composable tree and asserts against its
 * semantics, entirely on the JVM (no emulator/device). `sdk = [34]` pins to a Robolectric-shadowed
 * API level rather than the project's compileSdk 36, which Robolectric doesn't fully shadow yet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MessageLineTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun message(content: String = "Hello there", isPinned: Boolean = false) =
        Message(id = "m1", role = "model", content = content, isPinned = isPinned)

    @Test
    fun `shows LISTEN when not speaking`() {
        composeRule.setContent {
            DaexAppTheme {
                MessageLine(message = message(), isSpeaking = false, onSpeak = {})
            }
        }
        composeRule.onNodeWithText("▶ LISTEN").assertExists()
    }

    @Test
    fun `shows STOP when speaking`() {
        composeRule.setContent {
            DaexAppTheme {
                MessageLine(message = message(), isSpeaking = true, onSpeak = {})
            }
        }
        composeRule.onNodeWithText("■ STOP").assertExists()
    }

    @Test
    fun `clicking LISTEN invokes onSpeak`() {
        var speakClicked = false
        composeRule.setContent {
            DaexAppTheme {
                MessageLine(message = message(), onSpeak = { speakClicked = true })
            }
        }

        composeRule.onNodeWithText("▶ LISTEN").performClick()

        assert(speakClicked) { "Expected onSpeak to have been invoked" }
    }

    @Test
    fun `pin label reflects the message's pinned state`() {
        composeRule.setContent {
            DaexAppTheme {
                MessageLine(message = message(isPinned = true), onTogglePin = {})
            }
        }
        composeRule.onNodeWithText("◆ PINNED").assertExists()
    }

    @Test
    fun `clicking PIN invokes onTogglePin`() {
        var toggled = false
        composeRule.setContent {
            DaexAppTheme {
                MessageLine(message = message(isPinned = false), onTogglePin = { toggled = true })
            }
        }

        composeRule.onNodeWithText("◇ PIN").performClick()

        assert(toggled) { "Expected onTogglePin to have been invoked" }
    }

    @Test
    fun `speak and pin controls are absent when their callbacks are null`() {
        composeRule.setContent {
            DaexAppTheme {
                MessageLine(message = message())
            }
        }

        composeRule.onNodeWithText("▶ LISTEN").assertDoesNotExist()
        composeRule.onNodeWithText("◇ PIN").assertDoesNotExist()
    }
}
