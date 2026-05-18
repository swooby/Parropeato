package com.swooby.parropeato

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ParropeatoViewModelTest {

    private lateinit var viewModel: ParropeatoViewModel

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        viewModel = ParropeatoViewModel(app)
    }

    // --- initial state ---

    @Test
    fun `initial state is Initializing`() {
        assertEquals(ParropeatoViewModel.State.Initializing, viewModel.state)
    }

    @Test
    fun `initial showSettings is false`() {
        assertFalse(viewModel.showSettings)
    }

    @Test
    fun `initial availableVoices is empty`() {
        assertTrue(viewModel.availableVoices.isEmpty())
    }

    // --- state transitions ---

    @Test
    fun `setting each State value does not throw`() {
        for (state in ParropeatoViewModel.State.entries) {
            viewModel.state = state
            assertEquals(state, viewModel.state)
        }
    }

    // --- voiceSpeed coercion ---

    @Test
    fun `voiceSpeed coerces value below min to VOICE_SPEED_MIN`() {
        viewModel.voiceSpeed = 0f
        assertEquals(VOICE_SPEED_MIN, viewModel.voiceSpeed)
    }

    @Test
    fun `voiceSpeed coerces value above max to VOICE_SPEED_MAX`() {
        viewModel.voiceSpeed = 100f
        assertEquals(VOICE_SPEED_MAX, viewModel.voiceSpeed)
    }

    @Test
    fun `voiceSpeed accepts value at min boundary`() {
        viewModel.voiceSpeed = VOICE_SPEED_MIN
        assertEquals(VOICE_SPEED_MIN, viewModel.voiceSpeed)
    }

    @Test
    fun `voiceSpeed accepts value at max boundary`() {
        viewModel.voiceSpeed = VOICE_SPEED_MAX
        assertEquals(VOICE_SPEED_MAX, viewModel.voiceSpeed)
    }

    @Test
    fun `voiceSpeed accepts value within range unchanged`() {
        viewModel.voiceSpeed = 1.5f
        assertEquals(1.5f, viewModel.voiceSpeed)
    }

    // --- voicePitch coercion ---

    @Test
    fun `voicePitch coerces value below min to VOICE_PITCH_MIN`() {
        viewModel.voicePitch = 0f
        assertEquals(VOICE_PITCH_MIN, viewModel.voicePitch)
    }

    @Test
    fun `voicePitch coerces value above max to VOICE_PITCH_MAX`() {
        viewModel.voicePitch = 100f
        assertEquals(VOICE_PITCH_MAX, viewModel.voicePitch)
    }

    @Test
    fun `voicePitch accepts value at min boundary`() {
        viewModel.voicePitch = VOICE_PITCH_MIN
        assertEquals(VOICE_PITCH_MIN, viewModel.voicePitch)
    }

    @Test
    fun `voicePitch accepts value at max boundary`() {
        viewModel.voicePitch = VOICE_PITCH_MAX
        assertEquals(VOICE_PITCH_MAX, viewModel.voicePitch)
    }

    @Test
    fun `voicePitch accepts value within range unchanged`() {
        viewModel.voicePitch = 1.0f
        assertEquals(1.0f, viewModel.voicePitch)
    }

    // --- volumePercent coercion ---

    @Test
    fun `volumePercent coerces negative value to 0`() {
        viewModel.volumePercent = -1f
        assertEquals(0f, viewModel.volumePercent)
    }

    @Test
    fun `volumePercent coerces value above 1 to 1`() {
        viewModel.volumePercent = 2f
        assertEquals(1f, viewModel.volumePercent)
    }

    @Test
    fun `volumePercent accepts 0`() {
        viewModel.volumePercent = 0f
        assertEquals(0f, viewModel.volumePercent)
    }

    @Test
    fun `volumePercent accepts 1`() {
        viewModel.volumePercent = 1f
        assertEquals(1f, viewModel.volumePercent)
    }

    @Test
    fun `volumePercent accepts value within range unchanged`() {
        viewModel.volumePercent = 0.5f
        assertEquals(0.5f, viewModel.volumePercent)
    }

    @Test
    fun `text setter updates value directly`() {
        viewModel.text = "Direct"
        assertEquals("Direct", viewModel.text)
    }

    // --- state + text consistency ---

    @Test
    fun `state setter updates text to match new state`() {
        viewModel.state = ParropeatoViewModel.State.Listening
        assertEquals(ParropeatoViewModel.State.Listening, viewModel.state)
        assertTrue("text should be non-blank after state change, got: ${viewModel.text}", viewModel.text.isNotBlank())
    }
}
