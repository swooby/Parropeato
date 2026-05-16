package com.swooby.parropeato.presentation

import android.speech.tts.Voice
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.swooby.parropeato.ACCENT_COLOR_OPTIONS
import com.swooby.parropeato.BuildConfig
import com.swooby.parropeato.GroupedLocaleOptions
import com.swooby.parropeato.LocaleLanguageGroup
import com.swooby.parropeato.SpeechLocalePreference
import com.swooby.parropeato.TextToSpeechVoicePreference
import com.swooby.parropeato.VoiceLanguageGroup
import com.swooby.parropeato.common.R
import com.swooby.parropeato.sttLocaleDisplaySubtitle
import com.swooby.parropeato.sttLocaleGroupSubtitle
import com.swooby.parropeato.ttsVoiceDisplaySubtitle
import com.swooby.parropeato.voiceSubtitle

// ─── Route constants ────────────────────────────────────────────────────────

private object Route {
    const val SETTINGS         = "settings"
    const val TTS_LANGUAGES    = "tts_languages"
    const val TTS_VARIANTS     = "tts_variants/{code}"
    const val SPEECH_LANGUAGES = "speech_languages"
    const val SPEECH_VARIANTS  = "speech_variants/{code}"
    const val ACCENT_COLOR     = "accent_color"

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
    installedSpeechLocales: Set<String>,
    isNetworkAvailable: Boolean,
    cuteIcons: Boolean,
    accentColor: Int,
    onVoiceSelected: (String?) -> Unit,
    onPreviewVoice: (String) -> Unit,
    onSpeechLocaleSelected: (String?) -> Unit,
    onOpenTtsSettings: () -> Unit,
    onOpenSpeechDownloadSettings: () -> Unit,
    onCuteIconsChanged: (Boolean) -> Unit,
    onAccentColorChanged: (Int) -> Unit,
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
    val cuteIconsState = rememberUpdatedState(cuteIcons)
    val accentColorState = rememberUpdatedState(accentColor)
    val installedSpeechLocalesState = rememberUpdatedState(installedSpeechLocales)
    val isNetworkAvailableState = rememberUpdatedState(isNetworkAvailable)

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
                installedSpeechLocales = installedSpeechLocalesState.value,
                isNetworkAvailable = isNetworkAvailableState.value,
                cuteIcons = cuteIconsState.value,
                accentColor = accentColorState.value,
                onNavigateTtsLanguages = { navController.navigate(Route.TTS_LANGUAGES) },
                onNavigateSpeechLanguages = { navController.navigate(Route.SPEECH_LANGUAGES) },
                onNavigateAccentColor = { navController.navigate(Route.ACCENT_COLOR) },
                onCuteIconsChanged = onCuteIconsChanged,
            )
        }

        // ── Accent color picker ───────────────────────────────────────────────
        composable(Route.ACCENT_COLOR) {
            AccentColorScreen(
                accentColor = accentColorState.value,
                onAccentColorSelected = { argb ->
                    onAccentColorChanged(argb)
                    navController.popBackStack(Route.SETTINGS, inclusive = false)
                },
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
                installedSpeechLocales = installedSpeechLocalesState.value,
                isNetworkAvailable = isNetworkAvailableState.value,
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
                onOpenSpeechDownloadSettings = onOpenSpeechDownloadSettings,
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
    installedSpeechLocales: Set<String>,
    isNetworkAvailable: Boolean,
    cuteIcons: Boolean,
    accentColor: Int,
    onNavigateTtsLanguages: () -> Unit,
    onNavigateSpeechLanguages: () -> Unit,
    onNavigateAccentColor: () -> Unit,
    onCuteIconsChanged: (Boolean) -> Unit,
) {
    val displayLocale = LocalLocale.current.platformLocale
    val deviceDefaultLabel = stringResource(R.string.speech_locale_device_default)
    val listState = rememberScalingLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        state = listState,
    ) {
        item { ListHeader { Text(stringResource(R.string.settings_title)) } }

        item {
            val subtitle = buildString {
                append(sttLocaleDisplaySubtitle(speechRecognizerLocale, displayLocale))
                if (!isNetworkAvailable && speechRecognizerLocale != null && speechRecognizerLocale !in installedSpeechLocales) {
                    append(" · ")
                    append(stringResource(R.string.settings_stt_download_offline))
                }
            }
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
                    Text(
                        subtitle,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(
                            animationMode = MarqueeAnimationMode.Immediately,
                            initialDelayMillis = 2_000,
                            repeatDelayMillis = 1_500,
                        ),
                    )
                },
            )
        }

        item {
            val subtitle = when {
                currentVoice != null -> ttsVoiceDisplaySubtitle(currentVoice, displayLocale)
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
                    Text(
                        subtitle,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(
                            animationMode = MarqueeAnimationMode.Immediately,
                            initialDelayMillis = 2_000,
                            repeatDelayMillis = 1_500,
                        ),
                    )
                }) else null,
            )
        }

        item {
            val currentOption = ACCENT_COLOR_OPTIONS.find { it.argb == accentColor }
            Chip(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateAccentColor,
                colors = ChipDefaults.secondaryChipColors(),
                label = { Text(stringResource(R.string.settings_accent_color), maxLines = 1) },
                secondaryLabel = currentOption?.let { opt ->
                    { Text(stringResource(opt.nameResId), maxLines = 1) }
                },
                icon = {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(currentOption?.color ?: Color.Transparent, CircleShape),
                    )
                },
            )
        }

        item {
            ToggleChip(
                modifier = Modifier.fillMaxWidth(),
                checked = cuteIcons,
                onCheckedChange = onCuteIconsChanged,
                label = { Text(stringResource(R.string.settings_cute_icons), maxLines = 1) },
                toggleControl = {
                    Icon(
                        imageVector = ToggleChipDefaults.switchIcon(cuteIcons),
                        contentDescription = if (cuteIcons) stringResource(com.swooby.parropeato.R.string.on)
                        else stringResource(com.swooby.parropeato.R.string.off),
                    )
                },
                colors = ToggleChipDefaults.toggleChipColors(
                    checkedToggleControlColor = Color(accentColor),
                ),
            )
        }
        if (BuildConfig.DEBUG) {
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { throw RuntimeException("Test Crash") },
                    colors = ChipDefaults.primaryChipColors(backgroundColor = Color.Red.copy(alpha = 0.8f)),
                    label = { Text("Test Crash", maxLines = 1) },
                )
            }
        }
    }
    PositionIndicator(scalingLazyListState = listState)
    }
}

