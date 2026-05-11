package com.swooby.ropeato.presentation

import android.speech.tts.Voice
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.Text
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.swooby.ropeato.GroupedLocaleOptions
import com.swooby.ropeato.LocaleLanguageGroup
import com.swooby.ropeato.SpeechLocalePreference
import com.swooby.ropeato.TextToSpeechVoicePreference
import com.swooby.ropeato.VoiceEntry
import com.swooby.ropeato.VoiceLanguageGroup
import com.swooby.ropeato.common.R
import java.util.Locale

// ─── Route constants ────────────────────────────────────────────────────────

private object Route {
    const val SETTINGS         = "settings"
    const val TTS_LANGUAGES    = "tts_languages"
    const val TTS_VARIANTS     = "tts_variants/{code}"
    const val SPEECH_LANGUAGES = "speech_languages"
    const val SPEECH_VARIANTS  = "speech_variants/{code}"

    fun ttsVariants(code: String)    = "tts_variants/$code"
    fun speechVariants(code: String) = "speech_variants/$code"
}

// ─── Public entry point ──────────────────────────────────────────────────────

@Composable
fun WearSettingsScreen(
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
    val navController = rememberSwipeDismissableNavController()
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

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = Route.SETTINGS,
    ) {
        // ── L1: summary ──────────────────────────────────────────────────────
        composable(Route.SETTINGS) {
            BackHandler(onBack = onDismiss)
            SettingsL1Screen(
                currentVoice = currentVoice,
                defaultVoice = defaultVoice,
                speechRecognizerLocale = speechRecognizerLocale,
                onNavigateTtsLanguages = { navController.navigate(Route.TTS_LANGUAGES) },
                onNavigateSpeechLanguages = { navController.navigate(Route.SPEECH_LANGUAGES) },
            )
        }

        // ── L2: TTS language list ─────────────────────────────────────────────
        composable(Route.TTS_LANGUAGES) {
            TtsLanguagesScreen(
                voiceGroups = voiceGroups,
                defaultVoice = defaultVoice,
                selectedVoiceName = selectedVoiceName,
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
                onLocaleSelected = { tag ->
                    onSpeechLocaleSelected(tag)
                    navController.popBackStack(Route.SETTINGS, inclusive = false)
                },
            )
        }
    }
}

// ─── L1 ─────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsL1Screen(
    currentVoice: Voice?,
    defaultVoice: Voice?,
    speechRecognizerLocale: String?,
    onNavigateTtsLanguages: () -> Unit,
    onNavigateSpeechLanguages: () -> Unit,
) {
    val displayLocale = LocalLocale.current.platformLocale
    val deviceDefaultLabel = stringResource(R.string.speech_locale_device_default)
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        state = rememberScalingLazyListState(),
    ) {
        item { ListHeader { Text(stringResource(R.string.settings_title)) } }

        item {
            val subtitle = when {
                currentVoice != null -> currentVoice.locale.getDisplayName(displayLocale)
                defaultVoice != null -> "$deviceDefaultLabel [${defaultVoice.locale.getDisplayName(displayLocale)}]"
                else -> ""
            }
            Chip(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateTtsLanguages,
                colors = ChipDefaults.secondaryChipColors(),
                label = {
                    Text(
                        stringResource(R.string.settings_section_tts_language),
                        maxLines = 1,
                    )
                },
                secondaryLabel = if (subtitle.isNotEmpty()) ({
                    Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }) else null,
            )
        }

        item {
            val subtitle = if (speechRecognizerLocale == null)
                "${stringResource(R.string.speech_locale_device_default)} [${displayLocale.getDisplayName(displayLocale)}]"
            else
                Locale.forLanguageTag(speechRecognizerLocale).getDisplayName(displayLocale)
            Chip(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateSpeechLanguages,
                colors = ChipDefaults.secondaryChipColors(),
                label = {
                    Text(
                        stringResource(R.string.settings_section_stt_language),
                        maxLines = 1,
                    )
                },
                secondaryLabel = {
                    Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
            )
        }
    }
}

// ─── L2: TTS languages ───────────────────────────────────────────────────────

@Composable
private fun TtsLanguagesScreen(
    voiceGroups: List<VoiceLanguageGroup>,
    defaultVoice: Voice?,
    selectedVoiceName: String?,
    onDeviceDefaultSelected: () -> Unit,
    onGroupSelected: (VoiceLanguageGroup) -> Unit,
    onOpenTtsSettings: () -> Unit,
) {
    val displayLocale = LocalLocale.current.platformLocale
    val deviceDefaultLabel = stringResource(R.string.speech_locale_device_default)
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        state = rememberScalingLazyListState(),
    ) {
        item { ListHeader { Text(stringResource(R.string.settings_section_tts_language)) } }

        // Device Default — always first, directly selectable
        item {
            SelectableChip(
                label = deviceDefaultLabel,
                secondaryLabel = defaultVoice?.locale?.getDisplayName(displayLocale),
                isSelected = selectedVoiceName == null,
                onClick = onDeviceDefaultSelected,
            )
        }

        if (voiceGroups.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.settings_tts_initializing),
                    color = Color.White.copy(alpha = 0.5f),
                )
            }
        } else {
            items(voiceGroups, key = { it.languageCode }) { group ->
                val isGroupSelected = group.voices.any { it.name == selectedVoiceName }
                SelectableChip(
                    label = if (group.hasVariants) "${group.displayLanguage} ›"
                            else group.displayLanguage,
                    isSelected = isGroupSelected,
                    onClick = { onGroupSelected(group) },
                )
            }
        }

        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenTtsSettings,
                colors = ChipDefaults.secondaryChipColors(),
                label = { Text(stringResource(R.string.settings_open_tts_settings)) },
            )
        }
    }
}

