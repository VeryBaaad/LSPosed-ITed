package org.lsposed.lspd.impl

import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.CtorInvoker
import io.github.libxposed.api.XposedInterface.ExceptionMode
import io.github.libxposed.api.XposedInterface.HookBuilder
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.error.HookFailedError
import org.lsposed.lspd.nativebridge.HookBridge
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.Collections

object LSPosedBridge {
    private const val TAG = "LSPosed-Bridge"
    private const val CAST_ERROR =
        "Return value's type from hook callback does not match the hooked method"
    private val EMPTY_ARRAY = arrayOfNulls<Any>(0)

    private val getCause: Method?

    init {
        var tmp: Method?
        try {
            tmp = InvocationTargetException::class.java.getMethod("getCause")
        } catch (_: Throwable) {
            tmp = null
        }
        getCause = tmp
    }

    fun log(text: String) {
        Log.i(TAG, text)
    }

    fun log(t: Throwable?) {
        val logStr = Log.getStackTraceString(t)
        Log.e(TAG, logStr)
    }

    private fun returnTypeOf(executable: Executable?): Class<*>? {
        if (executable !is Method) {
            return null
        }
        val returnType = executable.returnType
        return if (returnType.isPrimitive) null else returnType
    }

    private fun checkReturnType(result: Any?, returnType: Class<*>?): Any? {
        if (returnType != null && !HookBridge.instanceOf(result, returnType)) {
            throw ClassCastException(CAST_ERROR)
        }
        return result
    }

    @Throws(Throwable::class)
    private fun unwrapInvocationTarget(e: InvocationTargetException): Any? {
        if (getCause == null) {
            throw e
        }
        val cause = HookBridge.invokeOriginalMethod(method, getCause, e, false) as Throwable?
        if (cause != null) {
            throw cause
        }
        throw e
    }

    @Throws(Throwable::class)
    private fun invokeOriginal(
        method: Executable?,
        thisObject: Any?,
        args: Array<out Any?>?,
        isConstructor: Boolean
    ): Any? = try {
        HookBridge.invokeOriginalMethod(method, thisObject, args, isConstructor)
    } catch (e: InvocationTargetException) {
        unwrapInvocationTarget(e)
    }

    @JvmStatic
    fun doHook(
        hookMethod: Executable,
        priority: Int,
        hooker: XposedInterface.Hooker
    ): HookHandle {
        require(!Modifier.isAbstract(hookMethod.modifiers)) { "Cannot hook abstract methods: $hookMethod" }
        require(
            hookMethod.declaringClass
                .classLoader !== LSPosedContext::class.java.classLoader
        ) { "Do not allow hooking inner methods" }
        require(!(hookMethod.declaringClass == Method::class.java && hookMethod.name == "invoke")) { "Cannot hook Method.invoke" }

        if (HookBridge.hookMethod(hookMethod, NativeHooker::class.java, priority, hooker)) {
            return HookHandleImpl(hookMethod, hooker)
        }
        throw HookFailedError("Cannot hook $hookMethod")
    }

    @JvmStatic
    fun newHookBuilder(
        context: XposedInterface,
        executable: Executable,
        defaultExceptionMode: ExceptionMode
    ): HookBuilder = HookBuilderImpl(context, executable, defaultExceptionMode)

    @JvmStatic
    fun newClassInitializerHookBuilder(
        context: XposedInterface,
        clazz: Class<*>,
        defaultExceptionMode: ExceptionMode
    ): HookBuilder {
        require(clazz.classLoader !== LSPosedContext::class.java.classLoader) { "Do not allow hooking inner classes" }
        synchronized(clazz) {
            val classInitializer = HookBridge.findClassInitializer(clazz)
            requireNotNull(classInitializer) { "Cannot find class initializer for $clazz" }
            return HookBuilderImpl(context, classInitializer, defaultExceptionMode)
        }
    }

