package org.lsposed.lspd.service;

import org.lsposed.lspd.models.HotReloadOutcome;

interface IHotReloadOutcomeReceiver {
    oneway void onOutcome(in HotReloadOutcome outcome) = 1;
}
