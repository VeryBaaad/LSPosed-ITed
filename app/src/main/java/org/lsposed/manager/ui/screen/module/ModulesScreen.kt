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

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lsposed.manager.App
import org.lsposed.lspd.models.UserInfo
import org.lsposed.manager.R
import org.lsposed.manager.data.model.ModuleItem
import org.lsposed.manager.data.repository.ManagerRepository
import org.lsposed.manager.data.repository.SettingsRepository
import org.lsposed.manager.ui.component.AppIcon
import org.lsposed.manager.ui.component.ConfirmDialog
import org.lsposed.manager.ui.component.EmptyState
import org.lsposed.manager.ui.component.ManagerTopBar
import org.lsposed.manager.ui.component.SearchField
import org.lsposed.manager.ui.component.SingleChoiceDialog
import org.lsposed.manager.ui.viewmodel.ManagerViewModel

@Composable
fun ModulesScreen(
    viewModel: ManagerViewModel,
    onOpenModule: (String, Int) -> Unit,
    onOpenRepo: (String) -> Unit,
) {
    val context = LocalContext.current
    val modules by viewModel.modules.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val modulesLoaded by viewModel.modulesLoaded.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val framework by viewModel.framework.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var searchMode by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var sortMenu by remember { mutableStateOf(false) }
    var selectedUserId by remember { mutableStateOf<Int?>(null) }
    var actionModule by remember { mutableStateOf<ModuleItem?>(null) }
    var uninstallTarget by remember { mutableStateOf<ModuleItem?>(null) }
    var forceStopTarget by remember { mutableStateOf<ModuleItem?>(null) }
    var optimizeTarget by remember { mutableStateOf<ModuleItem?>(null) }
    var showUserPicker by remember { mutableStateOf(false) }
    var installTargetUser by remember { mutableStateOf<UserInfo?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    val realUsers = remember(users) { users.ifEmpty { emptyList() } }
    LaunchedEffect(realUsers) {
        if (realUsers.isNotEmpty() &&
            (selectedUserId == null || realUsers.none { it.id == selectedUserId })
        ) {
            selectedUserId = realUsers.firstOrNull { it.id == 0 }?.id ?: realUsers.first().id
        }
    }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            delay(1200)
            refreshing = false
        }
    }

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val currentUserId = selectedUserId
    val filtered = modules.asSequence()
        .filter { currentUserId == null || it.userId == currentUserId }
        .filter {
            query.isBlank() ||
                it.title.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true)
        }
        .sortedWith(moduleComparator(settings.listSort))
        .toList()

    Scaffold(
        topBar = {
            ManagerTopBar(
                title = stringResource(R.string.Modules),
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { searchMode = !searchMode; if (!searchMode) query = "" }) {
                        Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.module_search_hint))
                    }
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.Outlined.Sort, contentDescription = stringResource(R.string.sort))
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            SortMenuItem(
                                selected = settings.listSort % 2 == 0 && settings.listSort <= 1,
                                text = stringResource(R.string.sort_by_name),
                                onClick = {
                                    SettingsRepository.setListSort(0)
                                    sortMenu = false
                                },
                            )
                            SortMenuItem(
                                selected = settings.listSort in 2..3,
                                text = stringResource(R.string.sort_by_package),
                                onClick = {
                                    SettingsRepository.setListSort(2)
                                    sortMenu = false
                                },
                            )
                            SortMenuItem(
                                selected = settings.listSort in 4..5,
                                text = stringResource(R.string.sort_by_install),
                                onClick = {
                                    SettingsRepository.setListSort(4)
                                    sortMenu = false
                                },
                            )
                            SortMenuItem(
                                selected = settings.listSort in 6..7,
                                text = stringResource(R.string.sort_by_update),
                                onClick = {
                                    SettingsRepository.setListSort(6)
                                    sortMenu = false
                                },
                            )
                            SortMenuItem(
                                selected = settings.listSort % 2 == 1,
                                text = stringResource(R.string.sort_reverse),
                                onClick = {
                                    val next = if (settings.listSort % 2 == 0) settings.listSort + 1 else settings.listSort - 1
                                    SettingsRepository.setListSort(next)
                                    sortMenu = false
                                },
                            )
                        }
                    }
                    if (realUsers.size > 1) {
                        IconButton(onClick = { showUserPicker = true }) {
                            Icon(Icons.Outlined.PersonAdd, contentDescription = stringResource(R.string.install_to_user, ""))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .fillMaxSize(),
        ) {
            if (searchMode) {
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.module_search_hint),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (realUsers.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    realUsers.forEach { user ->
                        FilterChip(
                            selected = selectedUserId == user.id,
                            onClick = { selectedUserId = user.id },
                            label = { Text(user.name) },
                        )
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    refreshing = true
                    viewModel.refreshModules(force = true)
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (!modulesLoaded && filtered.isEmpty() && framework.binderAlive) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (filtered.isEmpty() && modulesLoaded) {
                    EmptyState(
                        icon = Icons.Outlined.Extension,
                        title = stringResource(R.string.no_modules),
                        summary = if (!framework.binderAlive) {
                            stringResource(R.string.not_installed_summary)
                        } else {
                            stringResource(R.string.no_modules_summary)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(filtered, key = { it.id }) { module ->
                            ModuleCard(
                                module = module,
                                onOpen = { onOpenModule(module.packageName, module.userId) },
                                onToggle = { enabled ->
                                    val ok = viewModel.toggleModule(module, enabled)
                                    if (!ok) showMessage(context.getString(R.string.failed_to_save_scope_list))
                                },
                                onAction = { actionModule = module },
                            )
                        }
                    }
                }
            }
        }
    }

    if (actionModule != null) {
        ModuleActionsDialog(
            module = actionModule!!,
            onDismiss = { actionModule = null },
            onOpen = { onOpenModule(it.packageName, it.userId) },
            onOpenRepo = onOpenRepo,
            onUninstall = { uninstallTarget = it },
            onForceStop = { forceStopTarget = it },
            onOptimize = { optimizeTarget = it },
            showMessage = ::showMessage,
        )
    }

    uninstallTarget?.let { module ->
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
                }
            },
            onDismiss = { uninstallTarget = null },
        )
    }

    forceStopTarget?.let { module ->
        ConfirmDialog(
            title = stringResource(R.string.force_stop_dlg_title),
            text = stringResource(R.string.force_stop_dlg_text),
            confirmText = stringResource(R.string.force_stop),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                ManagerRepository.forceStop(module.packageName, module.userId)
            },
            onDismiss = { forceStopTarget = null },
        )
    }

    optimizeTarget?.let { module ->
        val optimizing = remember(module.id) { mutableStateOf(true) }
        LaunchedEffect(module.id) {
            val error = withContext(Dispatchers.IO) {
                ManagerRepository.clearProfileDataAndOptimize(module.packageName)
            }
            optimizing.value = false
            showMessage(
                if (error == null) context.getString(R.string.compile_done)
                else context.getString(R.string.compile_failed_with_info) + (error.message ?: error.toString())
            )
        }
        AlertDialog(
            onDismissRequest = {},
            title = { Text(module.title) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(stringResource(R.string.compile_speed_msg), modifier = Modifier.padding(start = 16.dp))
                }
            },
            confirmButton = {
                if (!optimizing.value) {
                    TextButton(onClick = { optimizeTarget = null }) { Text(stringResource(R.string.close)) }
                }
            },
        )
    }

    if (showUserPicker) {
        SingleChoiceDialog(
            title = stringResource(R.string.install_to_user, ""),
            items = realUsers.map { it.name },
            selectedIndex = -1,
            onSelected = { index ->
                installTargetUser = realUsers.getOrNull(index)
                showUserPicker = false
            },
            onDismiss = { showUserPicker = false },
        )
    }

    installTargetUser?.let { user ->
        InstallToUserDialog(
            user = user,
            modules = modules,
            onDismiss = { installTargetUser = null },
            onInstall = { module ->
                installTargetUser = null
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        viewModel.installToUser(module, user)
                    }
                    showMessage(
                        if (ok) context.getString(R.string.module_installed, module.title, user.name)
                        else context.getString(R.string.module_install_failed)
                    )
                }
            },
        )
    }
}

