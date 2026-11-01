package com.privateai

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.privateai.ui.MainActivity
import org.junit.Rule
import org.junit.Test

/**
 * Instrumentation tests for the chat UI.
 *
 * These tests run on a real Android device or emulator.
 * They verify the UI components are displayed and interactive.
 */
class ChatScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appTitle_isDisplayed() {
        composeTestRule.onNodeWithText("Jarvis", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun inputField_isDisplayed() {
        composeTestRule.onNodeWithContentDescription("Send message")
            .assertIsDisplayed()
    }

    @Test
    fun inputField_acceptsText() {
        composeTestRule.onNodeWithText("Type a message...")
            .performClick()
            .performTextInput("Hello, AI!")
    }

    @Test
    fun sendButton_exists() {
        composeTestRule.onNodeWithContentDescription("Send message")
            .assertIsDisplayed()
            .performClick()
    }

    @Test
    fun resetButton_exists() {
        composeTestRule.onNodeWithContentDescription("Reset conversation")
            .assertIsDisplayed()
    }
}
