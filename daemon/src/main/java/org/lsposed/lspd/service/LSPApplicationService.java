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
 * Copyright (C) 2021 - 2022 LSPosed Contributors
 */

package org.lsposed.lspd.service;

import static org.lsposed.lspd.service.ServiceManager.TAG;

import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.lsposed.lspd.models.Module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import io.github.libxposed.service.HookedProcess;

public class LSPApplicationService extends ILSPApplicationService.Stub {
    final static int DEX_TRANSACTION_CODE = 1310096052;
    final static int OBFUSCATION_MAP_TRANSACTION_CODE = 724533732;

    static final int FIRST_APPLICATION_UID = 10000;

    // key: <uid, pid>
    private final Map<Pair<Integer, Integer>, ProcessInfo> processes = new ConcurrentHashMap<>();

    static final class HotReloadTarget {
        final long id;
        final String modulePackageName;
        final String processName;
        final int uid;
        final int pid;
        final boolean hotReloadable;
        volatile long loadedVersionCode;
        final AtomicInteger state = new AtomicInteger(HookedProcess.TARGET_STATE_UP_TO_DATE);

        HotReloadTarget(long id, String modulePackageName, String processName, int uid, int pid,
                        long loadedVersionCode, boolean hotReloadable) {
            this.id = id;
            this.modulePackageName = modulePackageName;
            this.processName = processName;
            this.uid = uid;
            this.pid = pid;
            this.loadedVersionCode = loadedVersionCode;
            this.hotReloadable = hotReloadable;
        }
    }

    private final Map<Long, HotReloadTarget> hotReloadTargets = new ConcurrentHashMap<>();

    private final AtomicLong nextHotReloadTargetId = new AtomicLong(1);

    static class ProcessInfo implements IBinder.DeathRecipient {
        final int uid;
        final int pid;
        final String processName;
        final IBinder heartBeat;
        final Map<String, Long> targetIds = new ConcurrentHashMap<>();

        volatile Runnable onDeath = null;

        volatile IProcessChannel channel = null;

        ProcessInfo(int uid, int pid, String processName, IBinder heartBeat) throws RemoteException {
            this.uid = uid;
            this.pid = pid;
            this.processName = processName;
            this.heartBeat = heartBeat;
            heartBeat.linkToDeath(this, 0);
            Log.d(TAG, "register " + this);
        }

        @Override
        public void binderDied() {
            Log.d(TAG, this + " is dead");
            heartBeat.unlinkToDeath(this, 0);
            var callback = onDeath;
            if (callback != null) callback.run();
        }

