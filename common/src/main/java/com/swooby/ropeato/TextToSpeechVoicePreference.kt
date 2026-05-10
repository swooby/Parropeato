package com.swooby.ropeato

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale
import java.util.MissingResourceException

object TextToSpeechVoicePreference {
    private const val EXACT_LOCALE_SCORE = 1000
    private const val LANGUAGE_ONLY_SCORE = 400
    private const val MALE_SCORE = 300
    private const val KNOWN_GOOGLE_MALE_SCORE = 260
    private const val SAMSUNG_MALE_SCORE = 220
    private const val FEMALE_PENALTY = 300
    private const val KNOWN_GOOGLE_FEMALE_PENALTY = 260
    private const val SAMSUNG_FEMALE_PENALTY = 150
    private const val OFFLINE_SCORE = 50

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
        val installedVoices = voices.orEmpty()
            .filterNot { it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true }
            .filter { it.locale.languageMatches(preferredLanguage) }

        return installedVoices.maxWithOrNull(
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
