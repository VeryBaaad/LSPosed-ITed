package org.lsposed.lspd.impl

import android.annotation.SuppressLint
import android.app.ActivityThread
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.DeadSystemException
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.RemoteException
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.CtorInvoker
import io.github.libxposed.api.XposedInterface.ExceptionMode
import io.github.libxposed.api.XposedInterface.HookBuilder
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import io.github.libxposed.api.error.XposedFrameworkError
import org.lsposed.lspd.core.BuildConfig
import org.lsposed.lspd.models.Module
import org.lsposed.lspd.nativebridge.NativeAPI
import org.lsposed.lspd.service.ILSPInjectedModuleService
import org.lsposed.lspd.util.LspModuleClassLoader
import java.io.File
import java.io.FileNotFoundException
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

@SuppressLint("NewApi")
class LSPosedContext internal constructor(
    private val mPackageName: String,
    private val mApplicationInfo: ApplicationInfo,
    private val service: ILSPInjectedModuleService,
    private val mDefaultExceptionMode: ExceptionMode
) : XposedInterface {
    private val mRemotePrefs: MutableMap<String, SharedPreferences> = ConcurrentHashMap()

    override fun getFrameworkName(): String = BuildConfig.FRAMEWORK_NAME

    override fun getFrameworkVersion(): String = BuildConfig.VERSION_NAME

    override fun getFrameworkVersionCode(): Long = BuildConfig.VERSION_CODE

    override fun getFrameworkProperties(): Long = try {
        service.frameworkProperties
    } catch (e: RemoteException) {
        throw XposedFrameworkError(e)
    }

    override fun hook(origin: Executable): HookBuilder =
        LSPosedBridge.newHookBuilder(this, origin, mDefaultExceptionMode)

    override fun hookClassInitializer(origin: Class<*>): HookBuilder =
        LSPosedBridge.newClassInitializerHookBuilder(this, origin, mDefaultExceptionMode)

    override fun deoptimize(executable: Executable): Boolean =
        LSPosedBridge.doDeoptimize(executable)

    override fun getInvoker(method: Method): XposedInterface.Invoker<*, Method> =
        LSPosedBridge.newInvoker(method)

    override fun <T> getInvoker(constructor: Constructor<T>): CtorInvoker<T> =
        LSPosedBridge.newInvoker(constructor)

    override fun log(priority: Int, tag: String?, msg: String) {
        log(priority, tag, msg, null)
    }

    override fun log(priority: Int, tag: String?, message: String, throwable: Throwable?) {
        if (message.isEmpty() && throwable == null) {
            return
        }

        val estimatedLength = max(0xFC2 - (tag?.length ?: 0), 100)
        val output = StringWriter(estimatedLength)
        val writer = PrintWriter(output)

        writer.println("[$mPackageName,$tag] $message")

        if (throwable != null) {
            var candidate: Throwable? = throwable
            while (candidate != null && candidate !is UnknownHostException) {
                if (candidate is DeadSystemException) {
                    writer.println("DeadSystemException: The system died; earlier logs will point to the root cause")
                    break
                }
                candidate = candidate.cause
            }
            if (candidate == null) {
                throwable.printStackTrace(writer)
            }
        }

        writer.flush()
        Log.println(priority, TAG, output.toString())
    }

    override fun getModuleApplicationInfo(): ApplicationInfo = mApplicationInfo

    override fun getRemotePreferences(group: String): SharedPreferences =
        mRemotePrefs.computeIfAbsent(group) { name ->
            try {
                LSPosedRemotePreferences(service, name)
            } catch (e: RemoteException) {
                log(Log.ERROR, "null", "Failed to get remote preferences", e)
                throw XposedFrameworkError(e)
            }
        }

    override fun listRemoteFiles(): Array<String> = try {
        service.remoteFileList
    } catch (e: RemoteException) {
        log(Log.ERROR, "null", "Failed to list remote files", e)
        throw XposedFrameworkError(e)
    }

    @Throws(FileNotFoundException::class)
    override fun openRemoteFile(name: String): ParcelFileDescriptor = try {
        service.openRemoteFile(name)
    } catch (e: RemoteException) {
        throw FileNotFoundException(e.message)
    }

    companion object {
        private const val TAG = "LSPosedContext"

        var isSystemServer: Boolean = false
        var appDir: String? = null
        var processName: String? = null

        private val modules: MutableSet<XposedModule> = ConcurrentHashMap.newKeySet()

        fun callOnPackageLoaded(param: PackageLoadedParam) {
            for (module in modules) {
                try {
                    module.onPackageLoaded(param)
                } catch (t: Throwable) {
                    Log.e(
                        TAG,
                        "Error when calling onPackageLoaded of ${module.moduleApplicationInfo.packageName}",
                        t
                    )
                }
            }
        }

        fun callOnPackageReady(param: PackageReadyParam) {
            for (module in modules) {
                try {
                    module.onPackageReady(param)
                } catch (t: Throwable) {
                    Log.e(
                        TAG,
                        "Error when calling onPackageReady of ${module.moduleApplicationInfo.packageName}",
                        t
                    )
                }
            }
        }

        fun callOnSystemServerStarting(param: SystemServerStartingParam) {
            for (module in modules) {
                try {
                    module.onSystemServerStarting(param)
                } catch (t: Throwable) {
                    Log.e(
                        TAG,
                        "Error when calling onSystemServerStarting of ${module.moduleApplicationInfo.packageName}",
                        t
                    )
                }
            }
        }

        @JvmStatic
        @SuppressLint("DiscouragedPrivateApi")
        fun loadModule(at: ActivityThread?, module: Module): Boolean {
            try {
                Log.d(TAG, "Loading module ${module.packageName}")
                val abis =
                    if (Process.is64Bit()) Build.SUPPORTED_64_BIT_ABIS else Build.SUPPORTED_32_BIT_ABIS
                val librarySearchPath = abis.joinToString("") { abi ->
                    "${module.apkPath}!/lib/$abi${File.pathSeparator}"
                }
                val initLoader = XposedModule::class.java.classLoader
                val mcl = LspModuleClassLoader.loadApk(
                    module.apkPath,
                    module.file.preLoadedDexes,
                    librarySearchPath,
                    initLoader
                )
                if (mcl.loadClass(XposedModule::class.java.name).classLoader !== initLoader) {
                    Log.e(TAG, "  Cannot load module: ${module.packageName}")
                    Log.e(TAG, "  The Xposed API classes are compiled into the module's APK.")
                    Log.e(
                        TAG,
                        "  This may cause strange issues and must be fixed by the module developer."
                    )
                    return false
                }
                module.file.moduleLibraryNames.forEach(NativeAPI::recordNativeEntrypoint)
                val defaultExceptionMode =
                    if (module.file.exceptionPassthrough) ExceptionMode.PASSTHROUGH
                    else ExceptionMode.PROTECTIVE
                val ctx = LSPosedContext(
                    module.packageName,
                    module.applicationInfo,
                    module.service,
                    defaultExceptionMode
                )
                for (entry in module.file.moduleClassNames) {
                    val moduleClass = mcl.loadClass(entry)
                    Log.d(TAG, "  Loading class $moduleClass")
                    if (!XposedModule::class.java.isAssignableFrom(moduleClass)) {
                        Log.e(
                            TAG,
                            "    This class doesn't implement any sub-interface of XposedModule, skipping it"
                        )
                        continue
                    }
                    try {
                        val moduleContext =
                            moduleClass.getConstructor().newInstance() as XposedModule
                        moduleContext.attachFramework(ctx)
                        moduleContext.onModuleLoaded(object : ModuleLoadedParam {
                            override fun isSystemServer() = Companion.isSystemServer

                            override fun getProcessName() = Companion.processName!!
                        })
                        modules.add(moduleContext)
                    } catch (e: Throwable) {
                        Log.e(TAG, "    Failed to load class $moduleClass", e)
                    }
                }
                Log.d(TAG, "Loaded module ${module.packageName}: $ctx")
            } catch (e: Throwable) {
                Log.d(TAG, "Loading module ${module.packageName}", e)
                return false
            }
            return true
        }
    }
}
