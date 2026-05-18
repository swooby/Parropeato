package com.swooby.parropeato

import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.SpeechRecognizer
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpeechRecognizerManagerTest {

    private lateinit var app: Application
    private lateinit var viewModel: ParropeatoViewModel
    private lateinit var manager: SpeechRecognizerManager

    // Records each callback invocation as a tagged string for easy assertion.
    private val events = mutableListOf<String>()

    private val callbacks = object : SpeechRecognizerManager.Callbacks {
        override fun setPersistentText(text: String) { events += "text" }
        override fun onResult(text: String) { events += "result:$text" }
        override fun onOfflineModelUnavailable() { events += "offline_unavailable" }
        override fun onRecognitionEmpty() { events += "empty" }
        override fun onRecognitionError(error: Int, willRetry: Boolean) {
            events += "error:${SpeechRecognizerManager.errorToString(error)}:retry=$willRetry"
        }
        override fun onRecognitionStart(locale: String?, isOnline: Boolean, hasOfflineModel: Boolean) {
            events += "start"
        }
        override fun onSavedLocaleInvalidated() { events += "locale_invalidated" }
    }

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        viewModel = ParropeatoViewModel(app)
        viewModel.isNetworkAvailable = true
        manager = SpeechRecognizerManager(
            context = app,
            handler = Handler(Looper.getMainLooper()),
            viewModel = viewModel,
            callbacks = callbacks,
        )
        manager.init(updatePrompt = false)
        events.clear()
    }

    @After
    fun tearDown() {
        manager.destroy()
    }

    // Fire a RecognitionListener callback on the current listener stored by the manager.
    // This avoids relying on ShadowSpeechRecognizer's static accessor, which is unreliable
    // in Robolectric 4.16/API 34.
    private fun fireOnResults(bundle: Bundle) =
        requireNotNull(manager.recognitionListener) { "recognitionListener is null; call manager.init() first" }
            .onResults(bundle)

    private fun fireOnError(error: Int) =
        requireNotNull(manager.recognitionListener) { "recognitionListener is null; call manager.init() first" }
            .onError(error)

    private fun fireOnPartialResults(bundle: Bundle) =
        requireNotNull(manager.recognitionListener) { "recognitionListener is null; call manager.init() first" }
            .onPartialResults(bundle)

    // Helper to build a results Bundle the same way Android delivers it.
    private fun resultsBundle(vararg pairs: Pair<String, Float>): Bundle = Bundle().apply {
        putStringArrayList(
            SpeechRecognizer.RESULTS_RECOGNITION,
            ArrayList(pairs.map { it.first }),
        )
        putFloatArray(
            SpeechRecognizer.CONFIDENCE_SCORES,
            pairs.map { it.second }.toFloatArray(),
        )
    }

    // ── start() ───────────────────────────────────────────────────────────────

    @Test
    fun `start sets isListening true and fires onRecognitionStart`() {
        manager.start()
        assertTrue(manager.isListening)
        assertTrue(events.any { it == "start" })
    }

    @Test
    fun `start with no network and no offline model fires onOfflineModelUnavailable`() {
        viewModel.isNetworkAvailable = false
        viewModel.speechRecognizerLocale = "fr-FR"
        viewModel.installedSpeechLocales = emptySet() // fr-FR not installed
        manager.start()
        assertFalse(manager.isListening)
        assertTrue(events.contains("offline_unavailable"))
    }

    @Test
    fun `start with no network but installed locale proceeds`() {
        viewModel.isNetworkAvailable = false
        viewModel.speechRecognizerLocale = "fr-FR"
        viewModel.installedSpeechLocales = setOf("fr-FR")
        manager.start()
        assertTrue(manager.isListening)
        assertFalse(events.contains("offline_unavailable"))
    }

    // ── onResults ─────────────────────────────────────────────────────────────

    @Test
    fun `onResults with single recognition fires onResult`() {
        manager.start()
        events.clear()
        fireOnResults(resultsBundle("hello world" to 0.9f))
        assertEquals(listOf("result:hello world"), events)
        assertFalse(manager.isListening)
    }

    @Test
    fun `onResults picks highest confidence candidate`() {
        manager.start()
        events.clear()
        fireOnResults(resultsBundle("low" to 0.3f, "medium" to 0.6f, "high" to 0.95f))
        assertTrue(events.contains("result:high"))
    }

    @Test
    fun `onResults with empty list fires onRecognitionEmpty`() {
        manager.start()
        events.clear()
        val emptyBundle = Bundle().apply {
            putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, ArrayList())
        }
        fireOnResults(emptyBundle)
        assertTrue(events.contains("empty"))
        assertFalse(manager.isListening)
    }

    @Test
    fun `onResults with no confidence scores uses first candidate`() {
        manager.start()
        events.clear()
        val bundle = Bundle().apply {
            putStringArrayList(
                SpeechRecognizer.RESULTS_RECOGNITION,
                arrayListOf("first", "second"),
            )
            // No CONFIDENCE_SCORES key → empty float array
        }
        fireOnResults(bundle)
        assertTrue(events.contains("result:first"))
    }

    @Test
    fun `onResults is ignored when shouldProcessResults is false`() {
        // consumeFallback clears shouldProcessResults before onResults fires
        manager.start()
        manager.consumeFallback()
        events.clear()
        fireOnResults(resultsBundle("ignored" to 1.0f))
        assertFalse(events.any { it.startsWith("result:") })
    }

    // ── onError retry logic ───────────────────────────────────────────────────

    @Test
    fun `ERROR_NO_MATCH retries when PTT pressed`() {
        manager.isPushToTalkPressed = true
        manager.start()
        events.clear()
        fireOnError(SpeechRecognizer.ERROR_NO_MATCH)
        assertTrue(events.any { it.contains("ERROR_NO_MATCH") && it.contains("retry=true") })
    }

    @Test
    fun `ERROR_SPEECH_TIMEOUT retries when PTT pressed`() {
        manager.isPushToTalkPressed = true
        manager.start()
        events.clear()
        fireOnError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
        assertTrue(events.any { it.contains("ERROR_SPEECH_TIMEOUT") && it.contains("retry=true") })
    }

    @Test
    fun `ERROR_CLIENT retries when PTT pressed`() {
        manager.isPushToTalkPressed = true
        manager.start()
        events.clear()
        fireOnError(SpeechRecognizer.ERROR_CLIENT)
        assertTrue(events.any { it.contains("ERROR_CLIENT") && it.contains("retry=true") })
    }

    @Test
    fun `ERROR_RECOGNIZER_BUSY retries when PTT pressed`() {
        manager.isPushToTalkPressed = true
        manager.start()
        events.clear()
        fireOnError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
        assertTrue(events.any { it.contains("ERROR_RECOGNIZER_BUSY") && it.contains("retry=true") })
    }

    @Test
    fun `ERROR_NETWORK does not retry even when PTT pressed`() {
        manager.isPushToTalkPressed = true
        manager.start()
        events.clear()
        fireOnError(SpeechRecognizer.ERROR_NETWORK)
        assertTrue(events.any { it.contains("ERROR_NETWORK") && it.contains("retry=false") })
    }

    @Test
    fun `ERROR_NO_MATCH does not retry when PTT not pressed`() {
        manager.isPushToTalkPressed = false
        manager.start()
        events.clear()
        fireOnError(SpeechRecognizer.ERROR_NO_MATCH)
        assertTrue(events.any { it.contains("ERROR_NO_MATCH") && it.contains("retry=false") })
    }

    @Test
    fun `retry schedules start after delay and fires onRecognitionStart`() {
        manager.isPushToTalkPressed = true
        manager.start()
        events.clear()
        fireOnError(SpeechRecognizer.ERROR_NO_MATCH)
        // Advance past RECOGNIZER_RETRY_DELAY_MS (150ms)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        assertTrue(events.any { it == "start" })
    }

    @Test
    fun `retry does not fire if PTT released before delay elapses`() {
        manager.isPushToTalkPressed = true
        manager.start()
        fireOnError(SpeechRecognizer.ERROR_NO_MATCH)
        manager.isPushToTalkPressed = false
        manager.cancelRetry()
        events.clear()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        assertFalse(events.any { it == "start" })
    }

    // ── consumeFallback ───────────────────────────────────────────────────────

    @Test
    fun `consumeFallback returns true when listening`() {
        manager.start()
        assertTrue(manager.consumeFallback())
    }

    @Test
    fun `consumeFallback clears isListening`() {
        manager.start()
        manager.consumeFallback()
        assertFalse(manager.isListening)
    }

    @Test
    fun `consumeFallback returns false when not listening`() {
        assertFalse(manager.consumeFallback())
    }

    @Test
    fun `consumeFallback returns false on second call`() {
        manager.start()
        manager.consumeFallback()
        assertFalse(manager.consumeFallback())
    }

    // ── bestPartialRecognition ────────────────────────────────────────────────

    @Test
    fun `bestPartialRecognition returns null before any partial results`() {
        assertNull(manager.bestPartialRecognition())
    }

    @Test
    fun `bestPartialRecognition returns stable partial when no unstable`() {
        manager.start()
        val bundle = Bundle().apply {
            putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf("stable text"))
        }
        fireOnPartialResults(bundle)
        assertEquals("stable text", manager.bestPartialRecognition())
    }

    @Test
    fun `bestPartialRecognition prefers non-blank unstable over stable`() {
        manager.start()
        val bundle = Bundle().apply {
            putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf("stable"))
            putStringArrayList("android.speech.extra.UNSTABLE_TEXT", arrayListOf("unstable"))
        }
        fireOnPartialResults(bundle)
        assertEquals("unstable", manager.bestPartialRecognition())
    }

    @Test
    fun `bestPartialRecognition falls back to stable when unstable is blank`() {
        manager.start()
        val bundle = Bundle().apply {
            putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf("stable"))
            putStringArrayList("android.speech.extra.UNSTABLE_TEXT", arrayListOf("   "))
        }
        fireOnPartialResults(bundle)
        assertEquals("stable", manager.bestPartialRecognition())
    }

    @Test
    fun `bestPartialRecognition returns null when all partials are blank`() {
        manager.start()
        val bundle = Bundle().apply {
            putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(""))
            putStringArrayList("android.speech.extra.UNSTABLE_TEXT", arrayListOf(""))
        }
        fireOnPartialResults(bundle)
        assertNull(manager.bestPartialRecognition())
    }
}
