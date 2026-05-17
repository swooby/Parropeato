package com.swooby.parropeato

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.smartfoo.android.core.FooReflection
import com.smartfoo.android.core.FooString
import com.smartfoo.android.core.FooString.quote
import com.smartfoo.android.core.logging.FooLog
import com.smartfoo.android.core.platform.FooPlatformUtils
import com.smartfoo.android.core.texttospeech.FooTextToSpeech
import com.swooby.parropeato.common.BuildConfig
import com.swooby.parropeato.common.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import android.provider.Settings as AndroidSettings

abstract class BaseMainActivity : ComponentActivity() {
    companion object {
        private val speechRecognizerErrors = FooReflection.mapConstants(SpeechRecognizer::class, "ERROR_")
        fun speechRecognizerErrorToString(error: Int): String = FooReflection.toString(speechRecognizerErrors, error)
    }

    protected open val TAG: String by lazy { FooLog.TAG(this::class) }

    protected lateinit var tts: FooTextToSpeech
    protected lateinit var speechRecognizer: SpeechRecognizer
    protected lateinit var audioManager: AudioManager
    protected lateinit var settings: Settings
    protected val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var connectivityManager: ConnectivityManager
    private val ttsCallbacks = object : FooTextToSpeech.FooTextToSpeechCallbacks {
        override fun onTextToSpeechInitialized(status: Int) {
            this@BaseMainActivity.onTextToSpeechInitialized(status)
        }
    }
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { viewModel.isNetworkAvailable = true }
        override fun onLost(network: Network) { viewModel.isNetworkAvailable = false }
    }

    protected val viewModel by viewModels<ParropeatoViewModel>()

    protected open val textToSpeechVoiceSpeed: Float = VOICE_SPEED_DEFAULT
    protected open val watchFaceSceneScale: Float = 0.92f
    protected open val watchFaceControlsScale: Float = 0.88f
    protected open val watchFaceBorderOutset: Boolean = false
    protected open val greetingBottomInsetDp: Float = 24f
    protected open val greetingScrollIndicator: (@Composable (ScrollState) -> Unit)? = null

    protected open fun setupUI() {
        setContent {
            ParropeatoApp(
                viewModel = viewModel,
                logTag = TAG,
                sceneScale = watchFaceSceneScale,
                controlsScale = watchFaceControlsScale,
                borderOutset = watchFaceBorderOutset,
                platformOverlay = { onSettingsClick -> PlatformOverlay(onSettingsClick) },
                onPushToTalkPressed = ::onPushToTalkPressed,
                onPushToTalkReleased = ::onPushToTalkReleased,
                onVolumeChange = ::setVolumePercent,
                onVoiceSpeedChange = ::setVoiceSpeed,
                onVoicePitchChange = ::setVoicePitch,
                greetingBottomInsetDp = greetingBottomInsetDp,
                greetingScrollIndicator = greetingScrollIndicator,
                settingsOverlay = {
                    if (viewModel.showSettings) {
                        SettingsOverlay(onDismiss = { viewModel.showSettings = false })
                    }
                },
            )
        }
    }

    @Composable
    protected open fun PlatformOverlay(onSettingsClick: () -> Unit) {
    }

    @Composable
    protected open fun SettingsOverlay(onDismiss: () -> Unit) {
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG, "+onCreate(...)")
        super.onCreate(savedInstanceState)
        audioManager = getSystemService(AudioManager::class.java)
        settings = Settings(this, defaultTtsVoiceSpeed = textToSpeechVoiceSpeed)
        updateMediaVolumeState(updateText = false)
        connectivityManager = getSystemService(ConnectivityManager::class.java)
        viewModel.isNetworkAvailable = connectivityManager
            .getNetworkCapabilities(connectivityManager.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        viewModel.voiceSpeed = settings.ttsVoiceSpeed
        viewModel.voicePitch = settings.ttsPitch
        viewModel.speechRecognizerLocale = settings.speechRecognizerLocale
        viewModel.cuteIcons = settings.cuteIcons
        viewModel.accentColor = settings.accentColor
        setupUI()
        initTextToSpeech()
        initSpeechRecognizer()
        initSupportedSpeechLocales()
        Log.i(TAG, "-onCreate(...)")
    }

    private fun initTextToSpeech() {
        tts = FooTextToSpeech.instance
        tts.dedupe = false
        tts.setAudioAttributes(AudioAttributes.USAGE_MEDIA)
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
                    SpeechRecognizer.ERROR_CLIENT,
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> true
                    else -> false
                }
                if (shouldRetry) {
                    viewModel.state = ParropeatoViewModel.State.Listening
                    setPersistentText(getString(R.string.status_listening))
                    resetSpeechRecognizer()
                    mainHandler.postDelayed({
                        if (isPushToTalkPressed && !isListening) {
                            speechRecognizerStart()
                        }
                    }, 150)
                    return
                }
                resetSpeechRecognizer()
                viewModel.state = ParropeatoViewModel.State.Idle
                setPersistentText(when (error) {
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT       -> getString(R.string.error_stt_network_timeout)
                    SpeechRecognizer.ERROR_NETWORK               -> getString(R.string.error_stt_network)
                    SpeechRecognizer.ERROR_AUDIO                 -> getString(R.string.error_stt_audio)
                    SpeechRecognizer.ERROR_SERVER                -> getString(R.string.error_stt_server)
                    SpeechRecognizer.ERROR_CLIENT                -> getString(R.string.error_stt_client)
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT        -> getString(R.string.error_stt_speech_timeout)
                    SpeechRecognizer.ERROR_NO_MATCH              -> getString(R.string.error_stt_no_match)
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY       -> getString(R.string.error_stt_recognizer_busy)
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> getString(R.string.error_stt_insufficient_permissions)
                    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS     -> getString(R.string.error_stt_too_many_requests)
                    SpeechRecognizer.ERROR_SERVER_DISCONNECTED   -> getString(R.string.error_stt_server_disconnected)
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> getString(R.string.error_stt_language_not_supported)
                    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE  -> getString(R.string.error_stt_language_unavailable)
                    SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT  -> getString(R.string.error_stt_cannot_check_support)
                    SpeechRecognizer.ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS  -> getString(R.string.error_stt_cannot_listen_to_download_events)
                    else -> getString(R.string.error_stt_generic, speechRecognizerErrorToString(error))
                })
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
                    viewModel.state = ParropeatoViewModel.State.Idle
                    setPersistentText(getString(R.string.error_stt_no_match))
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
                viewModel.state = ParropeatoViewModel.State.Speaking
                speakRecognition(highestConfidenceResult)
                resetSpeechRecognizer()
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                FooLog.d(TAG, "onEvent(eventType=$eventType, params=${FooPlatformUtils.toString(params)})")
            }
        })

        if (updatePrompt) {
            viewModel.state = ParropeatoViewModel.State.Idle
            setPersistentText(getString(if (viewModel.cuteIcons) R.string.status_hold_cute_mic_to_talk else R.string.status_hold_mic_to_talk))
        }
    }

    private fun initSupportedSpeechLocales() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        speechRecognizer.checkRecognitionSupport(
            intent,
            mainExecutor,
            object : RecognitionSupportCallback {
                override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                    viewModel.installedSpeechLocales = recognitionSupport.installedOnDeviceLanguages.toSet()
                    val supportedTags: Set<String> = mutableSetOf<String>().apply {
                        addAll(recognitionSupport.installedOnDeviceLanguages)
                        addAll(recognitionSupport.supportedOnDeviceLanguages)
                        addAll(recognitionSupport.onlineLanguages)
                    }
                    val filtered = SpeechLocalePreference.CANDIDATES
                        .filter { it in supportedTags }
                        .sortedBy { java.util.Locale.forLanguageTag(it).getDisplayName(java.util.Locale.getDefault()) }
                    Log.i(TAG, "initSupportedSpeechLocales: ${filtered.size} locales supported")
                    viewModel.supportedSpeechLocales = filtered
                    viewModel.speechLocalesSupportChecked = true
                    val savedLocale = viewModel.speechRecognizerLocale
                    if (savedLocale != null && savedLocale !in filtered) {
                        Log.w(TAG, "initSupportedSpeechLocales: saved locale $savedLocale no longer supported, clearing")
                        viewModel.speechRecognizerLocale = null
                        settings.speechRecognizerLocale = null
                    }
                }

                override fun onError(errorCode: Int) {
                    Log.w(TAG, "initSupportedSpeechLocales: checkRecognitionSupport error=$errorCode, using full candidate list")
                    viewModel.supportedSpeechLocales = SpeechLocalePreference.CANDIDATES
                        .sortedBy { java.util.Locale.forLanguageTag(it).getDisplayName(java.util.Locale.getDefault()) }
                    viewModel.speechLocalesSupportChecked = true
                }
            }
        )
    }

    override fun onStart() {
        super.onStart()
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        tts.start(this, ttsCallbacks)
    }

    override fun onResume() {
        super.onResume()
        // Re-attach silently after a brief onPause/onResume (e.g. notification shade)
        // where onStop was never called. tts.start() is intentionally NOT called here
        // to avoid re-firing onTextToSpeechInitialized (and replaying the greeting).
        tts.attach(ttsCallbacks)
    }

    override fun onPause() {
        super.onPause()
        viewModel.state = ParropeatoViewModel.State.ShuttingDown
        tts.detach(ttsCallbacks)
        speechRecognizerStop()
    }

    override fun onStop() {
        super.onStop()
        tts.stop()
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.state = ParropeatoViewModel.State.Shutdown
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
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.permission_mic_rationale_title))
            .setMessage(getString(R.string.permission_mic_rationale_message))
            .setPositiveButton(android.R.string.ok) { _, _ -> permissionRecordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        FooLog.i(TAG, "-permissionRecordAudioRationale()")
    }

    private fun onPermissionRecordAudioResult(isGranted: Boolean) {
        FooLog.i(TAG, "+onPermissionRecordAudioResult(isGranted=$isGranted)")
        if (isGranted && pendingStartAfterPermission) {
            pendingStartAfterPermission = false
            speechRecognizerStart()
        } else if (!isGranted) {
            pendingStartAfterPermission = false
            viewModel.state = ParropeatoViewModel.State.Idle
            setPersistentText(getString(R.string.error_mic_insufficient_permission))
        } else {
            viewModel.state = ParropeatoViewModel.State.Idle
            setPersistentText(getString(if (viewModel.cuteIcons) R.string.status_hold_cute_mic_to_talk else R.string.status_hold_mic_to_talk))
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
                viewModel.state = ParropeatoViewModel.State.Idle
                setPersistentText(getString(R.string.status_thinking))
                mainHandler.postDelayed({
                    if (isListening && shouldProcessResults) {
                        isListening = false
                        shouldProcessResults = false
                        viewModel.state = ParropeatoViewModel.State.Speaking
                        speakRecognition(fallbackRecognition)
                        resetSpeechRecognizer()
                    }
                }, 500)
            } else {
                viewModel.state = ParropeatoViewModel.State.Idle
                setPersistentText(getString(R.string.status_thinking))
            }
        } else if (!fallbackRecognition.isNullOrBlank()) {
            viewModel.state = ParropeatoViewModel.State.Speaking
            speakRecognition(fallbackRecognition)
            resetSpeechRecognizer()
        } else {
            viewModel.state = ParropeatoViewModel.State.Idle
            setPersistentText(getString(R.string.error_stt_no_match))
        }
        FooLog.i(TAG, "-onPushToTalkReleased()")
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
        if (::settings.isInitialized) {
            settings.ttsVoiceSpeed = viewModel.voiceSpeed
        }
        showTransientText(getString(R.string.status_voice_speed, viewModel.voiceSpeed))
    }

    protected fun setVoicePitch(voicePitch: Float) {
        viewModel.voicePitch = voicePitch
        if (::tts.isInitialized) {
            tts.voicePitch = viewModel.voicePitch
        }
        if (::settings.isInitialized) {
            settings.ttsPitch = viewModel.voicePitch
        }
        showTransientText(getString(R.string.status_voice_pitch, viewModel.voicePitch))
    }

    protected fun onSettingsVoiceSelected(voiceName: String?) {
        tts.setVoiceName(voiceName)  // null → engine default
        viewModel.selectedVoiceName = voiceName
        settings.ttsVoiceName = voiceName
    }

    protected fun onSettingsVoicePreview(voiceName: String) {
        if (!tts.isStarted) return
        val restoreName = viewModel.selectedVoiceName
        tts.clear()
        tts.setVoiceName(voiceName)
        tts.speak(
            getString(R.string.tts_voice_preview),
            callbacks = object : FooTextToSpeech.SequenceCallbacks {
                override fun onSequenceStart(sequenceId: String) {}
                override fun onSequenceComplete(sequenceId: String, neverStarted: Boolean, errorCode: Int) {
                    tts.setVoiceName(restoreName)
                }
            },
        )
    }

    protected fun onSettingsSpeechLocaleSelected(locale: String?) {
        viewModel.speechRecognizerLocale = locale
        settings.speechRecognizerLocale = locale
    }

    protected fun onSettingsCuteIconsChanged(value: Boolean) {
        viewModel.cuteIcons = value
        settings.cuteIcons = value
        val holdMic = getString(R.string.status_hold_mic_to_talk)
        val holdCuteMic = getString(R.string.status_hold_cute_mic_to_talk)
        if (lastPersistentText == holdMic || lastPersistentText == holdCuteMic) {
            setPersistentText(getString(if (value) R.string.status_hold_cute_mic_to_talk else R.string.status_hold_mic_to_talk))
        }
    }

    protected fun onSettingsAccentColorChanged(argb: Int) {
        viewModel.accentColor = argb
        settings.accentColor = argb
    }

    protected fun openTtsSettings() {
        try {
            startActivity(Intent("com.android.settings.TTS_SETTINGS"))
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(AndroidSettings.ACTION_SETTINGS))
        }
    }

    fun openSpeechDownloadSettings() {
        try {
            startActivity(Intent(AndroidSettings.ACTION_VOICE_INPUT_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(AndroidSettings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            } catch (_: ActivityNotFoundException) {
                // no suitable settings screen available
            }
        }
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
            showTransientText(getString(R.string.status_volume, volume, volumeMax))
        }
    }

    protected fun speechRecognizerStop() {
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.stopListening()
        }
    }

    @Suppress("unused")
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
        viewModel.state = ParropeatoViewModel.State.Listening
        setPersistentText(getString(R.string.status_listening))
        val recognizerIntent = Intent()
        recognizerIntent.action = RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        viewModel.speechRecognizerLocale?.let { locale ->
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
        }
        recognizerIntent.putExtra(
            RecognizerIntent.EXTRA_ENABLE_FORMATTING,
            RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY
        )
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MASK_OFFENSIVE_WORDS, false)
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        val selectedLocale = viewModel.speechRecognizerLocale
        val isOnline = viewModel.isNetworkAvailable
        val hasOfflineModel = selectedLocale == null || selectedLocale in viewModel.installedSpeechLocales
        if (!isOnline && !hasOfflineModel) {
            isListening = false
            shouldProcessResults = false
            viewModel.state = ParropeatoViewModel.State.Idle
            setPersistentText(getString(R.string.error_stt_offline_no_model))
            return
        }
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, !isOnline)
        speechRecognizer.startListening(recognizerIntent)
        FooLog.i(TAG, "-speechRecognizerStart()")
    }

    private fun onTextToSpeechInitialized(status: Int) {
        Log.i(TAG, "onTextToSpeechInitialized(status=${FooTextToSpeech.statusToString(status)})")

        if (status != TextToSpeech.SUCCESS) {
            viewModel.state = ParropeatoViewModel.State.InitializingError
            var text = getString(R.string.error_tts_init_failed)
            if (BuildConfig.DEBUG) {
                text += "\n" + getString(R.string.error_tts_emulator_hint)
            }
            text += "\nstatus=${FooTextToSpeech.statusToString(status)}"
            setPersistentText(text)
            return
        }

        viewModel.state = ParropeatoViewModel.State.Initialized
        setPersistentText(getString(if (viewModel.cuteIcons) R.string.status_hold_cute_mic_to_talk else R.string.status_hold_mic_to_talk))

        val voices = TextToSpeechVoicePreference.installedVoices(tts.voices)
        Log.i(TAG, "onTextToSpeechInitialized: voices=${FooString.toString(voices, true)}")
        viewModel.availableVoices = voices.toList()

        // Probe the engine's default voice name (setVoiceName(null) resolves to defaultVoice).
        tts.setVoiceName(null)
        viewModel.ttsDefaultVoiceName = tts.voiceName
        Log.i(TAG, "onTextToSpeechInitialized: ttsDefaultVoiceName=${viewModel.ttsDefaultVoiceName}")

        // One-time migration: older builds auto-selected a voice via preferredEnglishVoice() and
        // persisted it without any user interaction. Reset to Device Default so the user starts
        // fresh with the correct engine default rather than a stale auto-chosen voice.
        if (settings.settingsVersion < Settings.CURRENT_VERSION) {
            Log.i(TAG, "onTextToSpeechInitialized: migrating settings " +
                    "v${settings.settingsVersion} → ${Settings.CURRENT_VERSION}, clearing ttsVoiceName")
            settings.ttsVoiceName = null
            settings.settingsVersion = Settings.CURRENT_VERSION
        }

        val savedVoiceName = settings.ttsVoiceName
        if (savedVoiceName != null && voices.any { it.name == savedVoiceName }) {
            tts.setVoiceName(savedVoiceName)
        } else if (savedVoiceName != null) {
            // Previously saved voice is no longer installed — reset to device default.
            settings.ttsVoiceName = null
        }
        // null savedVoiceName → already on engine default from the probe above.
        viewModel.selectedVoiceName = settings.ttsVoiceName
        Log.i(TAG, "onTextToSpeechInitialized: selectedVoiceName=${viewModel.selectedVoiceName}")
        tts.voiceSpeed = viewModel.voiceSpeed
        Log.i(TAG, "onTextToSpeechInitialized: voiceSpeed=${viewModel.voiceSpeed}")
        tts.voicePitch = viewModel.voicePitch
        Log.i(TAG, "onTextToSpeechInitialized: voicePitch=${viewModel.voicePitch}")

        tts.speak(getString(R.string.tts_greeting))
    }

    private var lastPersistentText: String = ""
    private var transientTextJob: Job? = null

    private fun setPersistentText(text: String) {
        transientTextJob?.cancel()
        transientTextJob = null
        lastPersistentText = text
        viewModel.text = text
    }

    private fun showTransientText(text: String, durationMs: Long = 5_000L) {
        viewModel.text = text
        transientTextJob?.cancel()
        transientTextJob = lifecycleScope.launch {
            delay(durationMs)
            viewModel.text = lastPersistentText
            transientTextJob = null
        }
    }

    private fun speakRecognition(text: String) {
        val spokenText = text.trim()
        setPersistentText(spokenText)
        tts.speak(spokenText)
    }

    private fun bestPartialRecognition(): String? =
        latestUnstableRecognition?.takeIf { it.isNotBlank() }
            ?: latestPartialRecognition?.takeIf { it.isNotBlank() }
}