// ─── Accent color picker ──────────────────────────────────────────────────────

@Composable
private fun AccentColorScreen(
    accentColor: Int,
    onAccentColorSelected: (Int) -> Unit,
) {
    val listState = rememberScalingLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            state = listState,
        ) {
            item { ListHeader { Text(stringResource(R.string.settings_accent_color)) } }
            items(ACCENT_COLOR_OPTIONS) { option ->
                val isSelected = option.argb == accentColor
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onAccentColorSelected(option.argb) },
                    colors = if (isSelected) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                    label = { Text(stringResource(option.nameResId), maxLines = 1) },
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(option.color, CircleShape),
                        )
                    },
                )
            }
        }
        PositionIndicator(scalingLazyListState = listState)
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
    // index 0 = header, 1 = Device Default, 2+ = groups
    val scrollIndex = remember(voiceGroups, selectedVoiceName) {
        if (selectedVoiceName == null || voiceGroups.isEmpty()) {
            1
        } else {
            val i = voiceGroups.indexOfFirst { g -> g.voices.any { it.name == selectedVoiceName } }
            if (i >= 0) i + 2 else 1
        }
    }
    val listState = rememberScalingLazyListState(initialCenterItemIndex = scrollIndex)
    Box(modifier = Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            state = listState,
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            text = stringResource(R.string.settings_tts_initializing),
                            color = Color.White.copy(alpha = 0.5f),
                        )
                    }
                }
            } else {
                items(voiceGroups, key = { it.languageCode }) { group ->
                    val isGroupSelected = group.voices.any { it.name == selectedVoiceName }
                    val variantSubtitle = if (isGroupSelected && selectedVoiceName != null) {
                        TextToSpeechVoicePreference.voiceEntries(group.voices, displayLocale)
                            .find { it.isSelected(selectedVoiceName) }
                            ?.let { ttsVoiceDisplaySubtitle(it.preferredVoice, displayLocale, includeLocale = false) }
                    } else null
                    SelectableChip(
                        label = if (group.hasVariants) "${group.displayLanguage} ›"
                                else group.displayLanguage,
                        secondaryLabel = variantSubtitle,
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
        PositionIndicator(scalingLazyListState = listState)
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
    val listState = rememberScalingLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            state = listState,
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
                            imageVector = Icons.Outlined.PlayCircle,
                            contentDescription = stringResource(R.string.cd_preview_voice),
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
        PositionIndicator(scalingLazyListState = listState)
    }
}

// ─── L2: Speech languages ────────────────────────────────────────────────────

@Composable
private fun SpeechLanguagesScreen(
    localeGroups: GroupedLocaleOptions,
    speechRecognizerLocale: String?,
    speechLocalesSupportChecked: Boolean,
    installedSpeechLocales: Set<String>,
    isNetworkAvailable: Boolean,
    onDeviceDefaultSelected: () -> Unit,
    onGroupSelected: (LocaleLanguageGroup) -> Unit,
    onOpenSpeechDownloadSettings: () -> Unit,
) {
    val displayLocale = LocalLocale.current.platformLocale
    // index 0 = header, 1 = Device Default, 2+ = groups
    val scrollIndex = remember(localeGroups, speechRecognizerLocale, speechLocalesSupportChecked) {
        if (speechRecognizerLocale == null || !speechLocalesSupportChecked || localeGroups.languageGroups.isEmpty()) {
            1
        } else {
            val i = localeGroups.languageGroups.indexOfFirst { g -> g.options.any { it.tag == speechRecognizerLocale } }
            if (i >= 0) i + 2 else 1
        }
    }
    val listState = rememberScalingLazyListState(initialCenterItemIndex = scrollIndex)
    Box(modifier = Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            state = listState,
        ) {
            item { ListHeader { Text(stringResource(R.string.settings_section_stt_language)) } }

            // Device Default — always present, no drill-down
            item {
                SelectableChip(
                    label = localeGroups.deviceDefault.displayName,
                    secondaryLabel = displayLocale.getDisplayName(displayLocale),
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
                    val variantSubtitle = if (isGroupSelected && speechRecognizerLocale != null)
                        sttLocaleGroupSubtitle(speechRecognizerLocale, displayLocale)
                    else null
                    val groupHasOffline = group.options.any { it.tag != null && it.tag in installedSpeechLocales }
                    val offlineLabel = if (groupHasOffline) stringResource(R.string.settings_stt_offline_ready) else null
                    val secondaryLabel = listOfNotNull(variantSubtitle, offlineLabel).joinToString(" · ").ifEmpty { null }
                    SelectableChip(
                        label = if (group.hasVariants) "${group.displayLanguage} ›"
                                else group.displayLanguage,
                        secondaryLabel = secondaryLabel,
                        isSelected = isGroupSelected,
                        onClick = { onGroupSelected(group) },
                    )
                }
            }
            if (!isNetworkAvailable && speechRecognizerLocale != null && speechRecognizerLocale !in installedSpeechLocales) {
                item {
                    Chip(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onOpenSpeechDownloadSettings,
                        colors = ChipDefaults.secondaryChipColors(),
                        label = { Text(stringResource(R.string.settings_stt_download_offline), maxLines = 2) },
                    )
                }
            }
        }
        PositionIndicator(scalingLazyListState = listState)
    }
}

// ─── L3: Speech variants ─────────────────────────────────────────────────────

@Composable
private fun SpeechVariantsScreen(
    group: LocaleLanguageGroup,
    speechRecognizerLocale: String?,
    onLocaleSelected: (String?) -> Unit,
) {
    val listState = rememberScalingLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            state = listState,
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
        PositionIndicator(scalingLazyListState = listState)
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

