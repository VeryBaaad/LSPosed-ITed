/*
 * This file is part of LSPosed.
 *
 * LSPosed is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LSPosed is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LSPosed.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2020 EdXposed Contributors
 * Copyright (C) 2021 LSPosed Contributors
 */
package org.lsposed.lspd.hooker

import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedInit
import de.robv.android.xposed.callbacks.XC_LoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.libxposed.api.XposedInterface
import org.lsposed.lspd.impl.LSPosedContext
import org.lsposed.lspd.util.Hookers
import org.lsposed.lspd.util.Utils

class StartBootstrapServicesHooker : XposedInterface.Hooker {
    @Throws(Throwable::class)
    override fun intercept(chain: XposedInterface.Chain): Any? {
        Utils.logD("SystemServer#startBootstrapServices() starts")

        try {
            XposedInit.loadedPackagesInProcess.add("android")

            val lpparam = LoadPackageParam(XposedBridge.sLoadedPackageCallbacks)
            lpparam.packageName = "android"
            lpparam.processName =
                "android" // it's actually system_server, but other functions return this as well
            lpparam.classLoader = HandleSystemServerProcessHooker.systemServerCL
            lpparam.appInfo = null
            lpparam.isFirstApplication = true
            XC_LoadPackage.callAll(lpparam)

            LSPosedContext.callOnSystemServerStarting { HandleSystemServerProcessHooker.systemServerCL!! }
        } catch (t: Throwable) {
            Hookers.logE("error when hooking startBootstrapServices", t)
        }
        return chain.proceed()
    }
}
