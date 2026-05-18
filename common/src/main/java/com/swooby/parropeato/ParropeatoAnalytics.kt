package com.swooby.parropeato

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlin.math.roundToInt

/**
 * Privacy-preserving diagnostics bridge for Firebase Analytics and Crashlytics.
 *
 * Diagnostics are disabled by default and must only be enabled when the user opts in.
 * [setDiagnosticsCollectionEnabled] controls both Analytics and Crashlytics collection.
 *
 * Do not log or attach raw speech audio, recognized speech text, text spoken by TTS,
 * custom user-entered content, stable identifiers added by app code, exact selected
 * speech locale tags, exact selected or previewed TTS voice names, or exact slider values.
 * Use coarse enums and buckets instead.
 *
 * Analytics events:
 *
 * | Event | Meaning | Parameters |
 * |-------|---------|------------|
 * | `parropeato_launch` | App launch source was observed. | `source`, `action`, `assistant_role` |
 * | `diagnostics_consent_accept` | User accepted diagnostics from the first-run prompt. | `source` |
 * | `diagnostics_setting_changed` | User enabled or disabled diagnostics from the prompt or Settings. | `enabled`, `source` |
 * | `settings_open` | Settings overlay opened. | `platform` |
 * | `settings_close` | Settings overlay closed. | `platform` |
 * | `settings_screen_open` | User opened a Settings sub-screen. | `screen` |
 * | `setting_changed` | User changed an app setting. | `name`, `value`, `unit` |
 * | `mic_permission_result` | Microphone permission request completed. | `result`, `rationale_shown` |
 * | `speech_session_start` | Push-to-talk session started. | `input`, `mode`, `network`, `offline_model` |
 * | `speech_session_end` | Push-to-talk session ended. | `outcome`, `duration_bucket` |
 * | `stt_error` | Speech recognizer returned an error. | `error_class`, `retry` |
 * | `tts_init` | Text-to-speech engine initialized or failed. | `success`, `error_class` |
 * | `tts_speak` | App submitted greeting, preview, or recognized speech to TTS. | `source`, `success` |
 * | `tts_voice_preview` | User tapped a voice preview. | `mode` |
 * | `external_settings_open` | App tried to open Android TTS, speech-download, or button-gesture settings. | `target`, `success`, `fallback` |
 * | `button_settings_open` | Wear button-settings fallback attempt completed. | `method`, `success` |
 *
 * Coarse setting values:
 *
 * - Accent color is logged as a color category, such as `purple`, `teal`, `blue`,
 *   `green`, `yellow`, `orange`, `pink`, `red`, or `custom`.
 * - Cute icons are logged as `true` or `false`.
 * - Speech locale and TTS voice are logged as `device_default` or `specific`.
 * - Volume, voice speed, and voice pitch are logged as buckets, not exact values.
 *
 * Speech session values:
 *
 * - `speech_session_start.input`: `touch` or `hardware`.
 * - `speech_session_start.mode`: `device_default` or `specific`.
 * - `speech_session_start.network`: `online` or `offline`.
 * - `speech_session_start.offline_model`: `available`, `missing`, `not_applicable`,
 *   or `unknown`.
 * - `speech_session_end.outcome`: `success`, `fallback_partial`, `no_match`,
 *   `offline_model_missing`, `permission_denied`, `cancelled`, or `error`.
 * - `speech_session_end.duration_bucket`: `lt_1s`, `1_3s`, `3_10s`, `10_30s`,
 *   or `gte_30s`.
 *
 * Crashlytics can receive exception type, message, stack trace, app version/build,
 * package and process details, OS version, device model/class information supplied by
 * Firebase, thread state, crash timestamp, and Firebase session metadata. Parropeato
 * must not attach raw speech, recognized text, TTS text, custom user content, exact
 * locale selections, or exact TTS voice names to Crashlytics reports.
 *
 * Intentional gaps: the debug-only Test Crash button is covered by Crashlytics testing,
 * not Analytics; debug-only App Info opens are not logged; unsupported Wear stem buttons
 * 2 and 3 are consumed but not logged because they do not trigger a user-visible feature.
 */
