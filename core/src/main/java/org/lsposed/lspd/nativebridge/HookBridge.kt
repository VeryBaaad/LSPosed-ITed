package org.lsposed.lspd.nativebridge

import dalvik.annotation.optimization.FastNative
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

object HookBridge {
    @JvmStatic
    external fun hookMethod(
        hookMethod: Executable,
        hooker: Class<*>,
        priority: Int,
        callback: Any
    ): Boolean

    @JvmStatic
    external fun unhookMethod(hookMethod: Executable, callback: Any): Boolean

    @JvmStatic
    external fun deoptimizeMethod(method: Executable): Boolean

    @JvmStatic
    @Throws(InstantiationException::class)
    external fun <T> allocateObject(clazz: Class<T>): T

    @JvmStatic
    @Throws(InstantiationException::class)
    external fun <T> allocateSpecialReceiver(constructor: Constructor<*>, clazz: Class<T>): T

    @JvmStatic
    external fun findClassInitializer(clazz: Class<*>): Method

    @JvmStatic
    @Throws(
        IllegalAccessException::class,
        IllegalArgumentException::class,
        InvocationTargetException::class
    )
    external fun invokeOriginalMethod(
        method: Executable,
        thisObject: Any,
        args: Array<out Any>,
        isConstructor: Boolean
    ): Any

    @Throws(
        IllegalAccessException::class,
        java.lang.IllegalArgumentException::class,
        InvocationTargetException::class
    )

    @JvmStatic
    fun invokeOriginalMethod(
        method: Executable,
        thisObject: Any,
        vararg args: Any
    ): Any =
        invokeOriginalMethod(method, thisObject, args, method is Constructor<*>)

    @JvmStatic
    @Throws(
        IllegalAccessException::class,
        IllegalArgumentException::class,
        InvocationTargetException::class,
        InstantiationException::class
    )
    external fun <T> invokeSpecialMethod(
        method: Executable,
        clazz: Class<T>?,
        thisObject: Any,
        vararg args: Any
    ): Any

    @JvmStatic
    @Throws(
        IllegalAccessException::class,
        IllegalArgumentException::class,
        InvocationTargetException::class,
        InstantiationException::class
    )
    fun invokeSpecialMethod(method: Executable, thisObject: Any, vararg args: Any): Any =
        invokeSpecialMethod<Any>(method, null, thisObject, *args)

    @JvmStatic
    @FastNative
    external fun instanceOf(obj: Any, clazz: Class<*>): Boolean

    @JvmStatic
    @FastNative
    external fun gettid(): Int

    @JvmStatic
    @FastNative
    external fun setTrusted(cookie: Any): Boolean

    @JvmStatic
    external fun callbackSnapshot(method: Executable, maxPriority: Int): Array<Any>
}