@Suppress("SameParameterValue")
@Composable
private fun ParropeatoApp(
    viewModel: ParropeatoViewModel,
    logTag: String,
    sceneScale: Float,
    controlsScale: Float,
    borderOutset: Boolean,
    platformOverlay: @Composable (onSettingsClick: () -> Unit) -> Unit,
    onPushToTalkPressed: () -> Unit,
    onPushToTalkReleased: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onVoiceSpeedChange: (Float) -> Unit,
    onVoicePitchChange: (Float) -> Unit,
    greetingBottomInsetDp: Float,
    greetingScrollIndicator: (@Composable (ScrollState) -> Unit)?,
    settingsOverlay: @Composable () -> Unit,
) {
    val greetingScrollState = rememberScrollState()
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(viewModel.accentColor),
            background = Color.Black,
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            if (viewModel.state == ParropeatoViewModel.State.Initializing) {
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
                    AllArcControls(
                        modifier = Modifier.size(controlsSize),
                        volumePercent = viewModel.volumePercent,
                        voiceSpeed = viewModel.voiceSpeed,
                        voicePitch = viewModel.voicePitch,
                        scale = controlScale,
                        cuteIcons = viewModel.cuteIcons,
                        onVolumeChange = onVolumeChange,
                        onVoiceSpeedChange = onVoiceSpeedChange,
                        onVoicePitchChange = onVoicePitchChange,
                    )
                    PushToTalkButton(
                        modifier = Modifier.align(Alignment.Center),
                        isListening = viewModel.state == ParropeatoViewModel.State.Listening,
                        scale = controlScale,
                        cuteIcons = viewModel.cuteIcons,
                        logTag = logTag,
                        onPushToTalkPressed = onPushToTalkPressed,
                        onPushToTalkReleased = onPushToTalkReleased,
                    )
                    val sceneRadius = sceneSize / 2
                    val greetingTopY = 40.5.dp * controlScale
                    val greetingBottomY = (sceneRadius - greetingBottomInsetDp.dp)
                        .coerceAtLeast(greetingTopY + 20.dp)
                    val greetingHeight = greetingBottomY - greetingTopY
                    val greetingCenterY = (greetingTopY + greetingBottomY) / 2
                    val greetingWidthFraction = with(LocalDensity.current) {
                        val rPx = sceneRadius.toPx()
                        val yPx = greetingCenterY.toPx()
                        (sqrt(maxOf(0f, rPx * rPx - yPx * yPx)) / rPx * 0.92f).coerceIn(0.3f, 1f)
                    }
                    Greeting(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(greetingWidthFraction)
                            .height(greetingHeight)
                            .offset(y = greetingCenterY),
                        text = viewModel.text,
                        scrollState = greetingScrollState,
                        showScrollbar = greetingScrollIndicator == null,
                    )
                }
                platformOverlay { viewModel.showSettings = true }
            }
        }

        settingsOverlay()
        greetingScrollIndicator?.invoke(greetingScrollState)
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

