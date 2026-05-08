package com.smartfoo.android.core.media

import android.content.Context
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import com.smartfoo.android.core.FooReflection
import com.smartfoo.android.core.FooString
import com.smartfoo.android.core.R
import com.smartfoo.android.core.logging.FooLog
import kotlin.math.roundToInt

@Suppress("unused")
object FooAudioUtils {
    private val TAG = FooLog.TAG(FooAudioUtils::class)

    @JvmStatic
    val audioStreamTypes by lazy {
        FooReflection.mapConstants(AudioManager::class, "STREAM_")
            .toMutableMap()
            .apply {
                /** [android.media.AudioManager.STREAM_BLUETOOTH_SCO] is hidden */
                put(6, "STREAM_BLUETOOTH_SCO")
                /** [android.media.AudioManager.STREAM_SYSTEM_ENFORCED] is hidden */
                put(7, "STREAM_SYSTEM_ENFORCED")
                /** [android.media.AudioManager.STREAM_TTS] is hidden */
                put(9, "STREAM_TTS")
            }
    }

    @JvmStatic
    fun audioStreamTypeToString(audioStreamType: Int) =
        audioStreamTypeToString(null, audioStreamType)

    @JvmStatic
    fun audioStreamTypeToString(
        context: Context?,
        audioStreamType: Int,
    ): String {
        val s = if (context != null) {
            when (audioStreamType) {
                AudioManager.STREAM_VOICE_CALL -> context.getString(R.string.audio_stream_voice_call)
                AudioManager.STREAM_SYSTEM -> context.getString(R.string.audio_stream_system)
                AudioManager.STREAM_RING -> context.getString(R.string.audio_stream_ring)
                AudioManager.STREAM_MUSIC -> context.getString(R.string.audio_stream_media)
                AudioManager.STREAM_ALARM -> context.getString(R.string.audio_stream_alarm)
                AudioManager.STREAM_NOTIFICATION -> context.getString(R.string.audio_stream_notification)
                /** [android.media.AudioManager.STREAM_BLUETOOTH_SCO] is hidden */
                6 -> context.getString(R.string.audio_stream_bluetooth_sco)
                /** [android.media.AudioManager.STREAM_SYSTEM_ENFORCED] is hidden */
                7 -> context.getString(R.string.audio_stream_system_enforced)
                AudioManager.STREAM_DTMF -> context.getString(R.string.audio_stream_dtmf)
                /** [android.media.AudioManager.STREAM_TTS] is hidden */
                9 -> context.getString(R.string.audio_stream_text_to_speech)
                else -> context.getString(R.string.audio_stream_unknown)
            }
        } else null
        return s ?: FooReflection.toString(audioStreamTypes, audioStreamType)
    }

    @JvmStatic
    val audioFocusMap by lazy {
        FooReflection.mapConstants(AudioManager::class, "AUDIOFOCUS_NONE", "AUDIOFOCUS_GAIN", "AUDIOFOCUS_LOSS")
    }

    @JvmStatic
    fun audioFocusGainLossToString(audioFocusGainLoss: Int) =
        FooReflection.toString(audioFocusMap, audioFocusGainLoss)

    @JvmStatic
    val audioFocusRequestMap by lazy {
        FooReflection.mapConstants(AudioManager::class, "AUDIOFOCUS_REQUEST_")
    }

    @JvmStatic
    fun audioFocusRequestToString(audioFocusRequest: Int) =
        FooReflection.toString(audioFocusRequestMap, audioFocusRequest)

    @JvmStatic
    fun getVolumePercentFromAbsolute(
        audioManager: AudioManager,
        audioStreamType: Int,
        volume: Int,
    ): Float {
        val volumeMax = audioManager.getStreamMaxVolume(audioStreamType)
        return volume / volumeMax.toFloat()
    }

    @JvmStatic
    fun getVolumeAbsoluteFromPercent(
        audioManager: AudioManager,
        audioStreamType: Int,
        volumePercent: Float,
    ): Int {
        val volumeMax = audioManager.getStreamMaxVolume(audioStreamType)
        return (volumeMax * volumePercent).roundToInt()
    }

    @JvmStatic
    fun getVolumeAbsolute(
        audioManager: AudioManager,
        audioStreamType: Int,
    ) = audioManager.getStreamVolume(audioStreamType)

    @JvmStatic
    fun getVolumePercent(
        audioManager: AudioManager,
        audioStreamType: Int,
    ): Float {
        val volume = getVolumeAbsolute(audioManager, audioStreamType)
        return getVolumePercentFromAbsolute(audioManager, audioStreamType, volume)
    }

    @JvmStatic
    fun getRingtone(
        context: Context?,
        ringtoneUri: Uri?,
    ): Ringtone? {
        if (FooString.isNullOrEmpty(FooString.toString(ringtoneUri))) {
            return null
        }
        return RingtoneManager.getRingtone(context, ringtoneUri)
    }
}
