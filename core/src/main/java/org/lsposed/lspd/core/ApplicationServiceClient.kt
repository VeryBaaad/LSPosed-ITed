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

package org.lsposed.lspd.core

import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import org.lsposed.lspd.models.Module
import org.lsposed.lspd.service.ILSPApplicationService
import org.lsposed.lspd.util.Utils

class ApplicationServiceClient @Throws(RemoteException::class) private constructor(
    @JvmField val service: ILSPApplicationService,
    @JvmField val processName: String
) : ILSPApplicationService, IBinder.DeathRecipient {

    init {
        service.asBinder().linkToDeath(this, 0)
    }

    override fun getLegacyModulesList(): List<Module> {
        try {
            return service.legacyModulesList
        } catch (_: RemoteException) {
        } catch (_: NullPointerException) {
        }
        return emptyList()
    }

    override fun getModulesList(): List<Module> {
        try {
            return service.modulesList
        } catch (_: RemoteException) {
        } catch (_: NullPointerException) {
        }
        return emptyList()
    }

    override fun getPrefsPath(packageName: String?): String? {
        try {
            return service.getPrefsPath(packageName)
        } catch (_: RemoteException) {
        } catch (_: NullPointerException) {
        }
        return null
    }

    override fun requestInjectedManagerBinder(binder: MutableList<IBinder>?): ParcelFileDescriptor? {
        try {
            return service.requestInjectedManagerBinder(binder)
        } catch (_: RemoteException) {
        } catch (_: NullPointerException) {
        }
        return null
    }

    override fun asBinder(): IBinder = service.asBinder()

    override fun binderDied() {
        service.asBinder().unlinkToDeath(this, 0)
        serviceClient = null
    }

    companion object {
        @JvmField
        var serviceClient: ApplicationServiceClient? = null

        @JvmStatic
        @Synchronized
        fun Init(service: ILSPApplicationService, niceName: String) {
            val binder = service.asBinder()
            if (serviceClient == null && binder != null) {
                try {
                    serviceClient = ApplicationServiceClient(service, niceName)
                } catch (e: RemoteException) {
                    Utils.logE("link to death error: ", e)
                }
            }
        }
    }
}