class ParropeatoAnalytics(context: Context) {
    private companion object {
        private const val ACTION_VOICE_ASSIST = "android.intent.action.VOICE_ASSIST"

        private const val EVENT_APP_LAUNCH = "parropeato_launch"
        private const val EVENT_BUTTON_SETTINGS_OPEN = "button_settings_open"
        private const val EVENT_DIAGNOSTICS_CONSENT_ACCEPT = "diagnostics_consent_accept"
        private const val EVENT_DIAGNOSTICS_SETTING_CHANGED = "diagnostics_setting_changed"
        private const val EVENT_EXTERNAL_SETTINGS_OPEN = "external_settings_open"
        private const val EVENT_MIC_PERMISSION_RESULT = "mic_permission_result"
        private const val EVENT_SETTINGS_CLOSE = "settings_close"
        private const val EVENT_SETTINGS_OPEN = "settings_open"
        private const val EVENT_SETTINGS_SCREEN_OPEN = "settings_screen_open"
        private const val EVENT_SPEECH_SESSION_END = "speech_session_end"
        private const val EVENT_SPEECH_SESSION_START = "speech_session_start"
        private const val EVENT_STT_ERROR = "stt_error"
        private const val EVENT_TTS_INIT = "tts_init"
        private const val EVENT_TTS_SPEAK = "tts_speak"
        private const val EVENT_TTS_VOICE_PREVIEW = "tts_voice_preview"
        private const val EVENT_SETTING_CHANGED = "setting_changed"

        private const val PARAM_ACTION = "action"
        private const val PARAM_ASSISTANT_ROLE = "assistant_role"
        private const val PARAM_DURATION_BUCKET = "duration_bucket"
        private const val PARAM_ENABLED = "enabled"
        private const val PARAM_ERROR_CLASS = "error_class"
        private const val PARAM_FALLBACK = "fallback"
        private const val PARAM_INPUT = "input"
        private const val PARAM_METHOD = "method"
        private const val PARAM_MODE = "mode"
        private const val PARAM_NAME = "name"
        private const val PARAM_NETWORK = "network"
        private const val PARAM_OFFLINE_MODEL = "offline_model"
        private const val PARAM_OUTCOME = "outcome"
        private const val PARAM_PLATFORM = "platform"
        private const val PARAM_RATIONALE_SHOWN = "rationale_shown"
        private const val PARAM_RESULT = "result"
        private const val PARAM_RETRY = "retry"
        private const val PARAM_SCREEN = "screen"
        private const val PARAM_SOURCE = "source"
        private const val PARAM_SUCCESS = "success"
        private const val PARAM_TARGET = "target"
        private const val PARAM_UNIT = "unit"
        private const val PARAM_VALUE = "value"

        private const val ROLE_HELD = "held"
        private const val ROLE_NOT_HELD = "not_held"
        private const val ROLE_UNAVAILABLE = "unavailable"

        private const val SOURCE_ASSIST = "assist"
        private const val SOURCE_LAUNCHER = "launcher"
        private const val SOURCE_OTHER = "other"
        private const val SOURCE_UNKNOWN = "unknown"
        private const val SOURCE_VOICE_ASSIST = "voice_assist"
    }

    enum class ButtonSettingsMethod(val analyticsValue: String) {
        GeneralSettings("general_settings"),
        SamsungAction("samsung_action"),
        SamsungComponent("samsung_component"),
    }

    enum class ConsentSource(val analyticsValue: String) {
        FirstRunPrompt("first_run_prompt"),
        Settings("settings"),
    }

    enum class ExternalSettingsTarget(val analyticsValue: String) {
        ButtonGestures("button_gestures"),
        SpeechDownload("speech_download"),
        Tts("tts"),
    }

    enum class InputSource(val analyticsValue: String) {
        Hardware("hardware"),
        Touch("touch"),
    }

    enum class LocaleMode(val analyticsValue: String) {
        DeviceDefault("device_default"),
        Specific("specific"),
    }

    enum class NetworkState(val analyticsValue: String) {
        Offline("offline"),
        Online("online"),
    }

    enum class OfflineModelState(val analyticsValue: String) {
        Available("available"),
        Missing("missing"),
        NotApplicable("not_applicable"),
        Unknown("unknown"),
    }

    enum class PermissionResult(val analyticsValue: String) {
        Denied("denied"),
        Granted("granted"),
    }

    enum class SettingName(val analyticsValue: String) {
        AccentColor("accent_color"),
        CuteIcons("cute_icons"),
        Diagnostics("diagnostics"),
        SpeechLocale("speech_locale"),
        TtsVoice("tts_voice"),
        VoicePitch("voice_pitch"),
        VoiceSpeed("voice_speed"),
        Volume("volume"),
    }

