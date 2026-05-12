package com.swooby.ropeato

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.swooby.ropeato.common.R
import java.util.Locale
import java.util.MissingResourceException

/**
 * A deduplicated voice model: the same underlying voice available in local and/or network form.
 * [preferredVoice] is the local variant when both exist (avoids network dependency).
 */
data class VoiceEntry(
    val localVoice: Voice?,
    val networkVoice: Voice?,
) {
    val preferredVoice: Voice get() = localVoice ?: networkVoice!!
    fun isSelected(selectedName: String?): Boolean =
        selectedName != null && (localVoice?.name == selectedName || networkVoice?.name == selectedName)
}

data class VoiceLanguageGroup(
    val languageCode: String,
    val displayLanguage: String,
    val voices: List<Voice>,            // sorted by full display name within the group
) {
    /**
     * True when there is more than one distinct voice *model* in the group.
     * Local + network variants of the same model count as one.
     */
    val hasVariants: Boolean get() = voices
        .map { it.name.removeSuffix("-local").removeSuffix("-network") }
        .distinct()
        .size > 1
}

object TextToSpeechVoicePreference {

    fun groupedVoices(
        voices: Collection<Voice>,
        displayLocale: Locale = Locale.getDefault(),
    ): List<VoiceLanguageGroup> =
        voices
            .groupBy { it.locale.language }
            .map { (_, groupVoices) ->
                val sorted = groupVoices.sortedBy { it.locale.getDisplayName(displayLocale) }
                VoiceLanguageGroup(
                    languageCode = sorted.first().locale.language,
                    displayLanguage = sorted.first().locale.getDisplayLanguage(displayLocale),
                    voices = sorted,
                )
            }
            .sortedBy { it.displayLanguage }
    private const val EXACT_LOCALE_SCORE = 1000
    private const val LANGUAGE_ONLY_SCORE = 400
    private const val MALE_SCORE = 300
    private const val KNOWN_GOOGLE_MALE_SCORE = 260
    private const val SAMSUNG_MALE_SCORE = 220
    private const val FEMALE_PENALTY = 300
    private const val KNOWN_GOOGLE_FEMALE_PENALTY = 260
    private const val SAMSUNG_FEMALE_PENALTY = 150
    private const val OFFLINE_SCORE = 50

    /**
     * Returns the voice name with the connectivity suffix ("-local" / "-network") removed.
     * This is the stable key used to group local and network variants of the same voice model.
     * Example: "en-au-x-aub-local" → "en-au-x-aub"
     */
    fun baseName(voice: Voice): String =
        voice.name.removeSuffix("-local").removeSuffix("-network")

    /**
     * Returns a short identifier derived from the voice name by stripping the locale prefix
     * (e.g. "en-au-") and the connectivity suffix ("-local" / "-network").
     * Matching is case-insensitive so names like "en-AU-language" are handled correctly.
     */
    fun shortId(voice: Voice): String {
        val localePrefix = "${voice.locale.language}-${voice.locale.country.lowercase(Locale.ROOT)}-"
        val nameLower = voice.name.lowercase(Locale.ROOT)
        val stripped = if (nameLower.startsWith(localePrefix)) {
            voice.name.substring(localePrefix.length)
        } else {
            voice.name
        }
        return stripped
            .removeSuffix("-local")
            .removeSuffix("-network")
            .ifBlank { voice.name }
    }

    /**
     * Returns "male", "female", or null if gender cannot be determined.
     * Uses the same heuristics as the voice scoring functions.
     */
    fun gender(voice: Voice): String? = with(voice) {
        when {
            maleScore() > 0     -> "male"
            femalePenalty() > 0 -> "female"
            else                -> null
        }
    }

    /**
     * Collapses a flat voice list into deduplicated [VoiceEntry] items, one per voice model.
     * Local and network variants of the same model are merged; local is preferred when both exist.
     * Sorted by locale display name first (groups regional variants together), then by short ID
     * within each locale for a stable secondary order.
     */
    fun voiceEntries(
        voices: List<Voice>,
        displayLocale: Locale = Locale.getDefault(),
    ): List<VoiceEntry> =
        voices
            .groupBy { baseName(it) }
            .map { (_, group) ->
                VoiceEntry(
                    localVoice   = group.find { !it.isNetworkConnectionRequired },
                    networkVoice = group.find { it.isNetworkConnectionRequired },
                )
            }
            .sortedWith(
                compareBy(
                    { it.preferredVoice.locale.getDisplayName(displayLocale) },
                    { shortId(it.preferredVoice) },
                )
            )

    fun installedVoices(voices: Set<Voice>?): Set<Voice> =
        voices.orEmpty()
            .filterNot { it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true }
            .toSet()

    fun preferredEnglishVoice(voices: Set<Voice>?): Voice? =
        preferredVoice(
            voices = voices,
            preferredLanguage = "en",
            preferredCountry = "GB",
        )

