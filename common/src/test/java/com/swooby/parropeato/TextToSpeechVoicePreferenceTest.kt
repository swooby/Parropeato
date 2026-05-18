package com.swooby.parropeato

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class TextToSpeechVoicePreferenceTest {

    private fun voice(
        name: String,
        locale: Locale,
        quality: Int = 200,
        latency: Int = 500,
        needsNetwork: Boolean = false,
        features: Set<String> = emptySet(),
    ) = Voice(name, locale, quality, latency, needsNetwork, features)

    // --- baseName ---

    @Test
    fun `baseName strips -local suffix`() {
        val v = voice("en-au-x-aub-local", Locale.forLanguageTag("en-AU"))
        assertEquals("en-au-x-aub", TextToSpeechVoicePreference.baseName(v))
    }

    @Test
    fun `baseName strips -network suffix`() {
        val v = voice("en-au-x-aub-network", Locale.forLanguageTag("en-AU"))
        assertEquals("en-au-x-aub", TextToSpeechVoicePreference.baseName(v))
    }

    @Test
    fun `baseName leaves name without suffix unchanged`() {
        val v = voice("en-au-x-aub", Locale.forLanguageTag("en-AU"))
        assertEquals("en-au-x-aub", TextToSpeechVoicePreference.baseName(v))
    }

    // --- shortId ---

    @Test
    fun `shortId strips locale prefix and -local suffix`() {
        val v = voice("en-au-x-aub-local", Locale.forLanguageTag("en-AU"))
        assertEquals("x-aub", TextToSpeechVoicePreference.shortId(v))
    }

    @Test
    fun `shortId strips locale prefix and -network suffix`() {
        val v = voice("en-us-x-sfg-network", Locale.forLanguageTag("en-US"))
        assertEquals("x-sfg", TextToSpeechVoicePreference.shortId(v))
    }

    @Test
    fun `shortId returns name unchanged when no locale prefix matches`() {
        val v = voice("custom-voice-name", Locale.forLanguageTag("en-US"))
        assertEquals("custom-voice-name", TextToSpeechVoicePreference.shortId(v))
    }

    // --- gender ---

    @Test
    fun `gender returns male for gender=male feature`() {
        val v = voice("test-voice", Locale.forLanguageTag("en-US"), features = setOf("gender=male"))
        assertEquals("male", TextToSpeechVoicePreference.gender(v))
    }

    @Test
    fun `gender returns female for gender=female feature`() {
        val v = voice("test-voice", Locale.forLanguageTag("en-US"), features = setOf("gender=female"))
        assertEquals("female", TextToSpeechVoicePreference.gender(v))
    }

    @Test
    fun `gender returns male for known Google male voice prefix`() {
        val v = voice("en-gb-x-gbb-local", Locale.forLanguageTag("en-GB"))
        assertEquals("male", TextToSpeechVoicePreference.gender(v))
    }

    @Test
    fun `gender returns female for known Google female voice prefix`() {
        val v = voice("en-gb-x-gba-local", Locale.forLanguageTag("en-GB"))
        assertEquals("female", TextToSpeechVoicePreference.gender(v))
    }

    @Test
    fun `gender returns male for _male substring in name`() {
        val v = voice("en-us-voice_male", Locale.forLanguageTag("en-US"))
        assertEquals("male", TextToSpeechVoicePreference.gender(v))
    }

    @Test
    fun `gender returns female for _female substring in name`() {
        val v = voice("en-us-voice_female", Locale.forLanguageTag("en-US"))
        assertEquals("female", TextToSpeechVoicePreference.gender(v))
    }

    @Test
    fun `gender returns male for Samsung smtm marker in name`() {
        val v = voice("en-us-smtm-voice", Locale.forLanguageTag("en-US"))
        assertEquals("male", TextToSpeechVoicePreference.gender(v))
    }

    @Test
    fun `gender returns female for Samsung smtf marker in name`() {
        val v = voice("en-us-smtf-voice", Locale.forLanguageTag("en-US"))
        assertEquals("female", TextToSpeechVoicePreference.gender(v))
    }

    @Test
    fun `gender returns null for unknown voice`() {
        val v = voice("en-us-x-unknown", Locale.forLanguageTag("en-US"))
        assertNull(TextToSpeechVoicePreference.gender(v))
    }

    // --- voiceEntries ---

    @Test
    fun `voiceEntries merges local and network variants into single entry`() {
        val local = voice("en-au-x-aub-local", Locale.forLanguageTag("en-AU"), needsNetwork = false)
        val network = voice("en-au-x-aub-network", Locale.forLanguageTag("en-AU"), needsNetwork = true)
        val entries = TextToSpeechVoicePreference.voiceEntries(listOf(local, network))
        assertEquals(1, entries.size)
        assertEquals(local, entries[0].localVoice)
        assertEquals(network, entries[0].networkVoice)
    }

    @Test
    fun `voiceEntries preferredVoice is local when both variants present`() {
        val local = voice("en-au-x-aub-local", Locale.forLanguageTag("en-AU"), needsNetwork = false)
        val network = voice("en-au-x-aub-network", Locale.forLanguageTag("en-AU"), needsNetwork = true)
        val entries = TextToSpeechVoicePreference.voiceEntries(listOf(network, local))
        assertEquals(local, entries[0].preferredVoice)
    }

    @Test
    fun `voiceEntries network-only entry has null localVoice`() {
        val network = voice("en-us-x-sfg-network", Locale.forLanguageTag("en-US"), needsNetwork = true)
        val entries = TextToSpeechVoicePreference.voiceEntries(listOf(network))
        assertEquals(1, entries.size)
        assertNull(entries[0].localVoice)
        assertEquals(network, entries[0].networkVoice)
    }

    @Test
    fun `voiceEntries treats distinct base names as separate entries`() {
        val v1 = voice("en-us-x-sfg-local", Locale.forLanguageTag("en-US"))
        val v2 = voice("en-us-x-tpc-local", Locale.forLanguageTag("en-US"))
        val entries = TextToSpeechVoicePreference.voiceEntries(listOf(v1, v2))
        assertEquals(2, entries.size)
    }

    @Test
    fun `voiceEntries returns empty list for empty input`() {
        assertTrue(TextToSpeechVoicePreference.voiceEntries(emptyList()).isEmpty())
    }

    // --- groupedVoices ---

    @Test
    fun `groupedVoices groups en-US and en-GB under same language code`() {
        val enUS = voice("en-us-v1-local", Locale.forLanguageTag("en-US"))
        val enGB = voice("en-gb-v1-local", Locale.forLanguageTag("en-GB"))
        val frFR = voice("fr-fr-v1-local", Locale.forLanguageTag("fr-FR"))
        val groups = TextToSpeechVoicePreference.groupedVoices(listOf(enUS, enGB, frFR), Locale.ENGLISH)
        assertEquals(2, groups.size)
        val enGroup = groups.first { it.languageCode == "en" }
        assertEquals(2, enGroup.voices.size)
    }

    @Test
    fun `groupedVoices sorts groups by display language alphabetically`() {
        val frFR = voice("fr-fr-v1-local", Locale.forLanguageTag("fr-FR"))
        val enUS = voice("en-us-v1-local", Locale.forLanguageTag("en-US"))
        val groups = TextToSpeechVoicePreference.groupedVoices(listOf(frFR, enUS), Locale.ENGLISH)
        assertEquals("en", groups[0].languageCode)
        assertEquals("fr", groups[1].languageCode)
    }

    @Test
    fun `groupedVoices hasVariants is true when group has multiple locale voices`() {
        val enUS = voice("en-us-v1-local", Locale.forLanguageTag("en-US"))
        val enGB = voice("en-gb-v1-local", Locale.forLanguageTag("en-GB"))
        val groups = TextToSpeechVoicePreference.groupedVoices(listOf(enUS, enGB), Locale.ENGLISH)
        assertTrue(groups.first().hasVariants)
    }

    @Test
    fun `groupedVoices hasVariants is false for single voice in group`() {
        val frFR = voice("fr-fr-v1-local", Locale.forLanguageTag("fr-FR"))
        val groups = TextToSpeechVoicePreference.groupedVoices(listOf(frFR), Locale.ENGLISH)
        assertFalse(groups.first().hasVariants)
    }

    // --- preferredVoice ---

    @Test
    fun `preferredVoice returns null for null set`() {
        assertNull(TextToSpeechVoicePreference.preferredVoice(null, "en", "US"))
    }

    @Test
    fun `preferredVoice returns null for empty set`() {
        assertNull(TextToSpeechVoicePreference.preferredVoice(emptySet(), "en", "US"))
    }

    @Test
    fun `preferredVoice returns null when no voices match language`() {
        val frVoice = voice("fr-fr-v1", Locale.forLanguageTag("fr-FR"))
        assertNull(TextToSpeechVoicePreference.preferredVoice(setOf(frVoice), "en", "US"))
    }

    @Test
    fun `preferredVoice prefers exact locale match over language-only match`() {
        val enUS = voice("en-us-v1", Locale.forLanguageTag("en-US"), quality = 300)
        val enGB = voice("en-gb-v1", Locale.forLanguageTag("en-GB"), quality = 300)
        val result = TextToSpeechVoicePreference.preferredVoice(setOf(enUS, enGB), "en", "GB")
        assertEquals(enGB, result)
    }

    @Test
    fun `preferredVoice prefers offline voice over network voice at equal quality`() {
        val local = voice("en-us-v1-local", Locale.forLanguageTag("en-US"), quality = 300, needsNetwork = false)
        val network = voice("en-us-v1-network", Locale.forLanguageTag("en-US"), quality = 300, needsNetwork = true)
        val result = TextToSpeechVoicePreference.preferredVoice(setOf(local, network), "en", "US")
        assertEquals(local, result)
    }

    @Test
    fun `preferredVoice scores known Google male prefix above unknown gender`() {
        // en-gb-x-gbb is a known Google male prefix — gets KNOWN_GOOGLE_MALE_SCORE bonus
        val maleVoice = voice("en-gb-x-gbb-local", Locale.forLanguageTag("en-GB"), quality = 300)
        val unknownVoice = voice("en-gb-x-unk-local", Locale.forLanguageTag("en-GB"), quality = 300)
        val result = TextToSpeechVoicePreference.preferredVoice(setOf(maleVoice, unknownVoice), "en", "GB")
        assertEquals(maleVoice, result)
    }

    @Test
    fun `preferredVoice penalizes female voice below unknown gender`() {
        // en-gb-x-gba is a known Google female prefix — gets KNOWN_GOOGLE_FEMALE_PENALTY
        val femaleVoice = voice("en-gb-x-gba-local", Locale.forLanguageTag("en-GB"), quality = 300)
        val unknownVoice = voice("en-gb-x-unk-local", Locale.forLanguageTag("en-GB"), quality = 300)
        val result = TextToSpeechVoicePreference.preferredVoice(setOf(femaleVoice, unknownVoice), "en", "GB")
        assertEquals(unknownVoice, result)
    }

    // --- installedVoices ---

    @Test
    fun `installedVoices removes voices with KEY_FEATURE_NOT_INSTALLED`() {
        val installed = voice("en-us-v1", Locale.forLanguageTag("en-US"))
        val notInstalled = voice(
            "en-us-v2", Locale.forLanguageTag("en-US"),
            features = setOf(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
        )
        val result = TextToSpeechVoicePreference.installedVoices(setOf(installed, notInstalled))
        assertEquals(setOf(installed), result)
    }

    @Test
    fun `installedVoices returns empty set for null input`() {
        assertEquals(emptySet<Voice>(), TextToSpeechVoicePreference.installedVoices(null))
    }

    @Test
    fun `installedVoices returns all voices when none have NOT_INSTALLED feature`() {
        val v1 = voice("en-us-v1", Locale.forLanguageTag("en-US"))
        val v2 = voice("en-us-v2", Locale.forLanguageTag("en-US"))
        val result = TextToSpeechVoicePreference.installedVoices(setOf(v1, v2))
        assertEquals(setOf(v1, v2), result)
    }

    // --- VoiceEntry ---

    @Test
    fun `VoiceEntry isSelected matches local voice name`() {
        val local = voice("en-us-v1-local", Locale.forLanguageTag("en-US"))
        val entry = VoiceEntry(localVoice = local, networkVoice = null)
        assertTrue(entry.isSelected("en-us-v1-local"))
        assertFalse(entry.isSelected("en-us-v2-local"))
    }

    @Test
    fun `VoiceEntry isSelected matches network voice name`() {
        val network = voice("en-us-v1-network", Locale.forLanguageTag("en-US"), needsNetwork = true)
        val entry = VoiceEntry(localVoice = null, networkVoice = network)
        assertTrue(entry.isSelected("en-us-v1-network"))
    }

    @Test
    fun `VoiceEntry isSelected returns false for null selectedName`() {
        val local = voice("en-us-v1-local", Locale.forLanguageTag("en-US"))
        val entry = VoiceEntry(localVoice = local, networkVoice = null)
        assertFalse(entry.isSelected(null))
    }

    @Test
    fun `VoiceEntry preferredVoice is networkVoice when localVoice is null`() {
        val network = voice("en-us-v1-network", Locale.forLanguageTag("en-US"), needsNetwork = true)
        val entry = VoiceEntry(localVoice = null, networkVoice = network)
        assertEquals(network, entry.preferredVoice)
    }
}
