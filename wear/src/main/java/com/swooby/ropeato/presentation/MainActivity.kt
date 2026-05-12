package com.swooby.ropeato.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.TimeText
import com.swooby.ropeato.BaseMainActivity
import com.swooby.ropeato.SettingsGearButton

class MainActivity : BaseMainActivity() {
    override val watchFaceSceneScale: Float = 1f
    override val watchFaceControlsScale: Float = 1f
    override val watchFaceBorderOutset: Boolean = true

    @Composable
    override fun PlatformOverlay(onSettingsClick: () -> Unit) {
        TimeText()
        SettingsGearButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize(Alignment.BottomCenter),
        )
    }

    @Composable
    override fun SettingsOverlay(onDismiss: () -> Unit) {
        WearSettingsScreen(
            availableVoices = viewModel.availableVoices,
            ttsDefaultVoiceName = viewModel.ttsDefaultVoiceName,
            selectedVoiceName = viewModel.selectedVoiceName,
            speechRecognizerLocale = viewModel.speechRecognizerLocale,
            supportedSpeechLocales = viewModel.supportedSpeechLocales,
            speechLocalesSupportChecked = viewModel.speechLocalesSupportChecked,
            cuteIcons = viewModel.cuteIcons,
            accentColor = viewModel.accentColor,
            onVoiceSelected = ::onSettingsVoiceSelected,
            onPreviewVoice = ::onSettingsVoicePreview,
            onSpeechLocaleSelected = ::onSettingsSpeechLocaleSelected,
            onOpenTtsSettings = ::openTtsSettings,
            onCuteIconsChanged = ::onSettingsCuteIconsChanged,
            onAccentColorChanged = ::onSettingsAccentColorChanged,
            onDismiss = onDismiss,
        )
    }
}
