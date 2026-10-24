package org.lsposed.lspd.impl;

import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

import org.lsposed.lspd.models.HotReloadOutcome;
import org.lsposed.lspd.models.Module;
import org.lsposed.lspd.service.IHotReloadOutcomeReceiver;
import org.lsposed.lspd.service.IProcessChannel;
import org.lsposed.lspd.util.Utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LSPosedProcessChannel extends IProcessChannel.Stub {

    private static final String TAG = "LSPosedProcessChannel";

    private static final LSPosedProcessChannel INSTANCE = new LSPosedProcessChannel();

    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "LSPosedHotReload"));

    private LSPosedProcessChannel() {
    }

    public static LSPosedProcessChannel getInstance() {
        return INSTANCE;
    }

    @Override
    public void hotReload(String modulePackageName, Bundle extras, Module module,
                          IHotReloadOutcomeReceiver receiver) {
        var caller = Binder.getCallingUid();
        if (caller != Process.SYSTEM_UID && caller != 0) {
            Utils.logW(TAG + ": refusing a hot reload request from uid " + caller);
            return;
        }

        worker.execute(() -> {
            var outcome = LSPosedModuleManager.hotReload(modulePackageName, extras, module);
            try {
                if (receiver != null) receiver.onOutcome(outcome);
            } catch (Throwable t) {
                Utils.logW(TAG + ": cannot report the hot reload outcome", t);
            }
        });
    }
}
