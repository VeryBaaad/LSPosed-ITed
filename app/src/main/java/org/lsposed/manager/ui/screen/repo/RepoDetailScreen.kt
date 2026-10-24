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

import android.text.Html
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.lsposed.manager.R
import org.lsposed.manager.data.repository.ManagerRepository
import org.lsposed.manager.repo.model.OnlineModule
import org.lsposed.manager.repo.model.Release
import org.lsposed.manager.repo.model.ReleaseAsset
import kotlinx.coroutines.delay
import org.lsposed.manager.ui.component.DetailTopBar
import org.lsposed.manager.ui.component.EmptyState
import org.lsposed.manager.ui.component.HtmlContent
import org.lsposed.manager.ui.component.SettingRow
import org.lsposed.manager.ui.component.SettingsGroup
import org.lsposed.manager.ui.component.StatusTag
import org.lsposed.manager.ui.util.formatBytes
import org.lsposed.manager.ui.util.formatDateTime
import org.lsposed.manager.ui.util.openUrl
import org.lsposed.manager.ui.viewmodel.ManagerViewModel

@Composable
fun RepoDetailScreen(
    viewModel: ManagerViewModel,
    packageName: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repoItems by viewModel.repoItems.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val item = repoItems.firstOrNull { it.packageName == packageName }
    val module = ManagerRepository.onlineModule(packageName) ?: item?.module

    var tab by remember { mutableIntStateOf(0) }
    var assets by remember { mutableStateOf<List<ReleaseAsset>?>(null) }
    var loadingMore by remember { mutableStateOf(false) }

    LaunchedEffect(module?.releasesLoaded) {
        if (module?.releasesLoaded == true) loadingMore = false
    }
    LaunchedEffect(loadingMore) {
        if (loadingMore) {
            delay(8000)
            loadingMore = false
        }
    }

    if (module == null) {
        Scaffold(
            topBar = {
                DetailTopBar(
                    title = packageName,
                    subtitle = null,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                )
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) { padding ->
            if (item == null) {
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
                    title = stringResource(R.string.repo_error),
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                )
            }
        }
        return
    }

    val title = module.description ?: packageName
    val update = item?.upgradableVersion

    Scaffold(
        topBar = {
            DetailTopBar(
                title = title,
                subtitle = packageName,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        context.openUrl("https://modules.lsposed.org/module/$packageName")
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = stringResource(R.string.repo_open_browser),
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            RepoHeader(
                module = module,
                update = update,
                installed = item?.installed == true,
            )
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text(stringResource(R.string.module_readme)) },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text(stringResource(R.string.module_releases)) },
                )
                Tab(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    text = { Text(stringResource(R.string.module_information)) },
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
            when (tab) {
                0 -> {
                    val readme = module.readmeHTML
                    if (readme.isNullOrBlank()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            EmptyState(
                                icon = Icons.Outlined.Info,
                                title = stringResource(R.string.module_readme),
                                summary = stringResource(R.string.list_empty),
                            )
                        }
                    } else {
                        HtmlContent(html = readme, modifier = Modifier.fillMaxSize())
                    }
                }
                1 -> {
                    val releases = filteredReleases(module, settings.updateChannel)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(releases, key = { it.url ?: it.name ?: it.publishedAt ?: it.hashCode().toString() }) { release ->
                            ReleaseCard(
                                release = release,
                                onOpen = { release.url?.let(context::openUrl) },
                                onAssets = { assets = release.releaseAssets.orEmpty() },
                            )
                        }
                        if (!module.releasesLoaded) {
                            item {
                                TextButton(
                                    onClick = {
                                        loadingMore = true
                                        ManagerRepository.loadRemoteReleases(packageName)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    if (loadingMore) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                    } else {
                                        Text(stringResource(R.string.repo_load_more))
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    RepoInformation(module = module)
                }
            }
            }
        }
    }

    assets?.let { list ->
        AssetsDialog(
            assets = list,
            onDismiss = { assets = null },
            onOpen = {
                context.openUrl(it)
                assets = null
            },
        )
    }
}

@Composable
private fun RepoHeader(module: OnlineModule, update: String?, installed: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Extension,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                ) {
                    Text(
                        module.description ?: module.name ?: "",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        module.name ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when {
                    update != null -> StatusTag(
                        label = stringResource(R.string.update_available, update),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    installed -> {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = stringResource(R.string.installed),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            module.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun ReleaseCard(
    release: Release,
    onOpen: () -> Unit,
    onAssets: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                release.name ?: release.tagName ?: "",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val date = formatDateTime(release.publishedAt)
            if (date != null) {
                Text(
                    stringResource(R.string.module_repo_published_time, date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            val description = release.descriptionHTML?.let(::htmlToText)?.ifBlank { release.description }
            if (!description.isNullOrBlank()) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .heightIn(max = 160.dp),
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onOpen) {
                    Text(stringResource(R.string.repo_open_browser))
                }
                if (!release.releaseAssets.isNullOrEmpty()) {
                    TextButton(onClick = onAssets) {
                        Text(stringResource(R.string.module_release_view_assets))
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoInformation(module: OnlineModule) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        SettingsGroup(modifier = Modifier.padding(0.dp)) {
            if (!module.homepageUrl.isNullOrBlank()) {
                SettingRow(
                    title = stringResource(R.string.module_information_homepage),
                    summary = module.homepageUrl,
                    onClick = { module.homepageUrl?.let(context::openUrl) },
                )
            }
            if (!module.sourceUrl.isNullOrBlank()) {
                SettingRow(
                    title = stringResource(R.string.module_information_source_url),
                    summary = module.sourceUrl,
                    onClick = { module.sourceUrl?.let(context::openUrl) },
                )
            }
            val collaborators = module.collaborators.orEmpty()
            if (collaborators.isNotEmpty()) {
                SettingRow(
                    title = stringResource(R.string.module_information_collaborators),
                    summary = collaborators.joinToString(", ") { it.login ?: it.name ?: "" },
                    onClick = {
                        collaborators.firstOrNull()?.login?.let { context.openUrl("https://github.com/$it") }
                    },
                )
            }
            val release = module.latestRelease
            if (!release.isNullOrBlank()) {
                SettingRow(
                    title = stringResource(R.string.module_releases),
                    summary = release,
                )
            }
        }
    }
}

@Composable
private fun AssetsDialog(
    assets: List<ReleaseAsset>,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.module_release_view_assets)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(assets, key = { it.downloadUrl ?: it.name ?: it.hashCode().toString() }) { asset ->
                    TextButton(
                        onClick = { asset.downloadUrl?.let(onOpen) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                asset.name ?: "",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${formatBytes(asset.size.toLong())} / ${
                                    pluralStringResource(
                                        R.plurals.module_release_assets_download_count,
                                        asset.downloadCount,
                                        asset.downloadCount,
                                    )
                                }",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

private fun filteredReleases(module: OnlineModule, channel: String): List<Release> {
    val releases = ManagerRepository.releases(module.name ?: "") ?: module.releases.orEmpty()
    return when (channel) {
        "CHANNEL_BETA" -> releases.filterNot {
            it.name?.lowercase()?.startsWith("snapshot") == true ||
                it.name?.lowercase()?.startsWith("nightly") == true
        }
        "CHANNEL_NIGHTLY" -> releases
        else -> releases.filterNot {
            it.isPrerelease == true ||
                it.name?.lowercase()?.startsWith("snapshot") == true ||
                it.name?.lowercase()?.startsWith("nightly") == true
        }
    }
}

private fun htmlToText(html: String): String {
    return try {
        Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().trim()
    } catch (_: Throwable) {
        html
    }
}
