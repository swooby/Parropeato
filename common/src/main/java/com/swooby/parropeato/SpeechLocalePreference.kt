package com.swooby.parropeato

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.swooby.parropeato.common.R
import java.util.Locale

data class SpeechLocaleOption(
    val tag: String?,
    val displayName: String,
)

data class LocaleLanguageGroup(
    val languageCode: String,
    val displayLanguage: String,
    val options: List<SpeechLocaleOption>,  // sorted by display name within the group
) {
    val hasVariants: Boolean get() = options.size > 1
}

data class GroupedLocaleOptions(
    val deviceDefault: SpeechLocaleOption,
    val languageGroups: List<LocaleLanguageGroup>,
)

/**
 * Human-readable L1 subtitle for the current STT locale setting.
 * Returns e.g. "Device Default [English (United States)]" or "English (United States)".
 */
@Composable
fun sttLocaleDisplaySubtitle(speechRecognizerLocale: String?, displayLocale: Locale): String {
    val deviceDefaultLabel = stringResource(R.string.speech_locale_device_default)
    return if (speechRecognizerLocale == null) {
        "$deviceDefaultLabel [${displayLocale.getDisplayName(displayLocale)}]"
    } else {
        Locale.forLanguageTag(speechRecognizerLocale).getDisplayName(displayLocale)
    }
}

/**
 * Country label for a selected STT locale within a language group.
 * Returns e.g. "United States" for "en-US".
 */
fun sttLocaleGroupSubtitle(tag: String, displayLocale: Locale): String =
    Locale.forLanguageTag(tag).getDisplayCountry(displayLocale)
        .ifEmpty { Locale.forLanguageTag(tag).getDisplayName(displayLocale) }

object SpeechLocalePreference {
    val CANDIDATES: Set<String> = setOf(
        "af-ZA", "am-ET", "ar-EG", "ar-SA", "az-AZ", "bg-BG", "bn-BD", "bn-IN",
        "bs-BA", "ca-ES", "cs-CZ", "cy-GB", "da-DK", "de-DE", "el-GR",
        "en-AU", "en-CA", "en-GB", "en-IN", "en-NZ", "en-US", "en-ZA",
        "es-AR", "es-ES", "es-MX", "es-US", "et-EE", "eu-ES", "fa-IR",
        "fi-FI", "fil-PH", "fr-CA", "fr-FR", "gl-ES", "gu-IN", "he-IL",
        "hi-IN", "hr-HR", "hu-HU", "hy-AM", "id-ID", "is-IS", "it-IT",
        "ja-JP", "ka-GE", "kk-KZ", "km-KH", "kn-IN", "ko-KR", "ky-KG",
        "lo-LA", "lt-LT", "lv-LV", "mk-MK", "ml-IN", "mn-MN", "mr-IN",
        "ms-MY", "my-MM", "nb-NO", "ne-NP", "nl-NL", "pa-IN", "pl-PL",
        "pt-BR", "pt-PT", "ro-RO", "ru-RU", "si-LK", "sk-SK", "sl-SI",
        "sq-AL", "sr-RS", "sv-SE", "sw-TZ", "ta-IN", "te-IN", "th-TH",
        "tr-TR", "uk-UA", "ur-PK", "uz-UZ", "vi-VN",
        "yue-Hant-HK", "zh-CN", "zh-HK", "zh-TW", "zu-ZA",
    )

    fun localeOptions(supportedTags: List<String>, deviceDefaultLabel: String): List<SpeechLocaleOption> =
        listOf(SpeechLocaleOption(tag = null, displayName = deviceDefaultLabel)) + supportedTags.map { tag ->
            SpeechLocaleOption(
                tag = tag,
                displayName = Locale.forLanguageTag(tag).getDisplayName(Locale.getDefault()),
            )
        }

    fun groupedLocaleOptions(
        supportedTags: List<String>,
        deviceDefaultLabel: String,
        displayLocale: Locale = Locale.getDefault(),
    ): GroupedLocaleOptions {
        val groups = supportedTags
            .groupBy { Locale.forLanguageTag(it).language }
            .map { (_, tags) ->
                val options = tags.map { tag ->
                    SpeechLocaleOption(
                        tag = tag,
                        displayName = Locale.forLanguageTag(tag).getDisplayName(displayLocale),
                    )
                }.sortedBy { it.displayName }
                LocaleLanguageGroup(
                    languageCode = Locale.forLanguageTag(tags.first()).language,
                    displayLanguage = Locale.forLanguageTag(tags.first()).getDisplayLanguage(displayLocale),
                    options = options,
                )
            }
            .sortedBy { it.displayLanguage }
        return GroupedLocaleOptions(
            deviceDefault = SpeechLocaleOption(tag = null, displayName = deviceDefaultLabel),
            languageGroups = groups,
        )
    }
}
