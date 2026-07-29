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
package org.lsposed.lspd.deopt

import de.robv.android.xposed.XposedHelpers
import org.lsposed.lspd.deopt.InlinedMethodCallers.KEY_BOOT_IMAGE
import org.lsposed.lspd.deopt.InlinedMethodCallers.KEY_BOOT_IMAGE_MIUI_RES
import org.lsposed.lspd.deopt.InlinedMethodCallers.KEY_SYSTEM_SERVER
import org.lsposed.lspd.nativebridge.HookBridge
import org.lsposed.lspd.util.Hookers
import org.lsposed.lspd.util.Utils

object PrebuiltMethodsDeopter {
    @JvmStatic
    fun deoptMethods(where: String, cl: ClassLoader?) {
        val callers = InlinedMethodCallers[where] ?: return
        for (caller in callers) {
            try {
                if (caller.size < 2) continue
                val className = caller[0] as? String ?: continue
                val methodName = caller[1] as? String ?: continue
                val params = caller.copyOfRange(2, caller.size)
                val method = if (methodName == "<init>") {
                    XposedHelpers.findConstructorExactIfExists(className, cl, *params)
                } else {
                    XposedHelpers.findMethodExactIfExists(className, cl, methodName, *params)
                }
                if (method != null) {
                    Hookers.logD("deoptimizing $method")
                    HookBridge.deoptimizeMethod(method)
                }
            } catch (throwable: Throwable) {
                Utils.logE("error when deopting method: ${caller.contentToString()}", throwable)
            }
        }
    }

    @JvmStatic
    fun deoptBootMethods() {
        // todo check if has been done before
        deoptMethods(KEY_BOOT_IMAGE, null)
    }

    @JvmStatic
    fun deoptResourceMethods() {
        if (Utils.isMIUI) {
            //deopt these only for MIUI
            deoptMethods(KEY_BOOT_IMAGE_MIUI_RES, null)
        }
    }

    @JvmStatic
    fun deoptSystemServerMethods(sysCL: ClassLoader?) {
        deoptMethods(KEY_SYSTEM_SERVER, sysCL)
    }
}
