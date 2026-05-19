package com.swooby.parropeato.testing

import android.app.Activity
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.swooby.parropeato.ACCENT_COLOR_OPTIONS
import com.swooby.parropeato.common.R
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

abstract class PlayStoreScreenshotTestBase<T : Activity>(
    private val activityClass: Class<T>,
    private val screenshotGroup: String,
) {
    private val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun test_01_watchFaceDefault()   { captureMainScreen("01_watch_face") }
    @Test fun test_02_settingsMain()       { captureSettingsScreen("02_settings_main") }
    @Test fun test_03_settingsStt()        { captureSettingsSubScreen("03_settings_stt", targetContext.getString(R.string.settings_section_stt_language)) }
    @Test fun test_04_settingsTts()        { captureSettingsSubScreen("04_settings_tts", targetContext.getString(R.string.settings_section_tts_language)) }
    @Test fun test_05_cuteIconsRed()       { captureWithCuteIcons(5,  R.string.accent_color_red) }
    @Test fun test_06_cuteIconsOrange()    { captureWithCuteIcons(6,  R.string.accent_color_orange) }
    @Test fun test_07_cuteIconsYellow()    { captureWithCuteIcons(7,  R.string.accent_color_yellow) }
    @Test fun test_08_cuteIconsGreen()     { captureWithCuteIcons(8,  R.string.accent_color_green) }
    @Test fun test_09_cuteIconsTeal()      { captureWithCuteIcons(9,  R.string.accent_color_teal) }
    @Test fun test_10_cuteIconsBlue()      { captureWithCuteIcons(10, R.string.accent_color_blue) }
    @Test fun test_11_cuteIconsPurple()    { captureWithCuteIcons(11, R.string.accent_color_purple) }
    @Test fun test_12_cuteIconsPink()      { captureWithCuteIcons(12, R.string.accent_color_pink) }

    private fun captureMainScreen(name: String) {
        setupPrefs()
        ActivityScenario.launch(activityClass).use {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            waitForMainScreen(device)
            device.waitForIdle()
            captureScreenshot(device, name)
        }
    }

    private fun captureSettingsScreen(name: String) {
        setupPrefs()
        ActivityScenario.launch(activityClass).use {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            waitForMainScreen(device)
            openSettings(device)
            device.waitForIdle()
            captureScreenshot(device, name)
        }
    }

    private fun captureSettingsSubScreen(name: String, rowText: String) {
        setupPrefs()
        ActivityScenario.launch(activityClass).use {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            waitForMainScreen(device)
            openSettings(device)
            val row = device.wait(Until.findObject(By.text(rowText)), UiTimeoutMs)
            assertNotNull("Settings row '$rowText' not found", row)
            row.click()
            device.waitForIdle()
            captureScreenshot(device, name)
        }
    }

    private fun captureWithCuteIcons(screenshotIndex: Int, nameResId: Int) {
        val option = ACCENT_COLOR_OPTIONS.first { it.nameResId == nameResId }
        setupPrefs(cuteIcons = true, accentColor = option.argb)
        ActivityScenario.launch(activityClass).use {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            waitForMainScreen(device)
            device.waitForIdle()
            val name = "${screenshotIndex.toString().padStart(2, '0')}_cute_icons_${option.analyticsName}"
            captureScreenshot(device, name)
        }
    }

    private fun setupPrefs(cuteIcons: Boolean = false, accentColor: Int? = null) {
        // Clear all prefs for a clean known state, then pre-mark the diagnostics prompt as shown
        // so the AlertDialog never fires and blocks the main screen.
        targetContext
            .getSharedPreferences("parropeato_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putBoolean("diagnostics_prompt_shown", true)
            .also { editor ->
                if (cuteIcons) editor.putBoolean("cute_icons", true)
                if (accentColor != null) editor.putInt("accent_color", accentColor)
            }
            .commit()
    }

    private fun waitForMainScreen(device: UiDevice) {
        val holdToTalkDesc = targetContext.getString(R.string.cd_hold_to_talk)
        val element = device.wait(Until.findObject(By.desc(holdToTalkDesc)), MainScreenTimeoutMs)
        assertNotNull("Hold-to-talk button not found within ${MainScreenTimeoutMs}ms", element)
    }

    private fun openSettings(device: UiDevice) {
        val gearDesc = targetContext.getString(R.string.cd_open_settings)
        val gear = device.wait(Until.findObject(By.desc(gearDesc)), UiTimeoutMs)
        assertNotNull("Settings gear button not found", gear)
        gear.click()
        val settingsTitle = targetContext.getString(R.string.settings_title)
        val title = device.wait(Until.findObject(By.text(settingsTitle)), UiTimeoutMs)
        assertNotNull("Settings screen did not open (title '$settingsTitle' not found)", title)
    }

    private fun captureScreenshot(device: UiDevice, name: String): String {
        val publicDir = "/sdcard/Pictures/Parropeato/play-store/$screenshotGroup"
        val publicPath = "$publicDir/$name.png"
        device.executeShellCommand("mkdir -p $publicDir")
        device.executeShellCommand("screencap -p $publicPath")
        val bytes = device.executeShellCommand("wc -c $publicPath")
            .trim()
            .substringBefore(' ')
            .toLongOrNull() ?: 0L
        assertTrue("Failed to capture $publicPath", bytes > 0)
        return publicPath
    }

    companion object {
        private const val MainScreenTimeoutMs = 10_000L
        private const val UiTimeoutMs = 5_000L
    }
}
