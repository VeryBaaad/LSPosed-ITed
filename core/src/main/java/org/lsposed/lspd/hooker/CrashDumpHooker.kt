package org.lsposed.lspd.hooker

import io.github.libxposed.api.XposedInterface
import org.lsposed.lspd.impl.LSPosedBridge

class CrashDumpHooker : XposedInterface.Hooker {
    @Throws(Throwable::class)
    override fun intercept(chain: XposedInterface.Chain): Any {
        try {
            val e = chain.getArg(0) as Throwable?
            LSPosedBridge.log("Crash unexpectedly: " + android.util.Log.getStackTraceString(e))
        } catch (_: Throwable) {
        }
        return chain.proceed()
    }
}