// Returns how far normAngle is clockwise from startAngleDegrees, in [0°, 360°).
private fun arcAngularOffset(normAngle: Float, startAngleDegrees: Float): Float {
    val normStart = ((startAngleDegrees % 360f) + 360f) % 360f
    return ((normAngle - normStart + 360f) % 360f)
}

// Identifies which arc curve the touch point lands on.
// Requires BOTH: radial proximity to the arc's drawn radius AND angular position within the sweep.
// Volume and Pitch sit at outerR; Speed sits at innerR (inset by speedExtraInset).
// All three composables call this so the radius-proximity tiebreaker works for the tiny
// overlap zones near the arc endpoints.
private enum class ActiveArc { NONE, VOLUME, SPEED, PITCH }

private fun identifyTouchedArc(
    x: Float, y: Float, size: IntSize,
    strokeWidthF: Float, radiusInsetF: Float, speedExtraInsetF: Float,
): ActiveArc {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val dx = x - cx
    val dy = y - cy
    val r = sqrt(dx * dx + dy * dy)
    val rawAngle = atan2(dy, dx) * 180f / PI.toFloat()
    val normAngle = ((rawAngle % 360f) + 360f) % 360f
    val outerR = size.width / 2f - strokeWidthF - radiusInsetF
    val innerR = outerR - speedExtraInsetF
    val hitBand = strokeWidthF * 2f
    val nearOuter = abs(r - outerR) <= hitBand
    val nearInner = abs(r - innerR) <= hitBand
    val inVolumeAngle = arcAngularOffset(normAngle, VOLUME_ARC_START_ANGLE_DEGREES) <= VOLUME_ARC_SWEEP_DEGREES
    val inSpeedAngle = arcAngularOffset(normAngle, VOICE_SPEED_ARC_START_ANGLE_DEGREES) <= VOICE_SPEED_ARC_SWEEP_DEGREES
    val inPitchAngle = arcAngularOffset(normAngle, VOICE_PITCH_ARC_START_ANGLE_DEGREES) <= VOICE_PITCH_ARC_SWEEP_DEGREES
    val hitVolume = nearOuter && inVolumeAngle
    val hitSpeed = nearInner && inSpeedAngle
    val hitPitch = nearOuter && inPitchAngle
    return when {
        !hitVolume && !hitSpeed && !hitPitch -> ActiveArc.NONE
        hitVolume && hitSpeed -> if (abs(r - outerR) <= abs(r - innerR)) ActiveArc.VOLUME else ActiveArc.SPEED
        hitSpeed && hitPitch -> if (abs(r - innerR) <= abs(r - outerR)) ActiveArc.SPEED else ActiveArc.PITCH
        hitVolume -> ActiveArc.VOLUME
        hitSpeed -> ActiveArc.SPEED
        else -> ActiveArc.PITCH
    }
}

