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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import org.lsposed.manager.ui.component.material.SnackBarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lsposed.lspd.models.UserInfo
import org.lsposed.manager.App
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
import org.lsposed.manager.ui.component.material.ExpressiveSwitch
import org.lsposed.manager.ui.component.material.StatusTagMaterial
import org.lsposed.manager.ui.component.material.TonalCard
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

    var query by remember { mutableStateOf("") }
    var sortMenu by remember { mutableStateOf(false) }
    var selectedUserId by remember { mutableStateOf<Int?>(null) }
    var uninstallTarget by remember { mutableStateOf<ModuleItem?>(null) }
    var forceStopTarget by remember { mutableStateOf<ModuleItem?>(null) }
    var optimizeTarget by remember { mutableStateOf<ModuleItem?>(null) }
    var showUserPicker by remember { mutableStateOf(false) }
    var installTargetUser by remember { mutableStateOf<UserInfo?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(users) {
        if (users.isNotEmpty() &&
            (selectedUserId == null || users.none { it.id == selectedUserId })
        ) {
            selectedUserId = users.firstOrNull { it.id == 0 }?.id ?: users.first().id
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
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.Outlined.Sort, contentDescription = stringResource(R.string.sort))
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            SortMenuItem(
                                selected = settings.listSort % 2 == 0 && settings.listSort <= 1,
                                text = stringResource(R.string.sort_by_name),
                            ) {
                                SettingsRepository.setListSort(0)
                                sortMenu = false
                            }
                            SortMenuItem(
                                selected = settings.listSort in 2..3,
                                text = stringResource(R.string.sort_by_package),
                            ) {
                                SettingsRepository.setListSort(2)
                                sortMenu = false
                            }
                            SortMenuItem(
                                selected = settings.listSort in 4..5,
                                text = stringResource(R.string.sort_by_install),
                            ) {
                                SettingsRepository.setListSort(4)
                                sortMenu = false
                            }
                            SortMenuItem(
                                selected = settings.listSort in 6..7,
                                text = stringResource(R.string.sort_by_update),
                            ) {
                                SettingsRepository.setListSort(6)
                                sortMenu = false
                            }
                            SortMenuItem(
                                selected = settings.listSort % 2 == 1,
                                text = stringResource(R.string.sort_reverse),
                            ) {
                                val next = if (settings.listSort % 2 == 0) settings.listSort + 1 else settings.listSort - 1
                                SettingsRepository.setListSort(next)
                                sortMenu = false
                            }
                        }
                    }
                    if (users.size > 1) {
                        IconButton(onClick = { showUserPicker = true }) {
                            Icon(Icons.Outlined.PersonAdd, contentDescription = stringResource(R.string.install_to_user, ""))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackBarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .fillMaxSize(),
        ) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.module_search_hint),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (users.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    users.forEach { user ->
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
                when {
                    !modulesLoaded && filtered.isEmpty() && framework.binderAlive -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    filtered.isEmpty() && modulesLoaded -> {
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
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(13.dp),
                        ) {
                            items(filtered, key = { it.id }) { module ->
                                ModuleCard(
                                    module = module,
                                    onOpen = { onOpenModule(module.packageName, module.userId) },
                                    onToggle = { enabled ->
                                        if (!viewModel.toggleModule(module, enabled)) {
                                            showMessage(context.getString(R.string.failed_to_save_scope_list))
                                        }
                                    },
                                    onUninstall = { uninstallTarget = module },
                                    onForceStop = { forceStopTarget = module },
                                    onOptimize = { optimizeTarget = module },
                                    onOpenRepo = onOpenRepo,
                                    onResetScopeRequest = {
                                        val ok = ManagerRepository.resetScopeRequest(module.packageName, module.userId)
                                        showMessage(
                                            context.getString(
                                                if (ok) R.string.scope_request_setting_reset
                                                else R.string.scope_request_setting_reset_failed
                                            )
                                        )
                                    },
                                    showMessage = ::showMessage,
                                )
                            }
                        }
                    }
                }
            }
        }
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
            onConfirm = { ManagerRepository.forceStop(module.packageName, module.userId) },
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
            items = users.map { it.name },
            selectedIndex = -1,
            onSelected = { index ->
                installTargetUser = users.getOrNull(index)
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
                    val ok = withContext(Dispatchers.IO) { viewModel.installToUser(module, user) }
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
        trailingIcon = { if (selected) Icon(Icons.Outlined.Check, contentDescription = null) },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModuleCard(
    module: ModuleItem,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onUninstall: () -> Unit,
    onForceStop: () -> Unit,
    onOptimize: () -> Unit,
    onOpenRepo: (String) -> Unit,
    onResetScopeRequest: () -> Unit,
    showMessage: (String) -> Unit,
) {
    var expanded by rememberSaveable(module.id) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val hasRepo = ManagerRepository.onlineModule(module.packageName) != null
    val settingsIntent = remember(module.id) {
        ManagerRepository.settingsIntent(module.packageName, module.userId)
            ?: ManagerRepository.launchIntent(module.packageName, module.userId)
    }

    TonalCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .alpha(if (module.enabled) 1f else 0.6f),
                ) {
                    Text(
                        text = module.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${module.versionName} · ${module.packageName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ExpressiveSwitch(
                    checked = module.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            if (module.description.isNotBlank()) {
                Text(
                    text = module.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clickable { expanded = !expanded },
                )
            }

            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (module.legacy) {
                    StatusTagMaterial(
                        label = stringResource(R.string.legacy),
                        backgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (module.updateVersion != null) {
                    StatusTagMaterial(
                        label = stringResource(R.string.update_available, module.updateVersion),
                        backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            if (module.warning != null) {
                Text(
                    text = module.warning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = 8.dp),
                thickness = Dp.Hairline,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = onOpen,
                    modifier = Modifier.defaultMinSize(52.dp, 32.dp),
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                ) {
                    Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        text = stringResource(R.string.scope_title),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 7.dp),
                    )
                }
                FilledTonalButton(
                    onClick = onUninstall,
                    modifier = Modifier.defaultMinSize(52.dp, 32.dp),
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        text = stringResource(R.string.module_uninstall),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 7.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (settingsIntent != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.module_settings)) },
                                leadingIcon = { Icon(Icons.Outlined.PlayArrow, null) },
                                onClick = {
                                    ManagerRepository.startActivityAsUser(settingsIntent, module.userId)
                                    menu = false
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
                                menu = false
                            },
                        )
                        if (hasRepo) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.view_in_repo)) },
                                leadingIcon = { Icon(Icons.Outlined.Storefront, null) },
                                onClick = {
                                    onOpenRepo(module.packageName)
                                    menu = false
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.module_reoptimize)) },
                            leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                            onClick = {
                                menu = false
                                onOptimize()
                            },
                        )
                        if (!module.legacy) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.reset_scope_request_setting)) },
                                leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                                onClick = {
                                    menu = false
                                    onResetScopeRequest()
                                },
                            )
                        }
                        if (module.userId == 0) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.force_stop)) },
                                leadingIcon = { Icon(Icons.Outlined.StopCircle, null) },
                                onClick = {
                                    menu = false
                                    onForceStop()
                                },
                            )
                        }
                    }
                }
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
