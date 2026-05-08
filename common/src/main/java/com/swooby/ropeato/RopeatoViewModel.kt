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

    fun appendText(text: String) {
        _text.value += " $text"
    }
}
