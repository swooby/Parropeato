package com.swooby.ropeato

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

class RopeatoApp : Application(), ViewModelStoreOwner {
    private val appViewModelStore: ViewModelStore = ViewModelStore()

    override val viewModelStore: ViewModelStore
        get() = appViewModelStore
}
