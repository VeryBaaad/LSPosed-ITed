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
 * Copyright (C) 2021 - 2022 LSPosed Contributors
 */
package org.lsposed.lspd.core

import android.app.ActivityThread
import android.app.LoadedApk
import android.content.pm.ApplicationInfo
import android.content.res.CompatibilityInfo
import android.os.Build
import com.android.internal.os.ZygoteInit
import dalvik.system.DexFile
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedInit
import org.lsposed.lspd.deopt.PrebuiltMethodsDeopter
import org.lsposed.lspd.hooker.AttachHooker
import org.lsposed.lspd.hooker.CrashDumpHooker
import org.lsposed.lspd.hooker.HandleSystemServerProcessHooker
import org.lsposed.lspd.hooker.LoadedApkCreateAppFactoryHooker
import org.lsposed.lspd.hooker.LoadedApkCreateCLHooker
import org.lsposed.lspd.hooker.LoadedApkCtorHooker
import org.lsposed.lspd.hooker.OpenDexFileHooker
import org.lsposed.lspd.impl.LSPosedContext
import org.lsposed.lspd.impl.LSPosedHelper
import org.lsposed.lspd.service.ILSPApplicationService
import org.lsposed.lspd.util.Utils

object Startup {
    private fun startBootstrapHook(isSystem: Boolean) {
        Utils.logD("startBootstrapHook starts: isSystem = $isSystem")
        LSPosedHelper.hookMethod(
            CrashDumpHooker(),
            Thread::class.java,
            "dispatchUncaughtException",
            Throwable::class.java
        )
        if (isSystem) {
            LSPosedHelper.hookAllMethods(
                HandleSystemServerProcessHooker(),
                ZygoteInit::class.java,
                "handleSystemServerProcess"
            )
        } else {
            val openDexFileHooker = OpenDexFileHooker()
            LSPosedHelper.hookAllMethods(openDexFileHooker, DexFile::class.java, "openDexFile")
            LSPosedHelper.hookAllMethods(
                openDexFileHooker,
                DexFile::class.java,
                "openInMemoryDexFile"
            )
            LSPosedHelper.hookAllMethods(
                openDexFileHooker,
                DexFile::class.java,
                "openInMemoryDexFiles"
            )
        }
        LSPosedHelper.hookConstructor(
            LoadedApkCtorHooker(),
            LoadedApk::class.java,
            ActivityThread::class.java,
            ApplicationInfo::class.java,
            CompatibilityInfo::class.java,
            ClassLoader::class.java,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType
        )
        LSPosedHelper.hookMethod(
            LoadedApkCreateCLHooker(),
            LoadedApk::class.java,
            "createOrUpdateClassLoaderLocked",
            List::class.java
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            LSPosedHelper.hookMethod(
                LoadedApkCreateAppFactoryHooker(), LoadedApk::class.java,
                "createAppFactory", ApplicationInfo::class.java, ClassLoader::class.java
            )
        }
        LSPosedHelper.hookAllMethods(AttachHooker(), ActivityThread::class.java, "attach")
    }

    @JvmStatic
    fun bootstrapXposed() {
        // Initialize the Xposed framework
        try {
            startBootstrapHook(XposedInit.startsSystemServer)
            XposedInit.loadLegacyModules()
        } catch (t: Throwable) {
            Utils.logE("error during Xposed initialization", t)
        }
    }

    @JvmStatic
    fun initXposed(
        isSystem: Boolean,
        processName: String,
        appDir: String,
        service: ILSPApplicationService
    ) {
        // init logger
        ApplicationServiceClient.Init(service, processName)
        XposedBridge.initXResources()
        XposedInit.startsSystemServer = isSystem
        LSPosedContext.isSystemServer = isSystem
        LSPosedContext.appDir = appDir
        LSPosedContext.processName = processName
        PrebuiltMethodsDeopter.deoptBootMethods() // do it once for secondary zygote
    }
}
