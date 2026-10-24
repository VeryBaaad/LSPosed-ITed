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
 */

package org.lsposed.lspd.service;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.List;

import io.github.libxposed.service.HookedProcess;
import io.github.libxposed.service.IXposedScopeCallback;

final class LSPModuleServiceLegacy100 extends Binder {

    private static final String TAG = "LSPosedModuleService100";

    private static final String SERVICE_DESCRIPTOR = "io.github.libxposed.service.IXposedService";
    private static final String SCOPE_CALLBACK_DESCRIPTOR =
            "io.github.libxposed.service.IXposedScopeCallback";

    private static final int FRAMEWORK_PRIVILEGE_ROOT = 0;

    private final LSPModuleService service;

    LSPModuleServiceLegacy100(LSPModuleService service) {
        this.service = service;
    }

    @Override
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        switch (code) {
            case INTERFACE_TRANSACTION:
                reply.writeString(SERVICE_DESCRIPTOR);
                return true;

            case 1: // int getAPIVersion()
                data.enforceInterface(SERVICE_DESCRIPTOR);
                service.ensureCaller();
                reply.writeNoException();
                reply.writeInt(LSPModuleService.XPOSED_API_VERSION);
                return true;
            case 2: { // String getFrameworkName()
                data.enforceInterface(SERVICE_DESCRIPTOR);
                var name = service.getFrameworkName();
                reply.writeNoException();
                reply.writeString(name);
                return true;
            }
            case 3: { // String getFrameworkVersion()
                data.enforceInterface(SERVICE_DESCRIPTOR);
                var version = service.getFrameworkVersion();
                reply.writeNoException();
                reply.writeString(version);
                return true;
            }
            case 4: { // long getFrameworkVersionCode()
                data.enforceInterface(SERVICE_DESCRIPTOR);
                var versionCode = service.getFrameworkVersionCode();
                reply.writeNoException();
                reply.writeLong(versionCode);
                return true;
            }
            case 5: // int getFrameworkPrivilege()
                data.enforceInterface(SERVICE_DESCRIPTOR);
                reply.writeNoException();
                reply.writeInt(FRAMEWORK_PRIVILEGE_ROOT);
                return true;

            case 10: { // List<String> getScope()
                data.enforceInterface(SERVICE_DESCRIPTOR);
                var scope = service.getScope();
                reply.writeNoException();
                reply.writeStringList(scope);
                return true;
            }
            case 11: // oneway void requestScope(String packageName, IXposedScopeCallback cb)
                data.enforceInterface(SERVICE_DESCRIPTOR);
                requestScope(data.readString(), data.readStrongBinder());
                return true;
            case 12: { // String removeScope(String packageName)
                data.enforceInterface(SERVICE_DESCRIPTOR);
                var error = removeScope(data.readString());
                reply.writeNoException();
                reply.writeString(error);
                return true;
            }

            case 13: { // List<HookedProcess> getRunningTargets()
                data.enforceInterface(SERVICE_DESCRIPTOR);
                var targets = service.getRunningTargets();
                reply.writeNoException();
                writeLegacyTargets(reply, targets);
                return true;
            }

            case 20: { // Bundle requestRemotePreferences(String group)
                data.enforceInterface(SERVICE_DESCRIPTOR);
                var group = data.readString();
                var prefs = service.requestRemotePreferences(group);
                reply.writeNoException();
                reply.writeBundle(prefs);
                return true;
            }
            case 21: // void updateRemotePreferences(String group, Bundle diff)
                data.enforceInterface(SERVICE_DESCRIPTOR);
                service.updateRemotePreferences(data.readString(),
                        data.readBundle(getClass().getClassLoader()));
                reply.writeNoException();
                return true;
            case 22: // void deleteRemotePreferences(String group)
                data.enforceInterface(SERVICE_DESCRIPTOR);
                service.deleteRemotePreferences(data.readString());
                reply.writeNoException();
                return true;

            case 30: { // String[] listRemoteFiles()
                data.enforceInterface(SERVICE_DESCRIPTOR);
                var files = service.listRemoteFiles();
                reply.writeNoException();
                reply.writeStringArray(files);
                return true;
            }
            case 31: { // ParcelFileDescriptor openRemoteFile(String name)
                data.enforceInterface(SERVICE_DESCRIPTOR);
                var fd = service.openRemoteFile(data.readString());
                reply.writeNoException();
                reply.writeParcelable(fd, 0);
                return true;
            }
            case 32: { // boolean deleteRemoteFile(String name)
                data.enforceInterface(SERVICE_DESCRIPTOR);
                var deleted = service.deleteRemoteFile(data.readString());
                reply.writeNoException();
                reply.writeInt(deleted ? 1 : 0);
                return true;
            }

            default:
                return super.onTransact(code, data, reply, flags);
        }
    }

    private void requestScope(@Nullable String packageName, @Nullable IBinder callback) {
        if (packageName == null || callback == null) return;
        var adapter = new LegacyScopeCallback(packageName, callback);
        try {
            adapter.prompted();
            service.requestScope(java.util.Collections.singletonList(packageName), adapter);
        } catch (Throwable t) {
            adapter.failed(packageName, t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    @Nullable
    private String removeScope(@Nullable String scopePackageName) {
        if (scopePackageName == null) return "Invalid request";
        try {
            var userId = service.ensureCaller();
            if (!ConfigManager.getInstance()
                    .removeModuleScope(service.modulePackageName(),
                            java.util.Collections.singletonList(scopePackageName), userId)) {
                return "Invalid request";
            }
            return null;
        } catch (Throwable t) {
            Log.w(TAG, "removeScope failed for " + service.modulePackageName(), t);
            return t.getMessage() == null ? t.toString() : t.getMessage();
        }
    }

    private static void writeLegacyTargets(Parcel reply, List<HookedProcess> targets) {
        reply.writeInt(targets.size());
        for (var target : targets) {
            reply.writeInt(1);
            reply.writeInt(target.uid);
            reply.writeInt(target.pid);
            reply.writeString(target.processName);
            reply.writeInt(target.state == HookedProcess.TARGET_STATE_UP_TO_DATE ? 1 : 0);
        }
    }

    private static final class LegacyScopeCallback extends IXposedScopeCallback.Stub {

        private final String packageName;
        private final IBinder callback;

        LegacyScopeCallback(String packageName, IBinder callback) {
            this.packageName = packageName;
            this.callback = callback;
        }

        @Override
        public void onScopeRequestApproved(List<String> approved) {
            for (var approvedPackage : approved) {
                send(2, approvedPackage);
            }
        }

        @Override
        public void onScopeRequestFailed(String message) {
            if (message == null) {
                failed(packageName, "Unknown error");
            } else if (message.equals("User rejected")) {
                send(3, packageName);
            } else if (message.equals("Timeout")) {
                send(4, packageName);
            } else {
                failed(packageName, message);
            }
        }

        void prompted() {
            send(1, packageName);
        }

        void failed(String packageName, String message) {
            var data = Parcel.obtain();
            try {
                data.writeInterfaceToken(SCOPE_CALLBACK_DESCRIPTOR);
                data.writeString(packageName);
                data.writeString(message);
                callback.transact(5, data, null, IBinder.FLAG_ONEWAY);
            } catch (RemoteException ignored) {
            } finally {
                data.recycle();
            }
        }

        private void send(int code, String packageName) {
            var data = Parcel.obtain();
            try {
                data.writeInterfaceToken(SCOPE_CALLBACK_DESCRIPTOR);
                data.writeString(packageName);
                callback.transact(code, data, null, IBinder.FLAG_ONEWAY);
            } catch (RemoteException ignored) {
                // The module app is gone; nothing left to report to.
            } finally {
                data.recycle();
            }
        }
    }
}