    @JvmStatic
    fun doDeoptimize(executable: Executable): Boolean {
        require(!Modifier.isAbstract(executable.modifiers)) { "Cannot deoptimize abstract methods: $executable" }
        require(!Proxy.isProxyClass(executable.declaringClass)) { "Cannot deoptimize methods from proxy class: $executable" }
        return HookBridge.deoptimizeMethod(executable)
    }

    @JvmStatic
    fun newInvoker(method: Method): XposedInterface.Invoker<*, Method> = MethodInvokerImpl(method)

    @JvmStatic
    fun <T> newInvoker(constructor: Constructor<T>): CtorInvoker<T> = CtorInvokerImpl(constructor)

    class NativeHooker private constructor(method: Executable) {
        private val params: Array<Any?> = arrayOf(
            method,
            returnTypeOf(method),
            Modifier.isStatic(method.modifiers),
        )

        @Throws(Throwable::class)
        fun callback(rawArgs: Array<Any?>): Any? {
            val method = params[0] as Executable
            val returnType = params[1] as Class<*>?
            val isStatic = params[2] as Boolean

            val thisObject: Any?
            val args: Array<Any?>
            if (isStatic) {
                thisObject = null
                args = rawArgs
            } else {
                thisObject = rawArgs[0]
                args = rawArgs.copyOfRange(1, rawArgs.size)
            }

            val hookers = HookBridge.callbackSnapshot(method, XposedInterface.PRIORITY_HIGHEST)
            if (hookers.isEmpty()) {
                return invokeOriginal(method, thisObject, args, method is Constructor<*>)
            }

            val chain = ChainImpl(method, returnType, hookers, thisObject, args, false)
            try {
                return chain.proceed()
            } finally {
                chain.close()
            }
        }
    }

    internal class ChainImpl<T : Executable>(
        private val executable: T, private val returnType: Class<*>?, hookers: Array<Any?>?,
        private var thisObject: Any?, args: Array<out Any?>?, private val special: Boolean
    ) : XposedInterface.Chain {
        private val threadId = HookBridge.gettid()
        private val hookers: Array<Any?> = hookers ?: EMPTY_ARRAY
        private var index = -1
        private var active = true
        private var args: Array<out Any?> = args ?: EMPTY_ARRAY

        fun close() {
            active = false
        }

        private fun checkActive() {
            check(HookBridge.gettid() == threadId) { "Chain must be accessed in the same thread as the hooked method" }
            check(active) { "Chain cannot be used after the interception ends" }
        }

        override fun getExecutable(): Executable = executable

        override fun getThisObject(): Any? = thisObject

        override fun getArgs(): List<Any?> = Collections.unmodifiableList(args.asList())

        @Throws(IndexOutOfBoundsException::class, ClassCastException::class)
        override fun getArg(index: Int): Any? {
            return args[index]
        }

        @Throws(Throwable::class)
        override fun proceed(): Any? {
            checkActive()
            val next = ++index
            try {
                if (next != hookers.size) {
                    val result = (hookers[next] as XposedInterface.Hooker).intercept(this)
                    return checkReturnType(result, returnType)
                }
                if (special) {
                    try {
                        return checkReturnType(
                            HookBridge.invokeSpecialMethod(
                                executable,
                                null,
                                thisObject,
                                *args
                            ), returnType
                        )
                    } catch (e: InvocationTargetException) {
                        return unwrapInvocationTarget(e)
                    }
                }
                return checkReturnType(
                    invokeOriginal(
                        executable,
                        thisObject,
                        args,
                        executable is Constructor<*>
                    ), returnType
                )
            } finally {
                index--
            }
        }

        @Throws(Throwable::class)
        override fun proceed(args: Array<Any?>): Any? {
            val oldArgs = this.args
            this.args = args
            try {
                return proceed()
            } finally {
                this.args = oldArgs
            }
        }

        @Throws(Throwable::class)
        override fun proceedWith(thisObject: Any): Any? {
            val oldThisObject = this.thisObject
            this.thisObject = thisObject
            try {
                return proceed()
            } finally {
                this.thisObject = oldThisObject
            }
        }

        @Throws(Throwable::class)
        override fun proceedWith(thisObject: Any, args: Array<Any?>): Any? {
            val oldThisObject = this.thisObject
            val oldArgs = this.args
            this.thisObject = thisObject
            this.args = args
            try {
                return proceed()
            } finally {
                this.thisObject = oldThisObject
                this.args = oldArgs
            }
        }
    }

