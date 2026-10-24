package org.lsposed.lspd.impl;

import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.lsposed.lspd.models.HotReloadOutcome;
import org.lsposed.lspd.nativebridge.NativeAPI;
import org.lsposed.lspd.util.LspModuleClassLoader;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam;
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;

public final class LSPosedModuleManager {

    private static final String TAG = "LSPosedModuleManager";

    private static final int HOT_RELOAD_SUCCEEDED = 0;
    private static final int HOT_RELOAD_FAILED = 1;
    private static final int HOT_RELOAD_UNSUPPORTED = 2;
    private static final int HOT_RELOAD_IN_PROGRESS = 3;

    private LSPosedModuleManager() {
    }

    static final class Generation {
        final ClassLoader classLoader;
        final LSPosedContext context;
        final int targetApiVersion;
        final boolean outdatedModern;
        final boolean isSystemServer;
        final String processName;
        private final List<WeakReference<XposedModule>> entryRefs;

        Generation(ClassLoader classLoader, LSPosedContext context, List<XposedModule> entries,
                   int targetApiVersion, boolean isSystemServer, String processName) {
            this.classLoader = classLoader;
            this.context = context;
            this.targetApiVersion = targetApiVersion;
            this.outdatedModern = targetApiVersion <= 100;
            this.isSystemServer = isSystemServer;
            this.processName = processName;
            this.entryRefs = new ArrayList<>(entries.size());
            for (var entry : entries) entryRefs.add(new WeakReference<>(entry));
        }

        List<XposedModule> entries() {
            var result = new ArrayList<XposedModule>(entryRefs.size());
            for (var ref : entryRefs) {
                var entry = ref.get();
                if (entry != null) result.add(entry);
            }
            return result;
        }

        List<XposedModule> liveEntries() {
            var result = new ArrayList<XposedModule>();
            for (var ref : entryRefs) {
                var entry = ref.get();
                if (entry != null && isActive(entry)) result.add(entry);
            }
            return result;
        }
    }

    private static final Map<String, Generation> generations = new ConcurrentHashMap<>();
    private static final Map<String, ReentrantLock> reloadLocks = new ConcurrentHashMap<>();
    static final java.util.Set<XposedModule> activeModules = ConcurrentHashMap.newKeySet();

    static void detach(XposedModule module) {
        if (activeModules.remove(module)) {
            Log.d(TAG, "Detached entry " + module.getClass().getName());
        }
    }

    static boolean isActive(XposedModule module) {
        return activeModules.contains(module);
    }

    public static boolean loadModule(org.lsposed.lspd.models.Module module) {
        var generation = buildGeneration(module, LSPosedContext.isSystemServer,
                LSPosedContext.processName);
        if (generation == null) return false;

        for (var entry : generation.entries()) activeModules.add(entry);
        generations.put(module.packageName, generation);

        if (!generation.outdatedModern) {
            var param = moduleLoadedParam(generation);
            for (var entry : generation.liveEntries()) {
                try {
                    entry.onModuleLoaded(param);
                } catch (Throwable t) {
                    Log.e(TAG, "Error in onModuleLoaded for " + entry.getClass().getName(), t);
                }
            }
        }

        Log.d(TAG, "Loaded module " + module.packageName + " successfully.");
        return true;
    }

    @Nullable
    private static Generation buildGeneration(org.lsposed.lspd.models.Module module,
                                              boolean isSystemServer, String processName) {
        try {
            Log.d(TAG, "Loading module " + module.packageName);

            if (module.file == null || module.file.moduleClassNames == null
                    || module.file.moduleClassNames.isEmpty()) {
                Log.e(TAG, "Module " + module.packageName + " has no entry class");
                return null;
            }

            var sb = new StringBuilder();
            var abis = Process.is64Bit() ? Build.SUPPORTED_64_BIT_ABIS : Build.SUPPORTED_32_BIT_ABIS;
            for (String abi : abis) {
                sb.append(module.apkPath).append("!/lib/").append(abi).append(File.pathSeparator);
            }
            var librarySearchPath = sb.toString();
            var initLoader = XposedModule.class.getClassLoader();

            var targetApiVersion = module.file.targetApiVersion;
            var moduleClassLoader = LspModuleClassLoader.loadApk(
                    module.apkPath,
                    module.file.preLoadedDexes,
                    librarySearchPath,
                    initLoader,
                    targetApiVersion >= XposedInterface.API_102);

            if (moduleClassLoader.loadClass(XposedModule.class.getName()).getClassLoader()
                    != initLoader) {
                Log.e(TAG, "The Xposed API classes are compiled into " + module.packageName);
                return null;
            }

            var defaultExceptionMode = module.file.exceptionPassthrough
                    ? XposedInterface.ExceptionMode.PASSTHROUGH
                    : XposedInterface.ExceptionMode.PROTECTIVE;
            var context = new LSPosedContext(module.packageName, module.applicationInfo,
                    module.service, defaultExceptionMode, targetApiVersion);

            if (module.file.moduleLibraryNames != null) {
                for (var libraryName : module.file.moduleLibraryNames) {
                    NativeAPI.recordNativeEntrypoint(libraryName);
                }
            }

            var entries = new ArrayList<XposedModule>();
            for (var className : module.file.moduleClassNames) {
                try {
                    var moduleClass = moduleClassLoader.loadClass(className);
                    Log.d(TAG, "  Loading class " + moduleClass);
                    if (!XposedModule.class.isAssignableFrom(moduleClass)) {
                        Log.e(TAG, "    This class does not implement XposedModule, skipping it");
                        continue;
                    }
                    entries.add(instantiate(moduleClass, context, targetApiVersion, isSystemServer,
                            processName));
                } catch (Throwable t) {
                    Log.e(TAG, "    Failed to instantiate class " + className, t);
                }
            }

            if (entries.isEmpty()) {
                Log.e(TAG, "No entry class of " + module.packageName + " could be instantiated");
                return null;
            }

            return new Generation(moduleClassLoader, context, entries, targetApiVersion,
                    isSystemServer, processName);
        } catch (Throwable t) {
            Log.e(TAG, "Fatal error loading module " + module.packageName, t);
            return null;
        }
    }

