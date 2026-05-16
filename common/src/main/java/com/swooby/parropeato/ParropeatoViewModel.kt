package com.swooby.parropeato

import android.app.Application
import android.speech.tts.Voice
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import com.smartfoo.android.core.texttospeech.FooTextToSpeech
import com.swooby.parropeato.common.R

class ParropeatoViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "ParropeatoViewModel"
    }

    enum class State {
        Initializing,
        InitializingError,
        Initialized,
        Listening,
        Speaking,
        Idle,
        ShuttingDown,
        Shutdown,
    }

    private val _state = mutableStateOf(State.Initializing)
    var state: State
        get() = _state.value
        set(value) {
            _state.value = value
            val ctx = getApplication<Application>()
            text = when (state) {
                State.Initializing      -> ctx.getString(R.string.state_initializing)
                State.InitializingError -> ctx.getString(R.string.state_initializing_error)
                State.Initialized       -> ctx.getString(R.string.state_initialized)
                State.Listening         -> ctx.getString(R.string.state_listening)
                State.Speaking          -> ctx.getString(R.string.state_speaking)
                State.Idle              -> ctx.getString(R.string.state_idle)
                State.ShuttingDown      -> ctx.getString(R.string.state_shutting_down)
                State.Shutdown          -> ctx.getString(R.string.state_shutdown)
            }
        }

    val _text = mutableStateOf(application.getString(R.string.state_initializing))
    var text: String
        get() = _text.value
        set(value) {
            _text.value = value
        }

    private val _volumePercent = mutableStateOf(0f) // placeholder; always replaced by system volume in onCreate
    var volumePercent: Float
        get() = _volumePercent.value
        set(value) {
            _volumePercent.value = value.coerceIn(0f, 1f)
        }

    private val _voiceSpeed = mutableStateOf(VOICE_SPEED_DEFAULT)
    var voiceSpeed: Float
        get() = _voiceSpeed.value
        set(value) {
            _voiceSpeed.value = value.coerceIn(VOICE_SPEED_MIN, VOICE_SPEED_MAX)
        }

    private val _voicePitch = mutableStateOf(VOICE_PITCH_DEFAULT)
    var voicePitch: Float
        get() = _voicePitch.value
        set(value) {
            _voicePitch.value = value.coerceIn(VOICE_PITCH_MIN, VOICE_PITCH_MAX)
        }

    private val _showSettings = mutableStateOf(false)
    var showSettings: Boolean
        get() = _showSettings.value
        set(value) { _showSettings.value = value }

    private val _availableVoices = mutableStateOf<List<Voice>>(emptyList())
    var availableVoices: List<Voice>
        get() = _availableVoices.value
        set(value) { _availableVoices.value = value }

    private val _ttsDefaultVoiceName = mutableStateOf<String?>(null)
    var ttsDefaultVoiceName: String?
        get() = _ttsDefaultVoiceName.value
        set(value) { _ttsDefaultVoiceName.value = value }

    private val _selectedVoiceName = mutableStateOf<String?>(null)
    var selectedVoiceName: String?
        get() = _selectedVoiceName.value
        set(value) { _selectedVoiceName.value = value }

    private val _speechRecognizerLocale = mutableStateOf<String?>(null)
    var speechRecognizerLocale: String?
        get() = _speechRecognizerLocale.value
        set(value) { _speechRecognizerLocale.value = value }

    private val _supportedSpeechLocales = mutableStateOf<List<String>>(emptyList())
    var supportedSpeechLocales: List<String>
        get() = _supportedSpeechLocales.value
        set(value) { _supportedSpeechLocales.value = value }

    private val _speechLocalesSupportChecked = mutableStateOf(false)
    var speechLocalesSupportChecked: Boolean
        get() = _speechLocalesSupportChecked.value
        set(value) { _speechLocalesSupportChecked.value = value }

    private val _installedSpeechLocales = mutableStateOf<Set<String>>(emptySet())
    var installedSpeechLocales: Set<String>
        get() = _installedSpeechLocales.value
        set(value) { _installedSpeechLocales.value = value }

    private val _isNetworkAvailable = mutableStateOf(true)
    var isNetworkAvailable: Boolean
        get() = _isNetworkAvailable.value
        set(value) { _isNetworkAvailable.value = value }

    private val _cuteIcons = mutableStateOf(false)
    var cuteIcons: Boolean
        get() = _cuteIcons.value
        set(value) { _cuteIcons.value = value }

    private val _accentColor = mutableStateOf(ACCENT_COLOR_DEFAULT_ARGB)
    var accentColor: Int
        get() = _accentColor.value
        set(value) { _accentColor.value = value }

    fun appendText(text: String) {
        _text.value += " $text"
    }
}

const val VOICE_SPEED_MIN     = 0.1f
const val VOICE_SPEED_DEFAULT = FooTextToSpeech.DEFAULT_VOICE_SPEED
const val VOICE_SPEED_MAX     = 3.0f

const val VOICE_PITCH_MIN     = 0.25f
const val VOICE_PITCH_DEFAULT = FooTextToSpeech.DEFAULT_VOICE_PITCH
const val VOICE_PITCH_MAX     = 2.0f