// Returns true if (x, y) is within iconSizeF pixels of the icon centre at (angleDeg, radius).
private fun hitsIcon(
    x: Float, y: Float, size: IntSize,
    angleDeg: Float, radius: Float, iconSizeF: Float,
): Boolean {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val rad = Math.toRadians(angleDeg.toDouble())
    val icx = cx + cos(rad).toFloat() * radius
    val icy = cy + sin(rad).toFloat() * radius
    val dx = x - icx
    val dy = y - icy
    return sqrt(dx * dx + dy * dy) <= iconSizeF
}

@Composable
private fun AllArcControls(
    volumePercent: Float,
    voiceSpeed: Float,
    voicePitch: Float,
    scale: Float,
    cuteIcons: Boolean,
    onVolumeChange: (Float) -> Unit,
    onVoiceSpeedChange: (Float) -> Unit,
    onVoicePitchChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        VolumeArcControl(
            modifier = Modifier.fillMaxSize(),
            volumePercent = volumePercent,
            scale = scale,
            cuteIcons = cuteIcons,
            onVolumeChange = onVolumeChange,
        )
        VoiceSpeedArcControl(
            modifier = Modifier.fillMaxSize(),
            voiceSpeed = voiceSpeed,
            scale = scale,
            cuteIcons = cuteIcons,
            onVoiceSpeedChange = onVoiceSpeedChange,
        )
        VoicePitchArcControl(
            modifier = Modifier.fillMaxSize(),
            voicePitch = voicePitch,
            scale = scale,
            cuteIcons = cuteIcons,
            onVoicePitchChange = onVoicePitchChange,
        )
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun VolumeArcControl(
    volumePercent: Float,
    scale: Float,
    cuteIcons: Boolean,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val track = primary.copy(alpha = 0.22f)
    val density = LocalDensity.current
    val view = LocalView.current
    val layoutSize = remember { mutableStateOf(IntSize.Zero) }
    val isTracking = remember { mutableStateOf(false) }

    val boundedVolume = volumePercent.coerceIn(0f, 1f)
    val iconSize = 24.dp * scale
    val strokeWidth = 8.dp * scale
    val radiusInset = 5.dp * scale
    val speedExtraInset = VOICE_SPEED_ARC_EXTRA_INSET_DP.dp * scale  // needed for arc disambiguation

    val iconSizeF = with(density) { iconSize.toPx() }
    val iconSizePx = with(density) { iconSize.roundToPx() }
    val strokeWidthF = with(density) { strokeWidth.toPx() }
    val strokeWidthPx = with(density) { strokeWidth.roundToPx() }
    val radiusInsetF = with(density) { radiusInset.toPx() }
    val radiusInsetPx = with(density) { radiusInset.roundToPx() }
    val speedExtraInsetF = with(density) { speedExtraInset.toPx() }

    val maxIconRes = if (cuteIcons) R.drawable.volume_max_elephant_24px else R.drawable.volume_up
    val minIconRes = if (cuteIcons) R.drawable.volume_min_ladybug_24px else R.drawable.volume_down

    Box(
        modifier = modifier
            .onSizeChanged { layoutSize.value = it }
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        val size = layoutSize.value
                        if (size.width <= 0 || size.height <= 0) return@pointerInteropFilter false
                        val outerR = size.width / 2f - strokeWidthF - radiusInsetF
                        if (hitsIcon(event.x, event.y, size, VOLUME_ICON_MAX_ANGLE_DEGREES, outerR, iconSizeF)) {
                            isTracking.value = true
                            view.parent.requestDisallowInterceptTouchEvent(true)
                            onVolumeChange(1f)
                            return@pointerInteropFilter true
                        }
                        if (hitsIcon(event.x, event.y, size, VOLUME_ICON_MIN_ANGLE_DEGREES, outerR, iconSizeF)) {
                            isTracking.value = true
                            view.parent.requestDisallowInterceptTouchEvent(true)
                            onVolumeChange(0f)
                            return@pointerInteropFilter true
                        }
                        if (identifyTouchedArc(event.x, event.y, size, strokeWidthF, radiusInsetF, speedExtraInsetF) != ActiveArc.VOLUME) {
                            return@pointerInteropFilter false
                        }
                        isTracking.value = true
                        view.parent.requestDisallowInterceptTouchEvent(true)
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onVolumeChange(arcPercentFromPosition(event.x, event.y, size, VOLUME_ARC_START_ANGLE_DEGREES, VOLUME_ARC_SWEEP_DEGREES, reverse = true))
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!isTracking.value) return@pointerInteropFilter false
                        onVolumeChange(arcPercentFromPosition(event.x, event.y, layoutSize.value, VOLUME_ARC_START_ANGLE_DEGREES, VOLUME_ARC_SWEEP_DEGREES, reverse = true))
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val wasTracking = isTracking.value
                        if (wasTracking) {
                            isTracking.value = false
                            view.parent.requestDisallowInterceptTouchEvent(false)
                        }
                        wasTracking
                    }
                    else -> false
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val swPx = strokeWidth.toPx()
            val outerR = size.width / 2f - swPx - radiusInset.toPx()
            val c = Offset(size.width / 2f, size.height / 2f)
            val ovalTl = Offset(c.x - outerR, c.y - outerR)
            val ovalSz = androidx.compose.ui.geometry.Size(outerR * 2, outerR * 2)
            drawArc(color = track, startAngle = VOLUME_ARC_START_ANGLE_DEGREES, sweepAngle = VOLUME_ARC_SWEEP_DEGREES, useCenter = false, topLeft = ovalTl, size = ovalSz, style = Stroke(width = swPx, cap = StrokeCap.Round))
            val thumbDeg = VOLUME_ARC_START_ANGLE_DEGREES + VOLUME_ARC_SWEEP_DEGREES * (1f - boundedVolume)
            val thumbRad = Math.toRadians(thumbDeg.toDouble())
            val thumb = Offset(c.x + cos(thumbRad).toFloat() * outerR, c.y + sin(thumbRad).toFloat() * outerR)
            drawCircle(color = primary.copy(alpha = 0.18f), radius = (17.dp * scale).toPx(), center = thumb)
            drawCircle(color = primary, radius = (9.dp * scale).toPx(), center = thumb)
        }
        EdgeControlIcon(modifier = Modifier.offset { arcIconOffset(layoutSize.value, VOLUME_ICON_MAX_ANGLE_DEGREES, iconSizePx, strokeWidthPx, radiusInsetPx) }, icon = ImageVector.vectorResource(maxIconRes), contentDescription = stringResource(R.string.cd_volume_max), size = iconSize)
        EdgeControlIcon(modifier = Modifier.offset { arcIconOffset(layoutSize.value, VOLUME_ICON_MIN_ANGLE_DEGREES, iconSizePx, strokeWidthPx, radiusInsetPx) }, icon = ImageVector.vectorResource(minIconRes), contentDescription = stringResource(R.string.cd_volume_min), size = iconSize)
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun VoiceSpeedArcControl(
    voiceSpeed: Float,
    scale: Float,
    cuteIcons: Boolean,
    onVoiceSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val track = primary.copy(alpha = 0.22f)
    val density = LocalDensity.current
    val view = LocalView.current
    val layoutSize = remember { mutableStateOf(IntSize.Zero) }
    val isTracking = remember { mutableStateOf(false) }

    val speedPercent = (voiceSpeed.coerceIn(VOICE_SPEED_MIN, VOICE_SPEED_MAX) - VOICE_SPEED_MIN) / (VOICE_SPEED_MAX - VOICE_SPEED_MIN)
    val iconSize = 24.dp * scale
    val strokeWidth = 8.dp * scale
    val radiusInset = 5.dp * scale
    val speedExtraInset = VOICE_SPEED_ARC_EXTRA_INSET_DP.dp * scale

    val iconSizeF = with(density) { iconSize.toPx() }
    val iconSizePx = with(density) { iconSize.roundToPx() }
    val strokeWidthF = with(density) { strokeWidth.toPx() }
    val strokeWidthPx = with(density) { strokeWidth.roundToPx() }
    val radiusInsetF = with(density) { radiusInset.toPx() }
    val radiusInsetPx = with(density) { radiusInset.roundToPx() }
    val speedExtraInsetF = with(density) { speedExtraInset.toPx() }
    val speedExtraInsetPx = with(density) { speedExtraInset.roundToPx() }

    val maxIconRes = if (cuteIcons) R.drawable.speed_max_rabbit_24px else R.drawable.speed_24px
    val minIconRes = if (cuteIcons) R.drawable.speed_min_turtle_24px else R.drawable.speed_2_24px

    Box(
        modifier = modifier
            .onSizeChanged { layoutSize.value = it }
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        val size = layoutSize.value
                        if (size.width <= 0 || size.height <= 0) return@pointerInteropFilter false
                        val innerR = size.width / 2f - strokeWidthF - radiusInsetF - speedExtraInsetF
                        if (hitsIcon(event.x, event.y, size, VOICE_SPEED_ICON_MAX_ANGLE_DEGREES, innerR, iconSizeF)) {
                            isTracking.value = true
                            view.parent.requestDisallowInterceptTouchEvent(true)
                            onVoiceSpeedChange(VOICE_SPEED_MAX)
                            return@pointerInteropFilter true
                        }
                        if (hitsIcon(event.x, event.y, size, VOICE_SPEED_ICON_MIN_ANGLE_DEGREES, innerR, iconSizeF)) {
                            isTracking.value = true
                            view.parent.requestDisallowInterceptTouchEvent(true)
                            onVoiceSpeedChange(VOICE_SPEED_MIN)
                            return@pointerInteropFilter true
                        }
                        if (identifyTouchedArc(event.x, event.y, size, strokeWidthF, radiusInsetF, speedExtraInsetF) != ActiveArc.SPEED) {
                            return@pointerInteropFilter false
                        }
                        isTracking.value = true
                        view.parent.requestDisallowInterceptTouchEvent(true)
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        val pct = arcPercentFromPosition(event.x, event.y, size, VOICE_SPEED_ARC_START_ANGLE_DEGREES, VOICE_SPEED_ARC_SWEEP_DEGREES)
                        onVoiceSpeedChange(VOICE_SPEED_MIN + (VOICE_SPEED_MAX - VOICE_SPEED_MIN) * pct)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!isTracking.value) return@pointerInteropFilter false
                        val pct = arcPercentFromPosition(event.x, event.y, layoutSize.value, VOICE_SPEED_ARC_START_ANGLE_DEGREES, VOICE_SPEED_ARC_SWEEP_DEGREES)
                        onVoiceSpeedChange(VOICE_SPEED_MIN + (VOICE_SPEED_MAX - VOICE_SPEED_MIN) * pct)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val wasTracking = isTracking.value
                        if (wasTracking) {
                            isTracking.value = false
                            view.parent.requestDisallowInterceptTouchEvent(false)
                        }
                        wasTracking
                    }
                    else -> false
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val swPx = strokeWidth.toPx()
            val outerR = size.width / 2f - swPx - radiusInset.toPx()
            val innerR = outerR - speedExtraInset.toPx()
            val c = Offset(size.width / 2f, size.height / 2f)
            val ovalTl = Offset(c.x - innerR, c.y - innerR)
            val ovalSz = androidx.compose.ui.geometry.Size(innerR * 2, innerR * 2)
            drawArc(color = track, startAngle = VOICE_SPEED_ARC_START_ANGLE_DEGREES, sweepAngle = VOICE_SPEED_ARC_SWEEP_DEGREES, useCenter = false, topLeft = ovalTl, size = ovalSz, style = Stroke(width = swPx, cap = StrokeCap.Round))
            val thumbDeg = VOICE_SPEED_ARC_START_ANGLE_DEGREES + VOICE_SPEED_ARC_SWEEP_DEGREES * speedPercent
            val thumbRad = Math.toRadians(thumbDeg.toDouble())
            val thumb = Offset(c.x + cos(thumbRad).toFloat() * innerR, c.y + sin(thumbRad).toFloat() * innerR)
            drawCircle(color = primary.copy(alpha = 0.18f), radius = (17.dp * scale).toPx(), center = thumb)
            drawCircle(color = primary, radius = (9.dp * scale).toPx(), center = thumb)
        }
        EdgeControlIcon(modifier = Modifier.offset { arcIconOffset(layoutSize.value, VOICE_SPEED_ICON_MAX_ANGLE_DEGREES, iconSizePx, strokeWidthPx, radiusInsetPx + speedExtraInsetPx) }, icon = ImageVector.vectorResource(maxIconRes), contentDescription = stringResource(R.string.cd_voice_speed_max), size = iconSize)
        EdgeControlIcon(modifier = Modifier.offset { arcIconOffset(layoutSize.value, VOICE_SPEED_ICON_MIN_ANGLE_DEGREES, iconSizePx, strokeWidthPx, radiusInsetPx + speedExtraInsetPx) }, icon = ImageVector.vectorResource(minIconRes), contentDescription = stringResource(R.string.cd_voice_speed_min), size = iconSize)
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun VoicePitchArcControl(
    voicePitch: Float,
    scale: Float,
    cuteIcons: Boolean,
    onVoicePitchChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val track = primary.copy(alpha = 0.22f)
    val density = LocalDensity.current
    val view = LocalView.current
    val layoutSize = remember { mutableStateOf(IntSize.Zero) }
    val isTracking = remember { mutableStateOf(false) }

    val pitchPercent = (voicePitch.coerceIn(VOICE_PITCH_MIN, VOICE_PITCH_MAX) - VOICE_PITCH_MIN) / (VOICE_PITCH_MAX - VOICE_PITCH_MIN)
    val iconSize = 24.dp * scale
    val strokeWidth = 8.dp * scale
    val radiusInset = 5.dp * scale
    val speedExtraInset = VOICE_SPEED_ARC_EXTRA_INSET_DP.dp * scale  // needed for arc disambiguation

    val iconSizeF = with(density) { iconSize.toPx() }
    val iconSizePx = with(density) { iconSize.roundToPx() }
    val strokeWidthF = with(density) { strokeWidth.toPx() }
    val strokeWidthPx = with(density) { strokeWidth.roundToPx() }
    val radiusInsetF = with(density) { radiusInset.toPx() }
    val radiusInsetPx = with(density) { radiusInset.roundToPx() }
    val speedExtraInsetF = with(density) { speedExtraInset.toPx() }

    val maxIconRes = if (cuteIcons) R.drawable.pitch_max_mouse_24px else R.drawable.music_clef_treble
    val minIconRes = if (cuteIcons) R.drawable.pitch_min_whale_24px else R.drawable.music_clef_bass

    Box(
        modifier = modifier
            .onSizeChanged { layoutSize.value = it }
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        val size = layoutSize.value
                        if (size.width <= 0 || size.height <= 0) return@pointerInteropFilter false
                        val outerR = size.width / 2f - strokeWidthF - radiusInsetF
                        if (hitsIcon(event.x, event.y, size, VOICE_PITCH_ICON_MAX_ANGLE_DEGREES, outerR, iconSizeF)) {
                            isTracking.value = true
                            view.parent.requestDisallowInterceptTouchEvent(true)
                            onVoicePitchChange(VOICE_PITCH_MAX)
                            return@pointerInteropFilter true
                        }
                        if (hitsIcon(event.x, event.y, size, VOICE_PITCH_ICON_MIN_ANGLE_DEGREES, outerR, iconSizeF)) {
                            isTracking.value = true
                            view.parent.requestDisallowInterceptTouchEvent(true)
                            onVoicePitchChange(VOICE_PITCH_MIN)
                            return@pointerInteropFilter true
                        }
                        if (identifyTouchedArc(event.x, event.y, size, strokeWidthF, radiusInsetF, speedExtraInsetF) != ActiveArc.PITCH) {
                            return@pointerInteropFilter false
                        }
                        isTracking.value = true
                        view.parent.requestDisallowInterceptTouchEvent(true)
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        val pct = arcPercentFromPosition(event.x, event.y, size, VOICE_PITCH_ARC_START_ANGLE_DEGREES, VOICE_PITCH_ARC_SWEEP_DEGREES)
                        onVoicePitchChange(VOICE_PITCH_MIN + (VOICE_PITCH_MAX - VOICE_PITCH_MIN) * pct)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!isTracking.value) return@pointerInteropFilter false
                        val pct = arcPercentFromPosition(event.x, event.y, layoutSize.value, VOICE_PITCH_ARC_START_ANGLE_DEGREES, VOICE_PITCH_ARC_SWEEP_DEGREES)
                        onVoicePitchChange(VOICE_PITCH_MIN + (VOICE_PITCH_MAX - VOICE_PITCH_MIN) * pct)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val wasTracking = isTracking.value
                        if (wasTracking) {
                            isTracking.value = false
                            view.parent.requestDisallowInterceptTouchEvent(false)
                        }
                        wasTracking
                    }
                    else -> false
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val swPx = strokeWidth.toPx()
            val outerR = size.width / 2f - swPx - radiusInset.toPx()
            val c = Offset(size.width / 2f, size.height / 2f)
            val ovalTl = Offset(c.x - outerR, c.y - outerR)
            val ovalSz = androidx.compose.ui.geometry.Size(outerR * 2, outerR * 2)
            drawArc(color = track, startAngle = VOICE_PITCH_ARC_START_ANGLE_DEGREES, sweepAngle = VOICE_PITCH_ARC_SWEEP_DEGREES, useCenter = false, topLeft = ovalTl, size = ovalSz, style = Stroke(width = swPx, cap = StrokeCap.Round))
            val thumbDeg = VOICE_PITCH_ARC_START_ANGLE_DEGREES + VOICE_PITCH_ARC_SWEEP_DEGREES * pitchPercent
            val thumbRad = Math.toRadians(thumbDeg.toDouble())
            val thumb = Offset(c.x + cos(thumbRad).toFloat() * outerR, c.y + sin(thumbRad).toFloat() * outerR)
            drawCircle(color = primary.copy(alpha = 0.18f), radius = (17.dp * scale).toPx(), center = thumb)
            drawCircle(color = primary, radius = (9.dp * scale).toPx(), center = thumb)
        }
        EdgeControlIcon(modifier = Modifier.offset { arcIconOffset(layoutSize.value, VOICE_PITCH_ICON_MAX_ANGLE_DEGREES, iconSizePx, strokeWidthPx, radiusInsetPx) }, icon = ImageVector.vectorResource(maxIconRes), contentDescription = stringResource(R.string.cd_voice_pitch_max), size = iconSize)
        EdgeControlIcon(modifier = Modifier.offset { arcIconOffset(layoutSize.value, VOICE_PITCH_ICON_MIN_ANGLE_DEGREES, iconSizePx, strokeWidthPx, radiusInsetPx) }, icon = ImageVector.vectorResource(minIconRes), contentDescription = stringResource(R.string.cd_voice_pitch_min), size = iconSize)
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

