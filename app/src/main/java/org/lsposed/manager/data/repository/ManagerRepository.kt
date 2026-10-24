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

package org.lsposed.manager.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.lsposed.lspd.models.UserInfo
import org.lsposed.manager.App
import org.lsposed.manager.ConfigManager
import org.lsposed.manager.adapters.AppHelper
import org.lsposed.manager.data.model.ApplicationWithEquals
import org.lsposed.manager.data.model.ScopeAppItem
import org.lsposed.manager.data.model.ScopeSnapshot
import org.lsposed.manager.repo.RepoLoader
import org.lsposed.manager.repo.model.OnlineModule
import org.lsposed.manager.repo.model.Release
import org.lsposed.manager.util.ModuleUtil

/**
 * Bridges the legacy manager services (AIDL + [ModuleUtil] + [RepoLoader]) to the Compose UI.
 * Everything here follows the layout used by KernelSU's data layer: screens only observe flows.
 */
object ManagerRepository {
    private val moduleUtil get() = ModuleUtil.getInstance()
    private val repoLoader get() = RepoLoader.getInstance()

    private val _moduleRevision = MutableStateFlow(0)
    val moduleRevision: StateFlow<Int> = _moduleRevision.asStateFlow()

    private val _repoRevision = MutableStateFlow(0)
    val repoRevision: StateFlow<Int> = _repoRevision.asStateFlow()

    private val _repoError = MutableStateFlow<Throwable?>(null)
    val repoError: StateFlow<Throwable?> = _repoError.asStateFlow()

    @Volatile
    private var listenerRegistered = false

    fun ensureListeners() {
        if (listenerRegistered) return
        synchronized(this) {
            if (listenerRegistered) return
            moduleUtil.addListener(object : ModuleUtil.ModuleListener {
                override fun onModulesReloaded() {
                    _moduleRevision.update { it + 1 }
                }

                override fun onSingleModuleReloaded(module: ModuleUtil.InstalledModule?) {
                    _moduleRevision.update { it + 1 }
                }
            })
            repoLoader.addListener(object : RepoLoader.RepoListener {
                override fun onRepoLoaded() {
                    _repoError.value = null
                    _repoRevision.update { it + 1 }
                }

                override fun onModuleReleasesLoaded(module: OnlineModule?) {
                    _repoRevision.update { it + 1 }
                }

                override fun onThrowable(t: Throwable?) {
                    _repoError.value = t
                    _repoRevision.update { it + 1 }
                }
            })
            listenerRegistered = true
        }
        // The legacy singletons start loading as soon as they are created. Emit a revision so
        // late subscribers (a recreated ViewModel) immediately pick up the current snapshot.
        _moduleRevision.update { it + 1 }
        _repoRevision.update { it + 1 }
    }

    fun isBinderAlive(): Boolean = ConfigManager.isBinderAlive()

    fun modules(): Map<androidx.core.util.Pair<String, Int>, ModuleUtil.InstalledModule>? {
        ensureListeners()
        return moduleUtil.modules
    }

    fun users(): List<UserInfo> = moduleUtil.users ?: emptyList()

    fun module(packageName: String, userId: Int): ModuleUtil.InstalledModule? {
        ensureListeners()
        return moduleUtil.getModule(packageName, userId)
    }

    fun isModuleEnabled(packageName: String, userId: Int): Boolean =
        moduleUtil.isModuleEnabled(packageName, userId)

    fun enabledModuleCount(): Int = moduleUtil.enabledModulesCount

    fun refreshModules(reload: Boolean = true) {
        ensureListeners()
        if (reload) {
            App.getExecutorService().submit { moduleUtil.reloadInstalledModules() }
        } else {
            _moduleRevision.update { it + 1 }
        }
    }

    fun setModuleEnabled(packageName: String, userId: Int, enabled: Boolean): Boolean =
        moduleUtil.setModuleEnabled(packageName, userId, enabled)

    fun setModuleScope(
        packageName: String,
        legacy: Boolean,
        scope: Set<Pair<String, Int>>,
    ): Boolean {
        val applications = scope.mapTo(LinkedHashSet()) { (pkg, userId) ->
            ApplicationWithEquals(pkg, userId)
        }
        return ConfigManager.setModuleScope(packageName, legacy, applications)
    }

