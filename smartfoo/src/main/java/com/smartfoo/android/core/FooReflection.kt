package com.smartfoo.android.core

import kotlin.reflect.KClass

@Suppress("unused")
object FooReflection {

    @JvmStatic
    fun getClass(o: Any?) = o?.javaClass

    @JvmStatic
    fun getClass(c: Class<*>) = c

    @JvmStatic
    fun getClass(c: KClass<*>) = c.java

    /**
     * Returns the full or short class name.
     * Overloads handle KClass, Class, and Any.
     */
    @JvmStatic
    @JvmOverloads
    fun getClassName(o: Any?, short: Boolean = true): String =
        getClassName(getClass(o)?.name, short)

    @JvmStatic
    @JvmOverloads
    fun getClassName(c: Class<*>?, short: Boolean = true): String =
        getClassName(c?.name, short)

    @JvmStatic
    @JvmOverloads
    fun getClassName(c: KClass<*>?, short: Boolean = true): String =
        getClassName(c?.java?.name, short)

    /**
     * Base logic for string manipulation
     */
    @JvmStatic
    fun getClassName(className: String?, shortClassName: Boolean): String {
        val name = className ?: return "null"
        return if (shortClassName) name.substringAfterLast('.') else name
    }

    /**
     * Convenience methods for "Short" names to satisfy Java callers
     * and maintain a clean API.
     */
    @JvmStatic
    fun getShortClassName(o: Any?): String = getClassName(o, true)

    @JvmStatic
    fun getShortClassName(c: Class<*>?): String = getClassName(c, true)

    @JvmStatic
    fun getShortClassName(c: KClass<*>?): String = getClassName(c, true)

    @JvmStatic
    fun getShortClassName(className: String?): String = getClassName(className, true)

    @JvmStatic
    fun mapConstants(clazz: KClass<*>, vararg prefixes: String): Map<Int, String> =
        mapConstants(clazz.java, *prefixes)

    /***
     * Dynamically maps integer constant values to their field names using reflection.
     *
     * @param clazz The class to inspect (e.g., NotificationListenerService::class.java)
     * @param prefixes The constant prefix to look for (e.g., "REASON_")
     */
    @JvmStatic
    fun mapConstants(clazz: Class<*>, vararg prefixes: String): Map<Int, String> {
        return clazz.fields
            .filter { field ->
                prefixes.any { prefix -> field.name.startsWith(prefix) } &&
                        field.type == Int::class.javaPrimitiveType
            }
            .associate { field ->
                val value = runCatching { field.get(null) as Int }.getOrDefault(-1)
                value to field.name
            }
    }

    @JvmStatic
    fun toString(map: Map<Int, String>, value: Int, asFlags: Boolean = false): String {
        if (asFlags) {
            val joined = map.entries
                .filter { (key, _) -> (value and key) != 0 }
                .joinToString("|") { (key, name) -> "$name($key)" }
            return joined.ifEmpty { "0($value)" }
        } else {
            return (map[value] ?: "UNKNOWN").let { "$it($value)" }
        }
    }
}
