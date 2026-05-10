package com.swooby.ropeato

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

class RopeatoViewModel : ViewModel() {
    companion object {
        private const val TAG = "RopeatoViewModel"
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
            when (state) {
                State.Initializing -> text = "Initializing..."
                State.InitializingError -> text = "Error"
                State.Initialized -> text = "Initialized"
                State.Listening -> text = "Listening"
                State.Speaking -> text = "Speaking"
                State.Idle -> text = "Idle"
                State.ShuttingDown -> text = "Shutting Down"
                State.Shutdown -> text = "Shutdown"
            }
        }

    val _text = mutableStateOf("Initializing...")
    var text: String
        get() = _text.value
        set(value) {
            _text.value = value
        }

    private val _volumePercent = mutableStateOf(0.5f)
    var volumePercent: Float
        get() = _volumePercent.value
        set(value) {
            _volumePercent.value = value.coerceIn(0f, 1f)
        }

    private val _voiceSpeed = mutableStateOf(2.0f)
    var voiceSpeed: Float
        get() = _voiceSpeed.value
        set(value) {
            _voiceSpeed.value = value.coerceIn(VOICE_SPEED_MIN, VOICE_SPEED_MAX)
        }

    fun appendText(text: String) {
        _text.value += " $text"
    }
}

const val VOICE_SPEED_MIN = 0.1f
const val VOICE_SPEED_MAX = 3.0f
