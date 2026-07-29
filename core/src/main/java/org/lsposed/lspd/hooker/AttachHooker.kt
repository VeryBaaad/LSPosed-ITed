package org.lsposed.lspd.hooker

import android.app.ActivityThread
import de.robv.android.xposed.XposedInit
import io.github.libxposed.api.XposedInterface

class AttachHooker : XposedInterface.Hooker {
    @Throws(Throwable::class)
    override fun intercept(chain: XposedInterface.Chain): Any? {
        val result = chain.proceed()
        XposedInit.loadModules(chain.thisObject as ActivityThread?)
        return result
    }
}