    enum class SettingsScreen(val analyticsValue: String) {
        AccentColor("accent_color"),
        Main("main"),
        SpeechLanguage("speech_language"),
        SpeechVariant("speech_variant"),
        TtsLanguage("tts_language"),
        TtsVariant("tts_variant"),
    }

    enum class SpeechOutcome(val analyticsValue: String) {
        Cancelled("cancelled"),
        Error("error"),
        FallbackPartial("fallback_partial"),
        NoMatch("no_match"),
        OfflineModelMissing("offline_model_missing"),
        PermissionDenied("permission_denied"),
        Success("success"),
    }

    enum class TtsSource(val analyticsValue: String) {
        Greeting("greeting"),
        Preview("preview"),
        Recognition("recognition"),
    }

    private val appContext = context.applicationContext
    private val firebaseAnalytics: FirebaseAnalytics by lazy {
        FirebaseAnalytics.getInstance(appContext)
    }
    private val firebaseCrashlytics: FirebaseCrashlytics by lazy {
        FirebaseCrashlytics.getInstance()
    }

    fun setDiagnosticsCollectionEnabled(enabled: Boolean) {
        firebaseAnalytics.setAnalyticsCollectionEnabled(enabled)
        firebaseCrashlytics.setCrashlyticsCollectionEnabled(enabled)
    }

    fun logLaunchIntent(intent: Intent?) {
        val action = intent?.action
        val source = when (action) {
            Intent.ACTION_MAIN -> SOURCE_LAUNCHER
            Intent.ACTION_ASSIST -> SOURCE_ASSIST
            ACTION_VOICE_ASSIST -> SOURCE_VOICE_ASSIST
            null -> SOURCE_UNKNOWN
            else -> SOURCE_OTHER
        }
        logEvent(
            EVENT_APP_LAUNCH,
            PARAM_SOURCE to source,
            PARAM_ACTION to action.analyticsValue(),
            PARAM_ASSISTANT_ROLE to assistantRoleState(),
        )
    }

    fun logButtonSettingsOpen(method: ButtonSettingsMethod, success: Boolean) {
        logEvent(
            EVENT_BUTTON_SETTINGS_OPEN,
            PARAM_METHOD to method.analyticsValue,
            PARAM_SUCCESS to if (success) "true" else "false",
        )
    }

    fun logDiagnosticsConsentAccept(source: ConsentSource) {
        logEvent(EVENT_DIAGNOSTICS_CONSENT_ACCEPT, PARAM_SOURCE to source.analyticsValue)
    }

    fun logDiagnosticsSettingChanged(enabled: Boolean, source: ConsentSource) {
        logEvent(
            EVENT_DIAGNOSTICS_SETTING_CHANGED,
            PARAM_ENABLED to enabled.analyticsValue(),
            PARAM_SOURCE to source.analyticsValue,
        )
    }

    fun logExternalSettingsOpen(target: ExternalSettingsTarget, success: Boolean, fallback: String? = null) {
        logEvent(
            EVENT_EXTERNAL_SETTINGS_OPEN,
            PARAM_TARGET to target.analyticsValue,
            PARAM_SUCCESS to success.analyticsValue(),
            PARAM_FALLBACK to fallback.analyticsValue(),
        )
    }

    fun logMicPermissionResult(result: PermissionResult, rationaleShown: Boolean) {
        logEvent(
            EVENT_MIC_PERMISSION_RESULT,
            PARAM_RESULT to result.analyticsValue,
            PARAM_RATIONALE_SHOWN to rationaleShown.analyticsValue(),
        )
    }

    fun logSettingChanged(name: SettingName, value: String? = null, unit: String? = null) {
        logEvent(
            EVENT_SETTING_CHANGED,
            PARAM_NAME to name.analyticsValue,
            PARAM_VALUE to value.analyticsValue(),
            PARAM_UNIT to unit.analyticsValue(),
        )
    }

    fun logSettingsOpen(platform: String) {
        logEvent(EVENT_SETTINGS_OPEN, PARAM_PLATFORM to platform.analyticsValue())
    }

    fun logSettingsClose(platform: String) {
        logEvent(EVENT_SETTINGS_CLOSE, PARAM_PLATFORM to platform.analyticsValue())
    }

    fun logSettingsScreenOpen(screen: SettingsScreen) {
        logEvent(EVENT_SETTINGS_SCREEN_OPEN, PARAM_SCREEN to screen.analyticsValue)
    }

