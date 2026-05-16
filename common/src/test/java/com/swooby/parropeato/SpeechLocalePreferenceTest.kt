package com.swooby.parropeato

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SpeechLocalePreferenceTest {

    // --- localeOptions ---

    @Test
    fun `localeOptions first entry is device default with null tag`() {
        val options = SpeechLocalePreference.localeOptions(listOf("en-US"), "Device Default")
        assertNull(options.first().tag)
        assertEquals("Device Default", options.first().displayName)
    }

    @Test
    fun `localeOptions size is one plus number of tags`() {
        val tags = listOf("en-US", "fr-FR", "de-DE")
        val options = SpeechLocalePreference.localeOptions(tags, "Default")
        assertEquals(4, options.size)
    }

    @Test
    fun `localeOptions contains entry for each supplied tag`() {
        val tags = listOf("en-US", "fr-FR")
        val options = SpeechLocalePreference.localeOptions(tags, "Default")
        assertTrue(options.any { it.tag == "en-US" })
        assertTrue(options.any { it.tag == "fr-FR" })
    }

    @Test
    fun `localeOptions returns only default entry for empty tag list`() {
        val options = SpeechLocalePreference.localeOptions(emptyList(), "Default")
        assertEquals(1, options.size)
        assertNull(options.first().tag)
    }

    // --- groupedLocaleOptions ---

    @Test
    fun `groupedLocaleOptions device default has null tag and correct label`() {
        val result = SpeechLocalePreference.groupedLocaleOptions(
            listOf("en-US"), "My Default", Locale.ENGLISH
        )
        assertNull(result.deviceDefault.tag)
        assertEquals("My Default", result.deviceDefault.displayName)
    }

    @Test
    fun `groupedLocaleOptions groups en-US and en-GB under same language group`() {
        val result = SpeechLocalePreference.groupedLocaleOptions(
            listOf("en-US", "en-GB"), "Default", Locale.ENGLISH
        )
        assertEquals(1, result.languageGroups.size)
        assertEquals("en", result.languageGroups.first().languageCode)
        assertEquals(2, result.languageGroups.first().options.size)
    }

    @Test
    fun `groupedLocaleOptions separates different languages into distinct groups`() {
        val result = SpeechLocalePreference.groupedLocaleOptions(
            listOf("en-US", "fr-FR", "de-DE"), "Default", Locale.ENGLISH
        )
        assertEquals(3, result.languageGroups.size)
        val codes = result.languageGroups.map { it.languageCode }.toSet()
        assertEquals(setOf("en", "fr", "de"), codes)
    }

    @Test
    fun `groupedLocaleOptions language groups are sorted alphabetically by display name`() {
        val result = SpeechLocalePreference.groupedLocaleOptions(
            listOf("zh-CN", "en-US", "ar-EG"), "Default", Locale.ENGLISH
        )
        val names = result.languageGroups.map { it.displayLanguage }
        assertEquals(names.sorted(), names)
    }

    @Test
    fun `groupedLocaleOptions options within group are sorted alphabetically`() {
        val result = SpeechLocalePreference.groupedLocaleOptions(
            listOf("en-ZA", "en-AU", "en-US", "en-GB"), "Default", Locale.ENGLISH
        )
        val enGroup = result.languageGroups.first { it.languageCode == "en" }
        val names = enGroup.options.map { it.displayName }
        assertEquals(names.sorted(), names)
    }

    // --- hasVariants ---

    @Test
    fun `hasVariants is true when group contains multiple locales`() {
        val result = SpeechLocalePreference.groupedLocaleOptions(
            listOf("en-US", "en-GB"), "Default", Locale.ENGLISH
        )
        assertTrue(result.languageGroups.first().hasVariants)
    }

    @Test
    fun `hasVariants is false for single-locale group`() {
        val result = SpeechLocalePreference.groupedLocaleOptions(
            listOf("fr-FR"), "Default", Locale.ENGLISH
        )
        assertFalse(result.languageGroups.first().hasVariants)
    }

    // --- CANDIDATES ---

    @Test
    fun `all CANDIDATES parse as valid locale tags without exception`() {
        for (tag in SpeechLocalePreference.CANDIDATES) {
            val locale = Locale.forLanguageTag(tag)
            assertNotNull("Failed to parse tag: $tag", locale)
        }
    }

    @Test
    fun `CANDIDATES contains expected English variants`() {
        assertTrue("en-US" in SpeechLocalePreference.CANDIDATES)
        assertTrue("en-GB" in SpeechLocalePreference.CANDIDATES)
        assertTrue("en-AU" in SpeechLocalePreference.CANDIDATES)
    }

    // --- sttLocaleGroupSubtitle ---

    @Test
    fun `sttLocaleGroupSubtitle returns country name for regional tag`() {
        val result = sttLocaleGroupSubtitle("en-US", Locale.ENGLISH)
        assertEquals("United States", result)
    }

    @Test
    fun `sttLocaleGroupSubtitle falls back to display name when no country`() {
        // "en" has no country component; getDisplayCountry returns ""
        val result = sttLocaleGroupSubtitle("en", Locale.ENGLISH)
        assertEquals("English", result)
    }
}
