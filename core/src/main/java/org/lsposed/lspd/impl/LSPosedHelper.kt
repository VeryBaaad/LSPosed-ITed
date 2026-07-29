package org.lsposed.lspd.impl

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.error.HookFailedError
import org.lsposed.lspd.impl.LSPosedBridge.doHook

object LSPosedHelper {
    fun hookMethod(
        hooker: XposedInterface.Hooker,
        clazz: Class<*>,
        methodName: String,
        vararg parameterTypes: Class<*>?
    ): HookHandle {
        val method = try {
            clazz.getDeclaredMethod(methodName, *parameterTypes)
        } catch (e: NoSuchMethodException) {
            throw HookFailedError(e)
        }
        return doHook(method, XposedInterface.PRIORITY_DEFAULT, hooker)
    }

    fun hookAllMethods(
        hooker: XposedInterface.Hooker,
        clazz: Class<*>,
        methodName: String
    ): Set<HookHandle> = clazz.declaredMethods
        .filter { it.name == methodName }
        .mapTo(HashSet()) { doHook(it, XposedInterface.PRIORITY_DEFAULT, hooker) }

    fun hookConstructor(
        hooker: XposedInterface.Hooker,
        clazz: Class<*>,
        vararg parameterTypes: Class<*>?
    ): HookHandle {
        val constructor = try {
            clazz.getDeclaredConstructor(*parameterTypes)
        } catch (e: NoSuchMethodException) {
            throw HookFailedError(e)
        }
        return doHook(constructor, XposedInterface.PRIORITY_DEFAULT, hooker)
    }
}
