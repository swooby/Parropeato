package com.swooby.parropeato

import android.content.Context
import androidx.core.content.edit
import com.swooby.parropeato.Settings.Companion.CURRENT_VERSION

class Settings(context: Context, val defaultTtsVoiceSpeed: Float = VOICE_SPEED_DEFAULT) {
    private val prefs = context.getSharedPreferences("parropeato_settings", Context.MODE_PRIVATE)

    var ttsVoiceName: String?
        get() = prefs.getString(KEY_TTS_VOICE_NAME, null)
        set(value) = prefs.edit {
            if (value != null) putString(KEY_TTS_VOICE_NAME, value) else remove(KEY_TTS_VOICE_NAME)
        }

    var speechRecognizerLocale: String?
        get() = prefs.getString(KEY_SPEECH_RECOGNIZER_LOCALE, null)
        set(value) = prefs.edit {
            if (value != null) putString(KEY_SPEECH_RECOGNIZER_LOCALE, value) else remove(
                KEY_SPEECH_RECOGNIZER_LOCALE
            )
        }

    /** Returns the persisted speed, or [defaultTtsVoiceSpeed] if none has been saved yet. */
    var ttsVoiceSpeed: Float
        get() = if (prefs.contains(KEY_TTS_VOICE_SPEED)) prefs.getFloat(KEY_TTS_VOICE_SPEED, 0f) else defaultTtsVoiceSpeed
        set(value) = prefs.edit { putFloat(KEY_TTS_VOICE_SPEED, value) }

    /** Returns the persisted pitch, or [VOICE_PITCH_DEFAULT] if none has been saved yet. */
    var ttsPitch: Float
        get() = if (prefs.contains(KEY_TTS_VOICE_PITCH)) prefs.getFloat(KEY_TTS_VOICE_PITCH, 0f) else VOICE_PITCH_DEFAULT
        set(value) = prefs.edit { putFloat(KEY_TTS_VOICE_PITCH, value) }

    var cuteIcons: Boolean
        get() = prefs.getBoolean(KEY_CUTE_ICONS, false)
        set(value) = prefs.edit { putBoolean(KEY_CUTE_ICONS, value) }

    var accentColor: Int
        get() = prefs.getInt(KEY_ACCENT_COLOR, ACCENT_COLOR_DEFAULT_ARGB)
        set(value) = prefs.edit { putInt(KEY_ACCENT_COLOR, value) }

    /**
     * Settings schema version. Bump [CURRENT_VERSION] whenever a breaking change requires
     * clearing or transforming previously-persisted values on upgrade.
     *
     * History:
     *  0 (implicit) — initial; TTS voice auto-selected by preferredEnglishVoice() heuristic.
     *  1            — voice selection is now explicit; old auto-saved name must be cleared.
     */
    var settingsVersion: Int
        get() = prefs.getInt(KEY_SETTINGS_VERSION, 0)
        set(value) = prefs.edit { putInt(KEY_SETTINGS_VERSION, value) }

    companion object {
        private const val KEY_TTS_VOICE_NAME = "tts_voice_name"
        private const val KEY_SPEECH_RECOGNIZER_LOCALE = "speech_recognizer_locale"
        private const val KEY_TTS_VOICE_SPEED = "tts_voice_speed"
        private const val KEY_TTS_VOICE_PITCH = "tts_voice_pitch"
        private const val KEY_CUTE_ICONS = "cute_icons"
        private const val KEY_ACCENT_COLOR = "accent_color"
        private const val KEY_SETTINGS_VERSION = "settings_version"
        const val CURRENT_VERSION = 1
    }
}
