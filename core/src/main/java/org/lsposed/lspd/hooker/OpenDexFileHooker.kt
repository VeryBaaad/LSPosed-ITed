package org.lsposed.lspd.hooker

import android.os.Build
import io.github.libxposed.api.XposedInterface
import org.lsposed.lspd.impl.LSPosedBridge
import org.lsposed.lspd.nativebridge.HookBridge

class OpenDexFileHooker : XposedInterface.Hooker {
    @Throws(Throwable::class)
    override fun intercept(chain: XposedInterface.Chain): Any? {
        val result = chain.proceed()
        var classLoader: ClassLoader? = null
        for (arg in chain.getArgs()) {
            if (arg is ClassLoader) {
                classLoader = arg
            }
        }
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P && classLoader == null) {
            classLoader = LSPosedBridge::class.java.classLoader
        }
        while (classLoader != null) {
            if (classLoader === LSPosedBridge::class.java.classLoader) {
                HookBridge.setTrusted(result)
                return result
            } else {
                classLoader = classLoader.parent
            }
        }
        return result
    }
}