    fun uninstall(packageName: String, userId: Int): Boolean =
        ConfigManager.uninstallPackage(packageName, userId)

    fun installExistingPackageAsUser(packageName: String, userId: Int): Boolean =
        ConfigManager.installExistingPackageAsUser(packageName, userId)

    fun forceStop(packageName: String, userId: Int): Boolean =
        ConfigManager.forceStopPackage(packageName, userId)

    fun reboot() {
        ConfigManager.reboot()
    }

    fun resetScopeRequest(packageName: String, userId: Int): Boolean =
        ConfigManager.removeBlockedScopeRequest(packageName, userId)

    fun isModuleRepoLoaded(): Boolean = repoLoader.isRepoLoaded

    fun onlineModules(): Collection<OnlineModule>? = repoLoader.onlineModules

    fun onlineModule(packageName: String): OnlineModule? = repoLoader.getOnlineModule(packageName)

    fun latestVersion(packageName: String): RepoLoader.ModuleVersion? =
        repoLoader.getModuleLatestVersion(packageName)

    fun latestReleaseTime(packageName: String, channel: String): String? =
        repoLoader.getLatestReleaseTime(packageName, channel)

    fun releases(packageName: String): List<Release>? = repoLoader.getReleases(packageName)

    fun loadRemoteReleases(packageName: String) {
        repoLoader.loadRemoteReleases(packageName)
    }

    fun refreshRepo(remote: Boolean = true) {
        ensureListeners()
        App.getExecutorService().submit {
            if (remote) repoLoader.loadRemoteData() else repoLoader.loadLocalData(false)
        }
    }

    fun upgradeCount(): Int {
        val modules = moduleUtil.modules ?: return 0
        val seen = HashSet<String>()
        var count = 0
        modules.forEach { (key, module) ->
            if (seen.add(key.first)) {
                val version = repoLoader.getModuleLatestVersion(key.first)
                if (version != null && version.upgradable(module.versionCode, module.versionName)) {
                    count++
                }
            }
        }
        return count
    }

