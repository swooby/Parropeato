package com.swooby.parropeato.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swooby.parropeato.common.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchDisplaysHoldMicToTalkGreeting() {
        val greeting = composeTestRule.activity.getString(R.string.status_hold_mic_to_talk)
        composeTestRule.onNodeWithText(greeting).assertIsDisplayed()
    }
}
