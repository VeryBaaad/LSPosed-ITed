package org.lsposed.lspd.service;

import org.lsposed.lspd.models.Module;
import org.lsposed.lspd.service.IHotReloadOutcomeReceiver;

interface IProcessChannel {
    oneway void hotReload(String modulePackageName, in Bundle extras, in Module module,
                          IHotReloadOutcomeReceiver receiver) = 1;
}