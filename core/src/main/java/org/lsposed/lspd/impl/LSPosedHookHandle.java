package org.lsposed.lspd.impl;

import androidx.annotation.Nullable;

import org.lsposed.lspd.nativebridge.HookBridge;

import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.error.HookFailedError;

public final class LSPosedHookHandle implements XposedInterface.HookHandle {

    static final class Registration {
        final XposedInterface.Hooker hooker;
        final Object registered;
        final int priority;
        final XposedInterface.ExceptionMode exceptionMode;
        final boolean protective;
        final String id;

        Registration(XposedInterface.Hooker hooker, Object registered, int priority,
                     XposedInterface.ExceptionMode exceptionMode, boolean protective, String id) {
            this.hooker = hooker;
            this.registered = registered;
            this.priority = priority;
            this.exceptionMode = exceptionMode;
            this.protective = protective;
            this.id = id;
        }
    }

    private static final class IdKey {
        private final String moduleId;
        private final Executable executable;
        private final String id;

        IdKey(String moduleId, Executable executable, String id) {
            this.moduleId = moduleId;
            this.executable = executable;
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof IdKey)) return false;
            var key = (IdKey) o;
            return moduleId.equals(key.moduleId) && executable.equals(key.executable)
                    && id.equals(key.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(moduleId, executable, id);
        }
    }

    static final class Registry {
        private static final Map<IdKey, LSPosedHookHandle> ids = new ConcurrentHashMap<>();
        private static final Map<String, Set<LSPosedHookHandle>> byModule = new ConcurrentHashMap<>();
        private static final Map<String, Object> locks = new ConcurrentHashMap<>();

        static Object lockOf(String moduleId) {
            return locks.computeIfAbsent(moduleId, k -> new Object());
        }

        @Nullable
        static LSPosedHookHandle findId(String moduleId, Executable origin, String id) {
            return ids.get(new IdKey(moduleId, origin, id));
        }

        static void claimId(String moduleId, Executable origin, String id, LSPosedHookHandle handle) {
            ids.put(new IdKey(moduleId, origin, id), handle);
        }

        static void releaseId(String moduleId, Executable origin, String id, LSPosedHookHandle handle) {
            ids.remove(new IdKey(moduleId, origin, id), handle);
        }

        static void track(String moduleId, LSPosedHookHandle handle) {
            byModule.computeIfAbsent(moduleId, k -> ConcurrentHashMap.newKeySet()).add(handle);
        }

        static void forget(String moduleId, LSPosedHookHandle handle) {
            var handles = byModule.get(moduleId);
            if (handles != null) handles.remove(handle);
        }

        static List<XposedInterface.HookHandle> liveHandles(String moduleId) {
            var handles = byModule.get(moduleId);
            if (handles == null) return Collections.emptyList();
            var result = new ArrayList<XposedInterface.HookHandle>();
            for (var handle : handles) {
                if (handle.isLive()) result.add(handle);
            }
            return result;
        }
    }

    private final XposedInterface context;
    private final Executable executable;
    private final String moduleId;

    private volatile Registration record;
    private volatile boolean live = true;

    LSPosedHookHandle(XposedInterface context, Executable executable, @Nullable String moduleId,
                      Registration record) {
        this.context = context;
        this.executable = executable;
        this.moduleId = moduleId;
        this.record = record;
    }

    boolean isLive() {
        return live;
    }

    Registration record() {
        return record;
    }

    XposedInterface context() {
        return context;
    }

    @Nullable
    @Override
    public String getId() {
        return record.id;
    }

    @Override
    public Executable getExecutable() {
        return executable;
    }

    @Override
    public void unhook() {
        if (moduleId == null) {
            synchronized (this) {
                if (!live) return;
                live = false;
            }
            HookBridge.unhookMethod(executable, record.registered, false);
            return;
        }
        synchronized (Registry.lockOf(moduleId)) {
            if (!live) return;
            live = false;
            if (record.id != null) Registry.releaseId(moduleId, executable, record.id, this);
            Registry.forget(moduleId, this);
            HookBridge.unhookMethod(executable, record.registered, false);
        }
    }

    @Override
    public XposedInterface.HookHandle replaceHook(XposedInterface.Hooker hooker) {
        if (hooker == null) throw new IllegalArgumentException("hooker is null");

        var moduleId = this.moduleId;
        if (moduleId == null) {
            throw new IllegalStateException("This hook does not belong to a module");
        }

        synchronized (Registry.lockOf(moduleId)) {
            if (!live) throw new IllegalStateException("This hook handle is no longer valid");
            var current = record;
            return swapLocked(new Registration(
                    hooker,
                    wrap(hooker, current.protective),
                    current.priority,
                    current.exceptionMode,
                    current.protective,
                    current.id));
        }
    }

    LSPosedHookHandle swapLocked(Registration replacement) {
        if (!HookBridge.replaceCallback(false, executable, record.registered,
                replacement.registered, replacement.priority)) {
            throw new HookFailedError("Cannot replace the hook on " + executable);
        }

        live = false;
        Registry.forget(moduleId, this);

        var handle = new LSPosedHookHandle(context, executable, moduleId, replacement);
        Registry.track(moduleId, handle);
        if (replacement.id != null) {
            Registry.claimId(moduleId, executable, replacement.id, handle);
        }
        return handle;
    }

    private Object wrap(XposedInterface.Hooker hooker, boolean protective) {
        return protective ? new LSPosedBridge.ProtectiveHooker(context, hooker) : hooker;
    }
}
