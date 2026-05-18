package com.swooby.parropeato.presentation

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swooby.parropeato.common.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @Test
    fun launchDisplaysHoldToTalkControl() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context
            .getSharedPreferences("parropeato_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putBoolean("diagnostics_prompt_shown", true)
            .commit()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var holdToTalkDescription: String
            scenario.onActivity { activity ->
                holdToTalkDescription = activity.getString(R.string.cd_hold_to_talk)
            }
            composeTestRule.onNodeWithContentDescription(holdToTalkDescription).assertIsDisplayed()
        }
    }
}
