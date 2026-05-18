package com.swooby.parropeato

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.smartfoo.android.core.FooString
import com.smartfoo.android.core.logging.FooLog
import com.smartfoo.android.core.platform.FooPlatformUtils
import com.smartfoo.android.core.texttospeech.FooTextToSpeech
import com.swooby.parropeato.common.BuildConfig
import com.swooby.parropeato.common.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import android.provider.Settings as AndroidSettings

/**
 * Base activity that owns app lifecycle, TTS, audio/volume, microphone permission,
 * push-to-talk, and settings dispatch.
 *
 * Speech recognizer lifecycle lives in [SpeechRecognizerManager].
 * All Compose UI lives in [WatchFaceScreen] (WatchFaceScreen.kt).
 */
abstract class BaseMainActivity : ComponentActivity() {

    companion object {
        /** How long to wait for real onResults before speaking a partial fallback. */
        const val FALLBACK_PARTIAL_TIMEOUT_MS = 500L
        /** How long a transient status message stays on screen before reverting. */
        const val TRANSIENT_TEXT_DURATION_MS = 5_000L
    }

    @Suppress("PropertyName")
    protected open val TAG: String by lazy { FooLog.TAG(this::class) }

    protected lateinit var tts: FooTextToSpeech
    protected lateinit var audioManager: AudioManager
    protected lateinit var settings: Settings
    protected val mainHandler = Handler(Looper.getMainLooper())
    protected var isOpeningExternalActivity: Boolean = false
        private set
    protected var isRequestingRecordAudioPermission: Boolean = false
        private set

    private lateinit var connectivityManager: ConnectivityManager
    private val analytics: ParropeatoAnalytics by lazy {
        ParropeatoAnalytics(this)
    }
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

    // ── Speech recognizer ──────────────────────────────────────────────────────

    protected val speechRecognizerManager: SpeechRecognizerManager by lazy {
        SpeechRecognizerManager(
            context = this,
            handler = mainHandler,
            viewModel = viewModel,
            callbacks = object : SpeechRecognizerManager.Callbacks {
                override fun setPersistentText(text: String) =
                    this@BaseMainActivity.setPersistentText(text)

                override fun onResult(text: String) {
                    endSpeechSession(ParropeatoAnalytics.SpeechOutcome.Success)
                    viewModel.state = ParropeatoViewModel.State.Speaking
                    speakRecognition(text)
                }

                override fun onOfflineModelUnavailable() {
                    viewModel.state = ParropeatoViewModel.State.Idle
                    setPersistentText(getString(R.string.error_stt_offline_no_model))
                    endSpeechSession(ParropeatoAnalytics.SpeechOutcome.OfflineModelMissing)
                }

                override fun onRecognitionEmpty() {
                    endSpeechSession(ParropeatoAnalytics.SpeechOutcome.NoMatch)
                }

                override fun onRecognitionError(error: Int, willRetry: Boolean) {
                    analytics.logSttError(
                        errorClass = SpeechRecognizerManager.errorToString(error),
                        retry = willRetry,
                    )
                    if (!willRetry) {
                        endSpeechSession(ParropeatoAnalytics.SpeechOutcome.Error)
                    }
                }

                override fun onRecognitionStart(locale: String?, isOnline: Boolean, hasOfflineModel: Boolean) {
                    // Session start is logged when the user begins push-to-talk so permission
                    // denials and offline-model failures are still part of the same funnel.
                    // Do not log again here; recognizer retries may call this more than once.
                }

                override fun onSavedLocaleInvalidated() {
                    settings.speechRecognizerLocale = null
                    showTransientText(getString(R.string.info_speech_locale_reset))
                }
            }
        )
    }

    // ── PTT state ──────────────────────────────────────────────────────────────

    @Volatile private var pendingStartAfterPermission = false
    private var initialGreetingReady = false
    private var initialGreetingSpoken = false
    private var fallbackPartialJob: Job? = null

    // ── Platform-specific overrides ────────────────────────────────────────────

