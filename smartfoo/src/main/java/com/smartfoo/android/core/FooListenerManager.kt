package com.smartfoo.android.core

import com.smartfoo.android.core.logging.FooLog
import java.util.Collections

@Suppress("unused")
open class FooListenerManager<T>(name: String) {
    companion object {
        private val TAG = FooLog.TAG(FooListenerManager::class)

        @Suppress("SimplifyBooleanWithConstants", "KotlinConstantConditions", "RedundantSuppression", "UNREACHABLE_CODE")
        private val VERBOSE_LOG = false && BuildConfig.DEBUG
    }

    constructor(name: Any) : this(FooReflection.getShortClassName(name))

    private val name = FooString.quote(name.trim())
    private val listeners = mutableSetOf<T>()
    private val listenersToAdd = mutableSetOf<T>()
    private val listenersToRemove = mutableSetOf<T>()

    private var isTraversingListeners = false

    override fun toString() = "{ name=$name, size()=${size()} }"

    fun size(): Int {
        val size: Int
        synchronized(listeners) {
            val consolidated: MutableSet<T> = LinkedHashSet(listeners)
            consolidated.addAll(listenersToAdd)
            consolidated.removeAll(listenersToRemove)
            size = consolidated.size
        }
        /*
        if (VERBOSE_LOG)
        {
            FooLog.v(TAG, mName + " size() == " + size);
        }
        */
        return size
    }

    val isEmpty: Boolean
        get() = size() == 0

    fun hasListener(listener: T): Boolean {
        synchronized(listeners) {
            return listenersToAdd.contains(listener) ||
                listeners.contains(listener) ||
                listenersToRemove.contains(listener)
        }
    }

    fun attach(listener: T?) {
        if (VERBOSE_LOG) {
            FooLog.v(TAG, "$name attach(...)")
        }

        if (listener == null) {
            return
        }

        synchronized(listeners) {
            if (hasListener(listener)) {
                return
            }
            if (isTraversingListeners) {
                listenersToAdd.add(listener)
            } else {
                listeners.add(listener)
                updateListeners()
            }
        }
    }

    fun detach(listener: T?) {
        if (VERBOSE_LOG) {
            FooLog.v(TAG, "$name detach(...)")
        }

        if (listener == null) {
            return
        }

        synchronized(listeners) {
            if (isTraversingListeners) {
                listenersToRemove.add(listener)
            } else {
                listeners.remove(listener)
                updateListeners()
            }
        }
    }

    fun clear() {
        if (VERBOSE_LOG) {
            FooLog.v(TAG, "$name clear()")
        }
        synchronized(listeners) {
            listenersToAdd.clear()
            if (isTraversingListeners) {
                listenersToRemove.addAll(listeners)
            } else {
                listeners.clear()
                listenersToRemove.clear()
            }
        }
    }

    fun beginTraversing(): Set<T> {
        if (VERBOSE_LOG) {
            FooLog.v(TAG, "$name beginTraversing()")
        }
        synchronized(listeners) {
            isTraversingListeners = true
            return Collections.unmodifiableSet(listeners)
        }
    }

    fun endTraversing() {
        if (VERBOSE_LOG) {
            FooLog.v(TAG, "$name endTraversing()")
        }
        synchronized(listeners) {
            updateListeners()
            isTraversingListeners = false
        }
    }

    private fun updateListeners() {
        if (VERBOSE_LOG) {
            FooLog.v(TAG, "$name updateListeners()")
        }
        synchronized(listeners) {
            var it = listenersToAdd.iterator()
            while (it.hasNext()) {
                listeners.add(it.next())
                it.remove()
            }
            it = listenersToRemove.iterator()
            while (it.hasNext()) {
                listeners.remove(it.next())
                it.remove()
            }
            onListenersUpdated(listeners.size)
        }
    }

    protected open fun onListenersUpdated(listenersSize: Int) {
    }
}