    internal class ProtectiveChain(private val base: XposedInterface.Chain) :
        XposedInterface.Chain {
        var proceeded = false
            private set
        var result: Any? = null
            private set
        var throwable: Throwable? = null
            private set

        override fun getExecutable(): Executable = base.executable

        override fun getThisObject(): Any? = base.thisObject

        override fun getArgs(): List<Any?> = base.args

        @Throws(IndexOutOfBoundsException::class, ClassCastException::class)
        override fun getArg(index: Int): Any? {
            return base.getArg(index)
        }

        @Throws(Throwable::class)
        override fun proceed(): Any? {
            proceeded = true
            try {
                result = base.proceed()
                return result
            } catch (t: Throwable) {
                throwable = t
                throw t
            }
        }

        @Throws(Throwable::class)
        override fun proceed(args: Array<Any?>): Any? {
            proceeded = true
            try {
                result = base.proceed(args)
                return result
            } catch (t: Throwable) {
                throwable = t
                throw t
            }
        }

        @Throws(Throwable::class)
        override fun proceedWith(thisObject: Any): Any? {
            proceeded = true
            try {
                result = base.proceedWith(thisObject)
                return result
            } catch (t: Throwable) {
                throwable = t
                throw t
            }
        }

        @Throws(Throwable::class)
        override fun proceedWith(thisObject: Any, args: Array<Any?>): Any? {
            proceeded = true
            try {
                result = base.proceedWith(thisObject, args)
                return result
            } catch (t: Throwable) {
                throwable = t
                throw t
            }
        }
    }