// Volume arc: right edge; draws from top-right (-50°) clockwise to bottom-right (50°)
// Reverse-mapped: top = max volume, bottom = min volume
private const val VOLUME_ARC_START_ANGLE_DEGREES = -50f
private const val VOLUME_ARC_SWEEP_DEGREES = 100f
private const val VOLUME_ICON_MAX_ANGLE_DEGREES = -64f   // 14° before arc start
private const val VOLUME_ICON_MIN_ANGLE_DEGREES = 64f    // 14° after arc end

// Speed arc: top-center inner arc; draws from left (-140°) clockwise to right (-40°)
// Min speed at start, max speed at end
private const val VOICE_SPEED_ARC_START_ANGLE_DEGREES = -140f
private const val VOICE_SPEED_ARC_SWEEP_DEGREES = 100f
private const val VOICE_SPEED_ARC_EXTRA_INSET_DP = 32f
private const val VOICE_SPEED_ICON_MIN_ANGLE_DEGREES = -160f  // 20° before arc start
private const val VOICE_SPEED_ICON_MAX_ANGLE_DEGREES = -20f   // 20° after arc end

// Pitch arc: left edge; draws from bottom-left (130°) clockwise to top-left (230°)
// Min pitch at start, max pitch at end — arc crosses the ±180° boundary
private const val VOICE_PITCH_ARC_START_ANGLE_DEGREES = 130f
private const val VOICE_PITCH_ARC_SWEEP_DEGREES = 100f
private const val VOICE_PITCH_ICON_MIN_ANGLE_DEGREES = 116f   // 14° before arc start
private const val VOICE_PITCH_ICON_MAX_ANGLE_DEGREES = -116f  // 14° after arc end (244°)
private val WearReferenceSceneSize = 213.dp

