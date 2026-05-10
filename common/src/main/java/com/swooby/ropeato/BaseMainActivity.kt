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
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.smartfoo.android.core.FooString
import com.smartfoo.android.core.FooString.quote
import com.smartfoo.android.core.logging.FooLog
import com.smartfoo.android.core.platform.FooPlatformUtils
import com.smartfoo.android.core.texttospeech.FooTextToSpeech
import com.swooby.ropeato.ReflectionUtils.getMapOfIntFieldsToNames
import com.swooby.ropeato.ReflectionUtils.valueToString
import com.swooby.ropeato.common.BuildConfig
import com.swooby.ropeato.common.R
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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

    protected open val textToSpeechVoiceSpeed: Float = 2.0f
    protected open val watchFaceSceneScale: Float = 0.92f
    protected open val watchFaceControlsScale: Float = 0.88f
    protected open val watchFaceBorderOutset: Boolean = false

    protected open fun setupUI() {
        setContent {
            RopeatoApp(
                viewModel = viewModel,
                logTag = TAG,
                sceneScale = watchFaceSceneScale,
                controlsScale = watchFaceControlsScale,
                borderOutset = watchFaceBorderOutset,
                platformOverlay = { PlatformOverlay() },
                onPushToTalkPressed = ::onPushToTalkPressed,
                onPushToTalkReleased = ::onPushToTalkReleased,
                onVolumeChange = ::setVolumePercent,
                onVoiceSpeedChange = ::setVoiceSpeed,
            )
        }
    }

    @Composable
    protected open fun PlatformOverlay() {
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG, "+onCreate(...)")
        super.onCreate(savedInstanceState)
        audioManager = getSystemService(AudioManager::class.java)
        updateMediaVolumeState(updateText = false)
        viewModel.voiceSpeed = textToSpeechVoiceSpeed
        setupUI()
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

    protected fun setVolumePercent(volumePercent: Float) {
        val volumeMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val volume = (volumeMax * volumePercent.coerceIn(0f, 1f)).roundToInt()
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            volume,
            AudioManager.FLAG_PLAY_SOUND
        )
        updateMediaVolumeState(updateText = true)
    }

    protected fun setVoiceSpeed(voiceSpeed: Float) {
        viewModel.voiceSpeed = voiceSpeed
        if (::tts.isInitialized) {
            tts.voiceSpeed = viewModel.voiceSpeed
        }
        viewModel.text = "Voice speed ${"%.1f".format(viewModel.voiceSpeed)}x"
    }

    private fun adjustMediaVolume(direction: Int) {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            direction,
            AudioManager.FLAG_PLAY_SOUND
        )
        updateMediaVolumeState(updateText = true)
    }

    private fun updateMediaVolumeState(updateText: Boolean) {
        val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val volumeMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val volumePercent = volume / volumeMax.toFloat()
        if (::tts.isInitialized) {
            tts.volumeRelativeToAudioStream = volumePercent
        }
        viewModel.volumePercent = volumePercent
        if (updateText) {
            viewModel.text = "Volume $volume / $volumeMax"
        }
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
        val voice = TextToSpeechVoicePreference.preferredEnglishVoice(voices)
            ?: voices.first()
        Log.i(TAG, "onTextToSpeechInitialized: voice=$voice")
        tts.setVoiceName(voice.name)
        tts.voiceSpeed = viewModel.voiceSpeed
        Log.i(TAG, "onTextToSpeechInitialized: voiceSpeed=${viewModel.voiceSpeed}")

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

@Composable
private fun RopeatoApp(
    viewModel: RopeatoViewModel,
    logTag: String,
    sceneScale: Float,
    controlsScale: Float,
    borderOutset: Boolean,
    platformOverlay: @Composable () -> Unit,
    onPushToTalkPressed: () -> Unit,
    onPushToTalkReleased: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onVoiceSpeedChange: (Float) -> Unit,
) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = RopeatoPrimary,
            background = Color.Black,
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            if (viewModel.state == RopeatoViewModel.State.Initializing) {
                CircularProgressIndicator(modifier = Modifier.fillMaxSize())
            }

            val sceneSize = (if (maxWidth < maxHeight) maxWidth else maxHeight) * sceneScale
            val controlsSize = sceneSize * controlsScale
            val controlScale = (controlsSize / WearReferenceSceneSize).coerceIn(1f, 1.4f)
            val borderScale = (sceneSize / WearReferenceSceneSize).coerceIn(1f, 1.4f)
            Box(
                modifier = Modifier.size(sceneSize),
                contentAlignment = Alignment.Center,
            ) {
                WatchFaceBorder(
                    modifier = Modifier.fillMaxSize(),
                    scale = borderScale,
                    outset = borderOutset,
                )
                Box(
                    modifier = Modifier.align(Alignment.Center),
                    contentAlignment = Alignment.Center,
                ) {
                    VolumeControls(
                        modifier = Modifier.size(controlsSize),
                        volumePercent = viewModel.volumePercent,
                        scale = controlScale,
                        onVolumeChange = onVolumeChange,
                    )
                    VoiceSpeedControls(
                        modifier = Modifier.size(controlsSize),
                        voiceSpeed = viewModel.voiceSpeed,
                        scale = controlScale,
                        onVoiceSpeedChange = onVoiceSpeedChange,
                    )
                    PushToTalkButton(
                        modifier = Modifier.align(Alignment.Center),
                        isListening = viewModel.state == RopeatoViewModel.State.Listening,
                        scale = controlScale,
                        logTag = logTag,
                        onPushToTalkPressed = onPushToTalkPressed,
                        onPushToTalkReleased = onPushToTalkReleased,
                    )
                    Greeting(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .height(72.dp * controlScale)
                            .offset(y = 77.dp * controlScale),
                        text = viewModel.text,
                    )
                }
            }
            platformOverlay()
        }
    }
}