    fun appInfoIntent(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))

    fun showInOtherAppIntent(packageName: String): Intent =
        Intent(Intent.ACTION_SHOW_APP_INFO).apply {
            putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun launchIntent(packageName: String, userId: Int): Intent? =
        if (ConfigManager.isBinderAlive()) {
            AppHelper.getLaunchIntentForPackage(packageName, userId)
        } else {
            null
        }

    fun settingsIntent(packageName: String, userId: Int): Intent? =
        if (ConfigManager.isBinderAlive()) {
            AppHelper.getSettingsIntent(packageName, userId)
        } else {
            null
        }

    fun startActivityAsUser(intent: Intent, userId: Int): Int =
        ConfigManager.startActivityAsUserWithFeature(intent, userId)

    fun clearProfileDataAndOptimize(packageName: String): Throwable? {
        return try {
            org.lsposed.manager.receivers.LSPManagerServiceHolder.getService()
                .clearApplicationProfileData(packageName)
            if (org.lsposed.manager.receivers.LSPManagerServiceHolder.getService()
                    .performDexOptMode(packageName)
            ) {
                null
            } else {
                UnknownError()
            }
        } catch (e: Throwable) {
            e
        }
    }

    suspend fun loadScope(
        module: ModuleUtil.InstalledModule,
        filterSystemApps: Boolean,
        filterGames: Boolean,
        filterModules: Boolean,
        filterDenylist: Boolean,
        sort: Int,
    ): ScopeSnapshot = withContext(Dispatchers.IO) {
        val pm = App.getInstance().packageManager
        val appList = AppHelper.getAppList(false)
        val denyList = AppHelper.getDenyList(false)
        val enabled = moduleUtil.isModuleEnabled(module.packageName, module.userId)

        val recommendedPackages = module.scopeList ?: emptyList()
        var checked = ConfigManager.getModuleScope(module.packageName)
            .filter { it.userId == module.userId }
            .mapTo(HashSet()) { it.packageName to it.userId }
        val recommended = HashSet<Pair<String, Int>>()
        val installed = HashSet<Pair<String, Int>>()
        val result = ArrayList<ScopeAppItem>()

        appList.forEach { info ->
            val appInfo = info.applicationInfo ?: return@forEach
            val userId = appInfo.uid / App.PER_USER_RANGE
            val packageName = info.packageName
            if ((packageName == "system" && userId != 0) ||
                packageName == module.packageName ||
                packageName == org.lsposed.manager.BuildConfig.APPLICATION_ID
            ) {
                return@forEach
            }
            val key = packageName to userId
            installed.add(key)
            if (userId != module.userId) return@forEach

            val isRecommended = recommendedPackages.contains(packageName)
            if (isRecommended) {
                recommended.add(key)
                if (checked.isEmpty()) checked.add(key)
            }

            if (!isRecommended && shouldHideApp(
                    info,
                    key,
                    checked,
                    denyList,
                    filterSystemApps,
                    filterGames,
                    filterModules,
                    filterDenylist,
                )
            ) {
                return@forEach
            }

            val label = if (packageName == "system") {
                App.getInstance().getString(org.lsposed.manager.R.string.android_framework)
            } else {
                AppHelper.getAppLabel(info, pm) ?: packageName
            }

            result.add(
                ScopeAppItem(
                    packageName = packageName,
                    userId = userId,
                    label = label,
                    packageInfo = info,
                    applicationInfo = appInfo,
                    recommended = isRecommended,
                    denied = denyList.contains(packageName),
                    checked = checked.contains(key),
                )
            )
        }

        checked = checked.filterTo(HashSet()) { it in installed }

        val comparator = AppHelper.getAppListComparator(sort, pm)
        val frameworkComparator = Comparator<ScopeAppItem> { a, b ->
            when {
                a.packageName == "system" && b.packageName != "system" -> -1
                b.packageName == "system" && a.packageName != "system" -> 1
                else -> comparator.compare(a.packageInfo, b.packageInfo)
            }
        }
        val recommendedComparator = Comparator<ScopeAppItem> { a, b ->
            when {
                a.recommended && !b.recommended -> -1
                !a.recommended && b.recommended -> 1
                else -> frameworkComparator.compare(a, b)
            }
        }
        result.sortWith { a, b ->
            when {
                a.checked && !b.checked -> -1
                !a.checked && b.checked -> 1
                else -> recommendedComparator.compare(a, b)
            }
        }

        ScopeSnapshot(
            apps = result,
            checked = checked,
            recommended = recommended,
            enabled = enabled,
            loaded = true,
            denyList = denyList,
        )
    }

    private fun shouldHideApp(
        info: PackageInfo,
        key: Pair<String, Int>,
        checked: Set<Pair<String, Int>>,
        denyList: List<String>,
        filterSystemApps: Boolean,
        filterGames: Boolean,
        filterModules: Boolean,
        filterDenylist: Boolean,
    ): Boolean {
        if (info.packageName == "system") return false
        if (key in checked) return false
        if (filterDenylist && denyList.contains(info.packageName)) return true
        val appInfo = info.applicationInfo ?: return false
        if (filterModules) {
            val userId = appInfo.uid / App.PER_USER_RANGE
            if (moduleUtil.getModule(info.packageName, userId) != null) return true
        }
        if (filterGames) {
            if (appInfo.category == android.content.pm.ApplicationInfo.CATEGORY_GAME) return true
            @Suppress("DEPRECATION")
            if ((appInfo.flags and android.content.pm.ApplicationInfo.FLAG_IS_GAME) != 0) return true
        }
        return filterSystemApps &&
            (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
    }

    fun sortModules(
        modules: Collection<ModuleUtil.InstalledModule>,
        userId: Int,
        sort: Int,
    ): List<ModuleUtil.InstalledModule> {
        val pm = App.getInstance().packageManager
        val comparator = AppHelper.getAppListComparator(sort, pm)
        return modules.sortedWith { a, b ->
            val aChecked = moduleUtil.isModuleEnabled(a.packageName, a.userId)
            val bChecked = moduleUtil.isModuleEnabled(b.packageName, b.userId)
            when {
                aChecked != bChecked -> if (aChecked) -1 else 1
                else -> {
                    val c = comparator.compare(a.pkg, b.pkg)
                    if (c != 0) c
                    else if (a.userId == userId) -1
                    else if (b.userId == userId) 1
                    else a.userId.compareTo(b.userId)
                }
            }
        }
    }
}