private fun arcPercentFromPosition(
    x: Float,
    y: Float,
    size: IntSize,
    startAngleDegrees: Float,
    sweepDegrees: Float,
    reverse: Boolean = false,
): Float {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val rawAngle = atan2(y - cy, x - cx) * 180f / PI.toFloat()
    // Normalize both to [0°, 360°) so ±180° wrap never causes sign-flip jumps
    val normAngle = ((rawAngle % 360f) + 360f) % 360f
    val normStart = ((startAngleDegrees % 360f) + 360f) % 360f
    // Offset from arc start, wrapped into [0°, 360°)
    val offset = (((normAngle - normStart) % 360f) + 360f) % 360f
    // If within arc: use directly. If past arc end (<180° overshoot): snap to end.
    // If before arc start (>=180° wrap): snap to start, not end.
    val clampedOffset = when {
        offset <= sweepDegrees -> offset
        offset < 180f -> sweepDegrees
        else -> 0f
    }
    val percent = clampedOffset / sweepDegrees
    return if (reverse) 1f - percent else percent
}

private fun arcIconOffset(
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
    cuteIcons: Boolean,
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
            imageVector = if (cuteIcons) ImageVector.vectorResource(R.drawable.mic_parrot_24px) else Icons.Filled.Mic,
            contentDescription = if (isListening) stringResource(R.string.cd_listening) else stringResource(R.string.cd_hold_to_talk),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun Greeting(
    text: String,
    scrollState: ScrollState,
    showScrollbar: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = if (showScrollbar) modifier.verticalScrollbar(scrollState) else modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary,
            text = text,
        )
    }
}