@Composable
private fun WatchFaceBorder(
    scale: Float,
    outset: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = (12.dp * scale).toPx()
        val radius = if (outset) {
            size.minDimension / 2f + strokeWidth / 2f
        } else {
            size.minDimension / 2f - strokeWidth / 2f
        }
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color(0xFF1E1E1E),
                    Color(0xFF5F5F5F),
                    Color(0xFF2B2B2B),
                    Color(0xFF0F0F0F),
                    Color(0xFF6A6A6A),
                    Color(0xFF1E1E1E),
                ),
                center = center,
            ),
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.12f),
            radius = radius - strokeWidth / 2.7f,
            center = center,
            style = Stroke(width = 1.dp.toPx()),
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.45f),
            radius = radius + strokeWidth / 2.8f,
            center = center,
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun VolumeControls(
    volumePercent: Float,
    scale: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    val layoutSize = remember { mutableStateOf(IntSize.Zero) }
    val boundedVolume = volumePercent.coerceIn(0f, 1f)
    val density = LocalDensity.current
    val iconSize = 24.dp * scale
    val strokeWidth = 8.dp * scale
    val radiusInset = 5.dp * scale
    val iconSizePx = with(density) { iconSize.roundToPx() }
    val strokeWidthPx = with(density) { strokeWidth.roundToPx() }
    val radiusInsetPx = with(density) { radiusInset.roundToPx() }

    Box(
        modifier = modifier
            .onSizeChanged { layoutSize.value = it }
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_MOVE -> {
                        if (event.x < layoutSize.value.width * 0.62f) return@pointerInteropFilter false
                        val size = layoutSize.value
                        if (size.width > 0 && size.height > 0) {
                            onVolumeChange(volumePercentFromArcPosition(event.x, event.y, size))
                        }
                        true
                    }
                    else -> true
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidthPxFloat = strokeWidth.toPx()
            val radius = size.width / 2f - strokeWidthPxFloat - radiusInset.toPx()
            val center = Offset(size.width / 2f, size.height / 2f)
            val oval = Rect(
                left = center.x - radius,
                top = center.y - radius,
                right = center.x + radius,
                bottom = center.y + radius,
            )
            drawArc(
                color = track,
                startAngle = VOLUME_ARC_MAX_ANGLE_DEGREES,
                sweepAngle = VOLUME_ARC_SWEEP_DEGREES,
                useCenter = false,
                topLeft = oval.topLeft,
                size = oval.size,
                style = Stroke(width = strokeWidthPxFloat, cap = StrokeCap.Round),
            )
            drawArc(
                color = primary,
                startAngle = VOLUME_ARC_MIN_ANGLE_DEGREES,
                sweepAngle = -VOLUME_ARC_SWEEP_DEGREES * boundedVolume,
                useCenter = false,
                topLeft = oval.topLeft,
                size = oval.size,
                style = Stroke(width = strokeWidthPxFloat, cap = StrokeCap.Round),
            )

            val thumbAngle = Math.toRadians((VOLUME_ARC_MIN_ANGLE_DEGREES - VOLUME_ARC_SWEEP_DEGREES * boundedVolume).toDouble())
            val thumbCenter = Offset(
                x = center.x + cos(thumbAngle).toFloat() * radius,
                y = center.y + sin(thumbAngle).toFloat() * radius,
            )
            drawCircle(
                color = primary.copy(alpha = 0.18f),
                radius = (17.dp * scale).toPx(),
                center = thumbCenter,
            )
            drawCircle(color = primary, radius = (9.dp * scale).toPx(), center = thumbCenter)
        }
        EdgeControlIcon(
            modifier = Modifier.offset {
                volumeIconOffset(
                    size = layoutSize.value,
                    angleDegrees = VOLUME_ICON_MAX_ANGLE_DEGREES,
                    iconSizePx = iconSizePx,
                    strokeWidthPx = strokeWidthPx,
                    radiusInsetPx = radiusInsetPx,
                )
            },
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = "Maximum volume",
            size = iconSize,
        )
        EdgeControlIcon(
            modifier = Modifier.offset {
                volumeIconOffset(
                    size = layoutSize.value,
                    angleDegrees = VOLUME_ICON_MIN_ANGLE_DEGREES,
                    iconSizePx = iconSizePx,
                    strokeWidthPx = strokeWidthPx,
                    radiusInsetPx = radiusInsetPx,
                )
            },
            icon = Icons.AutoMirrored.Filled.VolumeDown,
            contentDescription = "Minimum volume",
            size = iconSize,
        )
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun VoiceSpeedControls(
    voiceSpeed: Float,
    scale: Float,
    onVoiceSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    val layoutSize = remember { mutableStateOf(IntSize.Zero) }
    val boundedVoiceSpeed = voiceSpeed.coerceIn(VOICE_SPEED_MIN, VOICE_SPEED_MAX)
    val voiceSpeedPercent = (boundedVoiceSpeed - VOICE_SPEED_MIN) / (VOICE_SPEED_MAX - VOICE_SPEED_MIN)
    val density = LocalDensity.current
    val iconSize = 24.dp * scale
    val strokeWidth = 8.dp * scale
    val radiusInset = 5.dp * scale
    val iconSizePx = with(density) { iconSize.roundToPx() }
    val strokeWidthPx = with(density) { strokeWidth.roundToPx() }
    val radiusInsetPx = with(density) { radiusInset.roundToPx() }

    Box(
        modifier = modifier
            .onSizeChanged { layoutSize.value = it }
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_MOVE -> {
                        if (event.x > layoutSize.value.width * 0.38f) return@pointerInteropFilter false
                        val size = layoutSize.value
                        if (size.width > 0 && size.height > 0) {
                            onVoiceSpeedChange(voiceSpeedFromArcPosition(event.x, event.y, size))
                        }
                        true
                    }
                    else -> true
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidthPxFloat = strokeWidth.toPx()
            val radius = size.width / 2f - strokeWidthPxFloat - radiusInset.toPx()
            val center = Offset(size.width / 2f, size.height / 2f)
            val oval = Rect(
                left = center.x - radius,
                top = center.y - radius,
                right = center.x + radius,
                bottom = center.y + radius,
            )
            drawArc(
                color = track,
                startAngle = VOICE_SPEED_ARC_MIN_ANGLE_DEGREES,
                sweepAngle = VOICE_SPEED_ARC_SWEEP_DEGREES,
                useCenter = false,
                topLeft = oval.topLeft,
                size = oval.size,
                style = Stroke(width = strokeWidthPxFloat, cap = StrokeCap.Round),
            )
            drawArc(
                color = primary,
                startAngle = VOICE_SPEED_ARC_MIN_ANGLE_DEGREES,
                sweepAngle = VOICE_SPEED_ARC_SWEEP_DEGREES * voiceSpeedPercent,
                useCenter = false,
                topLeft = oval.topLeft,
                size = oval.size,
                style = Stroke(width = strokeWidthPxFloat, cap = StrokeCap.Round),
            )

            val thumbAngle = Math.toRadians((VOICE_SPEED_ARC_MIN_ANGLE_DEGREES + VOICE_SPEED_ARC_SWEEP_DEGREES * voiceSpeedPercent).toDouble())
            val thumbCenter = Offset(
                x = center.x + cos(thumbAngle).toFloat() * radius,
                y = center.y + sin(thumbAngle).toFloat() * radius,
            )
            drawCircle(
                color = primary.copy(alpha = 0.18f),
                radius = (17.dp * scale).toPx(),
                center = thumbCenter,
            )
            drawCircle(color = primary, radius = (9.dp * scale).toPx(), center = thumbCenter)
        }
        EdgeControlIcon(
            modifier = Modifier.offset {
                volumeIconOffset(
                    size = layoutSize.value,
                    angleDegrees = VOICE_SPEED_ICON_MAX_ANGLE_DEGREES,
                    iconSizePx = iconSizePx,
                    strokeWidthPx = strokeWidthPx,
                    radiusInsetPx = radiusInsetPx,
                )
            },
            icon = Icons.Filled.Speed,
            contentDescription = "Maximum voice speed",
            size = iconSize,
        )
        EdgeControlIcon(
            modifier = Modifier.offset {
                volumeIconOffset(
                    size = layoutSize.value,
                    angleDegrees = VOICE_SPEED_ICON_MIN_ANGLE_DEGREES,
                    iconSizePx = iconSizePx,
                    strokeWidthPx = strokeWidthPx,
                    radiusInsetPx = radiusInsetPx,
                )
            },
            icon = ImageVector.vectorResource(id = R.drawable.speed_2_24px),
            contentDescription = "Minimum voice speed",
            size = iconSize,
        )
    }
}

