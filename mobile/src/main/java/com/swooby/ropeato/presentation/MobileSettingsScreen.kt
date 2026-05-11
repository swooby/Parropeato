package com.swooby.ropeato.presentation

import android.speech.tts.Voice
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.swooby.ropeato.GroupedLocaleOptions
import com.swooby.ropeato.LocaleLanguageGroup
import com.swooby.ropeato.SpeechLocalePreference
import com.swooby.ropeato.TextToSpeechVoicePreference
import com.swooby.ropeato.VoiceEntry
import com.swooby.ropeato.VoiceLanguageGroup
import com.swooby.ropeato.common.R
import java.util.Locale

// ─── Route constants ──────────────────────────────────────────────────────────

private object Route {
    const val SETTINGS         = "settings"
    const val TTS_LANGUAGES    = "tts_languages"
    const val TTS_VARIANTS     = "tts_variants/{code}"
    const val SPEECH_LANGUAGES = "speech_languages"
    const val SPEECH_VARIANTS  = "speech_variants/{code}"

    fun ttsVariants(code: String)    = "tts_variants/$code"
    fun speechVariants(code: String) = "speech_variants/$code"
}

// ─── Public entry point ───────────────────────────────────────────────────────

@Composable
fun MobileSettingsScreen(
    availableVoices: List<Voice>,
    ttsDefaultVoiceName: String?,
    selectedVoiceName: String?,
    speechRecognizerLocale: String?,
    supportedSpeechLocales: List<String>,
    speechLocalesSupportChecked: Boolean,
    onVoiceSelected: (String?) -> Unit,
    onPreviewVoice: (String) -> Unit,
    onSpeechLocaleSelected: (String?) -> Unit,
    onOpenTtsSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val navController = rememberNavController()
    val displayLocale = LocalLocale.current.platformLocale

    val voiceGroups = remember(availableVoices, displayLocale) {
        TextToSpeechVoicePreference.groupedVoices(availableVoices, displayLocale)
    }
    val deviceDefaultLabel = stringResource(R.string.speech_locale_device_default)
    val localeGroups = remember(supportedSpeechLocales, deviceDefaultLabel, displayLocale) {
        SpeechLocalePreference.groupedLocaleOptions(supportedSpeechLocales, deviceDefaultLabel, displayLocale)
    }
    val currentVoice = remember(availableVoices, selectedVoiceName) {
        availableVoices.find { it.name == selectedVoiceName }
    }
    val defaultVoice = remember(availableVoices, ttsDefaultVoiceName) {
        availableVoices.find { it.name == ttsDefaultVoiceName }
    }

    NavHost(
        navController = navController,
        startDestination = Route.SETTINGS,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.97f))
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        // ── L1: summary ───────────────────────────────────────────────────────
        composable(Route.SETTINGS) {
            BackHandler(onBack = onDismiss)
            SettingsL1Screen(
                currentVoice = currentVoice,
                defaultVoice = defaultVoice,
                speechRecognizerLocale = speechRecognizerLocale,
                onNavigateTtsLanguages = { navController.navigate(Route.TTS_LANGUAGES) },
                onNavigateSpeechLanguages = { navController.navigate(Route.SPEECH_LANGUAGES) },
                onDismiss = onDismiss,
            )
        }

        // ── L2: TTS language list ─────────────────────────────────────────────
        composable(Route.TTS_LANGUAGES) {
            TtsLanguagesScreen(
                voiceGroups = voiceGroups,
                defaultVoice = defaultVoice,
                selectedVoiceName = selectedVoiceName,
                onBack = { navController.popBackStack() },
                onDeviceDefaultSelected = {
                    onVoiceSelected(null)
                    navController.popBackStack(Route.SETTINGS, inclusive = false)
                },
                onGroupSelected = { group ->
                    if (!group.hasVariants) {
                        val entry = TextToSpeechVoicePreference.voiceEntries(group.voices, displayLocale).firstOrNull()
                        onVoiceSelected(entry?.preferredVoice?.name)
                        navController.popBackStack(Route.SETTINGS, inclusive = false)
                    } else {
                        navController.navigate(Route.ttsVariants(group.languageCode))
                    }
                },
                onOpenTtsSettings = onOpenTtsSettings,
            )
        }

        // ── L3: TTS regional variants ─────────────────────────────────────────
        composable(
            route = Route.TTS_VARIANTS,
            arguments = listOf(navArgument("code") { type = NavType.StringType }),
        ) { entry ->
            val code = entry.arguments?.getString("code") ?: return@composable
            val group = voiceGroups.find { it.languageCode == code } ?: return@composable
            TtsVariantsScreen(
                group = group,
                selectedVoiceName = selectedVoiceName,
                onBack = { navController.popBackStack() },
                onVoiceSelected = { name ->
                    onVoiceSelected(name)
                    navController.popBackStack(Route.SETTINGS, inclusive = false)
                },
                onPreviewVoice = onPreviewVoice,
            )
        }

        // ── L2: Speech language list ──────────────────────────────────────────
        composable(Route.SPEECH_LANGUAGES) {
            SpeechLanguagesScreen(
                localeGroups = localeGroups,
                speechRecognizerLocale = speechRecognizerLocale,
                speechLocalesSupportChecked = speechLocalesSupportChecked,
                onBack = { navController.popBackStack() },
                onDeviceDefaultSelected = {
                    onSpeechLocaleSelected(null)
                    navController.popBackStack(Route.SETTINGS, inclusive = false)
                },
                onGroupSelected = { group ->
                    if (!group.hasVariants) {
                        onSpeechLocaleSelected(group.options.first().tag)
                        navController.popBackStack(Route.SETTINGS, inclusive = false)
                    } else {
                        navController.navigate(Route.speechVariants(group.languageCode))
                    }
                },
            )
        }

        // ── L3: Speech regional variants ──────────────────────────────────────
        composable(
            route = Route.SPEECH_VARIANTS,
            arguments = listOf(navArgument("code") { type = NavType.StringType }),
        ) { entry ->
            val code = entry.arguments?.getString("code") ?: return@composable
            val group = localeGroups.languageGroups.find { it.languageCode == code } ?: return@composable
            SpeechVariantsScreen(
                group = group,
                speechRecognizerLocale = speechRecognizerLocale,
                onBack = { navController.popBackStack() },
                onLocaleSelected = { tag ->
                    onSpeechLocaleSelected(tag)
                    navController.popBackStack(Route.SETTINGS, inclusive = false)
                },
            )
        }
    }
}

