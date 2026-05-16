package com.swooby.parropeato.presentation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.TimeText
import com.swooby.parropeato.BaseMainActivity
import com.swooby.parropeato.SettingsGearButton

class MainActivity : BaseMainActivity() {
    override val watchFaceSceneScale: Float = 1f
    override val watchFaceControlsScale: Float = 1f
    override val watchFaceBorderOutset: Boolean = true
    override val greetingScrollIndicator: (@Composable (ScrollState) -> Unit) = { scrollState ->
        PositionIndicator(scrollState = scrollState)
    }

    override fun onStop() {
        super.onStop()
        finish()
    }

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
            installedSpeechLocales = viewModel.installedSpeechLocales,
            isNetworkAvailable = viewModel.isNetworkAvailable,
            cuteIcons = viewModel.cuteIcons,
            accentColor = viewModel.accentColor,
            onVoiceSelected = ::onSettingsVoiceSelected,
            onPreviewVoice = ::onSettingsVoicePreview,
            onSpeechLocaleSelected = ::onSettingsSpeechLocaleSelected,
            onOpenTtsSettings = ::openTtsSettings,
            onOpenSpeechDownloadSettings = ::openSpeechDownloadSettings,
            onCuteIconsChanged = ::onSettingsCuteIconsChanged,
            onAccentColorChanged = ::onSettingsAccentColorChanged,
            onDismiss = onDismiss,
        )
    }
}