    protected open val textToSpeechVoiceSpeed: Float = VOICE_SPEED_DEFAULT
    protected open val watchFaceSceneScale: Float = 0.92f
    protected open val watchFaceControlsScale: Float = 0.88f
    protected open val watchFaceBorderOutset: Boolean = false
    protected open val greetingBottomInsetDp: Float = 24f
    protected open val analyticsPlatform: String = "unknown"

    // ── UI setup ───────────────────────────────────────────────────────────────

    protected open fun setupUI() {
        setContent {
            WatchFaceScreen(
                viewModel = viewModel,
                logTag = TAG,
                sceneScale = watchFaceSceneScale,
                controlsScale = watchFaceControlsScale,
                borderOutset = watchFaceBorderOutset,
                platformOverlay = { onSettingsClick -> PlatformOverlay { openSettingsOverlay(onSettingsClick) } },
                onPushToTalkPressed = ::onPushToTalkPressed,
                onPushToTalkReleased = ::onPushToTalkReleased,
                onVolumeChange = ::setVolumePercent,
                onVoiceSpeedChange = ::setVoiceSpeed,
                onVoicePitchChange = ::setVoicePitch,
                greetingBottomInsetDp = greetingBottomInsetDp,
                settingsOverlay = {
                    if (viewModel.showSettings) {
                        SettingsOverlay(onDismiss = ::closeSettingsOverlay)
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

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG, "+onCreate(...)")
        super.onCreate(savedInstanceState)
        audioManager = getSystemService(AudioManager::class.java)
        settings = Settings(this, defaultTtsVoiceSpeed = textToSpeechVoiceSpeed)
        analytics.setDiagnosticsCollectionEnabled(settings.diagnosticsEnabled)
        analytics.logLaunchIntent(intent)
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
        viewModel.diagnosticsEnabled = settings.diagnosticsEnabled
        setupUI()
        initTextToSpeech()
        speechRecognizerManager.init()
        speechRecognizerManager.checkSupportedLocales(mainExecutor)
        mainHandler.post { maybeShowDiagnosticsConsentPrompt() }
        Log.i(TAG, "-onCreate(...)")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        analytics.logLaunchIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        tts.start(this, ttsCallbacks)
    }

    override fun onResume() {
        super.onResume()
        isOpeningExternalActivity = false
        // Re-attach silently after a brief onPause/onResume (e.g. notification shade)
        // where onStop was never called. tts.start() is intentionally NOT called here
        // to avoid re-firing onTextToSpeechInitialized (and replaying the greeting).
        tts.attach(ttsCallbacks)
    }

    override fun onPause() {
        super.onPause()
        if (!isRequestingRecordAudioPermission) {
            endSpeechSession(ParropeatoAnalytics.SpeechOutcome.Cancelled)
        }
        viewModel.state = ParropeatoViewModel.State.ShuttingDown
        tts.detach(ttsCallbacks)
        speechRecognizerManager.stop()
    }

    override fun onStop() {
        super.onStop()
        tts.stop()
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.state = ParropeatoViewModel.State.Shutdown
        speechRecognizerManager.destroy()
    }

    // ── Hardware buttons ───────────────────────────────────────────────────────

    // KeyEvent.KEYCODE_STEM_* constants are @RestrictTo(LIBRARY_GROUP) but are the only way
    // to detect Wear OS physical stem-button presses at the Activity level.
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

    private fun onHardwareButton1() {
        if (speechRecognizerManager.isListening) {
            onPushToTalkReleased()
        } else {
            onPushToTalkPressed(ParropeatoAnalytics.InputSource.Hardware)
        }
    }

    // ── Push-to-talk ───────────────────────────────────────────────────────────

    private var speechSessionStartedAtMs: Long? = null

    protected fun onPushToTalkPressed(inputSource: ParropeatoAnalytics.InputSource = ParropeatoAnalytics.InputSource.Touch) {
        FooLog.i(TAG, "+onPushToTalkPressed()")
        if (shouldShowDiagnosticsConsentPrompt()) {
            FooLog.i(TAG, "-onPushToTalkPressed() waiting for diagnostics consent")
            return
        }
        speechRecognizerManager.isPushToTalkPressed = true
        if (speechRecognizerManager.isListening) {
            FooLog.i(TAG, "-onPushToTalkPressed() already listening")
            return
        }
        beginSpeechSession(inputSource)
        tts.clear()
        pendingStartAfterPermission = true
        if (permissionRecordAudioCheck() && !speechRecognizerManager.isListening) {
            pendingStartAfterPermission = false
            speechRecognizerManager.start()
        }
        FooLog.i(TAG, "-onPushToTalkPressed()")
    }

    protected fun onPushToTalkReleased() {
        FooLog.i(TAG, "+onPushToTalkReleased()")
        speechRecognizerManager.isPushToTalkPressed = false
        pendingStartAfterPermission = false
        fallbackPartialJob?.cancel()
        fallbackPartialJob = null
        speechRecognizerManager.cancelRetry()
        val fallback = speechRecognizerManager.bestPartialRecognition()
        if (speechRecognizerManager.isListening) {
            speechRecognizerManager.stop()
            if (!fallback.isNullOrBlank()) {
                viewModel.state = ParropeatoViewModel.State.Idle
                setPersistentText(getString(R.string.status_thinking))
                // Give the recognizer FALLBACK_PARTIAL_TIMEOUT_MS to deliver proper onResults
                // before using the partial. consumeFallback() returns false if real results
                // already arrived, so the coroutine naturally no-ops in that case.
                fallbackPartialJob = lifecycleScope.launch {
                    delay(FALLBACK_PARTIAL_TIMEOUT_MS)
                    if (speechRecognizerManager.consumeFallback()) {
                        endSpeechSession(ParropeatoAnalytics.SpeechOutcome.FallbackPartial)
                        viewModel.state = ParropeatoViewModel.State.Speaking
                        speakRecognition(fallback)
                    }
                    fallbackPartialJob = null
                }
            } else {
                viewModel.state = ParropeatoViewModel.State.Idle
                setPersistentText(getString(R.string.status_thinking))
            }
        } else if (!fallback.isNullOrBlank()) {
            endSpeechSession(ParropeatoAnalytics.SpeechOutcome.FallbackPartial)
            viewModel.state = ParropeatoViewModel.State.Speaking
            speakRecognition(fallback)
        } else {
            endSpeechSession(ParropeatoAnalytics.SpeechOutcome.NoMatch)
            viewModel.state = ParropeatoViewModel.State.Idle
            setPersistentText(getString(R.string.error_stt_no_match))
        }
        FooLog.i(TAG, "-onPushToTalkReleased()")
    }

    // ── Microphone permission ──────────────────────────────────────────────────

    private val permissionRecordAudioLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            onPermissionRecordAudioResult(isGranted)
        }
    private var recordAudioRationaleShownForRequest = false

    private fun permissionRecordAudioCheck(): Boolean {
        try {
            FooLog.i(TAG, "+permissionRecordAudioCheck()")
            return when {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED -> {
                    true
                }
                shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                    permissionRecordAudioRationale()
                    false
                }
                else -> {
                    launchRecordAudioPermissionRequest()
                    false
                }
            }
        } finally {
            FooLog.i(TAG, "-permissionRecordAudioCheck()")
        }
    }

    private fun permissionRecordAudioRationale() {
        FooLog.i(TAG, "+permissionRecordAudioRationale()")
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.permission_mic_rationale_title))
            .setMessage(getString(R.string.permission_mic_rationale_message))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                launchRecordAudioPermissionRequest(rationaleShown = true)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                pendingStartAfterPermission = false
                endSpeechSession(ParropeatoAnalytics.SpeechOutcome.Cancelled)
            }
            .setOnCancelListener {
                pendingStartAfterPermission = false
                endSpeechSession(ParropeatoAnalytics.SpeechOutcome.Cancelled)
            }
            .show()
        FooLog.i(TAG, "-permissionRecordAudioRationale()")
    }

    private fun launchRecordAudioPermissionRequest(rationaleShown: Boolean = false) {
        isRequestingRecordAudioPermission = true
        recordAudioRationaleShownForRequest = rationaleShown
        permissionRecordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun maybeShowDiagnosticsConsentPrompt() {
        if (!::settings.isInitialized || settings.diagnosticsPromptShown || settings.diagnosticsEnabled) {
            trySpeakInitialGreeting()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.diagnostics_consent_title))
            .setMessage(getString(R.string.diagnostics_consent_message))
            .setPositiveButton(R.string.diagnostics_consent_positive) { _, _ ->
                settings.diagnosticsPromptShown = true
                onSettingsDiagnosticsEnabledChanged(true, ParropeatoAnalytics.ConsentSource.FirstRunPrompt)
                trySpeakInitialGreeting()
            }
            .setNegativeButton(R.string.diagnostics_consent_negative) { _, _ ->
                settings.diagnosticsPromptShown = true
                onSettingsDiagnosticsEnabledChanged(false, ParropeatoAnalytics.ConsentSource.FirstRunPrompt)
                trySpeakInitialGreeting()
            }
            .setOnCancelListener {
                settings.diagnosticsPromptShown = true
                onSettingsDiagnosticsEnabledChanged(false, ParropeatoAnalytics.ConsentSource.FirstRunPrompt)
                trySpeakInitialGreeting()
            }
            .show()
    }

    private fun onPermissionRecordAudioResult(isGranted: Boolean) {
        FooLog.i(TAG, "+onPermissionRecordAudioResult(isGranted=$isGranted)")
        isRequestingRecordAudioPermission = false
        analytics.logMicPermissionResult(
            result = if (isGranted) ParropeatoAnalytics.PermissionResult.Granted
            else ParropeatoAnalytics.PermissionResult.Denied,
            rationaleShown = recordAudioRationaleShownForRequest,
        )
        recordAudioRationaleShownForRequest = false
        if (isGranted && pendingStartAfterPermission) {
            pendingStartAfterPermission = false
            speechRecognizerManager.start()
        } else if (!isGranted) {
            pendingStartAfterPermission = false
            endSpeechSession(ParropeatoAnalytics.SpeechOutcome.PermissionDenied)
            viewModel.state = ParropeatoViewModel.State.Idle
            setPersistentText(getString(R.string.error_mic_insufficient_permission))
        } else {
            viewModel.state = ParropeatoViewModel.State.Idle
            setPersistentText(
                getString(
                    if (viewModel.cuteIcons) R.string.status_hold_cute_mic_to_talk
                    else R.string.status_hold_mic_to_talk
                )
            )
        }
        FooLog.i(TAG, "-onPermissionRecordAudioResult(isGranted=$isGranted)")
    }

    // ── TTS ────────────────────────────────────────────────────────────────────

    private fun initTextToSpeech() {
        tts = FooTextToSpeech.instance
        tts.dedupe = false
        tts.setAudioAttributes(AudioAttributes.USAGE_MEDIA)
    }

    private fun onTextToSpeechInitialized(status: Int) {
        Log.i(TAG, "onTextToSpeechInitialized(status=${FooTextToSpeech.statusToString(status)})")

        if (status != TextToSpeech.SUCCESS) {
            analytics.logTtsInit(success = false, errorClass = FooTextToSpeech.statusToString(status))
            viewModel.state = ParropeatoViewModel.State.InitializingError
            var text = getString(R.string.error_tts_init_failed)
            if (BuildConfig.DEBUG) {
                text += "\n" + getString(R.string.error_tts_emulator_hint)
                text += "\nstatus=${FooTextToSpeech.statusToString(status)}"
            } else {
                text += "\n" + getString(R.string.error_tts_check_settings)
            }
            setPersistentText(text)
            return
        }
        analytics.logTtsInit(success = true)

        viewModel.state = ParropeatoViewModel.State.Initialized
        setPersistentText(
            getString(
                if (viewModel.cuteIcons) R.string.status_hold_cute_mic_to_talk
                else R.string.status_hold_mic_to_talk
            )
        )

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
            Log.i(TAG, "onTextToSpeechInitialized: migrating settings " + "v${settings.settingsVersion} → ${Settings.CURRENT_VERSION}, clearing ttsVoiceName")
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

        initialGreetingReady = true
        trySpeakInitialGreeting()
    }

    private fun trySpeakInitialGreeting() {
        if (!initialGreetingReady || initialGreetingSpoken || shouldShowDiagnosticsConsentPrompt()) {
            return
        }
        initialGreetingSpoken = true
        tts.speak(getString(R.string.tts_greeting))
        analytics.logTtsSpeak(ParropeatoAnalytics.TtsSource.Greeting, success = true)
    }

    private fun shouldShowDiagnosticsConsentPrompt(): Boolean =
        ::settings.isInitialized && !settings.diagnosticsPromptShown && !settings.diagnosticsEnabled

    // ── Audio / volume ─────────────────────────────────────────────────────────

    protected fun setVolumePercent(volumePercent: Float) {
        val volumeMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val volume = (volumeMax * volumePercent.coerceIn(0f, 1f)).roundToInt()
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            volume,
            AudioManager.FLAG_PLAY_SOUND,
        )
        updateMediaVolumeState(updateText = true)
        logSettingBucketChanged(
            name = ParropeatoAnalytics.SettingName.Volume,
            value = analytics.percentBucket(viewModel.volumePercent),
            unit = "percent",
        )
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
            val volumePercentDisplay = (volumePercent * 100).roundToInt()
            val text = if (BuildConfig.DEBUG) {
                getString(R.string.status_volume_debug, volumePercentDisplay, volume, volumeMax)
            } else {
                getString(R.string.status_volume, volumePercentDisplay)
            }
            showTransientText(text)
        }
    }

    // ── Voice settings ─────────────────────────────────────────────────────────

    protected fun setVoiceSpeed(voiceSpeed: Float) {
        viewModel.voiceSpeed = voiceSpeed
        if (::tts.isInitialized) tts.voiceSpeed = viewModel.voiceSpeed
        if (::settings.isInitialized) settings.ttsVoiceSpeed = viewModel.voiceSpeed
        showTransientText(getString(R.string.status_voice_speed, viewModel.voiceSpeed))
        logSettingBucketChanged(
            name = ParropeatoAnalytics.SettingName.VoiceSpeed,
            value = analytics.multiplierBucket(viewModel.voiceSpeed),
            unit = "multiplier",
        )
    }

    protected fun setVoicePitch(voicePitch: Float) {
        viewModel.voicePitch = voicePitch
        if (::tts.isInitialized) tts.voicePitch = viewModel.voicePitch
        if (::settings.isInitialized) settings.ttsPitch = viewModel.voicePitch
        showTransientText(getString(R.string.status_voice_pitch, viewModel.voicePitch))
        logSettingBucketChanged(
            name = ParropeatoAnalytics.SettingName.VoicePitch,
            value = analytics.multiplierBucket(viewModel.voicePitch),
            unit = "multiplier",
        )
    }

    protected fun onSettingsVoiceSelected(voiceName: String?) {
        tts.setVoiceName(voiceName)  // null → engine default
        viewModel.selectedVoiceName = voiceName
        settings.ttsVoiceName = voiceName
        analytics.logSettingChanged(
            name = ParropeatoAnalytics.SettingName.TtsVoice,
            value = analytics.localeMode(voiceName).analyticsValue,
        )
    }

    protected fun onSettingsVoicePreview(voiceName: String) {
        if (!tts.isStarted) {
            analytics.logTtsSpeak(ParropeatoAnalytics.TtsSource.Preview, success = false)
            return
        }
        analytics.logTtsVoicePreview(ParropeatoAnalytics.LocaleMode.Specific)
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
        analytics.logTtsSpeak(ParropeatoAnalytics.TtsSource.Preview, success = true)
    }

    protected fun onSettingsSpeechLocaleSelected(locale: String?) {
        viewModel.speechRecognizerLocale = locale
        settings.speechRecognizerLocale = locale
        analytics.logSettingChanged(
            name = ParropeatoAnalytics.SettingName.SpeechLocale,
            value = analytics.localeMode(locale).analyticsValue,
        )
    }

    protected fun onSettingsCuteIconsChanged(value: Boolean) {
        viewModel.cuteIcons = value
        settings.cuteIcons = value
        analytics.logSettingChanged(
            name = ParropeatoAnalytics.SettingName.CuteIcons,
            value = value.toString(),
        )
        val holdMic = getString(R.string.status_hold_mic_to_talk)
        val holdCuteMic = getString(R.string.status_hold_cute_mic_to_talk)
        if (lastPersistentText == holdMic || lastPersistentText == holdCuteMic) {
            setPersistentText(
                getString(
                    if (value) R.string.status_hold_cute_mic_to_talk
                    else R.string.status_hold_mic_to_talk
                )
            )
        }
    }

    protected fun onSettingsAccentColorChanged(argb: Int) {
        viewModel.accentColor = argb
        settings.accentColor = argb
        analytics.logSettingChanged(
            name = ParropeatoAnalytics.SettingName.AccentColor,
            value = analytics.accentColorValue(argb),
        )
    }

    protected fun onSettingsDiagnosticsEnabledChanged(
        value: Boolean,
        source: ParropeatoAnalytics.ConsentSource = ParropeatoAnalytics.ConsentSource.Settings,
    ) {
        viewModel.diagnosticsEnabled = value
        settings.diagnosticsEnabled = value
        if (value) {
            analytics.setDiagnosticsCollectionEnabled(true)
            if (source == ParropeatoAnalytics.ConsentSource.FirstRunPrompt) {
                analytics.logDiagnosticsConsentAccept(source)
            }
            analytics.logDiagnosticsSettingChanged(enabled = true, source = source)
        } else {
            analytics.logDiagnosticsSettingChanged(enabled = false, source = source)
            analytics.setDiagnosticsCollectionEnabled(false)
        }
    }

    protected fun onSettingsScreenOpened(screen: ParropeatoAnalytics.SettingsScreen) {
        analytics.logSettingsScreenOpen(screen)
    }

    private fun openSettingsOverlay(onSettingsClick: () -> Unit) {
        analytics.logSettingsOpen(analyticsPlatform)
        analytics.logSettingsScreenOpen(ParropeatoAnalytics.SettingsScreen.Main)
        onSettingsClick()
    }

    private fun closeSettingsOverlay() {
        analytics.logSettingsClose(analyticsPlatform)
        viewModel.showSettings = false
    }

    private fun beginSpeechSession(inputSource: ParropeatoAnalytics.InputSource) {
        speechSessionStartedAtMs = SystemClock.elapsedRealtime()
        val locale = viewModel.speechRecognizerLocale
        val hasOfflineModel = locale == null || locale in viewModel.installedSpeechLocales
        analytics.logSpeechSessionStart(
            input = inputSource,
            localeMode = analytics.localeMode(locale),
            network = if (viewModel.isNetworkAvailable) {
                ParropeatoAnalytics.NetworkState.Online
            } else {
                ParropeatoAnalytics.NetworkState.Offline
            },
            offlineModel = analytics.offlineModelState(locale, hasOfflineModel),
        )
    }

    private fun endSpeechSession(outcome: ParropeatoAnalytics.SpeechOutcome) {
        val startedAt = speechSessionStartedAtMs ?: return
        speechSessionStartedAtMs = null
        analytics.logSpeechSessionEnd(
            outcome = outcome,
            durationMs = SystemClock.elapsedRealtime() - startedAt,
        )
    }

    private val lastLoggedSettingBuckets = mutableMapOf<ParropeatoAnalytics.SettingName, String>()

    private fun logSettingBucketChanged(
        name: ParropeatoAnalytics.SettingName,
        value: String,
        unit: String,
    ) {
        val previous = lastLoggedSettingBuckets.put(name, value)
        if (previous != value) {
            analytics.logSettingChanged(name = name, value = value, unit = unit)
        }
    }

    // ── Settings launchers ─────────────────────────────────────────────────────

    protected fun openTtsSettings() {
        tryLaunchExternalSettings(
            ParropeatoAnalytics.ExternalSettingsTarget.Tts,
            Intent("com.android.settings.TTS_SETTINGS") to null,
            Intent(AndroidSettings.ACTION_SETTINGS) to "general_settings",
        )
    }

    fun openSpeechDownloadSettings() {
        tryLaunchExternalSettings(
            ParropeatoAnalytics.ExternalSettingsTarget.SpeechDownload,
            Intent(AndroidSettings.ACTION_VOICE_INPUT_SETTINGS) to "voice_input",
            Intent(AndroidSettings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS) to "default_apps",
        )
    }

    /** Try each candidate intent in order; log the first that succeeds or "none" if all fail. */
    private fun tryLaunchExternalSettings(
        target: ParropeatoAnalytics.ExternalSettingsTarget,
        vararg candidates: Pair<Intent, String?>,
    ) {
        for ((intent, fallback) in candidates) {
            try {
                startExternalActivity(intent)
                analytics.logExternalSettingsOpen(target = target, success = true, fallback = fallback)
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        analytics.logExternalSettingsOpen(target = target, success = false, fallback = "none")
    }

    protected fun openButtonsAndGesturesSettings() {
        if (tryStartButtonsAndGesturesSettings(
                intent = Intent("android.settings.BUTTONS_GESTURES_SETTINGS"),
                method = ParropeatoAnalytics.ButtonSettingsMethod.SamsungAction,
            )
        ) {
            return
        }

        if (tryStartButtonsAndGesturesSettings(
                intent = Intent().setClassName(
                    "com.google.android.apps.wearable.settings",
                    "com.samsung.android.clockwork.settings.btngesture.StBtnGestureActivity",
                ),
                method = ParropeatoAnalytics.ButtonSettingsMethod.SamsungComponent,
            )
        ) {
            return
        }

        tryStartButtonsAndGesturesSettings(
            intent = Intent(AndroidSettings.ACTION_SETTINGS),
            method = ParropeatoAnalytics.ButtonSettingsMethod.GeneralSettings,
        )
    }

    protected fun openAppInfoSettings() {
        isOpeningExternalActivity = true
        try {
            FooPlatformUtils.showAppSettings(this)
        } catch (e: ActivityNotFoundException) {
            isOpeningExternalActivity = false
            throw e
        } catch (e: SecurityException) {
            isOpeningExternalActivity = false
            throw e
        }
    }

    private fun tryStartButtonsAndGesturesSettings(
        intent: Intent,
        method: ParropeatoAnalytics.ButtonSettingsMethod,
    ): Boolean {
        try {
            startExternalActivity(intent)
            analytics.logButtonSettingsOpen(method = method, success = true)
            analytics.logExternalSettingsOpen(
                target = ParropeatoAnalytics.ExternalSettingsTarget.ButtonGestures,
                success = true,
                fallback = method.analyticsValue,
            )
            return true
        } catch (_: ActivityNotFoundException) {
            analytics.logButtonSettingsOpen(method = method, success = false)
            analytics.logExternalSettingsOpen(
                target = ParropeatoAnalytics.ExternalSettingsTarget.ButtonGestures,
                success = false,
                fallback = method.analyticsValue,
            )
        } catch (_: SecurityException) {
            analytics.logButtonSettingsOpen(method = method, success = false)
            analytics.logExternalSettingsOpen(
                target = ParropeatoAnalytics.ExternalSettingsTarget.ButtonGestures,
                success = false,
                fallback = method.analyticsValue,
            )
        }
        return false
    }

    private fun startExternalActivity(intent: Intent) {
        isOpeningExternalActivity = true
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            isOpeningExternalActivity = false
            throw e
        } catch (e: SecurityException) {
            isOpeningExternalActivity = false
            throw e
        }
    }

    // ── Text management ────────────────────────────────────────────────────────

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
        analytics.logTtsSpeak(ParropeatoAnalytics.TtsSource.Recognition, success = true)
    }
}
