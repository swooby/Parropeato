package com.smartfoo.android.core.platform

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import androidx.annotation.RawRes
import androidx.core.content.res.ResourcesCompat
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale

@Suppress("unused")
object FooRes {
    fun getResources(context: Context): Resources {
        return context.resources
    }

    fun getConfiguration(context: Context): Configuration {
        return getResources(context).configuration
    }

    fun getDisplayMetrics(context: Context): DisplayMetrics {
        return getResources(context).displayMetrics
    }

    fun getString(context: Context, resId: Int, vararg formatArgs: Any?): String {
        return context.getString(resId, *formatArgs)
    }

    //@SuppressLint("NewApi", "ObsoleteSdkInt")
    fun getColor(res: Resources, resId: Int): Int {
        return res.getColor(resId, null)
    }

    //@SuppressLint("NewApi", "ObsoleteSdkInt")
    fun getDrawable(res: Resources, resId: Int): Drawable? {
        return ResourcesCompat.getDrawable(res, resId, null)
    }

    val systemResourcesDisplayMetricsHeightPixels: Int
        get() = Resources.getSystem().displayMetrics.heightPixels

    fun dip2px(context: Context, dpValue: Float): Int {
        val scale = getDisplayMetrics(context).density
        return (dpValue * scale + 0.5f).toInt()
    }

    //@SuppressLint("NewApi")
    fun getLocale(context: Context): Locale {
        val configuration = getConfiguration(context)
        return configuration.getLocales().get(0)
    }

    fun getOrientation(context: Context): Int {
        return getConfiguration(context).orientation
    }

    fun orientationToString(orientation: Int): String {
        @Suppress("DEPRECATION")
        return when (orientation) {
            Configuration.ORIENTATION_UNDEFINED -> "ORIENTATION_UNDEFINED"
            Configuration.ORIENTATION_PORTRAIT -> "ORIENTATION_PORTRAIT"
            Configuration.ORIENTATION_LANDSCAPE -> "ORIENTATION_LANDSCAPE"
            Configuration.ORIENTATION_SQUARE -> "ORIENTATION_SQUARE"
            else -> "UNKNOWN"
        }.let { "$it($orientation)" }
    }

    fun openRawResource(context: Context, @RawRes resId: Int): ByteArray? {
        val inputStream: InputStream?
        try {
            inputStream = getResources(context).openRawResource(resId)
        } catch (e: Resources.NotFoundException) {
            return null
        }

        val outputStream = ByteArrayOutputStream()

        var data: ByteArray?

        try {
            try {
                val bufferSize = 1024
                val buffer = ByteArray(bufferSize)
                var readSize: Int
                while ((inputStream.read(buffer, 0, bufferSize).also { readSize = it }) > 0) {
                    outputStream.write(buffer, 0, readSize)
                }
            } catch (e: IOException) {
                return null
            }

            data = outputStream.toByteArray()
        } finally {
            try {
                inputStream.close()
            } catch (e: IOException) {
                // ignore
            }

            try {
                outputStream.close()
            } catch (e: IOException) {
                // ignore
            }
        }

        return data
    }
}
