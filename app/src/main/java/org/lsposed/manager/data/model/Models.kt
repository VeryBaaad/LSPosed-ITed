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
 * Copyright (C) 2021 - 2026 LSPosed Contributors
 */

package org.lsposed.manager.data.model

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import org.lsposed.manager.repo.model.OnlineModule
import org.lsposed.manager.util.ModuleUtil

data class ModuleItem(
    val module: ModuleUtil.InstalledModule,
    val title: String,
    val description: String,
    val versionName: String,
    val versionCode: Long,
    val enabled: Boolean,
    val warning: String?,
    val updateVersion: String?,
    val recommendedScope: List<String>,
) {
    val packageName: String get() = module.packageName
    val userId: Int get() = module.userId
    val legacy: Boolean get() = module.legacy

    val id: String get() = "$packageName!$userId"
}

data class ScopeAppItem(
    val packageName: String,
    val userId: Int,
    val label: CharSequence,
    val packageInfo: PackageInfo,
    val applicationInfo: ApplicationInfo,
    val recommended: Boolean,
    val denied: Boolean,
    val checked: Boolean,
) {
    val id: String get() = "$packageName!$userId"
}

data class ScopeSnapshot(
    val apps: List<ScopeAppItem>,
    val checked: Set<Pair<String, Int>>,
    val recommended: Set<Pair<String, Int>>,
    val enabled: Boolean,
    val loaded: Boolean,
    val denyList: List<String>,
)

data class RepoItem(
    val module: OnlineModule,
    val title: String,
    val packageName: String,
    val summary: String?,
    val updatedAt: String?,
    val upgradableVersion: String?,
    val installed: Boolean,
)

data class FrameworkInfo(
    val binderAlive: Boolean = false,
    val versionName: String = "",
    val versionCode: Int = -1,
    val apiVersion: Int = -1,
    val apiProtected: Boolean = false,
    val dex2OatWrapper: Int = 0,
    val dex2OatFlagsLoaded: Boolean = false,
    val sepolicyLoaded: Boolean = false,
    val systemServerRequested: Boolean = false,
    val statusNotificationEnabled: Boolean = false,
    val verboseLog: Boolean = false,
)

data class ManagerInfo(
    val packageName: String = "",
    val versionName: String = "",
    val versionCode: Long = 0,
    val device: String = "",
    val systemVersion: String = "",
    val abi: String = "",
)
