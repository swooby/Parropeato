package com.swooby.parropeato

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.smartfoo.android.core.FooReflection
import com.smartfoo.android.core.FooString.quote
import com.smartfoo.android.core.logging.FooLog
import com.smartfoo.android.core.platform.FooPlatformUtils
import com.swooby.parropeato.common.R
import java.util.concurrent.Executor

/**
 * Owns the [SpeechRecognizer] lifecycle, retry logic, error mapping, and partial-result
 * accumulation. Extracted from [BaseMainActivity] to keep the activity focused on lifecycle
 * and UI wiring.
 *
 * Callbacks are delivered on the main thread (via the supplied [handler]).
 */
class SpeechRecognizerManager(
    private val context: Context,
    private val handler: Handler,
    private val viewModel: ParropeatoViewModel,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        /** Set persistent UI text (survives transient overlay updates). */
        fun setPersistentText(text: String)
        /** A recognition result is ready to be spoken. */
        fun onResult(text: String)
        /** The device is offline and no installed model exists for the selected locale. */
        fun onOfflineModelUnavailable()
        /** Speech recognition ended without any final recognition candidates. */
        fun onRecognitionEmpty()
        /** Speech recognition reported an error. */
        fun onRecognitionError(error: Int, willRetry: Boolean)
        /** Speech recognition is starting with the current locale/network configuration. */
        fun onRecognitionStart(locale: String?, isOnline: Boolean, hasOfflineModel: Boolean)
        /** The previously saved speech locale is no longer supported; persist the cleared value. */
        fun onSavedLocaleInvalidated()
    }

    companion object {
        private val TAG = FooLog.TAG(SpeechRecognizerManager::class)
        private val errorNames = FooReflection.mapConstants(SpeechRecognizer::class, "ERROR_")
        fun errorToString(error: Int): String = FooReflection.toString(errorNames, error)

        private const val RECOGNIZER_RETRY_DELAY_MS = 150L
        private const val LOCALE_CHECK_TIMEOUT_MS = 10_000L
    }

    // Written from the main thread; read from RecognitionListener callbacks on a binder thread.
    // @Volatile ensures writes are immediately visible across threads.
    @Volatile var isPushToTalkPressed = false
    @Volatile var isListening = false
        private set
    @Volatile private var shouldProcessResults = false
    @Volatile private var latestPartialRecognition: String? = null
    @Volatile private var latestUnstableRecognition: String? = null

    private lateinit var recognizer: SpeechRecognizer

    // Exposed so unit tests can fire RecognitionListener callbacks directly without
    // relying on Robolectric's ShadowSpeechRecognizer static accessor.
    @get:androidx.annotation.VisibleForTesting
    internal var recognitionListener: RecognitionListener? = null
        private set

    // Named runnables so they can be cancelled individually without clearing all callbacks.
    private val retryRunnable = Runnable {
        if (isPushToTalkPressed && !isListening) start()
    }
    private val localeCheckTimeoutRunnable = Runnable {
        if (!viewModel.speechLocalesSupportChecked) {
            FooLog.w(TAG, "checkSupportedLocales: timed out after ${LOCALE_CHECK_TIMEOUT_MS}ms, using full candidate list")
            viewModel.supportedSpeechLocales = SpeechLocalePreference.CANDIDATES
                .sortedBy { java.util.Locale.forLanguageTag(it).getDisplayName(java.util.Locale.getDefault()) }
            viewModel.speechLocalesSupportChecked = true
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * (Re-)create the recognizer and attach the recognition listener.
     * Call once from [BaseMainActivity.onCreate] and again after every [reset].
     * Pass [updatePrompt] = true to set the idle "hold to talk" prompt text.
     */
    fun init(updatePrompt: Boolean = true) {
        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val listener = object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                FooLog.d(TAG, "onReadyForSpeech(params=${FooPlatformUtils.toString(params)})")
            }

            override fun onBeginningOfSpeech() {
                FooLog.d(TAG, "onBeginningOfSpeech()")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Fires dozens of times per second — intentionally silent.
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                FooLog.d(TAG, "onBufferReceived(buffer=...)")
            }

            override fun onError(error: Int) {
                FooLog.d(TAG, "onError(error=${errorToString(error)})")
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
                    callbacks.onRecognitionError(error, willRetry = true)
                    viewModel.state = ParropeatoViewModel.State.Listening
                    callbacks.setPersistentText(context.getString(R.string.status_listening))
                    reset(updatePrompt = false)
                    handler.postDelayed(retryRunnable, RECOGNIZER_RETRY_DELAY_MS)
                    return
                }
                callbacks.onRecognitionError(error, willRetry = false)
                reset(updatePrompt = false)
                viewModel.state = ParropeatoViewModel.State.Idle
                callbacks.setPersistentText(errorText(error))
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
                handler.removeCallbacks(retryRunnable)
                if (!shouldProcessResults) {
                    reset(updatePrompt = false)
                    return
                }
                shouldProcessResults = false
                val recognitions = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: arrayListOf()
                val confidenceScores = results
                    ?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES) ?: floatArrayOf()
                if (recognitions.isEmpty()) {
                    reset(updatePrompt = false)
                    viewModel.state = ParropeatoViewModel.State.Idle
                    callbacks.setPersistentText(context.getString(R.string.error_stt_no_match))
                    callbacks.onRecognitionEmpty()
                    return
                }
                FooLog.i(TAG, "onResults: confidenceScores.size=${confidenceScores.size}")
                val best = if (confidenceScores.isEmpty()) {
                    recognitions[0]
                } else {
                    var best = recognitions[0]
                    var bestScore = confidenceScores[0]
                    FooLog.v(TAG, "onResults: recognition=${quote(best)}, score=$bestScore")
                    val count = minOf(recognitions.size, confidenceScores.size)
                    for (i in 1 until count) {
                        val r = recognitions[i]
                        val s = confidenceScores[i]
                        FooLog.v(TAG, "onResults: recognition=${quote(r)}, score=$s")
                        if (s > bestScore) { bestScore = s; best = r }
                    }
                    FooLog.i(TAG, "onResults: highestConfidenceScore=$bestScore")
                    best
                }
                FooLog.i(TAG, "onResults: best=${quote(best)}")
                viewModel.state = ParropeatoViewModel.State.Speaking
                callbacks.onResult(best)
                reset(updatePrompt = false)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                FooLog.d(TAG, "onEvent(eventType=$eventType, params=${FooPlatformUtils.toString(params)})")
            }
        }
        recognitionListener = listener
        recognizer.setRecognitionListener(listener)

        if (updatePrompt) {
            viewModel.state = ParropeatoViewModel.State.Idle
            callbacks.setPersistentText(
                context.getString(
                    if (viewModel.cuteIcons) R.string.status_hold_cute_mic_to_talk
                    else R.string.status_hold_mic_to_talk
                )
            )
        }
    }

    /**
     * Query which speech locales the on-device recognizer supports and update [viewModel].
     * Must be called after [init].
     */
    fun checkSupportedLocales(executor: Executor) {
        handler.postDelayed(localeCheckTimeoutRunnable, LOCALE_CHECK_TIMEOUT_MS)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        recognizer.checkRecognitionSupport(
            intent,
            executor,
            object : RecognitionSupportCallback {
                override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                    handler.removeCallbacks(localeCheckTimeoutRunnable)
                    viewModel.installedSpeechLocales =
                        recognitionSupport.installedOnDeviceLanguages.toSet()
                    val supportedTags: Set<String> = buildSet {
                        addAll(recognitionSupport.installedOnDeviceLanguages)
                        addAll(recognitionSupport.supportedOnDeviceLanguages)
                        addAll(recognitionSupport.onlineLanguages)
                    }
                    val filtered = SpeechLocalePreference.CANDIDATES
                        .filter { it in supportedTags }
                        .sortedBy {
                            java.util.Locale.forLanguageTag(it)
                                .getDisplayName(java.util.Locale.getDefault())
                        }
                    FooLog.i(TAG, "checkSupportedLocales: ${filtered.size} locales supported")
                    viewModel.supportedSpeechLocales = filtered
                    viewModel.speechLocalesSupportChecked = true
                    val saved = viewModel.speechRecognizerLocale
                    if (saved != null && saved !in filtered) {
                        FooLog.w(TAG, "checkSupportedLocales: saved locale $saved no longer supported, clearing")
                        viewModel.speechRecognizerLocale = null
                        callbacks.onSavedLocaleInvalidated()
                    }
                }

                override fun onError(errorCode: Int) {
                    handler.removeCallbacks(localeCheckTimeoutRunnable)
                    FooLog.w(TAG, "checkSupportedLocales: checkRecognitionSupport error=$errorCode, using full candidate list")
                    viewModel.supportedSpeechLocales = SpeechLocalePreference.CANDIDATES
                        .sortedBy {
                            java.util.Locale.forLanguageTag(it)
                                .getDisplayName(java.util.Locale.getDefault())
                        }
                    viewModel.speechLocalesSupportChecked = true
                }
            }
        )
    }

    // -------------------------------------------------------------------------
    // Listening control
    // -------------------------------------------------------------------------

    /** Cancel any pending retry scheduled by [onError]. Call before starting a new PTT cycle. */
    fun cancelRetry() {
        handler.removeCallbacks(retryRunnable)
    }

    /** Begin listening for speech. No-op if already listening. */
    fun start() {
        if (!::recognizer.isInitialized) {
            FooLog.e(TAG, "start: recognizer not initialized, skipping")
            return
        }
        FooLog.i(TAG, "+start()")
        isListening = true
        shouldProcessResults = true
        latestPartialRecognition = null
        latestUnstableRecognition = null
        viewModel.state = ParropeatoViewModel.State.Listening
        callbacks.setPersistentText(context.getString(R.string.status_listening))

        val selectedLocale = viewModel.speechRecognizerLocale
        val isOnline = viewModel.isNetworkAvailable
        val hasOfflineModel = selectedLocale == null || selectedLocale in viewModel.installedSpeechLocales
        if (!isOnline && !hasOfflineModel) {
            isListening = false
            shouldProcessResults = false
            callbacks.onOfflineModelUnavailable()
            return
        }
        callbacks.onRecognitionStart(
            locale = selectedLocale,
            isOnline = isOnline,
            hasOfflineModel = hasOfflineModel,
        )
        val intent = Intent().apply {
            action = RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            selectedLocale?.let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) }
            putExtra(
                RecognizerIntent.EXTRA_ENABLE_FORMATTING,
                RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY
            )
            putExtra(RecognizerIntent.EXTRA_MASK_OFFENSIVE_WORDS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, !isOnline)
        }
        recognizer.startListening(intent)
        FooLog.i(TAG, "-start()")
    }

    /** Stop the recognizer; results will still be delivered via [onResults]. */
    fun stop() {
        if (::recognizer.isInitialized) {
            recognizer.stopListening()
        }
    }

    /**
     * If the recognizer is currently waiting for results, claim those results for fallback
     * processing (using the best available partial), reset internal state, and return true.
     * Returns false if there is nothing to consume.
     *
     * Used by the activity's fallback timer in [BaseMainActivity.onPushToTalkReleased] to
     * speak a partial result when the recognizer hasn't delivered [onResults] within the timeout.
     */
    fun consumeFallback(): Boolean {
        if (!isListening || !shouldProcessResults) return false
        isListening = false
        shouldProcessResults = false
        reset(updatePrompt = false)
        return true
    }

    /** Destroy and re-initialize the recognizer. */
    fun reset(updatePrompt: Boolean = false) {
        destroy()
        init(updatePrompt)
    }

    /** Permanently destroy the recognizer (call from [BaseMainActivity.onDestroy]). */
    fun destroy() {
        if (::recognizer.isInitialized) {
            recognizer.destroy()
        }
    }

    // -------------------------------------------------------------------------
    // Partial result helpers
    // -------------------------------------------------------------------------

    /** Returns the best available partial text (unstable preferred over stable partial). */
    fun bestPartialRecognition(): String? =
        latestUnstableRecognition?.takeIf { it.isNotBlank() }
            ?: latestPartialRecognition?.takeIf { it.isNotBlank() }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun errorText(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            context.getString(R.string.error_stt_network_timeout)
        SpeechRecognizer.ERROR_NETWORK ->
            context.getString(R.string.error_stt_network)
        SpeechRecognizer.ERROR_AUDIO ->
            context.getString(R.string.error_stt_audio)
        SpeechRecognizer.ERROR_SERVER ->
            context.getString(R.string.error_stt_server)
        SpeechRecognizer.ERROR_CLIENT ->
            context.getString(R.string.error_stt_client)
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            context.getString(R.string.error_stt_speech_timeout)
        SpeechRecognizer.ERROR_NO_MATCH ->
            context.getString(R.string.error_stt_no_match)
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
            context.getString(R.string.error_stt_recognizer_busy)
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            context.getString(R.string.error_stt_insufficient_permissions)
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS ->
            context.getString(R.string.error_stt_too_many_requests)
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
            context.getString(R.string.error_stt_server_disconnected)
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
            context.getString(R.string.error_stt_language_not_supported)
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
            context.getString(R.string.error_stt_language_unavailable)
        SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT ->
            context.getString(R.string.error_stt_cannot_check_support)
        SpeechRecognizer.ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS ->
            context.getString(R.string.error_stt_cannot_listen_to_download_events)
        else ->
            context.getString(R.string.error_stt_generic, errorToString(error))
    }
}
