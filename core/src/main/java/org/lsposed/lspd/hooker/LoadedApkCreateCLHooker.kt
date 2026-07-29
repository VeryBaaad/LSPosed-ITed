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

import android.annotation.SuppressLint
import android.app.ActivityThread
import android.app.AppComponentFactory
import android.app.LoadedApk
import android.content.pm.ApplicationInfo
import android.os.Build
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedInit
import de.robv.android.xposed.callbacks.XC_LoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import org.lsposed.lspd.core.ApplicationServiceClient
import org.lsposed.lspd.impl.LSPosedContext
import org.lsposed.lspd.util.Hookers
import org.lsposed.lspd.util.MetaDataReader
import org.lsposed.lspd.util.Utils
import java.io.File
import java.io.IOException
import java.lang.reflect.Field
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("BlockedPrivateApi")
class LoadedApkCreateCLHooker : XposedInterface.Hooker {
    @Throws(Throwable::class)
    override fun intercept(chain: XposedInterface.Chain): Any? {
        val loadedApk = chain.getThisObject() as LoadedApk
        var result: Any? = null
        var proceeded = false
        var proceeding = false

        if (chain.getArg(0) != null || !loadedApks.contains(loadedApk)) {
            return chain.proceed()
        }

        try {
            Hookers.logD("LoadedApk#createClassLoader starts")

            var packageName = ActivityThread.currentPackageName()
            var processName = ActivityThread.currentProcessName()
            val isFirstPackage =
                packageName != null && processName != null && packageName == loadedApk.packageName
            if (!isFirstPackage) {
                packageName = loadedApk.packageName
                processName = ActivityThread.currentPackageName()
            } else if (packageName == "android") {
                packageName = "system"
            }

            if (!isFirstPackage && !XposedHelpers.getBooleanField(loadedApk, "mIncludeCode")) {
                val mAppDir = XposedHelpers.getObjectField(loadedApk, "mAppDir")
                Hookers.logD("LoadedApk#<init> mIncludeCode == false: $mAppDir")
                proceeding = true
                result = chain.proceed()
                proceeding = false
                proceeded = true
                return result
            }

            val param = PackageLoadParam(loadedApk, isFirstPackage)
            packageLoadParam.set(param)
            proceeding = true
            result = chain.proceed()
            proceeding = false
            proceeded = true
            packageLoadParam.remove()

            val mAppDir = XposedHelpers.getObjectField(loadedApk, "mAppDir")
            val classLoader =
                XposedHelpers.getObjectField(loadedApk, "mClassLoader") as ClassLoader?
            Hookers.logD("LoadedApk#createClassLoader ends: $mAppDir -> $classLoader")

            if (classLoader == null) {
                return result
            }
            param.setClassLoader(classLoader)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                param.setAppComponentFactory(
                    XposedHelpers.getObjectField(
                        loadedApk,
                        "mAppComponentFactory"
                    ) as AppComponentFactory?
                )
            }

            val lpparam = LoadPackageParam(
                XposedBridge.sLoadedPackageCallbacks
            )
            lpparam.packageName = packageName
            lpparam.processName = processName
            lpparam.classLoader = classLoader
            lpparam.appInfo = loadedApk.applicationInfo
            lpparam.isFirstApplication = isFirstPackage

            if (isFirstPackage && XposedInit.getLoadedModules()
                    .getOrDefault(packageName, Optional.empty()).isPresent
            ) {
                hookNewXSP(lpparam)
            }

            Hookers.logD("Call handleLoadedPackage: packageName=" + lpparam.packageName + " processName=" + lpparam.processName + " isFirstPackage=" + isFirstPackage + " classLoader=" + lpparam.classLoader + " appInfo=" + lpparam.appInfo)
            XC_LoadPackage.callAll(lpparam)

            LSPosedContext.callOnPackageReady(param)
        } catch (t: Throwable) {
            if (proceeding) {
                throw t
            }
            Hookers.logE("error when hooking LoadedApk#createClassLoader", t)
            if (!proceeded) {
                return chain.proceed()
            }
        } finally {
            packageLoadParam.remove()
            loadedApks.remove(loadedApk)
        }
        return result
    }

    class PackageLoadParam(
        private val loadedApk: LoadedApk,
        private val isFirstPackage: Boolean
    ) : PackageReadyParam {
        private var defaultClassLoader: ClassLoader? = null
        private var classLoader: ClassLoader? = null
        private var appComponentFactory: AppComponentFactory? = null

        fun setDefaultClassLoader(defaultClassLoader: ClassLoader?) {
            this.defaultClassLoader = defaultClassLoader
        }

        fun setClassLoader(classLoader: ClassLoader) {
            this.classLoader = classLoader
        }

        fun setAppComponentFactory(appComponentFactory: AppComponentFactory?) {
            this.appComponentFactory = appComponentFactory
        }

        override fun getPackageName(): String {
            return loadedApk.packageName
        }

        override fun getApplicationInfo(): ApplicationInfo {
            return loadedApk.applicationInfo
        }

        override fun getDefaultClassLoader(): ClassLoader {
            if (defaultClassLoader != null) {
                return defaultClassLoader!!
            }
            try {
                val defaultClassLoader: ClassLoader =
                    (if (defaultClassLoaderField == null) classLoader else defaultClassLoaderField.get(
                        loadedApk
                    ) as ClassLoader?)!!
                return defaultClassLoader
            } catch (e: IllegalStateException) {
                throw e
            } catch (t: Throwable) {
                throw IllegalStateException(t)
            }
        }

        override fun getClassLoader(): ClassLoader {
            checkNotNull(classLoader) { "ClassLoader is not ready" }
            return classLoader!!
        }

        override fun isFirstPackage(): Boolean {
            return isFirstPackage
        }

        override fun getAppComponentFactory(): AppComponentFactory {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                throw UnsupportedOperationException()
            }
            if (appComponentFactory != null) {
                return appComponentFactory!!
            }
            return XposedHelpers.getObjectField(
                loadedApk,
                "mAppComponentFactory"
            ) as AppComponentFactory
        }
    }

    companion object {
        private val defaultClassLoaderField: Field?

        private val loadedApks: MutableSet<LoadedApk?> = ConcurrentHashMap.newKeySet<LoadedApk?>()
        private val packageLoadParam = ThreadLocal<PackageLoadParam?>()

        init {
            var field: Field? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    field = LoadedApk::class.java.getDeclaredField("mDefaultClassLoader")
                    field.isAccessible = true
                } catch (_: Throwable) {
                }
            }
            defaultClassLoaderField = field
        }

        @JvmStatic
        fun addLoadedApk(loadedApk: LoadedApk?) {
            loadedApks.add(loadedApk)
        }

        fun getPackageLoadParam(): PackageLoadParam? {
            return packageLoadParam.get()
        }

        private fun hookNewXSP(lpparam: LoadPackageParam) {
            var xposedminversion = -1
            var xposedsharedprefs = false
            try {
                val metaData = MetaDataReader.getMetaData(File(lpparam.appInfo.sourceDir))
                val minVersionRaw = metaData["xposedminversion"]
                if (minVersionRaw is Int) {
                    xposedminversion = minVersionRaw
                } else if (minVersionRaw is String) {
                    xposedminversion = MetaDataReader.extractIntPart(minVersionRaw)
                }
                xposedsharedprefs = metaData.containsKey("xposedsharedprefs")
            } catch (e: NumberFormatException) {
                Hookers.logE("ApkParser fails", e)
            } catch (e: IOException) {
                Hookers.logE("ApkParser fails", e)
            }

            if (xposedminversion > 92 || xposedsharedprefs) {
                Utils.logI("New modules detected, hook preferences")
                XposedHelpers.findAndHookMethod(
                    "android.app.ContextImpl",
                    lpparam.classLoader,
                    "checkMode",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam<*>) {
                            if ((param.args[0] as Int and 1 /*Context.MODE_WORLD_READABLE*/) != 0) {
                                param.setThrowable(null)
                            }
                        }
                    })
                XposedHelpers.findAndHookMethod(
                    "android.app.ContextImpl",
                    lpparam.classLoader,
                    "getPreferencesDir",
                    object : XC_MethodReplacement() {
                        override fun replaceHookedMethod(param: MethodHookParam<*>?): Any {
                            return File(
                                ApplicationServiceClient.serviceClient!!.getPrefsPath(
                                    lpparam.packageName
                                ) as String
                            )
                        }
                    })
            }
        }
    }
}
