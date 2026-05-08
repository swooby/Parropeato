package com.smartfoo.android.core.view

import android.view.View

object FooViewUtils {
    fun viewVisibilityToString(visibility: Int): String {
        return when (visibility) {
            View.VISIBLE -> "VISIBLE"
            View.INVISIBLE -> "INVISIBLE"
            View.GONE -> "GONE"
            else -> "UNKNOWN"
        }.let { "$it($visibility))" }
    }
}
