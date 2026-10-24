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

package org.lsposed.manager.ui.screen.module

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lsposed.manager.R
import org.lsposed.manager.data.model.ModuleItem
import org.lsposed.manager.data.model.ScopeAppItem
import org.lsposed.manager.data.model.ScopeSnapshot
import org.lsposed.manager.data.repository.ManagerRepository
import org.lsposed.manager.data.repository.SettingsRepository
import org.lsposed.manager.ui.component.AppIcon
import org.lsposed.manager.ui.component.ConfirmDialog
import org.lsposed.manager.ui.component.DetailTopBar
import org.lsposed.manager.ui.component.EmptyState
import org.lsposed.manager.ui.component.SearchField
import org.lsposed.manager.ui.component.SettingsGroup
import org.lsposed.manager.ui.viewmodel.ManagerViewModel
import org.lsposed.manager.util.BackupUtils

@Composable
fun ModuleScopeScreen(
    viewModel: ManagerViewModel,
    packageName: String,
    userId: Int,
    onBack: () -> Unit,
    onOpenRepo: (String) -> Unit,
) {
    val context = LocalContext.current
    val modules by viewModel.modules.collectAsStateWithLifecycle()
    val modulesLoaded by viewModel.modulesLoaded.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val module = modules.firstOrNull { it.packageName == packageName && it.userId == userId }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var snapshot by remember(module?.id) { mutableStateOf<ScopeSnapshot?>(null) }
    var checked by remember(module?.id) { mutableStateOf(emptySet<Pair<String, Int>>()) }
    var enabled by remember(module?.id) { mutableStateOf(module?.enabled ?: false) }
    var query by remember { mutableStateOf("") }
    var searchMode by remember { mutableStateOf(false) }
    var filterMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var showNoSelection by remember { mutableStateOf(false) }
    var showRecommendedConfirm by remember { mutableStateOf(false) }
    var uninstallConfirm by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }

    fun showMessage(message: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                withDismissAction = actionLabel != null,
            )
            if (result == SnackbarResult.ActionPerformed) action?.invoke()
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gzip"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val error = withContext(Dispatchers.IO) {
                    runCatching { BackupUtils.backup(uri, packageName) }.exceptionOrNull()
                }
                showMessage(
                    if (error == null) context.getString(R.string.settings_backup)
                    else context.getString(R.string.settings_backup_failed2, error.message)
                )
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val error = withContext(Dispatchers.IO) {
                    runCatching { BackupUtils.restore(uri, packageName) }.exceptionOrNull()
                }
                showMessage(
                    if (error == null) context.getString(R.string.settings_restore)
                    else context.getString(R.string.settings_restore_failed2, error.message)
                )
                if (error == null) {
                    viewModel.refreshModules(force = true)
                    reloadKey++
                }
            }
        }
    }

    LaunchedEffect(
        module?.id,
        settings.filterSystemApps,
        settings.filterGames,
        settings.filterModules,
        settings.filterDenylist,
        settings.listSort,
        reloadKey,
    ) {
        val current = module ?: return@LaunchedEffect
        val loaded = runCatching {
            withContext(Dispatchers.IO) {
                ManagerRepository.loadScope(
                    module = current.module,
                    filterSystemApps = settings.filterSystemApps,
                    filterGames = settings.filterGames,
                    filterModules = settings.filterModules,
                    filterDenylist = settings.filterDenylist,
                    sort = settings.listSort,
                )
            }
        }.getOrElse {
            ScopeSnapshot(
                apps = emptyList(),
                checked = emptySet(),
                recommended = emptySet(),
                enabled = current.enabled,
                loaded = true,
                denyList = emptyList(),
            )
        }
        snapshot = loaded
        checked = loaded.checked
        enabled = loaded.enabled
    }

    fun persistScope(newScope: Set<Pair<String, Int>>, onSuccess: () -> Unit) {
        val current = module ?: return
        val ok = viewModel.setModuleScope(current, newScope)
        if (ok) {
            checked = newScope
            onSuccess()
        } else {
            showMessage(context.getString(R.string.failed_to_save_scope_list))
        }
    }

    fun setEnabled(newEnabled: Boolean) {
        val current = module ?: return
        if (newEnabled && checked.isNotEmpty()) {
            if (!viewModel.setModuleScope(current, checked)) {
                showMessage(context.getString(R.string.failed_to_save_scope_list))
                return
            }
        }
        val ok = viewModel.toggleModule(current, newEnabled)
        if (ok) {
            enabled = newEnabled
        } else {
            showMessage(context.getString(R.string.failed_to_save_scope_list))
        }
    }

    fun handleBack() {
        if (snapshot?.loaded == true && enabled && checked.isEmpty()) {
            showNoSelection = true
        } else {
            onBack()
        }
    }

    BackHandler(enabled = snapshot?.loaded == true && enabled) {
        handleBack()
    }

    if (module == null) {
        Scaffold(
            topBar = {
                DetailTopBar(
                    title = packageName,
                    subtitle = null,
                    navigationIcon = {
                        IconButton(onClick = ::handleBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                )
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) { padding ->
            if (!modulesLoaded) {
                Box(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                EmptyState(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.not_installed),
                    summary = stringResource(R.string.not_installed_summary),
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                )
            }
        }
        return
    }

    val recommended = snapshot?.recommended ?: emptySet()
    val filteredApps = snapshot?.apps?.filter {
        query.isBlank() ||
            it.label.toString().contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true)
    } ?: emptyList()

    Scaffold(
        topBar = {
            DetailTopBar(
                title = module.title,
                subtitle = module.packageName,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = ::handleBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (!enabled) {
                            showMessage(context.getString(R.string.module_is_not_activated_yet))
                        } else if (recommended.isEmpty()) {
                            showMessage(context.getString(R.string.no_scope_selected))
                        } else {
                            showRecommendedConfirm = true
                        }
                    }) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = stringResource(R.string.use_recommended))
                    }
                    Box {
                        IconButton(onClick = { filterMenu = true }) {
                            Icon(Icons.Outlined.FilterList, contentDescription = stringResource(R.string.filter))
                        }
                        DropdownMenu(
                            expanded = filterMenu,
                            onDismissRequest = { filterMenu = false },
                        ) {
                            CheckableMenuRow(
                                label = stringResource(R.string.filter_system_apps),
                                checked = settings.filterSystemApps,
                            ) {
                                SettingsRepository.setFilters(systemApps = !settings.filterSystemApps)
                            }
                            CheckableMenuRow(
                                label = stringResource(R.string.filter_games),
                                checked = settings.filterGames,
                            ) {
                                SettingsRepository.setFilters(games = !settings.filterGames)
                            }
                            CheckableMenuRow(
                                label = stringResource(R.string.filter_modules),
                                checked = settings.filterModules,
                            ) {
                                SettingsRepository.setFilters(modules = !settings.filterModules)
                            }
                            CheckableMenuRow(
                                label = stringResource(R.string.filter_denylist),
                                checked = settings.filterDenylist,
                            ) {
                                SettingsRepository.setFilters(denylist = !settings.filterDenylist)
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.Outlined.Sort, contentDescription = stringResource(R.string.sort))
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            listOf(
                                R.string.sort_by_name to 0,
                                R.string.sort_by_package to 2,
                                R.string.sort_by_install to 4,
                                R.string.sort_by_update to 6,
                            ).forEach { (label, value) ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(label)) },
                                    onClick = {
                                        SettingsRepository.setListSort(value)
                                        sortMenu = false
                                    },
                                    trailingIcon = {
                                        if (settings.listSort / 2 == value / 2) {
                                            Icon(Icons.Outlined.Check, contentDescription = null)
                                        }
                                    },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_reverse)) },
                                onClick = {
                                    val next = if (settings.listSort % 2 == 0) settings.listSort + 1 else settings.listSort - 1
                                    SettingsRepository.setListSort(next)
                                    sortMenu = false
                                },
                                trailingIcon = {
                                    if (settings.listSort % 2 == 1) {
                                        Icon(Icons.Outlined.Check, contentDescription = null)
                                    }
                                },
                            )
                        }
                    }
                    Box {
                        var moreMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { moreMenu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                            val settingsIntent = ManagerRepository.settingsIntent(module.packageName, module.userId)
                            if (settingsIntent != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.module_settings)) },
                                    leadingIcon = { Icon(Icons.Outlined.PlayArrow, null) },
                                    onClick = {
                                        ManagerRepository.startActivityAsUser(settingsIntent, module.userId)
                                        moreMenu = false
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.module_app_info)) },
                                leadingIcon = { Icon(Icons.Outlined.Info, null) },
                                onClick = {
                                    ManagerRepository.startActivityAsUser(
                                        ManagerRepository.appInfoIntent(module.packageName),
                                        module.userId,
                                    )
                                    moreMenu = false
                                },
                            )
                            if (ManagerRepository.onlineModule(module.packageName) != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.view_in_repo)) },
                                    leadingIcon = { Icon(Icons.Outlined.Storefront, null) },
                                    onClick = {
                                        onOpenRepo(module.packageName)
                                        moreMenu = false
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings_backup)) },
                                leadingIcon = { Icon(Icons.Outlined.Backup, null) },
                                onClick = {
                                    moreMenu = false
                                    backupLauncher.launch("${module.packageName}.lsp")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings_restore)) },
                                leadingIcon = { Icon(Icons.Outlined.Restore, null) },
                                onClick = {
                                    moreMenu = false
                                    restoreLauncher.launch(arrayOf("*/*"))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.module_reoptimize)) },
                                leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                                onClick = {
                                    moreMenu = false
                                    scope.launch {
                                        val error = withContext(Dispatchers.IO) {
                                            ManagerRepository.clearProfileDataAndOptimize(module.packageName)
                                        }
                                        showMessage(
                                            if (error == null) context.getString(R.string.compile_done)
                                            else context.getString(R.string.compile_failed_with_info) + (error.message ?: error.toString())
                                        )
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.module_uninstall), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    moreMenu = false
                                    uninstallConfirm = true
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            ScopeHeader(
                module = module,
                enabled = enabled,
                selectedCount = checked.size,
                onEnabledChange = ::setEnabled,
                onOpenSettings = {
                    ManagerRepository.settingsIntent(module.packageName, module.userId)?.let {
                        ManagerRepository.startActivityAsUser(it, module.userId)
                    }
                },
            )

            if (searchMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = stringResource(R.string.app_search_hint),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        searchMode = false
                        query = ""
                    }) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.close))
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = searchMode,
                        onClick = { searchMode = true },
                        label = { Text(stringResource(R.string.app_search_hint)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(18.dp)) },
                    )
                    FilterChip(
                        selected = false,
                        onClick = {
                            if (!enabled) {
                                showMessage(context.getString(R.string.module_is_not_activated_yet))
                            } else if (recommended.isEmpty()) {
                                showMessage(context.getString(R.string.no_scope_selected))
                            } else {
                                showRecommendedConfirm = true
                            }
                        },
                        label = { Text(stringResource(R.string.use_recommended_short)) },
                        leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp)) },
                    )
                    Text(
                        stringResource(R.string.scope_selected, checked.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (snapshot == null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (filteredApps.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Search,
                    title = stringResource(R.string.no_scope_apps),
                    summary = stringResource(R.string.no_scope_apps_summary),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filteredApps, key = { it.id }) { app ->
                        ScopeAppRow(
                            app = app,
                            checked = (app.packageName to app.userId) in checked,
                            enabled = enabled,
                            onCheckedChange = { value ->
                                if (!enabled) {
                                    showMessage(context.getString(R.string.module_is_not_activated_yet))
                                } else {
                                    val newScope = checked.toMutableSet().apply {
                                        if (value) add(app.packageName to app.userId)
                                        else remove(app.packageName to app.userId)
                                    }
                                    persistScope(newScope) {
                                        when {
                                            app.packageName == "system" -> showMessage(
                                                context.getString(R.string.reboot_required),
                                                context.getString(R.string.reboot),
                                            ) { ManagerRepository.reboot() }
                                            app.denied -> showMessage(
                                                context.getString(R.string.deny_list, app.label)
                                            )
                                        }
                                    }
                                }
                            },
                            onOptimize = {
                                scope.launch {
                                    val error = withContext(Dispatchers.IO) {
                                        ManagerRepository.clearProfileDataAndOptimize(app.packageName)
                                    }
                                    showMessage(
                                        if (error == null) context.getString(R.string.compile_done)
                                        else context.getString(R.string.compile_failed_with_info) + (error.message ?: error.toString())
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showRecommendedConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.use_recommended),
            text = stringResource(R.string.use_recommended_message),
            confirmText = stringResource(R.string.ok),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                persistScope(recommended) { showMessage(context.getString(R.string.use_recommended)) }
            },
            onDismiss = { showRecommendedConfirm = false },
        )
    }

    if (showNoSelection) {
        ConfirmDialog(
            title = stringResource(R.string.enable_module),
            text = if (recommended.isNotEmpty()) {
                stringResource(R.string.no_scope_selected_has_recommended)
            } else {
                stringResource(R.string.no_scope_selected)
            },
            confirmText = if (recommended.isNotEmpty()) {
                stringResource(R.string.use_recommended_short)
            } else {
                stringResource(R.string.ok)
            },
            dismissText = stringResource(R.string.disable_module),
            dismissOnConfirm = false,
            onConfirm = {
                if (recommended.isNotEmpty()) {
                    persistScope(recommended) {
                        showNoSelection = false
                    }
                } else {
                    showNoSelection = false
                }
            },
            onDismiss = {
                showNoSelection = false
                setEnabled(false)
                showMessage(context.getString(R.string.module_disabled_no_selection, module.title))
                onBack()
            },
        )
    }

    if (uninstallConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.module_uninstall),
            text = stringResource(R.string.module_uninstall_message),
            confirmText = stringResource(R.string.module_uninstall),
            dismissText = stringResource(R.string.cancel),
            destructive = true,
            onConfirm = {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        ManagerRepository.uninstall(module.packageName, module.userId)
                    }
                    viewModel.refreshModules(force = true)
                    showMessage(
                        if (ok) context.getString(R.string.module_uninstalled, module.title)
                        else context.getString(R.string.module_uninstall_failed)
                    )
                    if (ok) onBack()
                }
            },
            onDismiss = { uninstallConfirm = false },
        )
    }
}