@Composable
private fun EdgeControlIcon(
    icon: ImageVector,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Icon(
        modifier = modifier.size(size),
        imageVector = icon,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.primary,
    )
}

private const val VOLUME_ARC_MAX_ANGLE_DEGREES = -50f
private const val VOLUME_ARC_MIN_ANGLE_DEGREES = 50f
private const val VOLUME_ARC_SWEEP_DEGREES = 100f
private const val VOLUME_ICON_MAX_ANGLE_DEGREES = -62f
private const val VOLUME_ICON_MIN_ANGLE_DEGREES = 62f
private const val VOICE_SPEED_ARC_MIN_ANGLE_DEGREES = 130f
private const val VOICE_SPEED_ARC_SWEEP_DEGREES = 100f
private const val VOICE_SPEED_ICON_MAX_ANGLE_DEGREES = -118f
private const val VOICE_SPEED_ICON_MIN_ANGLE_DEGREES = 118f
private val RopeatoPrimary = Color(0xFFBB86FC)
private val WearReferenceSceneSize = 213.dp

private fun volumePercentFromArcPosition(x: Float, y: Float, size: IntSize): Float {
    return arcPercentFromPosition(
        x = x,
        y = y,
        size = size,
        startAngleDegrees = VOLUME_ARC_MAX_ANGLE_DEGREES,
        sweepDegrees = VOLUME_ARC_SWEEP_DEGREES,
        reverse = true,
    )
}

