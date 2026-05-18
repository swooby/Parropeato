package com.swooby.parropeato.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swooby.parropeato.BaseMainActivity
import com.swooby.parropeato.SettingsGearButton
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainActivity : BaseMainActivity() {
    override val greetingBottomInsetDp: Float = 52f
    override val analyticsPlatform: String = "mobile"

    @Composable
    override fun PlatformOverlay(onSettingsClick: () -> Unit) {
        WatchClock(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize(Alignment.TopCenter)
                .offset(y = 28.dp),
        )
        SettingsGearButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize(Alignment.TopCenter)
                .offset(y = 46.dp),
        )
    }

    @Composable
    override fun SettingsOverlay(onDismiss: () -> Unit) {
        MobileSettingsScreen(
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
            diagnosticsEnabled = viewModel.diagnosticsEnabled,
            onVoiceSelected = ::onSettingsVoiceSelected,
            onPreviewVoice = ::onSettingsVoicePreview,
            onSpeechLocaleSelected = ::onSettingsSpeechLocaleSelected,
            onOpenTtsSettings = ::openTtsSettings,
            onOpenSpeechDownloadSettings = ::openSpeechDownloadSettings,
            onOpenAppInfoSettings = ::openAppInfoSettings,
            onCuteIconsChanged = ::onSettingsCuteIconsChanged,
            onAccentColorChanged = ::onSettingsAccentColorChanged,
            onDiagnosticsEnabledChanged = ::onSettingsDiagnosticsEnabledChanged,
            onSettingsScreenOpened = ::onSettingsScreenOpened,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun WatchClock(
    modifier: Modifier = Modifier,
) {
    val timeText = remember { mutableStateOf(currentClockText()) }
    LaunchedEffect(Unit) {
        while (true) {
            timeText.value = currentClockText()
            delay(1_000)
        }
    }
    Text(
        modifier = modifier,
        text = timeText.value,
        color = Color.White.copy(alpha = 0.88f),
        style = MaterialTheme.typography.titleMedium,
        fontSize = 20.sp,
        textAlign = TextAlign.Center,
    )
}

private val ClockTimeFormatter = DateTimeFormatter.ofPattern("h:mm")

private fun currentClockText(): String = LocalTime.now().format(ClockTimeFormatter)
