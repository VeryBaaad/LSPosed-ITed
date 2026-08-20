package org.lsposed.lspd;

import org.lsposed.lspd.models.HotReloadOutcome;

interface IHotReloadReceiver {
    oneway void onOutcome(in HotReloadOutcome outcome) = 1;
}
