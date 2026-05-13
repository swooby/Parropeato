package com.swooby.parropeato

import kotlin.reflect.KClass
import kotlin.reflect.full.staticProperties

object ReflectionUtils {
    @Suppress("unused")
    private val TAG = TAG(ReflectionUtils::class)

    fun getMapOfIntFieldsToNames(clazz: KClass<*>, startsWith: String?): Map<Int, String> {
        return clazz.staticProperties.filter {
            (it.returnType.classifier == Integer::class) && (startsWith.isNullOrBlank() || it.name.startsWith(startsWith))
        }.associate {
            it.getter.call() as Int to it.name
        }
    }

    fun valueToString(map: Map<Int, String>, value: Int): String {
        return map.getOrDefault(value, "UNKNOWN") + "($value)"
    }

    @Suppress("FunctionName", "MemberVisibilityCanBePrivate", "unused")
    fun TAG(obj: Any): String = getShortClassName(obj)

    @Suppress("FunctionName", "MemberVisibilityCanBePrivate")
    fun <T : Any> TAG(clazz: KClass<T>): String = getShortClassName(clazz)

    @Suppress("MemberVisibilityCanBePrivate")
    fun getShortClassName(obj: Any): String = getShortClassName(clazzName = obj.javaClass.simpleName)

    @Suppress("MemberVisibilityCanBePrivate")
    fun <T : Any> getShortClassName(clazz: KClass<T>): String = getShortClassName(clazz.simpleName)

    @Suppress("MemberVisibilityCanBePrivate")
    fun getShortClassName(clazzName: String?): String = clazzName ?: "null"
}