// ─── L1 ──────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsL1Screen(
    currentVoice: Voice?,
    defaultVoice: Voice?,
    speechRecognizerLocale: String?,
    onNavigateTtsLanguages: () -> Unit,
    onNavigateSpeechLanguages: () -> Unit,
    onDismiss: () -> Unit,
) {
    val displayLocale = LocalLocale.current.platformLocale
    val deviceDefaultLabel = stringResource(R.string.speech_locale_device_default)
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.settings_title), onAction = {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cd_close_settings),
                    tint = Color.White,
                )
            }
        })
        HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                val subtitle = when {
                    currentVoice != null -> currentVoice.locale.getDisplayName(displayLocale)
                    defaultVoice != null -> "$deviceDefaultLabel [${defaultVoice.locale.getDisplayName(displayLocale)}]"
                    else -> stringResource(R.string.settings_tts_initializing)
                }
                DrillDownRow(
                    label = stringResource(R.string.settings_section_tts_language),
                    value = subtitle,
                    onClick = onNavigateTtsLanguages,
                )
            }
            item { HorizontalDivider(color = Color.White.copy(alpha = 0.08f)) }
            item {
                val subtitle = if (speechRecognizerLocale == null)
                    "${stringResource(R.string.speech_locale_device_default)} [${displayLocale.getDisplayName(displayLocale)}]"
                else
                    Locale.forLanguageTag(speechRecognizerLocale).getDisplayName(displayLocale)
                DrillDownRow(
                    label = stringResource(R.string.settings_section_stt_language),
                    value = subtitle,
                    onClick = onNavigateSpeechLanguages,
                )
            }
        }
    }
}

// ─── L2: TTS languages ───────────────────────────────────────────────────────

@Composable
private fun TtsLanguagesScreen(
    voiceGroups: List<VoiceLanguageGroup>,
    defaultVoice: Voice?,
    selectedVoiceName: String?,
    onBack: () -> Unit,
    onDeviceDefaultSelected: () -> Unit,
    onGroupSelected: (VoiceLanguageGroup) -> Unit,
    onOpenTtsSettings: () -> Unit,
) {
    val displayLocale = LocalLocale.current.platformLocale
    val deviceDefaultLabel = stringResource(R.string.speech_locale_device_default)
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.settings_section_tts_language), onBack = onBack)
        HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            // Device Default — always first, directly selectable
            item {
                SelectableRow(
                    label = deviceDefaultLabel,
                    subtitle = defaultVoice?.locale?.getDisplayName(displayLocale),
                    isSelected = selectedVoiceName == null,
                    onClick = onDeviceDefaultSelected,
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
            }

            if (voiceGroups.isEmpty()) {
                item { StatusText(stringResource(R.string.settings_tts_initializing)) }
            } else {
                items(voiceGroups, key = { it.languageCode }) { group ->
                    SelectableRow(
                        label = group.displayLanguage,
                        isSelected = group.voices.any { it.name == selectedVoiceName },
                        hasChildren = group.hasVariants,
                        onClick = { onGroupSelected(group) },
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                }
            }
            item {
                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                Button(
                    onClick = onOpenTtsSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(stringResource(R.string.settings_open_tts_settings))
                }
            }
        }
    }
}

// ─── L3: TTS variants ────────────────────────────────────────────────────────

