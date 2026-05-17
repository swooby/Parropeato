package com.swooby.parropeato

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

class ParropeatoAnalytics(context: Context) {
    private companion object {
        private const val ACTION_VOICE_ASSIST = "android.intent.action.VOICE_ASSIST"

        private const val EVENT_APP_LAUNCH = "parropeato_launch"
        private const val EVENT_BUTTON_SETTINGS_OPEN = "button_settings_open"

        private const val PARAM_ACTION = "action"
        private const val PARAM_ASSISTANT_ROLE = "assistant_role"
        private const val PARAM_METHOD = "method"
        private const val PARAM_SOURCE = "source"
        private const val PARAM_SUCCESS = "success"

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

    private fun logEvent(name: String, vararg params: Pair<String, String>) {
        val bundle = Bundle()
        params.forEach { (key, value) -> bundle.putString(key, value) }
        firebaseAnalytics.logEvent(name, bundle)
    }

    private fun String?.analyticsValue(): String {
        if (this == null) return SOURCE_UNKNOWN
        return take(100)
    }

    private fun assistantRoleState(): String {
        val roleManager = appContext.getSystemService(RoleManager::class.java) ?: return ROLE_UNAVAILABLE
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
            return ROLE_UNAVAILABLE
        }
        return if (roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) ROLE_HELD else ROLE_NOT_HELD
    }
}