        @NonNull
        @Override
        public String toString() {
            return "ProcessInfo{" +
                    "uid=" + uid +
                    ", pid=" + pid +
                    ", processName='" + processName + '\'' +
                    ", heartBeat=" + heartBeat +
                    '}';
        }
    }

    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        Log.d(TAG, "LSPApplicationService.onTransact: code=" + code);
        switch (code) {
            case DEX_TRANSACTION_CODE: {
                var shm = ConfigManager.getInstance().getPreloadDex();
                if (shm == null) return false;
                // assume that write only a fd
                shm.writeToParcel(reply, 0);
                reply.writeLong(shm.getSize());
                return true;
            }
            case OBFUSCATION_MAP_TRANSACTION_CODE: {
                var obfuscation = ConfigManager.getInstance().dexObfuscate();
                var signatures = ObfuscationManager.getSignatures();
                reply.writeInt(signatures.size() * 2);
                for (Map.Entry<String, String> entry : signatures.entrySet()) {
                    reply.writeString(entry.getKey());
                    // return val = key if obfuscation disabled
                    reply.writeString(obfuscation ? entry.getValue() : entry.getKey());
                }
                return true;
            }
        }
        return super.onTransact(code, data, reply, flags);
    }

    public boolean registerHeartBeat(int uid, int pid, String processName, IBinder heartBeat) {
        var key = new Pair<>(uid, pid);
        try {
            var info = new ProcessInfo(uid, pid, processName, heartBeat);
            info.onDeath = () -> {
                processes.remove(key, info);
                forgetProcess(info);
            };
            processes.put(key, info);
            return true;
        } catch (RemoteException e) {
            return false;
        }
    }

    @Override
    public void attachProcessChannel(IProcessChannel channel) {
        final ProcessInfo info;
        try {
            info = ensureRegistered();
        } catch (RemoteException e) {
            Log.w(TAG, "Refusing a process channel from an unregistered caller", e);
            return;
        }
        info.channel = channel;
        Log.d(TAG, "Process channel attached for " + info.processName + " (pid=" + info.pid + ")");
    }

    private List<Module> getAllModulesList() throws RemoteException {
        var processInfo = ensureRegistered();
        if (processInfo.uid == Process.SYSTEM_UID && processInfo.processName.equals("system")) {
            return ConfigManager.getInstance().getModulesForSystemServer();
        }
        if (ServiceManager.getManagerService().isRunningManager(processInfo.pid, processInfo.uid))
            return Collections.emptyList();
        return ConfigManager.getInstance().getModulesForProcess(processInfo.processName, processInfo.uid);
    }

    @Override
    public List<Module> getLegacyModulesList() throws RemoteException {
        return getAllModulesList().stream().filter(m -> m.file.legacy).collect(Collectors.toList());
    }

    @Override
    public List<Module> getModulesList() throws RemoteException {
        var modules = getAllModulesList().stream().filter(m -> !m.file.legacy)
                .collect(Collectors.toList());
        recordHotReloadTargets(ensureRegistered(), modules);
        return modules;
    }

    @Override
    public String getPrefsPath(String packageName) throws RemoteException {
        ensureRegistered();
        return ConfigManager.getInstance().getPrefsPath(packageName, getCallingUid());
    }

    @Override
    public ParcelFileDescriptor requestInjectedManagerBinder(List<IBinder> binder) throws RemoteException {
        var processInfo = ensureRegistered();
        if (ServiceManager.getManagerService().postStartManager(processInfo.pid, processInfo.uid) ||
                ConfigManager.getInstance().isManager(processInfo.uid)) {
            binder.add(ServiceManager.getManagerService().obtainManagerBinder(processInfo.heartBeat, processInfo.pid, processInfo.uid));
        }
        return ConfigManager.getInstance().getManagerApk();
    }

    public boolean hasRegister(int uid, int pid) {
        return processes.containsKey(new Pair<>(uid, pid));
    }

    @NonNull
    private ProcessInfo ensureRegistered() throws RemoteException {
        var uid = getCallingUid();
        var pid = getCallingPid();
        var key = new Pair<>(uid, pid);
        ProcessInfo processInfo = processes.getOrDefault(key, null);
        if (processInfo == null || uid != processInfo.uid || pid != processInfo.pid) {
            processes.remove(key, processInfo);
            Log.w(TAG, "non-authorized: info=" + processInfo + " uid=" + uid + " pid=" + pid);
            throw new RemoteException("Not registered");
        }
        return processInfo;
    }

    private void recordHotReloadTargets(ProcessInfo info, List<Module> modules) {
        for (var module : modules) {
            info.targetIds.computeIfAbsent(module.packageName, pkg -> {
                var id = nextHotReloadTargetId.getAndIncrement();
                hotReloadTargets.put(id, new HotReloadTarget(
                        id,
                        pkg,
                        info.processName,
                        info.uid,
                        info.pid,
                        module.versionCode,
                        module.file != null
                                && module.file.targetApiVersion >= 102
                                && module.file.moduleClassNames != null
                                && module.file.moduleClassNames.size() == 1));
                return id;
            });
        }
    }

    private void forgetProcess(ProcessInfo info) {
        for (var id : info.targetIds.values()) {
            hotReloadTargets.remove(id);
        }
        info.targetIds.clear();
        info.channel = null;
    }

    private boolean addressableBy(HotReloadTarget target, int userId) {
        return target.uid < FIRST_APPLICATION_UID
                || target.uid / PackageService.PER_USER_RANGE == userId;
    }

    public List<HookedProcess> getHotReloadTargets(String modulePackageName, int userId) {
        prune();
        var installedVersion = ConfigManager.getInstance().getModuleVersion(modulePackageName);
        var result = new ArrayList<HookedProcess>();
        for (var target : hotReloadTargets.values()) {
            if (!target.modulePackageName.equals(modulePackageName)
                    || !addressableBy(target, userId)) continue;
            var hooked = new HookedProcess();
            hooked.targetId = target.id;
            hooked.uid = target.uid;
            hooked.pid = target.pid;
            hooked.processName = target.processName;
            hooked.state = reportedState(target, installedVersion);
            hooked.loadedVersionCode = target.loadedVersionCode;
            result.add(hooked);
        }
        return result;
    }

    private int reportedState(HotReloadTarget target, long installedVersion) {
        var state = target.state.get();
        if (state != HookedProcess.TARGET_STATE_UP_TO_DATE) return state;
        if (target.loadedVersionCode == 0L || installedVersion == 0L) return state;
        return installedVersion != target.loadedVersionCode
                ? HookedProcess.TARGET_STATE_STALE
                : state;
    }

    public void backfillLoadedVersions() {
        for (var target : hotReloadTargets.values()) {
            if (target.loadedVersionCode != 0L) continue;
            var version = ConfigManager.getInstance().getModuleVersion(target.modulePackageName);
            if (version != 0L) target.loadedVersionCode = version;
        }
    }

    public void forgetHotReloadTargets(String modulePackageName) {
        hotReloadTargets.values().removeIf(t -> t.modulePackageName.equals(modulePackageName));
        for (var info : processes.values()) {
            info.targetIds.remove(modulePackageName);
        }
    }

    public void retainHotReloadTargets(java.util.Set<String> modulePackageNames) {
        hotReloadTargets.values().removeIf(t -> !modulePackageNames.contains(t.modulePackageName));
        for (var info : processes.values()) {
            info.targetIds.keySet().retainAll(modulePackageNames);
        }
    }

    public List<HotReloadTarget> staleHotReloadTargets(String modulePackageName) {
        var installedVersion = ConfigManager.getInstance().getModuleVersion(modulePackageName);
        if (installedVersion == 0L) return Collections.emptyList();
        var result = new ArrayList<HotReloadTarget>();
        for (var target : hotReloadTargets.values()) {
            if (!target.modulePackageName.equals(modulePackageName)) continue;
            if (!target.hotReloadable) continue;
            if (target.loadedVersionCode == 0L) continue;
            if (target.loadedVersionCode == installedVersion) continue;
            result.add(target);
        }
        return result;
    }

    @Nullable
    public HotReloadTarget getHotReloadTarget(long targetId, String modulePackageName, int userId) {
        var target = hotReloadTargets.get(targetId);
        if (target == null) return null;
        if (!target.modulePackageName.equals(modulePackageName)) return null;
        if (!addressableBy(target, userId)) return null;
        return target;
    }

    public boolean isProcessRegistered(HotReloadTarget target) {
        return processes.containsKey(new Pair<>(target.uid, target.pid));
    }

    public boolean beginHotReload(HotReloadTarget target) {
        while (true) {
            var current = target.state.get();
            if (current == HookedProcess.TARGET_STATE_RELOADING) return false;
            if (target.state.compareAndSet(current, HookedProcess.TARGET_STATE_RELOADING))
                return true;
        }
    }

    public void endHotReload(HotReloadTarget target, int state, long loadedVersionCode) {
        if (loadedVersionCode != 0L) target.loadedVersionCode = loadedVersionCode;
        target.state.set(state);
    }

    @Nullable
    public IProcessChannel getHotReloadBinder(HotReloadTarget target) {
        var info = processes.get(new Pair<>(target.uid, target.pid));
        return info == null ? null : info.channel;
    }

    public void prune() {
        for (var entry : processes.entrySet()) {
            var info = entry.getValue();
            if (!info.heartBeat.isBinderAlive()) {
                processes.remove(entry.getKey(), info);
                forgetProcess(info);
            }
        }
    }
}
