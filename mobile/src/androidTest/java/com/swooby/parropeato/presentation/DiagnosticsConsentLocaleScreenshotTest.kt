package com.swooby.parropeato.presentation

import com.swooby.parropeato.testing.DiagnosticsConsentLocaleScreenshotTestBase
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class DiagnosticsConsentLocaleScreenshotTest(
    localeTag: String,
) : DiagnosticsConsentLocaleScreenshotTestBase<MainActivity>(
    activityClass = MainActivity::class.java,
    screenshotGroup = "mobile",
    localeTag = localeTag,
)
