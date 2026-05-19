package com.swooby.parropeato.presentation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swooby.parropeato.testing.PlayStoreScreenshotTestBase
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayStoreScreenshotTest : PlayStoreScreenshotTestBase<MainActivity>(
    activityClass = MainActivity::class.java,
    screenshotGroup = "mobile",
)
