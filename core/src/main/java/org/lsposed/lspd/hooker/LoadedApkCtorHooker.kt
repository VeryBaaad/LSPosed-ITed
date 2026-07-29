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

import android.app.LoadedApk
import android.content.res.XResources
import android.util.Log
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedInit
import io.github.libxposed.api.XposedInterface
import org.lsposed.lspd.hooker.LoadedApkCreateCLHooker.Companion.addLoadedApk
import org.lsposed.lspd.util.Hookers

// when a package is loaded for an existing process, trigger the callbacks as well
class LoadedApkCtorHooker : XposedInterface.Hooker {
    @Throws(Throwable::class)
    override fun intercept(chain: XposedInterface.Chain): Any? {
        val result = chain.proceed()
        Hookers.logD("LoadedApk#<init> starts")

        try {
            val loadedApk = checkNotNull(chain.getThisObject() as LoadedApk)
            var packageName = loadedApk.packageName
            val mAppDir = XposedHelpers.getObjectField(loadedApk, "mAppDir")
            Hookers.logD("LoadedApk#<init> ends: $mAppDir")

            if (!XposedInit.disableResources) {
                XResources.setPackageNameForResDir(packageName, loadedApk.resDir)
            }

            val includeCode = XposedHelpers.getBooleanField(loadedApk, "mIncludeCode")
            if (packageName == "android") {
                if (XposedInit.startsSystemServer) {
                    Hookers.logD("LoadedApk#<init> is android, skip: $mAppDir")
                    return result
                } else {
                    packageName = "system"
                }
            } else if (!includeCode) {
                Hookers.logD("LoadedApk#<init> has no code, skip: $mAppDir")
                return result
            }

            if (!XposedInit.loadedPackagesInProcess.add(packageName)) {
                Hookers.logD("LoadedApk#<init> has been loaded before, skip: $mAppDir")
                return result
            }

            // OnePlus magic...
            if (Log.getStackTraceString(Throwable()).contains
                    ($$"android.app.ActivityThread$ApplicationThread.schedulePreload")
            ) {
                Hookers.logD("LoadedApk#<init> maybe oneplus's custom opt, skip")
                XposedInit.loadedPackagesInProcess.remove(packageName)
                return result
            }

            addLoadedApk(loadedApk)
        } catch (t: Throwable) {
            Hookers.logE("error when hooking LoadedApk.<init>", t)
        }
        return result
    }
}
