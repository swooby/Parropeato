package com.swooby.parropeato.testing

import android.app.Activity
import android.app.LocaleManager
import android.content.ContentValues
import android.os.LocaleList
import android.provider.MediaStore
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.swooby.parropeato.common.R
import java.io.File
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runners.Parameterized
import org.xmlpull.v1.XmlPullParser

abstract class DiagnosticsConsentLocaleScreenshotTestBase<T : Activity>(
    private val activityClass: Class<T>,
    private val screenshotGroup: String,
    private val localeTag: String,
) {
    @After
    fun resetLocale() {
        targetContext.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.getEmptyLocaleList()
    }

    @Test
    fun firstLaunchShowsDiagnosticsConsentMessageAndCapturesScreenshot() {
        clearFirstRunState()
        setAppLocale(localeTag)

        ActivityScenario.launch(activityClass).use {
            val expectedMessage = targetContext.getString(R.string.diagnostics_consent_message)
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val message = device.wait(Until.findObject(By.text(expectedMessage)), DialogTimeoutMs)

            assertNotNull("Missing diagnostics consent message for $localeTag: $expectedMessage", message)
            assertTrue("Diagnostics consent message is not visible for $localeTag", message.visibleBounds.height() > 0)

            val screenshot = screenshotFile(localeTag)
            assertTrue("Failed to capture $screenshot", device.takeScreenshot(screenshot))
            val publicPath = publishScreenshot(screenshot)
            Log.i(Tag, "Saved diagnostics consent screenshot for $localeTag to $publicPath")

            val negativeButton = device.wait(
                Until.findObject(By.text(targetContext.getString(R.string.diagnostics_consent_negative))),
                DialogTimeoutMs,
            )
            negativeButton?.click()
        }
    }

    private fun clearFirstRunState() {
        targetContext
            .getSharedPreferences("parropeato_settings", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun setAppLocale(tag: String) {
        targetContext.getSystemService(LocaleManager::class.java).applicationLocales =
            LocaleList.forLanguageTags(tag)
    }

    private fun screenshotFile(tag: String): File {
        val dir = File(targetContext.filesDir, "test-screenshots/diagnostics-consent/$screenshotGroup")
        assertTrue("Failed to create screenshot dir $dir", dir.mkdirs() || dir.isDirectory)
        return File(dir, "diagnostics-consent-${tag.replace('-', '_')}.png")
    }

    private fun publishScreenshot(screenshot: File): String {
        val publicDir = "Pictures/Parropeato/diagnostics-consent/$screenshotGroup"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, screenshot.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, publicDir)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = targetContext.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw AssertionError("Failed to create MediaStore entry for $screenshot")

        val output = resolver.openOutputStream(uri)
            ?: throw AssertionError("Failed to open MediaStore output stream for $uri")
        output.use { out ->
            screenshot.inputStream().use { input -> input.copyTo(out) }
        }

        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        return "/sdcard/$publicDir/${screenshot.name}"
    }

    companion object {
        private const val Tag = "DiagnosticsConsentTest"
        private const val DialogTimeoutMs = 5_000L
        private const val androidNamespace = "http://schemas.android.com/apk/res/android"

        private val targetContext
            get() = InstrumentationRegistry.getInstrumentation().targetContext

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun locales(): List<String> = targetContext.resources
            .getXml(R.xml.locales_config)
            .use { parser ->
                buildList {
                    var event = parser.eventType
                    while (event != XmlPullParser.END_DOCUMENT) {
                        if (event == XmlPullParser.START_TAG && parser.name == "locale") {
                            parser.getAttributeValue(androidNamespace, "name")?.let(::add)
                        }
                        event = parser.next()
                    }
                }
            }
    }
}
