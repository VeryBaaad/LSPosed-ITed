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

import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

final class ProcessFreezer {

    private static final String TAG = "LSPosedFreezer";
    private static final String CGROUP_ROOT = "/sys/fs/cgroup";

    private ProcessFreezer() {
    }

    @Nullable
    private static File freezeFile(int pid) {
        try {
            var lines = Files.readAllLines(new File("/proc/" + pid + "/cgroup").toPath());
            String path = null;
            for (var line : lines) {
                if (!line.startsWith("0::")) continue;
                path = line.substring(3).trim();
                break;
            }
            if (path == null || path.isEmpty() || path.equals("/")) return null;
            if (!path.contains("/pid_")) return null;
            var file = new File(CGROUP_ROOT + path + "/cgroup.freeze");
            return file.exists() ? file : null;
        } catch (Throwable t) {
            return null;
        }
    }

    static boolean isFrozen(int pid) {
        var file = freezeFile(pid);
        if (file == null) return false;
        try {
            return "1".equals(read(file).trim());
        } catch (Throwable t) {
            return false;
        }
    }

    @Nullable
    static Runnable thaw(int pid) {
        var file = freezeFile(pid);
        if (file == null) return null;
        final boolean wasFrozen;
        try {
            wasFrozen = "1".equals(read(file).trim());
        } catch (Throwable t) {
            return null;
        }
        if (!wasFrozen) return null;

        try {
            write(file, "0");
        } catch (Throwable t) {
            Log.w(TAG, "Cannot thaw pid=" + pid + " through " + file.getPath(), t);
            return null;
        }

        Log.d(TAG, "Thawed pid=" + pid + " for a daemon transaction");
        return () -> {
            try {
                if ("0".equals(read(file).trim())) {
                    write(file, "1");
                } else {
                    Log.d(TAG, "Left pid=" + pid + " alone: something else changed its freezer state");
                }
            } catch (Throwable t) {
                Log.w(TAG, "Cannot re-freeze pid=" + pid, t);
            }
        };
    }

    private static String read(File file) throws Exception {
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        return lines.isEmpty() ? "" : lines.get(0);
    }

    private static void write(File file, String value) throws Exception {
        Files.write(file.toPath(), value.getBytes(StandardCharsets.UTF_8));
    }
}