@Composable
private fun TtsVariantsScreen(
    group: VoiceLanguageGroup,
    selectedVoiceName: String?,
    onBack: () -> Unit,
    onVoiceSelected: (String) -> Unit,
    onPreviewVoice: (String) -> Unit,
) {
    val displayLocale = LocalLocale.current.platformLocale
    val entries = remember(group.voices, displayLocale) {
        TextToSpeechVoicePreference.voiceEntries(group.voices, displayLocale)
    }
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = group.displayLanguage, onBack = onBack)
        HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(entries, key = { TextToSpeechVoicePreference.baseName(it.preferredVoice) }) { entry ->
                val locale = entry.preferredVoice.locale
                val countryName = locale.getDisplayCountry(displayLocale).ifEmpty { locale.getDisplayName(displayLocale) }
                val genderLabel = when (TextToSpeechVoicePreference.gender(entry.preferredVoice)) {
                    "male"   -> stringResource(R.string.voice_gender_male)
                    "female" -> stringResource(R.string.voice_gender_female)
                    else     -> null
                }
                SelectableRow(
                    label = if (genderLabel != null) "$countryName · $genderLabel" else countryName,
                    subtitle = voiceSubtitle(entry),
                    isSelected = entry.isSelected(selectedVoiceName),
                    onClick = { onVoiceSelected(entry.preferredVoice.name) },
                    onPreview = { onPreviewVoice(entry.preferredVoice.name) },
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
            }
        }
    }
}

// ─── L2: Speech languages ─────────────────────────────────────────────────────

@Composable
private fun SpeechLanguagesScreen(
    localeGroups: GroupedLocaleOptions,
    speechRecognizerLocale: String?,
    speechLocalesSupportChecked: Boolean,
    onBack: () -> Unit,
    onDeviceDefaultSelected: () -> Unit,
    onGroupSelected: (LocaleLanguageGroup) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.settings_section_stt_language), onBack = onBack)
        HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            // Device Default — always first, directly selectable
            item {
                val deviceLocaleName = LocalLocale.current.platformLocale.let {
                    it.getDisplayName(it)
                }
                SelectableRow(
                    label = localeGroups.deviceDefault.displayName,
                    subtitle = deviceLocaleName,
                    isSelected = speechRecognizerLocale == null,
                    onClick = onDeviceDefaultSelected,
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
            }

            if (!speechLocalesSupportChecked) {
                item { StatusText(stringResource(R.string.settings_speech_checking)) }
            } else if (localeGroups.languageGroups.isEmpty()) {
                item { StatusText(stringResource(R.string.settings_speech_none_found)) }
            } else {
                items(localeGroups.languageGroups, key = { it.languageCode }) { group ->
                    SelectableRow(
                        label = group.displayLanguage,
                        isSelected = group.options.any { it.tag == speechRecognizerLocale },
                        hasChildren = group.hasVariants,
                        onClick = { onGroupSelected(group) },
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                }
            }
        }
    }
}

// ─── L3: Speech variants ──────────────────────────────────────────────────────

@Composable
private fun SpeechVariantsScreen(
    group: LocaleLanguageGroup,
    speechRecognizerLocale: String?,
    onBack: () -> Unit,
    onLocaleSelected: (String?) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = group.displayLanguage, onBack = onBack)
        HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(group.options, key = { it.tag ?: "" }) { option ->
                SelectableRow(
                    label = option.displayName,
                    isSelected = option.tag == speechRecognizerLocale,
                    onClick = { onLocaleSelected(option.tag) },
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
            }
        }
    }
}

// ─── Shared UI components ─────────────────────────────────────────────────────

@Composable
private fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    onAction: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        } else {
            Spacer(modifier = Modifier.width(16.dp))
        }
        Text(
            text = title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
        )
        onAction?.invoke()
    }
}

@Composable
private fun DrillDownRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(value, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        }
        Text("›", color = Color.White.copy(alpha = 0.4f), fontSize = 22.sp)
    }
}

@Composable
private fun SelectableRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
    hasChildren: Boolean = false,
    onPreview: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = if (onPreview != null) 4.dp else 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Color.White, fontSize = 14.sp)
            if (subtitle != null) {
                Text(subtitle, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
            }
        }
        when {
            isSelected -> Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(R.string.cd_selected),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            hasChildren -> Text("›", color = Color.White.copy(alpha = 0.4f), fontSize = 22.sp)
        }
        if (onPreview != null) {
            IconButton(onClick = onPreview, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.cd_preview_voice),
                    tint = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.5f),
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

// ─── Voice subtitle ───────────────────────────────────────────────────────────

@Composable
private fun voiceSubtitle(entry: VoiceEntry): String {
    val localLabel   = stringResource(R.string.voice_connectivity_offline)
    val networkLabel = stringResource(R.string.voice_connectivity_online)
    val connectivity = when {
        entry.localVoice != null && entry.networkVoice != null -> "$localLabel + $networkLabel"
        entry.localVoice != null  -> localLabel
        else                      -> networkLabel
    }
    val voice = entry.preferredVoice
    val quality = when {
        voice.quality >= 400 -> stringResource(R.string.voice_quality_hd)
        voice.quality >= 300 -> stringResource(R.string.voice_quality_standard)
        else                 -> stringResource(R.string.voice_quality_basic)
    }
    val id = TextToSpeechVoicePreference.shortId(voice)
    return "$connectivity · $quality · $id"
}
