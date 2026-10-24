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

package org.lsposed.manager.ui.screen.repo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import org.lsposed.manager.ui.component.material.SnackBarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.lsposed.manager.R
import org.lsposed.manager.data.model.RepoItem
import org.lsposed.manager.data.repository.SettingsRepository
import org.lsposed.manager.ui.component.ManagerTopBar
import org.lsposed.manager.ui.component.SearchField
import org.lsposed.manager.ui.component.material.StatusTagMaterial
import org.lsposed.manager.ui.component.material.TonalCard
import org.lsposed.manager.ui.util.formatDate
import org.lsposed.manager.ui.viewmodel.ManagerViewModel

@Composable
fun RepositoryScreen(viewModel: ManagerViewModel, onOpenModule: (String) -> Unit) {
    val repo by viewModel.repoItems.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snackbarHostState = remember { SnackbarHostState() }
    var query by remember { mutableStateOf("") }
    var sortMenu by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!viewModel.repoLoaded.value) {
            viewModel.refreshRepo(remote = true)
        }
    }

    LaunchedEffect(refreshing) {
        if (refreshing) {
            delay(1500)
            refreshing = false
        }
    }

    val filtered = repo.filter {
        query.isBlank() ||
            it.title.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true) ||
            (it.summary?.contains(query, ignoreCase = true) == true)
    }

    Scaffold(
        topBar = {
            ManagerTopBar(
                title = stringResource(R.string.module_repo),
                scrollBehavior = scrollBehavior,
                actions = {
                    if (repo.isNotEmpty()) {
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.Outlined.Sort, contentDescription = stringResource(R.string.sort))
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.repo_sort_by_name)) },
                                onClick = {
                                    SettingsRepository.setRepoSort(0)
                                    sortMenu = false
                                },
                                trailingIcon = {
                                    if (settings.repoSort == 0) Icon(Icons.Outlined.Check, null)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.repo_sort_by_update)) },
                                onClick = {
                                    SettingsRepository.setRepoSort(1)
                                    sortMenu = false
                                },
                                trailingIcon = {
                                    if (settings.repoSort == 1) Icon(Icons.Outlined.Check, null)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.repo_upgradable_first)) },
                                onClick = {
                                    SettingsRepository.setUpgradableFirst(!settings.upgradableFirst)
                                    sortMenu = false
                                },
                                trailingIcon = {
                                    if (settings.upgradableFirst) Icon(Icons.Outlined.Check, null)
                                },
                            )
                        }
                    }
                    }
                },
            )
        },
        snackbarHost = { SnackBarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .fillMaxSize(),
        ) {
            if (repo.isNotEmpty()) {
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.repo_search_hint),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    refreshing = true
                    viewModel.refreshRepo(remote = true)
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (filtered.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(13.dp),
                    ) {
                        items(filtered, key = { it.packageName }) { item ->
                            RepoModuleCard(item, onClick = { onOpenModule(item.packageName) })
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun RepoModuleCard(item: RepoItem, onClick: () -> Unit) {
    TonalCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 10.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "ID: ${item.packageName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!item.summary.isNullOrBlank()) {
                Text(
                    text = item.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            val update = item.upgradableVersion
            if (update != null || item.installed) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (update != null) {
                        StatusTagMaterial(
                            label = stringResource(R.string.update_available, update),
                            backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    } else {
                        StatusTagMaterial(
                            label = stringResource(R.string.installed),
                            backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = 8.dp),
                thickness = Dp.Hairline,
            )
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.module.latestRelease ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                val date = formatDate(item.updatedAt)
                if (date != null) {
                    Text(
                        text = stringResource(R.string.module_repo_updated_time, date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
