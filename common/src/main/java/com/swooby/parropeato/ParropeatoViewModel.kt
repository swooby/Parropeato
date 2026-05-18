package com.swooby.parropeato

import android.app.Application
import android.speech.tts.Voice
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.AndroidViewModel
import com.smartfoo.android.core.texttospeech.FooTextToSpeech
import com.swooby.parropeato.common.R
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

private fun <T> composeState(initial: T): ReadWriteProperty<Any?, T> =
    object : ReadWriteProperty<Any?, T> {
        private val backing = mutableStateOf(initial)
        override fun getValue(thisRef: Any?, property: KProperty<*>) = backing.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) { backing.value = value }
    }

private fun coercedFloatState(initial: Float, min: Float, max: Float): ReadWriteProperty<Any?, Float> =
    object : ReadWriteProperty<Any?, Float> {
        private val backing = mutableStateOf(initial)
        override fun getValue(thisRef: Any?, property: KProperty<*>) = backing.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) { backing.value = value.coerceIn(min, max) }
    }

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
    private val _text = mutableStateOf(application.getString(R.string.state_initializing))

    var state: State
        get() = _state.value
        set(value) {
            val ctx = getApplication<Application>()
            val newText = when (value) {
                State.Initializing      -> ctx.getString(R.string.state_initializing)
                State.InitializingError -> ctx.getString(R.string.state_initializing_error)
                State.Initialized       -> ctx.getString(R.string.state_initialized)
                State.Listening         -> ctx.getString(R.string.state_listening)
                State.Speaking          -> ctx.getString(R.string.state_speaking)
                State.Idle              -> ctx.getString(R.string.state_idle)
                State.ShuttingDown      -> ctx.getString(R.string.state_shutting_down)
                State.Shutdown          -> ctx.getString(R.string.state_shutdown)
            }
            // Batch both writes into one snapshot commit so Compose triggers a single
            // recomposition instead of two separate ones.
            Snapshot.withMutableSnapshot {
                _state.value = value
                _text.value = newText
            }
        }

    var text: String
        get() = _text.value
        set(value) { _text.value = value }

    // placeholder; always replaced by system volume in onCreate
    var volumePercent: Float by coercedFloatState(0f, 0f, 1f)
    var voiceSpeed: Float by coercedFloatState(VOICE_SPEED_DEFAULT, VOICE_SPEED_MIN, VOICE_SPEED_MAX)
    var voicePitch: Float by coercedFloatState(VOICE_PITCH_DEFAULT, VOICE_PITCH_MIN, VOICE_PITCH_MAX)

    var showSettings: Boolean by composeState(false)
    var availableVoices: List<Voice> by composeState(emptyList())
    var ttsDefaultVoiceName: String? by composeState(null)
    var selectedVoiceName: String? by composeState(null)
    var speechRecognizerLocale: String? by composeState(null)
    var supportedSpeechLocales: List<String> by composeState(emptyList())
    var speechLocalesSupportChecked: Boolean by composeState(false)
    var installedSpeechLocales: Set<String> by composeState(emptySet())
    var isNetworkAvailable: Boolean by composeState(true)
    var cuteIcons: Boolean by composeState(false)
    var accentColor: Int by composeState(ACCENT_COLOR_DEFAULT_ARGB)
    var diagnosticsEnabled: Boolean by composeState(false)
}

const val VOICE_SPEED_MIN     = 0.1f
const val VOICE_SPEED_DEFAULT = FooTextToSpeech.DEFAULT_VOICE_SPEED
const val VOICE_SPEED_MAX     = 3.0f

const val VOICE_PITCH_MIN     = 0.25f
const val VOICE_PITCH_DEFAULT = FooTextToSpeech.DEFAULT_VOICE_PITCH
const val VOICE_PITCH_MAX     = 2.0f
