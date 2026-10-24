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
 * Copyright (C) 2021 LSPosed Contributors
 */

package org.lsposed.lspd.service;

import static org.lsposed.lspd.service.PackageService.PER_USER_RANGE;

import android.content.AttributionSource;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.lsposed.daemon.BuildConfig;
import org.lsposed.lspd.models.HotReloadOutcome;
import org.lsposed.lspd.models.Module;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.service.HookedProcess;
import io.github.libxposed.service.IHotReloadCallback;
import io.github.libxposed.service.IXposedScopeCallback;
import io.github.libxposed.service.IXposedService;

public class LSPModuleService extends IXposedService.Stub {

    private final static String TAG = "LSPosedModuleService";

    private final static ExecutorService hotReloadExecutor =
            Executors.newCachedThreadPool(r -> new Thread(r, "LSPosedHotReload"));
    private final static Set<Integer> uidSet = ConcurrentHashMap.newKeySet();
    private final static Set<ModuleBinderKey> sentBinderSet = ConcurrentHashMap.newKeySet();
    private final static Set<ModuleBinderKey> sendingBinderSet = ConcurrentHashMap.newKeySet();
    private final static Map<String, LSPModuleService> serviceMap = new ConcurrentHashMap<>();
    private final static ExecutorService binderExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "module-binder-delivery"));

    private final static long RELOAD_TIMEOUT_SECONDS = 30L;

    static final int XPOSED_API_VERSION = XposedInterface.LIB_API;

    public final static String FILES_DIR = "files";

    private final @NonNull
    Module loadedModule;

    private LSPModuleService(@NonNull Module loadedModule) {
        this.loadedModule = loadedModule;
    }

    private static LSPModuleService serviceOf(Module module) {
        return serviceMap.computeIfAbsent(module.packageName, pkg -> new LSPModuleService(module));
    }

    static void forget(String packageName) {
        serviceMap.remove(packageName);
    }

    static void uidClear() {
        uidSet.clear();
        sentBinderSet.clear();
        sendingBinderSet.clear();
    }

    static void uidStarts(int uid) {
        if (uidSet.add(uid)) {
            sendBinderForUid(uid);
        }
    }

    static void uidGone(int uid) {
        uidSet.remove(uid);
        sentBinderSet.removeIf(k -> k.uid == uid);
        sendingBinderSet.removeIf(k -> k.uid == uid);
    }

    static void autoHotReload(Module module) {
        if (module.file == null || !module.file.autoHotReload) return;
        var appService = ServiceManager.getApplicationService();
        if (appService == null) return;
        var service = serviceOf(module);
        for (var target : appService.staleHotReloadTargets(module.packageName)) {
            if (!target.hotReloadable) continue;
            if (!appService.beginHotReload(target)) continue;
            Log.d(TAG, "Auto hot reloading " + module.packageName + " in " + target.processName);
            hotReloadExecutor.execute(() -> service.runHotReload(target, null, null));
        }
    }

    static void sendBindersForRunningModules() {
        for (int uid : uidSet) {
            sendBinderForUid(uid);
        }
    }

    static void sendBinderForRunningModule(String packageName) {
        for (int uid : uidSet) {
            var module = ConfigManager.getInstance().getModule(uid);
            if (module != null && Objects.equals(module.packageName, packageName)) {
                sendBinderForModule(module, uid);
            }
        }
    }

    private static void sendBinderForUid(int uid) {
        var module = ConfigManager.getInstance().getModule(uid);
        if (module != null) {
            sendBinderForModule(module, uid);
        }
    }

    private static void sendBinderForModule(Module module, int uid) {
        if (module.file == null || module.file.legacy) {
            return;
        }
        var key = new ModuleBinderKey(module.packageName, uid);
        if (sentBinderSet.contains(key) || !sendingBinderSet.add(key)) {
            return;
        }
        try {
            var service = serviceOf(module);
            binderExecutor.execute(() -> service.sendBinder(uid, key));
        } catch (Throwable e) {
            sendingBinderSet.remove(key);
            Log.w(TAG, "failed to schedule module binder for uid " + uid, e);
        }
    }

    private void sendBinder(int uid, ModuleBinderKey key) {
        var name = loadedModule.packageName;
        try {
            int userId = uid / PackageService.PER_USER_RANGE;
            if (!ConfigManager.getInstance().isModuleEnabledForUser(name, userId)) {
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    DeviceIdleService.addPowerSaveTempWhitelistApp(name, userId, "shell");
                    Log.d(TAG, "add " + userId + ":" + name + " to power save temp whitelist for 30s");
                    try {
                        Thread.sleep(400L);
                    } catch (InterruptedException e) {
                        Log.d(TAG, "sendBinder interrupted while waiting for whitelist, continuing for " + name, e);
                    }
                } catch (Throwable e) {
                    Log.e(TAG, "failed to add " + userId + ":" + name + " to power save temp whitelist", e);
                }
            }
            var authority = name + AUTHORITY_SUFFIX;
            var provider = ActivityManagerService.getContentProvider(authority, userId);
            for (int attempt = 1; provider == null && attempt < 3; attempt++) {
                Log.d(TAG, "no service provider for " + name + ", attempt " + attempt);
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException e) {
                    Log.d(TAG, "sendBinder interrupted during retry sleep for " + name + ", continuing", e);
                }
                provider = ActivityManagerService.getContentProvider(authority, userId);
            }
            if (provider == null) {
                Log.d(TAG, "no service provider for " + name + " after 3 attempts");
                return;
            }
            var extra = new Bundle();
            extra.putBinder("binder", binderForModule());
            Bundle reply = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                reply = provider.call(new AttributionSource.Builder(1000).setPackageName("android").build(), authority, SEND_BINDER, null, extra);
            } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
                reply = provider.call("android", null, authority, SEND_BINDER, null, extra);
            } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                reply = provider.call("android", authority, SEND_BINDER, null, extra);
            } else {
                reply = provider.call("android", SEND_BINDER, null, extra);
            }
            if (reply != null) {
                Log.d(TAG, "sent module binder to " + name);
                sentBinderSet.add(key);
            } else {
                Log.w(TAG, "failed to send module binder to " + name);
            }
        } catch (Throwable e) {
            Log.w(TAG, "failed to send module binder for uid " + uid, e);
        } finally {
            sendingBinderSet.remove(key);
        }
    }

    private static final class ModuleBinderKey {
        private final String packageName;
        private final int uid;

        private ModuleBinderKey(String packageName, int uid) {
            this.packageName = packageName;
            this.uid = uid;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ModuleBinderKey)) return false;
            var key = (ModuleBinderKey) o;
            return uid == key.uid && Objects.equals(packageName, key.packageName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(packageName, uid);
        }
    }

    private android.os.Binder binderForModule() {
        var module = ConfigManager.getInstance().getCachedModule(loadedModule.packageName);
        var file = module != null ? module.file : loadedModule.file;
        if (file == null || file.targetApiVersion != 100) return this;
        var binder = legacy100;
        if (binder == null) {
            synchronized (this) {
                if (legacy100 == null) legacy100 = new LSPModuleServiceLegacy100(this);
                binder = legacy100;
            }
        }
        return binder;
    }

    private volatile LSPModuleServiceLegacy100 legacy100 = null;

    int ensureCaller() throws RemoteException {
        return ensureModule();
    }

    @NonNull
    String modulePackageName() {
        return loadedModule.packageName;
    }

    int ensureModule() throws RemoteException {
        var appId = Binder.getCallingUid() % PER_USER_RANGE;
        if (loadedModule.appId != appId) {
            throw new RemoteException("Module " + loadedModule.packageName + " is not for uid " + Binder.getCallingUid());
        }
        return Binder.getCallingUid() / PER_USER_RANGE;
    }

    @Override
    public int getApiVersion() throws RemoteException {
        ensureModule();
        return XPOSED_API_VERSION;
    }

    @Override
    public String getFrameworkName() throws RemoteException {
        ensureModule();
        return "LSPosed";
    }

    @Override
    public String getFrameworkVersion() throws RemoteException {
        ensureModule();
        return BuildConfig.VERSION_NAME;
    }

    @Override
    public long getFrameworkVersionCode() throws RemoteException {
        ensureModule();
        return BuildConfig.VERSION_CODE;
    }

    @Override
    public long getFrameworkProperties() throws RemoteException {
        ensureModule();
        var properties = IXposedService.PROP_CAP_SYSTEM | IXposedService.PROP_CAP_REMOTE;
        if (ConfigManager.getInstance().dexObfuscate()) {
            properties |= IXposedService.PROP_RT_API_PROTECTION;
        }
        return properties;
    }

    @Override
    public List<String> getScope() throws RemoteException {
        var userId = ensureModule();
        var scope = ConfigManager.getInstance().getModuleScope(loadedModule.packageName);
        if (scope == null) return new ArrayList<>();
        var result = new ArrayList<String>();
        for (var application : scope) {
            if (application.userId != userId && !application.packageName.equals("system")) continue;
            if (result.contains(application.packageName)) continue;
            result.add(application.packageName);
        }
        return result;
    }

    @Override
    public void requestScope(List<String> packages, IXposedScopeCallback callback) throws RemoteException {
        Objects.requireNonNull(packages, "packages cannot be null");
        Objects.requireNonNull(callback, "callback cannot be null");
        var userId = ensureModule();
        var scopePackages = new ArrayList<>(packages);
        if (scopePackages.isEmpty()) {
            callback.onScopeRequestFailed("Invalid request");
            return;
        }
        if (ConfigManager.getInstance().scopeRequestBlocked(loadedModule.packageName, userId)) {
            callback.onScopeRequestFailed("Blocked by user");
        } else {
            LSPNotificationManager.requestModuleScope(loadedModule.packageName, userId, scopePackages, callback);
        }
    }

    @Override
    public void removeScope(List<String> packages) throws RemoteException {
        Objects.requireNonNull(packages, "packages cannot be null");
        var userId = ensureModule();
        try {
            if (!ConfigManager.getInstance().removeModuleScope(loadedModule.packageName, packages, userId)) {
                throw new RemoteException("Invalid request");
            }
        } catch (Throwable e) {
            throw new RemoteException(e.getMessage());
        }
    }

    @Override
    public List<HookedProcess> getRunningTargets() throws RemoteException {
        var userId = ensureModule();
        return ServiceManager.getApplicationService().getHotReloadTargets(loadedModule.packageName, userId);
    }

    @Override
    public void hotReloadModule(long targetId, Bundle data, IHotReloadCallback callback) throws RemoteException {
        var userId = ensureModule();
        var appService = ServiceManager.getApplicationService();
        var target = appService.getHotReloadTarget(targetId, loadedModule.packageName, userId);
        if (target == null) {
            throw new SecurityException("Target " + targetId + " is not a target of " + loadedModule.packageName);
        }

        if (!target.hotReloadable) {
            report(callback, IXposedService.HOT_RELOAD_UNSUPPORTED,
                    "Module has no single Java entry class");
            return;
        }

        if (!appService.beginHotReload(target)) {
            report(callback, IXposedService.HOT_RELOAD_IN_PROGRESS, "A reload is already running");
            return;
        }

        hotReloadExecutor.execute(() -> runHotReload(target, data, callback));
    }

    private void runHotReload(LSPApplicationService.HotReloadTarget target, Bundle data,
                              IHotReloadCallback callback) {
        var appService = ServiceManager.getApplicationService();
        var status = IXposedService.HOT_RELOAD_FAILED;
        String message = "Hot reload did not run";
        Runnable refreeze = null;
        var loadedVersion = 0L;
        var answered = new CountDownLatch(1);
        var outcome = new AtomicReference<HotReloadOutcome>();

        try {
            var binder = appService.getHotReloadBinder(target);
            if (binder == null) {
                status = IXposedService.HOT_RELOAD_UNSUPPORTED;
                message = "Process " + target.processName + " has no hot reload entry point";
                return;
            }
            var newModule = ConfigManager.getInstance().getCachedModule(loadedModule.packageName);
            if (newModule == null) {
                status = IXposedService.HOT_RELOAD_UNSUPPORTED;
                message = "No installed generation of " + loadedModule.packageName + " to load";
                return;
            }
            if (newModule.file == null || newModule.file.targetApiVersion < 102) {
                status = IXposedService.HOT_RELOAD_UNSUPPORTED;
                message = "The installed generation of " + loadedModule.packageName
                        + " does not target API 102 or higher";
                return;
            }

            refreeze = ProcessFreezer.thaw(target.pid);
            if (ProcessFreezer.isFrozen(target.pid)) {
                status = IXposedService.HOT_RELOAD_FAILED;
                message = "Process " + target.processName + " is frozen and could not be thawed";
                return;
            }

            var receiver = new IHotReloadOutcomeReceiver.Stub() {
                @Override
                public void onOutcome(HotReloadOutcome result) {
                    outcome.set(result);
                    answered.countDown();
                }
            };
            binder.hotReload(loadedModule.packageName, data, newModule, receiver);

            if (!answered.await(RELOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                var gone = !appService.isProcessRegistered(target);
                status = gone
                        ? IXposedService.HOT_RELOAD_PROCESS_DIED
                        : IXposedService.HOT_RELOAD_FAILED;
                message = gone
                        ? "Process " + target.processName + " died during hot reload"
                        : "Process " + target.processName + " did not answer within " + RELOAD_TIMEOUT_SECONDS + "s";
                return;
            }

            var answer = outcome.get();
            if (answer == null) {
                status = IXposedService.HOT_RELOAD_FAILED;
                message = "Process " + target.processName + " answered with nothing";
                return;
            }

            status = answer.status;
            if (answer.generationChanged) loadedVersion = newModule.versionCode;
            if (answer.message != null) {
                message = answer.message;
            } else if (status == IXposedService.HOT_RELOAD_FAILED && !answer.refused) {
                message = "Hot reload failed without a diagnostic message";
            } else {
                message = null;
            }
        } catch (Throwable t) {
            var gone = !appService.isProcessRegistered(target);
            status = gone
                    ? IXposedService.HOT_RELOAD_PROCESS_DIED
                    : IXposedService.HOT_RELOAD_FAILED;
            message = gone
                    ? "Process " + target.processName + " died during hot reload"
                    : t.getClass().getName() + ": " + (t.getMessage() == null ? "no message" : t.getMessage());
            Log.e(TAG, "Hot reload of " + loadedModule.packageName + " failed", t);
        } finally {
            if (refreeze != null) refreeze.run();
            appService.endHotReload(target, stateFor(status), loadedVersion);
            report(callback, status, message);
        }
    }

    private static int stateFor(int status) {
        if (status == IXposedService.HOT_RELOAD_SUCCEEDED) {
            return HookedProcess.TARGET_STATE_UP_TO_DATE;
        }
        if (status == IXposedService.HOT_RELOAD_FAILED) {
            return HookedProcess.TARGET_STATE_FAILED;
        }
        return HookedProcess.TARGET_STATE_UP_TO_DATE;
    }

    private void report(@Nullable IHotReloadCallback callback, int status, @Nullable String message) {
        if (callback == null) return;
        try {
            callback.onHotReloadResult(status, message);
        } catch (Throwable t) {
            Log.w(TAG, "Cannot deliver hot reload result to " + loadedModule.packageName, t);
        }
    }

    @Override
    public Bundle requestRemotePreferences(String group) throws RemoteException {
        var userId = ensureModule();
        var bundle = new Bundle();
        bundle.putSerializable("map", ConfigManager.getInstance().getModulePrefs(loadedModule.packageName, userId, group));
        return bundle;
    }

    @Override
    public void updateRemotePreferences(String group, Bundle diff) throws RemoteException {
        var userId = ensureModule();
        Map<String, Object> values = new ArrayMap<>();
        if (diff.containsKey("delete")) {
            var deletes = (Set<?>) diff.getSerializable("delete");
            for (var key : deletes) {
                values.put((String) key, null);
            }
        }
        if (diff.containsKey("put")) {
            try {
                var puts = (Map<?, ?>) diff.getSerializable("put");
                for (var entry : puts.entrySet()) {
                    values.put((String) entry.getKey(), entry.getValue());
                }
            } catch (Throwable e) {
                Log.e(TAG, "updateRemotePreferences: ", e);
            }
        }
        try {
            ConfigManager.getInstance().updateModulePrefs(loadedModule.packageName, userId, group, values);
            ((LSPInjectedModuleService) loadedModule.service).onUpdateRemotePreferences(userId, group, diff);
        } catch (Throwable e) {
            throw new RemoteException(e.getMessage());
        }
    }

    @Override
    public void deleteRemotePreferences(String group) throws RemoteException {
        var userId = ensureModule();
        ConfigManager.getInstance().deleteModulePrefs(loadedModule.packageName, userId, group);
    }

    @Override
    public String[] listRemoteFiles() throws RemoteException {
        var userId = ensureModule();
        try {
            var dir = ConfigFileManager.resolveModuleDir(loadedModule.packageName, FILES_DIR, userId, Binder.getCallingUid());
            var files = dir.toFile().list();
            return files == null ? new String[0] : files;
        } catch (java.io.IOException e) {
            throw new RemoteException(e.getMessage());
        }
    }

    @Override
    public ParcelFileDescriptor openRemoteFile(String path) throws RemoteException {
        var userId = ensureModule();
        ConfigFileManager.ensureModuleFilePath(path);
        try {
            var dir = ConfigFileManager.resolveModuleDir(loadedModule.packageName, FILES_DIR, userId, Binder.getCallingUid());
            return ParcelFileDescriptor.open(dir.resolve(path).toFile(), ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_READ_WRITE);
        } catch (java.io.IOException e) {
            throw new RemoteException(e.getMessage());
        }
    }

    @Override
    public boolean deleteRemoteFile(String path) throws RemoteException {
        var userId = ensureModule();
        ConfigFileManager.ensureModuleFilePath(path);
        try {
            var dir = ConfigFileManager.resolveModuleDir(loadedModule.packageName, FILES_DIR, userId, Binder.getCallingUid());
            return dir.resolve(path).toFile().delete();
        } catch (java.io.IOException e) {
            throw new RemoteException(e.getMessage());
        }
    }
}