    fun preferredVoice(
        voices: Set<Voice>?,
        preferredLanguage: String,
        preferredCountry: String,
    ): Voice? {
        val candidates = voices.orEmpty()
            .filter { it.locale.languageMatches(preferredLanguage) }

        return candidates.maxWithOrNull(
            compareBy<Voice> { voice -> voice.score(preferredLanguage, preferredCountry) }
                .thenBy { voice -> voice.name }
        )
    }

    private fun Voice.score(language: String, country: String): Int =
        localeScore(language, country) +
                maleScore() -
                femalePenalty() +
                qualityScore()

    private fun Voice.localeScore(language: String, country: String): Int = when {
        locale.languageMatches(language) && locale.countryMatches(country) -> EXACT_LOCALE_SCORE
        locale.languageMatches(language) -> LANGUAGE_ONLY_SCORE
        else -> 0
    }

    private fun Voice.maleScore(): Int {
        val haystack = searchText
        return when {
            "gender=male" in haystack -> MALE_SCORE
            "#male" in haystack -> MALE_SCORE
            "_male" in haystack -> MALE_SCORE
            "-male" in haystack -> MALE_SCORE
            " male" in haystack -> MALE_SCORE
            googleVoicePrefix in knownGoogleMaleVoicePrefixes -> KNOWN_GOOGLE_MALE_SCORE
            "smtm" in haystack || "_m00" in haystack || "variant=m00" in haystack -> SAMSUNG_MALE_SCORE
            else -> 0
        }
    }

    private fun Voice.femalePenalty(): Int {
        val haystack = searchText
        return when {
            "gender=female" in haystack -> FEMALE_PENALTY
            "#female" in haystack -> FEMALE_PENALTY
            "_female" in haystack -> FEMALE_PENALTY
            "-female" in haystack -> FEMALE_PENALTY
            " female" in haystack -> FEMALE_PENALTY
            googleVoicePrefix in knownGoogleFemaleVoicePrefixes -> KNOWN_GOOGLE_FEMALE_PENALTY
            "smtf" in haystack || "_f00" in haystack || "variant=f00" in haystack -> SAMSUNG_FEMALE_PENALTY
            else -> 0
        }
    }

    private fun Voice.qualityScore(): Int {
        var score = quality - latency.coerceAtMost(500) / 10
        if (!isNetworkConnectionRequired) {
            score += OFFLINE_SCORE
        }
        return score
    }

    private val Voice.searchText: String
        get() = buildString {
            append(name.lowercase(Locale.ROOT))
            append(' ')
            features.orEmpty().forEach { feature ->
                append(feature.lowercase(Locale.ROOT))
                append(' ')
            }
        }

    private val Voice.googleVoicePrefix: String
        get() = name
            .lowercase(Locale.ROOT)
            .removeSuffix("-local")
            .removeSuffix("-network")

    private val knownGoogleMaleVoicePrefixes = setOf(
        "en-gb-x-gbb",
        "en-gb-x-gbd",
        "en-gb-x-rjs",
        "en-us-x-sfg",
        "en-us-x-tpc",
        "en-us-x-tpd",
        "en-us-x-iom",
        "en-au-x-aub",
        "en-au-x-aud",
    )

    private val knownGoogleFemaleVoicePrefixes = setOf(
        "en-gb-x-gba",
        "en-gb-x-gbc",
        "en-gb-x-gbg",
        "en-us-x-sfb",
        "en-us-x-iob",
        "en-us-x-iog",
        "en-us-x-iol",
        "en-us-x-tpf",
        "en-au-x-aua",
        "en-au-x-auc",
    )

    private fun Locale.languageMatches(language: String): Boolean =
        codesMatch(
            preferredCode = language,
            preferredIso3Code = Locale.forLanguageTag(language).safeIso3Language(),
            actualCode = this.language,
            actualIso3Code = safeIso3Language(),
        )

    private fun Locale.countryMatches(country: String): Boolean =
        codesMatch(
            preferredCode = country,
            preferredIso3Code = Locale.forLanguageTag("und-$country").safeIso3Country(),
            actualCode = this.country,
            actualIso3Code = safeIso3Country(),
        )

    private fun codesMatch(
        preferredCode: String,
        preferredIso3Code: String?,
        actualCode: String,
        actualIso3Code: String?,
    ): Boolean {
        val preferredCodes = setOfNotNull(
            preferredCode.takeIf { it.isNotBlank() }?.lowercase(Locale.ROOT),
            preferredIso3Code?.lowercase(Locale.ROOT),
        )
        val actualCodes = setOfNotNull(
            actualCode.takeIf { it.isNotBlank() }?.lowercase(Locale.ROOT),
            actualIso3Code?.lowercase(Locale.ROOT),
        )
        return preferredCodes.any { it in actualCodes }
    }

    private fun Locale.safeIso3Language(): String? =
        try {
            getISO3Language()
        } catch (_: MissingResourceException) {
            null
        }

    private fun Locale.safeIso3Country(): String? =
        try {
            getISO3Country()
        } catch (_: MissingResourceException) {
            null
        }
}

@Composable
fun voiceSubtitle(entry: VoiceEntry): String {
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