    @NonNull
    private static XposedModule instantiate(Class<?> moduleClass, LSPosedContext context,
                                           int targetApiVersion, boolean isSystemServer,
                                           String processName) throws Throwable {
        if (targetApiVersion <= 100) {
            var constructor = moduleClass.getConstructor(XposedInterface.class,
                    ModuleLoadedParam.class);
            return (XposedModule) constructor.newInstance(context,
                    moduleLoadedParam(isSystemServer, processName));
        }

        var instance = (XposedModule) moduleClass.getConstructor().newInstance();
        if (targetApiVersion >= XposedInterface.API_102) {
            instance.attachFramework(context, () -> detach(instance));
        } else {
            instance.attachFramework(context);
        }
        return instance;
    }

    @NonNull
    static ModuleLoadedParam moduleLoadedParam(Generation generation) {
        return moduleLoadedParam(generation.isSystemServer, generation.processName);
    }

    @NonNull
    private static ModuleLoadedParam moduleLoadedParam(boolean isSystemServer, String processName) {
        return new ModuleLoadedParam() {
            @Override
            public boolean isSystemServer() {
                return isSystemServer;
            }

            @NonNull
            @Override
            public String getProcessName() {
                return processName;
            }
        };
    }

    public static void callOnPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        dispatch(generation -> true, entry -> entry.onPackageLoaded(param), "onPackageLoaded");
    }

    public static void callOnPackageReady(XposedModuleInterface.PackageReadyParam param) {
        dispatch(generation -> !generation.outdatedModern, entry -> entry.onPackageReady(param),
                "onPackageReady");
    }

    public static void callOnSystemServerLoaded(XposedModuleInterface.SystemServerLoadedParam param) {
        dispatch(generation -> generation.outdatedModern,
                entry -> entry.onSystemServerLoaded(param),
                "onSystemServerLoaded");
    }

    public static void callOnSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        dispatch(generation -> !generation.outdatedModern,
                entry -> entry.onSystemServerStarting(param), "onSystemServerStarting");
    }

    private interface GenerationFilter {
        boolean accept(Generation generation);
    }

    private interface EntryCall {
        void call(XposedModule entry) throws Throwable;
    }

    private static void dispatch(GenerationFilter filter, EntryCall call, String callback) {
        for (var generation : generations.values()) {
            if (!filter.accept(generation)) continue;
            for (var entry : generation.liveEntries()) {
                try {
                    call.call(entry);
                } catch (Throwable t) {
                    Log.e(TAG, "Error in " + callback + " of " + entry.getClass().getName(), t);
                }
            }
        }
    }

    public static HotReloadOutcome hotReload(@Nullable String modulePackageName,
                                             @Nullable Bundle extras,
                                             @Nullable org.lsposed.lspd.models.Module newModule) {
        if (modulePackageName == null) {
            return unsupported("Hot reload was requested without a module");
        }
        var lock = reloadLocks.computeIfAbsent(modulePackageName, k -> new ReentrantLock());
        if (!lock.tryLock()) {
            return outcome(HOT_RELOAD_IN_PROGRESS,
                    "A reload of " + modulePackageName + " is already running in this process",
                    false, false);
        }
        try {
            return runHotReload(modulePackageName, extras, newModule);
        } catch (Throwable t) {
            Log.e(TAG, "Hot reload of " + modulePackageName + " failed", t);
            return failed(describe(t));
        } finally {
            lock.unlock();
        }
    }

    private static HotReloadOutcome runHotReload(String packageName, @Nullable Bundle extras,
                                                 @Nullable org.lsposed.lspd.models.Module newModule) {
        if (newModule == null) {
            return unsupported("No new generation of " + packageName + " was supplied");
        }
        var old = generations.get(packageName);
        if (old == null) {
            return unsupported(packageName + " is not loaded in " + LSPosedContext.processName);
        }
        if (old.targetApiVersion < XposedInterface.API_102) {
            return unsupported(packageName + " targets API " + old.targetApiVersion
                    + " and cannot be hot reloaded");
        }
        if (newModule.file == null || newModule.file.moduleClassNames == null
                || newModule.file.moduleClassNames.size() != 1) {
            return unsupported(packageName + " does not declare exactly one Java entry class");
        }

        var oldEntries = old.liveEntries();
        if (oldEntries.isEmpty()) {
            Log.w(TAG, "No attached entry of " + packageName + " can accept a hot reload");
            return unsupported("Every entry of " + packageName + " has detached in this process");
        }

        var newGeneration = buildGeneration(newModule, old.isSystemServer, old.processName);
        if (newGeneration == null) {
            return unsupported("Cannot build a new generation of " + packageName);
        }

        synchronized (LSPosedBridge.lockOf(packageName)) {
            old.context.freeze();
        }

        var savedState = new Object[1];
        var reloadingParam = new HotReloadingParam() {
            @Nullable
            @Override
            public Bundle getExtras() {
                return extras;
            }

            @Override
            public void setSavedInstanceState(@Nullable Object outState) {
                rejectOldGenerationState(outState, old.classLoader);
                savedState[0] = outState;
            }
        };

        var accepted = true;
        try {
            for (var entry : oldEntries) {
                if (!entry.onHotReloading(reloadingParam)) {
                    accepted = false;
                    break;
                }
            }
        } catch (Throwable t) {
            old.context.unfreeze();
            Log.e(TAG, "onHotReloading of " + packageName + " threw", t);
            return failed(describe(t));
        }
        if (!accepted) {
            old.context.unfreeze();
            Log.d(TAG, packageName + " refused the hot reload");
            return refusal();
        }

        var oldHandles = LSPosedBridge.snapshotHandles(packageName);

        for (var entry : oldEntries) activeModules.remove(entry);
        for (var entry : newGeneration.entries()) activeModules.add(entry);

        var reloadedParam = new HotReloadedParam() {
            @Override
            public boolean isSystemServer() {
                return old.isSystemServer;
            }

            @NonNull
            @Override
            public String getProcessName() {
                return old.processName;
            }

            @Nullable
            @Override
            public Bundle getExtras() {
                return extras;
            }

            @Nullable
            @Override
            public Object getSavedInstanceState() {
                return savedState[0];
            }

            @NonNull
            @Override
            public List<XposedInterface.HookHandle> getOldHookHandles() {
                return oldHandles;
            }
        };

        generations.put(packageName, newGeneration);

        Throwable failure = null;
        for (var entry : newGeneration.liveEntries()) {
            if (failure != null) break;
            try {
                entry.onHotReloaded(reloadedParam);
            } catch (Throwable t) {
                failure = t;
            }
        }

        if (failure != null) {
            Log.e(TAG, "onHotReloaded of " + packageName + " threw", failure);
            return failed(describe(failure), true);
        }

        Log.d(TAG, "Hot reloaded " + packageName);
        return outcome(HOT_RELOAD_SUCCEEDED, null, false, true);
    }

    private static void rejectOldGenerationState(@Nullable Object state, ClassLoader oldClassLoader) {
        if (state == null) return;
        reject(state, oldClassLoader);
        if (state instanceof Object[] array) {
            for (var element : array) {
                if (element != null) reject(element, oldClassLoader);
            }
        } else if (state instanceof Collection<?> collection) {
            for (var element : collection) {
                if (element != null) reject(element, oldClassLoader);
            }
        } else if (state instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                if (entry.getKey() != null) reject(entry.getKey(), oldClassLoader);
                if (entry.getValue() != null) reject(entry.getValue(), oldClassLoader);
            }
        }
    }

    private static void reject(Object value, ClassLoader oldClassLoader) {
        if (definedBy(value.getClass(), oldClassLoader)) {
            throw new IllegalArgumentException(
                    "Saved instance state contains " + value.getClass().getName()
                            + ", which was created under the old module classloader");
        }
    }

    private static boolean definedBy(Class<?> clazz, ClassLoader classLoader) {
        ClassLoader loader = (clazz.isArray() ? clazz.getComponentType() : clazz).getClassLoader();
        while (loader != null) {
            if (loader == classLoader) return true;
            loader = loader.getParent();
        }
        return false;
    }

    private static HotReloadOutcome outcome(int status, @Nullable String message,
                                            boolean refused, boolean generationChanged) {
        var result = new HotReloadOutcome();
        result.status = status;
        result.message = message;
        result.refused = refused;
        result.generationChanged = generationChanged;
        return result;
    }

    private static HotReloadOutcome unsupported(String message) {
        return outcome(HOT_RELOAD_UNSUPPORTED, message, false, false);
    }

    private static HotReloadOutcome failed(String message) {
        return failed(message, false);
    }

    private static HotReloadOutcome failed(String message, boolean generationChanged) {
        return outcome(HOT_RELOAD_FAILED, message, false, generationChanged);
    }

    private static HotReloadOutcome refusal() {
        return outcome(HOT_RELOAD_FAILED, null, true, false);
    }

    private static String describe(Throwable t) {
        return t.getClass().getName() + ": " + (t.getMessage() == null ? "no message" : t.getMessage());
    }
}