// ─── L3: TTS variants ────────────────────────────────────────────────────────

@Composable
private fun TtsVariantsScreen(
    group: VoiceLanguageGroup,
    selectedVoiceName: String?,
    onVoiceSelected: (String) -> Unit,
    onPreviewVoice: (String) -> Unit,
) {
    val displayLocale = LocalLocale.current.platformLocale
    val entries = remember(group.voices, displayLocale) {
        TextToSpeechVoicePreference.voiceEntries(group.voices, displayLocale)
    }
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        state = rememberScalingLazyListState(),
    ) {
        item { ListHeader { Text(group.displayLanguage) } }

        items(entries, key = { TextToSpeechVoicePreference.baseName(it.preferredVoice) }) { entry ->
            val locale = entry.preferredVoice.locale
            val countryName = locale.getDisplayCountry(displayLocale).ifEmpty { locale.getDisplayName(displayLocale) }
            val genderLabel = when (TextToSpeechVoicePreference.gender(entry.preferredVoice)) {
                "male"   -> stringResource(R.string.voice_gender_male)
                "female" -> stringResource(R.string.voice_gender_female)
                else     -> null
            }
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                SelectableChip(
                    label = if (genderLabel != null) "$countryName · $genderLabel" else countryName,
                    secondaryLabel = voiceSubtitle(entry),
                    isSelected = entry.isSelected(selectedVoiceName),
                    onClick = { onVoiceSelected(entry.preferredVoice.name) },
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .padding(start = 4.dp)
                        .clickable { onPreviewVoice(entry.preferredVoice.name) },
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.cd_preview_voice),
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

// ─── L2: Speech languages ────────────────────────────────────────────────────

@Composable
private fun SpeechLanguagesScreen(
    localeGroups: GroupedLocaleOptions,
    speechRecognizerLocale: String?,
    speechLocalesSupportChecked: Boolean,
    onDeviceDefaultSelected: () -> Unit,
    onGroupSelected: (LocaleLanguageGroup) -> Unit,
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        state = rememberScalingLazyListState(),
    ) {
        item { ListHeader { Text(stringResource(R.string.settings_section_stt_language)) } }

        // Device Default — always present, no drill-down
        item {
            val deviceLocaleName = LocalLocale.current.platformLocale.let { it.getDisplayName(it) }
            SelectableChip(
                label = localeGroups.deviceDefault.displayName,
                secondaryLabel = deviceLocaleName,
                isSelected = speechRecognizerLocale == null,
                onClick = onDeviceDefaultSelected,
            )
        }

        if (!speechLocalesSupportChecked) {
            item {
                Text(
                    text = stringResource(R.string.settings_speech_checking),
                    color = Color.White.copy(alpha = 0.5f),
                )
            }
        } else if (localeGroups.languageGroups.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.settings_speech_none_found),
                    color = Color.White.copy(alpha = 0.5f),
                )
            }
        } else {
            items(localeGroups.languageGroups, key = { it.languageCode }) { group ->
                val isGroupSelected = group.options.any { it.tag == speechRecognizerLocale }
                SelectableChip(
                    label = if (group.hasVariants) "${group.displayLanguage} ›"
                            else group.displayLanguage,
                    isSelected = isGroupSelected,
                    onClick = { onGroupSelected(group) },
                )
            }
        }
    }
}

// ─── L3: Speech variants ─────────────────────────────────────────────────────

@Composable
private fun SpeechVariantsScreen(
    group: LocaleLanguageGroup,
    speechRecognizerLocale: String?,
    onLocaleSelected: (String?) -> Unit,
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        state = rememberScalingLazyListState(),
    ) {
        item { ListHeader { Text(group.displayLanguage) } }

        items(group.options, key = { it.tag ?: "" }) { option ->
            SelectableChip(
                label = option.displayName,
                isSelected = option.tag == speechRecognizerLocale,
                onClick = { onLocaleSelected(option.tag) },
            )
        }
    }
}

// ─── Shared chip ─────────────────────────────────────────────────────────────

@Composable
private fun SelectableChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    secondaryLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    Chip(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        colors = if (isSelected) ChipDefaults.primaryChipColors()
                 else ChipDefaults.secondaryChipColors(),
        label = {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        secondaryLabel = secondaryLabel?.let { sl ->
            {
                Text(
                    text = sl,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(
                        animationMode = MarqueeAnimationMode.Immediately,
                        initialDelayMillis = 2_000,
                        repeatDelayMillis = 1_500,
                    ),
                )
            }
        },
        icon = if (isSelected) ({
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(R.string.cd_selected),
                modifier = Modifier.size(ChipDefaults.IconSize),
            )
        }) else null,
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