@Composable
private fun SortMenuItem(selected: Boolean, text: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        onClick = onClick,
        trailingIcon = {
            if (selected) Icon(Icons.Outlined.Check, contentDescription = null)
        },
    )
}

@Composable
private fun ModuleCard(
    module: ModuleItem,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onAction: () -> Unit,
) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            AppIcon(
                packageInfo = module.module.packageInfo,
                size = 48.dp,
                modifier = Modifier.alpha(if (module.enabled) 1f else 0.55f),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
                    .alpha(if (module.enabled) 1f else 0.6f),
            ) {
                Text(
                    module.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (module.description.isBlank()) module.packageName else module.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Text(
                        module.versionName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (module.legacy) {
                        Text(
                            " · " + stringResource(R.string.legacy),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onAction, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Outlined.MoreVert,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (module.warning != null) {
                    Text(
                        module.warning,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (module.updateVersion != null) {
                    Text(
                        stringResource(R.string.update_available, module.updateVersion),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Switch(
                checked = module.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun ModuleActionsDialog(
    module: ModuleItem,
    onDismiss: () -> Unit,
    onOpen: (ModuleItem) -> Unit,
    onOpenRepo: (String) -> Unit,
    onUninstall: (ModuleItem) -> Unit,
    onForceStop: (ModuleItem) -> Unit,
    onOptimize: (ModuleItem) -> Unit,
    showMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val hasRepo = ManagerRepository.onlineModule(module.packageName) != null
    val settingsIntent = ManagerRepository.settingsIntent(module.packageName, module.userId)
        ?: ManagerRepository.launchIntent(module.packageName, module.userId)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(module.title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (settingsIntent != null) {
                    ActionRow(Icons.Outlined.PlayArrow, stringResource(R.string.module_settings)) {
                        ManagerRepository.startActivityAsUser(settingsIntent, module.userId)
                        onDismiss()
                    }
                }
                ActionRow(Icons.Outlined.Info, stringResource(R.string.module_app_info)) {
                    ManagerRepository.startActivityAsUser(
                        ManagerRepository.appInfoIntent(module.packageName),
                        module.userId,
                    )
                    onDismiss()
                }
                if (hasRepo) {
                    ActionRow(Icons.Outlined.Storefront, stringResource(R.string.view_in_repo)) {
                        onOpenRepo(module.packageName)
                        onDismiss()
                    }
                }
                ActionRow(Icons.Outlined.Tune, stringResource(R.string.scope_title)) {
                    onOpen(module)
                    onDismiss()
                }
                ActionRow(Icons.Outlined.Refresh, stringResource(R.string.module_reoptimize)) {
                    onOptimize(module)
                    onDismiss()
                }
                ActionRow(Icons.Outlined.StopCircle, stringResource(R.string.force_stop)) {
                    onForceStop(module)
                    onDismiss()
                }
                if (!module.legacy) {
                    ActionRow(Icons.Outlined.Refresh, stringResource(R.string.reset_scope_request_setting)) {
                        val ok = ManagerRepository.resetScopeRequest(module.packageName, module.userId)
                        showMessage(
                            context.getString(
                                if (ok) R.string.scope_request_setting_reset
                                else R.string.scope_request_setting_reset_failed
                            )
                        )
                        onDismiss()
                    }
                }
                ActionRow(Icons.Outlined.Delete, stringResource(R.string.module_uninstall), destructive = true) {
                    onUninstall(module)
                    onDismiss()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text,
                    modifier = Modifier.padding(start = 16.dp),
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun InstallToUserDialog(
    user: UserInfo,
    modules: List<ModuleItem>,
    onDismiss: () -> Unit,
    onInstall: (ModuleItem) -> Unit,
) {
    val candidates = modules
        .filter { it.userId == 0 }
        .distinctBy { it.packageName }
        .filter { module -> modules.none { it.packageName == module.packageName && it.userId == user.id } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.install_to_user, user.name)) },
        text = {
            if (candidates.isEmpty()) {
                Text(stringResource(R.string.no_modules))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(candidates, key = { it.id }) { module ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(module.module.packageInfo, size = 36.dp)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp),
                            ) {
                                Text(module.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    module.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            TextButton(onClick = { onInstall(module) }) {
                                Text(stringResource(R.string.install))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}


private fun moduleComparator(sort: Int): Comparator<ModuleItem> {
    val collator = java.text.Collator.getInstance(App.getLocale())
    val base = Comparator<ModuleItem> { a, b ->
        val result = when (sort / 2) {
            1 -> a.packageName.compareTo(b.packageName, ignoreCase = true)
            2 -> a.module.installTime.compareTo(b.module.installTime)
            3 -> a.module.updateTime.compareTo(b.module.updateTime)
            else -> collator.compare(a.title, b.title)
        }
        if (result != 0) result else collator.compare(a.title, b.title)
    }
    val directed = if (sort % 2 == 1) base.reversed() else base
    return Comparator { a, b ->
        when {
            a.enabled != b.enabled -> if (a.enabled) -1 else 1
            else -> directed.compare(a, b)
        }
    }
}