private fun voiceSpeedFromArcPosition(x: Float, y: Float, size: IntSize): Float {
    val voiceSpeedPercent = arcPercentFromPosition(
        x = x,
        y = y,
        size = size,
        startAngleDegrees = VOICE_SPEED_ARC_MIN_ANGLE_DEGREES,
        sweepDegrees = VOICE_SPEED_ARC_SWEEP_DEGREES,
    )
    return VOICE_SPEED_MIN + (VOICE_SPEED_MAX - VOICE_SPEED_MIN) * voiceSpeedPercent
}

private fun arcPercentFromPosition(
    x: Float,
    y: Float,
    size: IntSize,
    startAngleDegrees: Float,
    sweepDegrees: Float,
    reverse: Boolean = false,
): Float {
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    val rawAngleDegrees = atan2(y - centerY, x - centerX) * 180f / PI.toFloat()
    val endAngleDegrees = startAngleDegrees + sweepDegrees
    val angleDegrees = if (endAngleDegrees > 180f && rawAngleDegrees < startAngleDegrees) {
        rawAngleDegrees + 360f
    } else {
        rawAngleDegrees
    }
    val percent = ((angleDegrees - startAngleDegrees) / sweepDegrees).coerceIn(0f, 1f)
    return if (reverse) 1f - percent else percent
}

private fun volumeIconOffset(
    size: IntSize,
    angleDegrees: Float,
    iconSizePx: Int,
    strokeWidthPx: Int,
    radiusInsetPx: Int,
): IntOffset {
    val radius = size.width / 2f - strokeWidthPx - radiusInsetPx
    val angle = Math.toRadians(angleDegrees.toDouble())
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    return IntOffset(
        x = (centerX + cos(angle).toFloat() * radius - iconSizePx / 2f).roundToInt(),
        y = (centerY + sin(angle).toFloat() * radius - iconSizePx / 2f).roundToInt(),
    )
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun PushToTalkButton(
    isListening: Boolean,
    scale: Float,
    logTag: String,
    onPushToTalkPressed: () -> Unit,
    onPushToTalkReleased: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(73.dp * scale)
            .border(
                width = 3.dp * scale,
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
            )
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        FooLog.i(logTag, "PushToTalkButton ACTION_DOWN")
                        onPushToTalkPressed()
                        true
                    }
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        FooLog.i(logTag, "PushToTalkButton ${MotionEvent.actionToString(event.actionMasked)}")
                        onPushToTalkReleased()
                        true
                    }
                    else -> true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(42.dp * scale),
            imageVector = Icons.Filled.Mic,
            contentDescription = if (isListening) "Listening" else "Hold to talk",
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun Greeting(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary,
            text = text,
        )
    }
}