@Composable
private fun ScopeHeader(
    module: ModuleItem,
    enabled: Boolean,
    selectedCount: Int,
    onEnabledChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(module.module.packageInfo, size = 52.dp)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp),
                ) {
                    Text(
                        module.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${module.versionName} · ${module.packageName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            if (module.description.isNotBlank()) {
                Text(
                    module.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            if (module.warning != null) {
                Text(
                    module.warning,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.scope_selected, selectedCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                AssistChip(
                    onClick = onOpenSettings,
                    label = { Text(stringResource(R.string.module_settings)) },
                    leadingIcon = { Icon(Icons.Outlined.PlayArrow, null, Modifier.size(18.dp)) },
                )
            }
        }
    }
}

@Composable
private fun ScopeAppRow(
    app: ScopeAppItem,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onOptimize: () -> Unit,
) {
    val recommendedLabel = stringResource(R.string.requested_by_module)
    val deniedLabel = stringResource(R.string.deny_list_info)
    val recommendations = buildList {
        if (app.recommended) add(recommendedLabel)
        if (app.denied) add(deniedLabel)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        colors = CardDefaults.cardColors(
            containerColor = if (checked) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app.packageInfo, size = 40.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 4.dp),
            ) {
                Text(
                    app.label.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (app.packageName == "system") {
                        "system"
                    } else {
                        "${app.packageName} · ${app.packageInfo.versionName ?: ""}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (recommendations.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        recommendations.forEach { label ->
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (label == deniedLabel) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                        }
                    }
                }
            }
            Box {
                var expanded by remember { mutableStateOf(false) }
                IconButton(onClick = { expanded = true }, enabled = enabled) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    val launch = ManagerRepository.launchIntent(app.packageName, app.userId)
                    if (launch != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.app_launch)) },
                            leadingIcon = { Icon(Icons.Outlined.PlayArrow, null) },
                            onClick = {
                                ManagerRepository.startActivityAsUser(launch, app.userId)
                                expanded = false
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.module_app_info)) },
                        leadingIcon = { Icon(Icons.Outlined.Info, null) },
                        onClick = {
                            ManagerRepository.startActivityAsUser(
                                ManagerRepository.appInfoIntent(app.packageName),
                                app.userId,
                            )
                            expanded = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.module_reoptimize)) },
                        leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                        onClick = {
                            expanded = false
                            onOptimize()
                        },
                    )
                    if (app.packageName != "system") {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.force_stop)) },
                            leadingIcon = { Icon(Icons.Outlined.StopCircle, null) },
                            onClick = {
                                ManagerRepository.forceStop(app.packageName, app.userId)
                                expanded = false
                            },
                        )
                    }
                }
            }
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun CheckableMenuRow(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = {
            Checkbox(checked = checked, onCheckedChange = null)
        },
    )
}
