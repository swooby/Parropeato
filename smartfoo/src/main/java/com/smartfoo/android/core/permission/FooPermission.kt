package com.smartfoo.android.core.permission

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

object FooPermission {
    fun isCallStatePermissionGranted(context: Context) =
        ContextCompat
            .checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
                PackageManager.PERMISSION_GRANTED

    fun isIgnoringBatteryOptimizations(context: Context) =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(context.packageName)

    @SuppressLint("BatteryLife")
    fun intentRequestIgnoreBatteryOptimizations(context: Context) =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData("package:${context.packageName}".toUri())

    @SuppressLint("BatteryLife")
    fun startActivityIgnoreBatteryOptimizations(context: Context) {
        if (isIgnoringBatteryOptimizations(context)) {
            //startActivityIgnoreBatteryOptimizationSettings(context) // this is the recommended way, but it is harder to access
            startActivityAppInfo(context) // this is not the recommended way, but it is easier to access
        } else {
            context.startActivity(
                intentRequestIgnoreBatteryOptimizations(context)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun intentIgnoreBatteryOptimizationSettings() =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)


    fun startActivityIgnoreBatteryOptimizationSettings(context: Context) =
        context.startActivity(
            intentIgnoreBatteryOptimizationSettings()
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    fun intentAppInfo(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData("package:${context.packageName}".toUri())

    fun startActivityAppInfo(context: Context) =
        context.startActivity(
            intentAppInfo(context)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    fun isAccessibilityServiceEnabled(
        context: Context,
        serviceComponent: ComponentName,
    ): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val me = serviceComponent.flattenToString()
        return enabled.split(':').any { it.equals(me, ignoreCase = true) }
    }

    fun intentOpenAccessibilitySettings(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
}