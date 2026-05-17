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
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.smartfoo.android.core.FooString
import com.smartfoo.android.core.logging.FooLog
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

    protected open val TAG: String by lazy { FooLog.TAG(this::class) }

    protected lateinit var tts: FooTextToSpeech
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
                    viewModel.state = ParropeatoViewModel.State.Speaking
                    speakRecognition(text)
                }

                override fun onOfflineModelUnavailable() {
                    viewModel.state = ParropeatoViewModel.State.Idle
                    setPersistentText(getString(R.string.error_stt_offline_no_model))
                }

                override fun onSavedLocaleInvalidated() {
                    settings.speechRecognizerLocale = null
                }
            }
        )
    }

    // ── PTT state ──────────────────────────────────────────────────────────────

    @Volatile private var pendingStartAfterPermission = false

    // ── Platform-specific overrides ────────────────────────────────────────────

    protected open val textToSpeechVoiceSpeed: Float = VOICE_SPEED_DEFAULT
    protected open val watchFaceSceneScale: Float = 0.92f
    protected open val watchFaceControlsScale: Float = 0.88f
    protected open val watchFaceBorderOutset: Boolean = false
    protected open val greetingBottomInsetDp: Float = 24f
    protected open val greetingScrollIndicator: (@Composable (ScrollState) -> Unit)? = null

    // ── UI setup ───────────────────────────────────────────────────────────────

    protected open fun setupUI() {
        setContent {
            WatchFaceScreen(
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

    // ── Lifecycle ──────────────────────────────────────────────────────────────

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
        speechRecognizerManager.init()
        speechRecognizerManager.checkSupportedLocales(mainExecutor)
        Log.i(TAG, "-onCreate(...)")
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
            onPushToTalkPressed()
        }
    }

    // ── Push-to-talk ───────────────────────────────────────────────────────────

    protected fun onPushToTalkPressed() {
        FooLog.i(TAG, "+onPushToTalkPressed()")
        speechRecognizerManager.isPushToTalkPressed = true
        if (speechRecognizerManager.isListening) {
            FooLog.i(TAG, "-onPushToTalkPressed() already listening")
            return
        }
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
        mainHandler.removeCallbacksAndMessages(null)
        val fallback = speechRecognizerManager.bestPartialRecognition()
        if (speechRecognizerManager.isListening) {
            speechRecognizerManager.stop()
            if (!fallback.isNullOrBlank()) {
                viewModel.state = ParropeatoViewModel.State.Idle
                setPersistentText(getString(R.string.status_thinking))
                // Give the recognizer 500 ms to deliver proper onResults before using the partial.
                mainHandler.postDelayed({
                    if (speechRecognizerManager.consumeFallback()) {
                        viewModel.state = ParropeatoViewModel.State.Speaking
                        speakRecognition(fallback)
                    }
                }, 500)
            } else {
                viewModel.state = ParropeatoViewModel.State.Idle
                setPersistentText(getString(R.string.status_thinking))
            }
        } else if (!fallback.isNullOrBlank()) {
            viewModel.state = ParropeatoViewModel.State.Speaking
            speakRecognition(fallback)
        } else {
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

    private fun permissionRecordAudioCheck(): Boolean {
        try {
            FooLog.i(TAG, "+permissionRecordAudioCheck()")
            return when {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED -> {
                    onPermissionRecordAudioResult(true)
                    true
                }
                shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                    permissionRecordAudioRationale()
                    false
                }
                else -> {
                    permissionRecordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
                permissionRecordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        FooLog.i(TAG, "-permissionRecordAudioRationale()")
    }

    private fun onPermissionRecordAudioResult(isGranted: Boolean) {
        FooLog.i(TAG, "+onPermissionRecordAudioResult(isGranted=$isGranted)")
        if (isGranted && pendingStartAfterPermission) {
            pendingStartAfterPermission = false
            speechRecognizerManager.start()
        } else if (!isGranted) {
            pendingStartAfterPermission = false
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
            Log.i(
                TAG,
                "onTextToSpeechInitialized: migrating settings " +
                    "v${settings.settingsVersion} → ${Settings.CURRENT_VERSION}, clearing ttsVoiceName"
            )
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

    // ── Voice settings ─────────────────────────────────────────────────────────

    protected fun setVoiceSpeed(voiceSpeed: Float) {
        viewModel.voiceSpeed = voiceSpeed
        if (::tts.isInitialized) tts.voiceSpeed = viewModel.voiceSpeed
        if (::settings.isInitialized) settings.ttsVoiceSpeed = viewModel.voiceSpeed
        showTransientText(getString(R.string.status_voice_speed, viewModel.voiceSpeed))
    }

    protected fun setVoicePitch(voicePitch: Float) {
        viewModel.voicePitch = voicePitch
        if (::tts.isInitialized) tts.voicePitch = viewModel.voicePitch
        if (::settings.isInitialized) settings.ttsPitch = viewModel.voicePitch
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
    }

    // ── Settings launchers ─────────────────────────────────────────────────────

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
                // No suitable settings screen available.
            }
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
    }
}
