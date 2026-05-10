package com.swooby.ropeato.presentation

import androidx.compose.runtime.Composable
import androidx.wear.compose.material.TimeText
import com.swooby.ropeato.BaseMainActivity

class MainActivity : BaseMainActivity() {
    override val textToSpeechVoiceSpeed: Float = 1.3f
    override val watchFaceSceneScale: Float = 1f
    override val watchFaceControlsScale: Float = 1f
    override val watchFaceBorderOutset: Boolean = true

    @Composable
    override fun PlatformOverlay() {
        TimeText()
    }
}
