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

package org.lsposed.manager.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.lsposed.lspd.models.UserInfo
import org.lsposed.manager.App
import org.lsposed.manager.BuildConfig
import org.lsposed.manager.ConfigManager
import org.lsposed.manager.R
import org.lsposed.manager.data.model.FrameworkInfo
import org.lsposed.manager.data.model.ManagerInfo
import org.lsposed.manager.data.model.ModuleItem
import org.lsposed.manager.data.model.RepoItem
import org.lsposed.manager.data.repository.ManagerRepository
import org.lsposed.manager.data.repository.SettingsRepository
import org.lsposed.manager.repo.RepoLoader
import org.lsposed.manager.util.ModuleUtil
import java.time.Instant

class ManagerViewModel(application: Application) : AndroidViewModel(application) {

    val settings = SettingsRepository.state

    private val _modules = MutableStateFlow<List<ModuleItem>>(emptyList())
    val modules: StateFlow<List<ModuleItem>> = _modules.asStateFlow()

    private val _users = MutableStateFlow<List<UserInfo>>(emptyList())
    val users: StateFlow<List<UserInfo>> = _users.asStateFlow()

    private val _modulesLoaded = MutableStateFlow(false)
    val modulesLoaded: StateFlow<Boolean> = _modulesLoaded.asStateFlow()

    private val _repoItems = MutableStateFlow<List<RepoItem>>(emptyList())
    val repoItems: StateFlow<List<RepoItem>> = _repoItems.asStateFlow()

    private val _repoLoaded = MutableStateFlow(false)
    val repoLoaded: StateFlow<Boolean> = _repoLoaded.asStateFlow()

    private val _upgradeCount = MutableStateFlow(0)
    val upgradeCount: StateFlow<Int> = _upgradeCount.asStateFlow()

    private val _framework = MutableStateFlow(FrameworkInfo(binderAlive = ConfigManager.isBinderAlive()))
    val framework: StateFlow<FrameworkInfo> = _framework.asStateFlow()

    private val _managerInfo = MutableStateFlow(ManagerInfo())
    val managerInfo: StateFlow<ManagerInfo> = _managerInfo.asStateFlow()

    init {
        ManagerRepository.ensureListeners()
        viewModelScope.launch {
            ManagerRepository.moduleRevision.collect { loadModules() }
        }
        viewModelScope.launch {
            ManagerRepository.repoRevision.collect { loadRepo() }
        }
        viewModelScope.launch {
            combine(ManagerRepository.repoRevision, settings) { _, _ -> }.collect {
                _upgradeCount.value = ManagerRepository.upgradeCount()
            }
        }
        viewModelScope.launch {
            settings
                .map { Triple(it.repoSort, it.upgradableFirst, it.updateChannel) }
                .distinctUntilChanged()
                .collect { loadRepo() }
        }
        refreshManagerInfo()
        loadModules()
        loadRepo()
        refreshFramework()
    }