    internal class ProtectiveHooker(
        private val context: XposedInterface,
        private val hooker: XposedInterface.Hooker
    ) : XposedInterface.Hooker {
        @Throws(Throwable::class)
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val protectiveChain = ProtectiveChain(chain)
            try {
                return hooker.intercept(protectiveChain)
            } catch (t: Throwable) {
                if (protectiveChain.throwable === t) {
                    throw t
                }
                context.log(Log.WARN, "ProtectiveHooker", "Exception in hooker", t)
                if (!protectiveChain.proceeded) {
                    return chain.proceed()
                }
                protectiveChain.throwable?.let { throw it }
                return protectiveChain.result
            }
        }
    }

    internal class HookBuilderImpl(
        private val context: XposedInterface, private val executable: Executable,
        private val defaultExceptionMode: ExceptionMode?
    ) : HookBuilder {
        private var priority = XposedInterface.PRIORITY_DEFAULT
        private var exceptionMode = ExceptionMode.DEFAULT

        override fun setPriority(priority: Int): HookBuilder {
            this.priority = priority
            return this
        }

        override fun setExceptionMode(mode: ExceptionMode): HookBuilder {
            this.exceptionMode = mode
            return this
        }

        override fun intercept(hooker: XposedInterface.Hooker): HookHandle {
            var hooker = hooker
            if (exceptionMode == ExceptionMode.PROTECTIVE
                || exceptionMode == ExceptionMode.DEFAULT
                && defaultExceptionMode == ExceptionMode.PROTECTIVE
            ) {
                hooker = ProtectiveHooker(context, hooker)
            }
            return doHook(executable, priority, hooker)
        }
    }

    internal class HookHandleImpl(
        private val executable: Executable,
        private val hooker: XposedInterface.Hooker?
    ) : HookHandle {
        override fun getExecutable(): Executable {
            return executable
        }

        override fun unhook() {
            HookBridge.unhookMethod(executable, hooker)
        }
    }

    internal open class BaseInvoker<T : Executable>(val executable: T) {
        protected var target: XposedInterface.Invoker.Type =
            XposedInterface.Invoker.Type.Chain.FULL

        init {
            executable.isAccessible = true
        }

        @Throws(
            InvocationTargetException::class,
            IllegalArgumentException::class,
            IllegalAccessException::class
        )
        fun invoke(thisObject: Any?, vararg args: Any?): Any? {
            val type = target
            if (type is XposedInterface.Invoker.Type.Origin) {
                return HookBridge.invokeOriginalMethod(
                    executable,
                    thisObject,
                    args,
                    executable is Constructor<*>
                )
            }
            check(type is XposedInterface.Invoker.Type.Chain) { "Unknown invoker type" }
            return invokeChain(thisObject, args, type.maxPriority, false)
        }

        @Throws(
            InvocationTargetException::class,
            IllegalArgumentException::class,
            IllegalAccessException::class
        )
        fun invokeSpecial(thisObject: Any, vararg args: Any?): Any? {
            require(!Modifier.isStatic(executable.modifiers)) { "Cannot invoke special on static method: $executable" }
            val type = target
            if (type is XposedInterface.Invoker.Type.Origin) {
                try {
                    return HookBridge.invokeSpecialMethod(executable, null, thisObject, *args)
                } catch (e: InstantiationException) {
                    throw InstantiationError(e.message)
                }
            }
            check(type is XposedInterface.Invoker.Type.Chain) { "Unknown invoker type" }
            return invokeChain(thisObject, args, type.maxPriority, true)
        }

        @Throws(InvocationTargetException::class, IllegalAccessException::class)
        fun invokeChain(
            thisObject: Any?,
            args: Array<out Any?>,
            maxPriority: Int,
            special: Boolean
        ): Any? {
            val hookers = HookBridge.callbackSnapshot(executable, maxPriority)
            val chain = ChainImpl(
                executable,
                returnTypeOf(executable),
                hookers,
                thisObject,
                args,
                special
            )
            try {
                return chain.proceed()
            } catch (e: Error) {
                throw e
            } catch (e: RuntimeException) {
                throw e
            } catch (e: InvocationTargetException) {
                throw e
            } catch (e: IllegalAccessException) {
                throw e
            } catch (t: Throwable) {
                throw InvocationTargetException(t)
            } finally {
                chain.close()
            }
        }

        fun setInvokerType(type: XposedInterface.Invoker.Type) {
            target = type
        }
    }

    internal class MethodInvokerImpl(executable: Method) : BaseInvoker<Method>(executable),
        XposedInterface.Invoker<MethodInvokerImpl, Method> {
        override fun setType(type: XposedInterface.Invoker.Type): MethodInvokerImpl {
            setInvokerType(type)
            return this
        }
    }

    internal class CtorInvokerImpl<T>(executable: Constructor<T>) :
        BaseInvoker<Constructor<T>>(executable), CtorInvoker<T> {
        @Throws(
            InvocationTargetException::class,
            IllegalArgumentException::class,
            IllegalAccessException::class,
            InstantiationException::class
        )
        override fun newInstance(vararg args: Any?): T {
            val instance = HookBridge.allocateObject(executable.declaringClass)
            invoke(instance, *args)
            return instance
        }

        @Suppress("UNCHECKED_CAST")
        @Throws(
            InvocationTargetException::class,
            IllegalArgumentException::class,
            IllegalAccessException::class,
            InstantiationException::class
        )
        override fun <U> newInstanceSpecial(subClass: Class<U>, vararg args: Any?): U {
            val type = target
            if (type is XposedInterface.Invoker.Type.Origin) {
                return HookBridge.invokeSpecialMethod(executable, subClass, null, *args) as U
            }
            check(type is XposedInterface.Invoker.Type.Chain) { "Unknown invoker type" }
            val instance = HookBridge.allocateSpecialReceiver(executable, subClass)
            invokeChain(instance, args, type.maxPriority, true)
            return instance
        }

        override fun setType(type: XposedInterface.Invoker.Type): CtorInvoker<T> {
            setInvokerType(type)
            return this
        }
    }
}
