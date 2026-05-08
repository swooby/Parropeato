package com.swooby.ropeato

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.smartfoo.android.core.FooString
import com.smartfoo.android.core.FooString.quote
import com.smartfoo.android.core.logging.FooLog
import com.smartfoo.android.core.platform.FooPlatformUtils
import com.smartfoo.android.core.texttospeech.FooTextToSpeech
import com.swooby.ropeato.ReflectionUtils.getMapOfIntFieldsToNames
import com.swooby.ropeato.ReflectionUtils.valueToString
import com.swooby.ropeato.common.BuildConfig

abstract class BaseMainActivity : ComponentActivity() {
    companion object {
        private val speechRecognizerErrors = getMapOfIntFieldsToNames(SpeechRecognizer::class, "ERROR_")
        fun speechRecognizerErrorToString(error: Int): String = valueToString(speechRecognizerErrors, error)
    }

    protected open val TAG: String by lazy { FooLog.TAG(this::class) }

    protected lateinit var tts: FooTextToSpeech
    protected lateinit var speechRecognizer: SpeechRecognizer
    protected lateinit var audioManager: AudioManager
    protected val mainHandler = Handler(Looper.getMainLooper())

    protected val viewModel by viewModels<RopeatoViewModel>()

    protected abstract fun setupUI()

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG, "+onCreate(...)")
        super.onCreate(savedInstanceState)
        setupUI()
        audioManager = getSystemService(AudioManager::class.java)
        initTextToSpeech()
        initSpeechRecognizer()
        Log.i(TAG, "-onCreate(...)")
    }

    private fun initTextToSpeech() {
        tts = FooTextToSpeech.instance
        tts.dedupe = false
        tts.setAudioAttributes(AudioAttributes.USAGE_MEDIA)
        tts.attach(object : FooTextToSpeech.FooTextToSpeechCallbacks {
            override fun onTextToSpeechInitialized(status: Int) {
                this@BaseMainActivity.onTextToSpeechInitialized(status)
            }
        })
        tts.start(this)
    }

    protected fun initSpeechRecognizer(updatePrompt: Boolean = true) {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                FooLog.d(TAG, "onReadyForSpeech(params=${FooPlatformUtils.toString(params)})")
            }

            override fun onBeginningOfSpeech() {
                FooLog.d(TAG, "onBeginningOfSpeech()")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // This callback can fire dozens of times per second on-device.
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                FooLog.d(TAG, "onBufferReceived(buffer=...)")
            }

            override fun onError(error: Int) {
                FooLog.d(TAG, "onError(error=${speechRecognizerErrorToString(error)})")
                isListening = false
                shouldProcessResults = false
                val shouldRetry = isPushToTalkPressed && when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                    SpeechRecognizer.ERROR_CLIENT -> true
                    else -> false
                }
                if (shouldRetry) {
                    viewModel.state = RopeatoViewModel.State.Listening
                    viewModel.text = "Listening..."
                    resetSpeechRecognizer()
                    mainHandler.postDelayed({
                        if (isPushToTalkPressed && !isListening) {
                            speechRecognizerStart()
                        }
                    }, 150)
                    return
                }
                resetSpeechRecognizer()
                viewModel.state = RopeatoViewModel.State.Idle
                viewModel.text = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that.\nHold the mic to try again."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard.\nHold the mic to try again."
                    else -> "Speech error ${speechRecognizerErrorToString(error)}.\nHold the mic to try again."
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                FooLog.d(TAG, "onPartialResults(partialResults=${FooPlatformUtils.toString(partialResults)})")
                latestPartialRecognition = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                latestUnstableRecognition = partialResults
                    ?.getStringArrayList("android.speech.extra.UNSTABLE_TEXT")
                    ?.joinToString(separator = "") { it }
                    ?.trim()
            }

            override fun onEndOfSpeech() {
                FooLog.d(TAG, "onEndOfSpeech()")
            }

            override fun onResults(results: Bundle?) {
                FooLog.d(TAG, "onResults(results=${FooPlatformUtils.toString(results)})")
                isListening = false
                mainHandler.removeCallbacksAndMessages(null)
                if (!shouldProcessResults) {
                    resetSpeechRecognizer()
                    return
                }
                shouldProcessResults = false
                val recognitions = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: arrayListOf()
                val confidenceScores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES) ?: floatArrayOf()
                if (recognitions.isEmpty()) {
                    viewModel.state = RopeatoViewModel.State.Idle
                    viewModel.text = "Didn't catch that.\nHold the mic to try again."
                    resetSpeechRecognizer()
                    return
                }
                FooLog.i(TAG, "onResults: confidenceScores.size=${confidenceScores.size}")
                val highestConfidenceResult = if (confidenceScores.isEmpty()) {
                    recognitions[0]
                } else {
                    var recognition = recognitions[0]
                    FooLog.v(TAG, "onResults: recognition=${quote(recognition)}")
                    var confidenceScore = confidenceScores[0]
                    FooLog.v(TAG, "onResults: confidenceScore=$confidenceScore")
                    var highestConfidenceResult = recognition
                    var highestConfidenceScore = confidenceScore
                    val resultCount = minOf(recognitions.size, confidenceScores.size)
                    for (i in 1 until resultCount) {
                        recognition = recognitions[i]
                        FooLog.v(TAG, "onResults: recognition=${quote(recognition)}")
                        confidenceScore = confidenceScores[i]
                        FooLog.v(TAG, "onResults: confidenceScore=$confidenceScore")
                        if (confidenceScore > highestConfidenceScore) {
                            highestConfidenceScore = confidenceScore
                            highestConfidenceResult = recognition
                        }
                    }
                    FooLog.i(TAG, "onResults: highestConfidenceScore=$highestConfidenceScore")
                    highestConfidenceResult
                }
                FooLog.i(TAG, "onResults: highestConfidenceResult=${quote(highestConfidenceResult)}")
                viewModel.state = RopeatoViewModel.State.Speaking
                speakRecognition(highestConfidenceResult)
                resetSpeechRecognizer()
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                FooLog.d(TAG, "onEvent(eventType=$eventType, params=${FooPlatformUtils.toString(params)})")
            }
        })

        if (updatePrompt) {
            viewModel.state = RopeatoViewModel.State.Idle
            viewModel.text = "Hold the mic\nto talk"
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.state = RopeatoViewModel.State.ShuttingDown
        speechRecognizerStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.state = RopeatoViewModel.State.Shutdown
        tts.stop()
        speechRecognizerDestroy()
    }

    private val permissionRecordAudioLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            onPermissionRecordAudioResult(isGranted)
        }

    private fun permissionRecordAudioCheck(): Boolean {
        try {
            FooLog.i(TAG, "+permissionRecordAudioCheck()")
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED -> {
                    onPermissionRecordAudioResult(true)
                    return true
                }

                shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                    permissionRecordAudioRationale()
                }

                else -> {
                    permissionRecordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
            return false
        } finally {
            FooLog.i(TAG, "-permissionRecordAudioCheck()")
        }
    }

    private fun permissionRecordAudioRationale() {
        FooLog.i(TAG, "+permissionRecordAudioRationale()")
        FooLog.i(TAG, "-permissionRecordAudioRationale()")
    }

    private fun onPermissionRecordAudioResult(isGranted: Boolean) {
        FooLog.i(TAG, "+onPermissionRecordAudioResult(isGranted=$isGranted)")
        if (isGranted && pendingStartAfterPermission) {
            pendingStartAfterPermission = false
            speechRecognizerStart()
        } else if (!isGranted) {
            pendingStartAfterPermission = false
            viewModel.state = RopeatoViewModel.State.Idle
            viewModel.text = "Microphone permission\nis required."
        } else {
            viewModel.state = RopeatoViewModel.State.Idle
            viewModel.text = "Hold the mic\nto talk"
        }
        FooLog.i(TAG, "-onPermissionRecordAudioResult(isGranted=$isGranted)")
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        FooLog.i(TAG, "dispatchKeyEvent(event=$event)")
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_STEM_1 -> {
                    FooLog.i(TAG, "dispatchKeyEvent: hardware button 1")
                    onHardwareButton1()
                    return true
                }

                KeyEvent.KEYCODE_STEM_2 -> {
                    FooLog.i(TAG, "dispatchKeyEvent: hardware button 2")
                    return true
                }

                KeyEvent.KEYCODE_STEM_3 -> {
                    FooLog.i(TAG, "dispatchKeyEvent: hardware button 3")
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private var isListening = false
    private var isPushToTalkPressed = false
    private var pendingStartAfterPermission = false
    private var shouldProcessResults = false
    private var latestPartialRecognition: String? = null
    private var latestUnstableRecognition: String? = null

    private fun onHardwareButton1() {
        isListening = if (isListening) {
            onPushToTalkReleased()
            false
        } else {
            onPushToTalkPressed()
            true
        }
    }

    protected fun onPushToTalkPressed() {
        FooLog.i(TAG, "+onPushToTalkPressed()")
        isPushToTalkPressed = true
        if (isListening) {
            FooLog.i(TAG, "-onPushToTalkPressed() already listening")
            return
        }
        tts.clear()
        pendingStartAfterPermission = true
        if (permissionRecordAudioCheck() && !isListening) {
            pendingStartAfterPermission = false
            speechRecognizerStart()
        }
        FooLog.i(TAG, "-onPushToTalkPressed()")
    }

    protected fun onPushToTalkReleased() {
        FooLog.i(TAG, "+onPushToTalkReleased()")
        isPushToTalkPressed = false
        pendingStartAfterPermission = false
        mainHandler.removeCallbacksAndMessages(null)
        val fallbackRecognition = bestPartialRecognition()
        if (isListening) {
            shouldProcessResults = true
            speechRecognizerStop()
            if (!fallbackRecognition.isNullOrBlank()) {
                viewModel.state = RopeatoViewModel.State.Idle
                viewModel.text = "Thinking..."
                mainHandler.postDelayed({
                    if (isListening && shouldProcessResults) {
                        isListening = false
                        shouldProcessResults = false
                        viewModel.state = RopeatoViewModel.State.Speaking
                        speakRecognition(fallbackRecognition)
                        resetSpeechRecognizer()
                    }
                }, 500)
            } else {
                viewModel.state = RopeatoViewModel.State.Idle
                viewModel.text = "Thinking..."
            }
        } else if (!fallbackRecognition.isNullOrBlank()) {
            viewModel.state = RopeatoViewModel.State.Speaking
            speakRecognition(fallbackRecognition)
            resetSpeechRecognizer()
        } else {
            viewModel.state = RopeatoViewModel.State.Idle
            viewModel.text = "Didn't catch that.\nHold the mic to try again."
        }
        FooLog.i(TAG, "-onPushToTalkReleased()")
    }

    protected fun volumeDown() {
        adjustMediaVolume(AudioManager.ADJUST_LOWER)
    }

    protected fun volumeUp() {
        adjustMediaVolume(AudioManager.ADJUST_RAISE)
    }

    private fun adjustMediaVolume(direction: Int) {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            direction,
            AudioManager.FLAG_PLAY_SOUND
        )
        val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val volumeMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        tts.volumeRelativeToAudioStream = volume / volumeMax.toFloat()
        viewModel.text = "Volume $volume / $volumeMax"
    }

    protected fun speechRecognizerStop() {
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.stopListening()
        }
    }

    private fun speechRecognizerCancel() {
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.cancel()
        }
    }

    private fun speechRecognizerDestroy() {
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.destroy()
        }
    }

    private fun resetSpeechRecognizer() {
        speechRecognizerDestroy()
        initSpeechRecognizer(updatePrompt = false)
    }

    private fun speechRecognizerStart() {
        FooLog.i(TAG, "+speechRecognizerStart()")
        isListening = true
        shouldProcessResults = true
        latestPartialRecognition = null
        latestUnstableRecognition = null
        viewModel.state = RopeatoViewModel.State.Listening
        viewModel.text = "Listening..."
        val recognizerIntent = Intent()
        recognizerIntent.action = RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            recognizerIntent.putExtra(
                RecognizerIntent.EXTRA_ENABLE_FORMATTING,
                RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY
            )
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_MASK_OFFENSIVE_WORDS, false)
        }
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        speechRecognizer.startListening(recognizerIntent)
        FooLog.i(TAG, "-speechRecognizerStart()")
    }

    private fun onTextToSpeechInitialized(status: Int) {
        Log.i(TAG, "onTextToSpeechInitialized(status=${FooTextToSpeech.statusToString(status)})")

        if (status != TextToSpeech.SUCCESS) {
            viewModel.state = RopeatoViewModel.State.InitializingError
            var text = "TextToSpeech init failed."
            if (BuildConfig.DEBUG) {
                text += "\nTTS may not work\nin an emulator."
            }
            text += "\nstatus=${FooTextToSpeech.statusToString(status)}"
            viewModel.text = text
            return
        }

        viewModel.state = RopeatoViewModel.State.Initialized
        viewModel.text = "Hold the mic\nto talk"

        val voices = tts.voices!!
        Log.i(TAG, "onTextToSpeechInitialized: voices=${FooString.toString(voices, true)}")
        val voice = voices.find { voice -> voice.name.equals("en-GB-language", ignoreCase = true) }
            ?: voices.find { voice -> voice.name.equals("en-US-default", ignoreCase = true) }
            ?: voices.find { voice -> voice.locale.language.equals("en", ignoreCase = true) }
            ?: voices.elementAt(0)
        Log.i(TAG, "onTextToSpeechInitialized: voice=$voice")
        tts.setVoiceName(voice.name)
        tts.voiceSpeed = 2.0f

        tts.speak("Talk to me!")
    }

    private fun speakRecognition(text: String) {
        val spokenText = text.trim()
        viewModel.text = spokenText
        tts.speak(spokenText)
    }

    private fun bestPartialRecognition(): String? =
        latestUnstableRecognition?.takeIf { it.isNotBlank() }
            ?: latestPartialRecognition?.takeIf { it.isNotBlank() }
}