    fun refreshManagerInfo() {
        val context = getApplication<Application>()
        val versionCode = BuildConfig.VERSION_CODE.toLong()
        val device = buildString {
            append(Build.MANUFACTURER.replaceFirstChar { it.uppercase() })
            if (!Build.BRAND.equals(Build.MANUFACTURER, ignoreCase = true)) {
                append(' ').append(Build.BRAND.replaceFirstChar { it.uppercase() })
            }
            append(' ').append(Build.MODEL)
        }
        val systemVersion = if (Build.VERSION.PREVIEW_SDK_INT != 0) {
            "${Build.VERSION.CODENAME} Preview (API ${Build.VERSION.SDK_INT})"
        } else {
            "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        }
        _managerInfo.value = ManagerInfo(
            packageName = context.packageName,
            versionName = BuildConfig.VERSION_NAME,
            versionCode = versionCode,
            device = device,
            systemVersion = systemVersion,
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
        )
    }

    private fun loadModules() {
        viewModelScope.launch(Dispatchers.IO) {
            val repo = ManagerRepository
            val installed = repo.modules()?.values
            val users = repo.users()
            val sort = SettingsRepository.state.value.listSort
            val apiVersion = if (repo.isBinderAlive()) ConfigManager.getXposedApiVersion() else -1
            val context = getApplication<Application>()

            val items = installed?.map { module ->
                val warning = when {
                    module.minVersion == 0 ->
                        context.getString(R.string.no_min_version_specified)
                    apiVersion > 0 && module.minVersion > apiVersion ->
                        context.getString(R.string.warning_xposed_min_version, module.minVersion)
                    module.targetVersion > apiVersion ->
                        context.getString(R.string.warning_target_version_higher, module.targetVersion)
                    module.minVersion < ModuleUtil.MIN_MODULE_VERSION ->
                        context.getString(
                            R.string.warning_min_version_too_low,
                            module.minVersion,
                            ModuleUtil.MIN_MODULE_VERSION,
                        )
                    module.isInstalledOnExternalStorage ->
                        context.getString(R.string.warning_installed_on_external_storage)
                    else -> null
                }
                val latest = repo.latestVersion(module.packageName)
                val updateVersion = if (latest != null && latest.upgradable(module.versionCode, module.versionName)) {
                    latest.versionName
                } else {
                    null
                }
                ModuleItem(
                    module = module,
                    title = module.appName,
                    description = module.description,
                    versionName = module.versionName ?: "",
                    versionCode = module.versionCode,
                    enabled = repo.isModuleEnabled(module.packageName, module.userId),
                    warning = warning,
                    updateVersion = updateVersion,
                    recommendedScope = module.scopeList ?: emptyList(),
                )
            } ?: emptyList()

            val sorted = items.sortedWith { a, b ->
                when {
                    a.enabled != b.enabled -> if (a.enabled) -1 else 1
                    else -> a.title.compareTo(b.title, ignoreCase = true)
                }
            }
            _modules.value = sorted
            _users.value = users
            _modulesLoaded.value = repo.modules() != null
            _upgradeCount.value = repo.upgradeCount()
        }
    }

    private fun loadRepo() {
        viewModelScope.launch(Dispatchers.IO) {
            val repo = ManagerRepository
            val modules = repo.onlineModules()
            val channel = SettingsRepository.state.value.updateChannel
            val sort = SettingsRepository.state.value.repoSort
            val upgradableFirst = SettingsRepository.state.value.upgradableFirst
            val labelComparator = rikka.core.util.LabelComparator()
            val installedModules = repo.modules()

            val items = modules?.mapNotNull { module ->
                val name = module.name ?: return@mapNotNull null
                if (module.isHide == true) return@mapNotNull null
                val installedModule = installedModules?.values?.firstOrNull { it.packageName == name }
                val latest = repo.latestVersion(name)
                val upgradable = if (installedModule != null &&
                    latest != null &&
                    latest.upgradable(installedModule.versionCode, installedModule.versionName)
                ) {
                    latest.versionName
                } else {
                    null
                }
                RepoItem(
                    module = module,
                    title = module.description ?: name,
                    packageName = name,
                    summary = module.summary,
                    updatedAt = repo.latestReleaseTime(name, channel) ?: module.latestReleaseTime,
                    upgradableVersion = upgradable,
                    installed = installedModule != null,
                )
            } ?: emptyList()

            val sorted = items.sortedWith { a, b ->
                if (upgradableFirst && (a.upgradableVersion != null) != (b.upgradableVersion != null)) {
                    if (a.upgradableVersion != null) -1 else 1
                } else if (sort == 0) {
                    labelComparator.compare(a.title, b.title)
                } else {
                    compareInstantDesc(a.updatedAt, b.updatedAt)
                }
            }
            _repoItems.value = sorted
            _repoLoaded.value = repo.isModuleRepoLoaded()
        }
    }

    private fun compareInstantDesc(a: String?, b: String?): Int {
        return try {
            val ai = a?.let { Instant.parse(it) }
            val bi = b?.let { Instant.parse(it) }
            when {
                ai == null && bi == null -> 0
                ai == null -> 1
                bi == null -> -1
                else -> bi.compareTo(ai)
            }
        } catch (_: Throwable) {
            0
        }
    }

    fun refreshFramework() {
        viewModelScope.launch(Dispatchers.IO) {
            val alive = ConfigManager.isBinderAlive()
            _framework.value = if (!alive) {
                FrameworkInfo(binderAlive = false)
            } else {
                FrameworkInfo(
                    binderAlive = true,
                    versionName = ConfigManager.getXposedVersionName(),
                    versionCode = ConfigManager.getXposedVersionCode(),
                    apiVersion = ConfigManager.getXposedApiVersion(),
                    apiProtected = ConfigManager.isDexObfuscateEnabled(),
                    dex2OatWrapper = ConfigManager.getDex2OatWrapperCompatibility(),
                    dex2OatFlagsLoaded = ConfigManager.dex2oatFlagsLoaded(),
                    sepolicyLoaded = ConfigManager.isSepolicyLoaded(),
                    systemServerRequested = ConfigManager.systemServerRequested(),
                    statusNotificationEnabled = ConfigManager.enableStatusNotification(),
                    verboseLog = ConfigManager.isVerboseLogEnabled(),
                )
            }
            _upgradeCount.value = ManagerRepository.upgradeCount()
        }
    }

    fun refreshModules(force: Boolean = false) {
        if (force) {
            ManagerRepository.refreshModules(true)
        } else {
            loadModules()
        }
    }

    fun refreshRepo(remote: Boolean = true) {
        ManagerRepository.refreshRepo(remote)
    }

    fun onResume() {
        refreshManagerInfo()
        refreshFramework()
        if (ManagerRepository.isBinderAlive()) {
            // Cheap enough: it only re-reads the cached module map unless the service changed.
            loadModules()
        }
        loadRepo()
    }

    fun toggleModule(module: ModuleItem, enabled: Boolean): Boolean {
        val ok = ManagerRepository.setModuleEnabled(module.packageName, module.userId, enabled)
        if (ok) loadModules()
        return ok
    }

    fun setModuleScope(module: ModuleItem, scope: Set<Pair<String, Int>>): Boolean {
        return ManagerRepository.setModuleScope(module.packageName, module.legacy, scope)
    }

    fun installToUser(module: ModuleItem, user: UserInfo): Boolean {
        val ok = ManagerRepository.installExistingPackageAsUser(module.packageName, user.id)
        if (ok) ManagerRepository.refreshModules(true)
        return ok
    }

    fun reloadSingleModule(packageName: String, userId: Int) {
        App.getExecutorService().submit {
            ModuleUtil.getInstance().reloadSingleModule(packageName, userId)
        }
    }
}