    fun logSpeechSessionStart(
        input: InputSource,
        localeMode: LocaleMode,
        network: NetworkState,
        offlineModel: OfflineModelState,
    ) {
        logEvent(
            EVENT_SPEECH_SESSION_START,
            PARAM_INPUT to input.analyticsValue,
            PARAM_MODE to localeMode.analyticsValue,
            PARAM_NETWORK to network.analyticsValue,
            PARAM_OFFLINE_MODEL to offlineModel.analyticsValue,
        )
    }

    fun logSpeechSessionEnd(outcome: SpeechOutcome, durationMs: Long?) {
        logEvent(
            EVENT_SPEECH_SESSION_END,
            PARAM_OUTCOME to outcome.analyticsValue,
            PARAM_DURATION_BUCKET to durationMs.durationBucket(),
        )
    }

    fun logSttError(errorClass: String, retry: Boolean) {
        logEvent(
            EVENT_STT_ERROR,
            PARAM_ERROR_CLASS to errorClass.analyticsValue(),
            PARAM_RETRY to retry.analyticsValue(),
        )
    }

    fun logTtsInit(success: Boolean, errorClass: String? = null) {
        logEvent(
            EVENT_TTS_INIT,
            PARAM_SUCCESS to success.analyticsValue(),
            PARAM_ERROR_CLASS to errorClass.analyticsValue(),
        )
    }

    fun logTtsSpeak(source: TtsSource, success: Boolean) {
        logEvent(
            EVENT_TTS_SPEAK,
            PARAM_SOURCE to source.analyticsValue,
            PARAM_SUCCESS to success.analyticsValue(),
        )
    }

    fun logTtsVoicePreview(mode: LocaleMode) {
        logEvent(EVENT_TTS_VOICE_PREVIEW, PARAM_MODE to mode.analyticsValue)
    }

    fun localeMode(value: String?): LocaleMode =
        if (value == null) LocaleMode.DeviceDefault else LocaleMode.Specific

    fun offlineModelState(locale: String?, hasOfflineModel: Boolean): OfflineModelState =
        when {
            locale == null -> OfflineModelState.NotApplicable
            hasOfflineModel -> OfflineModelState.Available
            else -> OfflineModelState.Missing
        }

    fun accentColorValue(argb: Int): String =
        when (ACCENT_COLOR_OPTIONS.indexOfFirst { it.argb == argb }) {
            0 -> "purple"
            1 -> "teal"
            2 -> "blue"
            3 -> "green"
            4 -> "yellow"
            5 -> "orange"
            6 -> "pink"
            7 -> "red"
            else -> "custom"
        }

    fun percentBucket(value: Float): String {
        val percent = (value.coerceIn(0f, 1f) * 100).roundToInt()
        return when {
            percent <= 0 -> "0"
            percent <= 25 -> "1_25"
            percent <= 50 -> "26_50"
            percent <= 75 -> "51_75"
            percent < 100 -> "76_99"
            else -> "100"
        }
    }

    fun multiplierBucket(value: Float): String = when {
        value < 0.5f -> "lt_0_5"
        value < 1.0f -> "0_5_1_0"
        value == 1.0f -> "1_0"
        value <= 1.5f -> "1_0_1_5"
        value <= 2.0f -> "1_5_2_0"
        else -> "gt_2_0"
    }

    private fun logEvent(name: String, vararg params: Pair<String, String>) {
        val bundle = Bundle()
        params.forEach { (key, value) -> bundle.putString(key, value) }
        firebaseAnalytics.logEvent(name, bundle)
    }

    private fun String?.analyticsValue(): String {
        if (this == null) return SOURCE_UNKNOWN
        return take(100)
    }

    private fun Boolean.analyticsValue(): String = if (this) "true" else "false"

    private fun Long?.durationBucket(): String = when {
        this == null -> SOURCE_UNKNOWN
        this < 1_000L -> "lt_1s"
        this < 3_000L -> "1_3s"
        this < 10_000L -> "3_10s"
        this < 30_000L -> "10_30s"
        else -> "gte_30s"
    }

    private fun assistantRoleState(): String {
        val roleManager = appContext.getSystemService(RoleManager::class.java) ?: return ROLE_UNAVAILABLE
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
            return ROLE_UNAVAILABLE
        }
        return if (roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) ROLE_HELD else ROLE_NOT_HELD
    }
}
