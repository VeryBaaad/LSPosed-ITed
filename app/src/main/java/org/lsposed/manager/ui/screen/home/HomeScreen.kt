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

package org.lsposed.manager.ui.screen.home

import android.os.Build
import android.text.Html
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import org.lsposed.manager.ui.component.material.SnackBarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.lsposed.lspd.ILSPManagerService
import org.lsposed.manager.R
import org.lsposed.manager.data.model.FrameworkInfo
import org.lsposed.manager.data.repository.ManagerRepository
import org.lsposed.manager.ui.component.ManagerTopBar
import org.lsposed.manager.ui.component.material.SegmentedColumn
import org.lsposed.manager.ui.component.material.SegmentedListItem
import org.lsposed.manager.ui.component.material.StatusTagMaterial
import org.lsposed.manager.ui.component.material.TonalCard
import org.lsposed.manager.ui.navigation.Routes
import org.lsposed.manager.ui.util.openUrl
import org.lsposed.manager.ui.viewmodel.ManagerViewModel
import org.lsposed.manager.util.UpdateUtil

@Composable
fun HomeScreen(
    viewModel: ManagerViewModel,
    onNavigate: (String) -> Unit,
) {
    val context = LocalContext.current
    val framework by viewModel.framework.collectAsStateWithLifecycle()
    val managerInfo by viewModel.managerInfo.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val binderAlive = framework.binderAlive
    val ready = !binderAlive || framework.versionCode >= 0
    val dex2oatAbnormal = ready && binderAlive &&
        framework.dex2OatWrapper != ILSPManagerService.DEX2OAT_OK &&
        !framework.dex2OatFlagsLoaded
    val sepolicyAbnormal = ready && binderAlive && !framework.sepolicyLoaded
    val systemServerAbnormal = ready && binderAlive && !framework.systemServerRequested
    val partial = ready && binderAlive &&
        (dex2oatAbnormal || sepolicyAbnormal || systemServerAbnormal)
    val apiValue = when {
        !binderAlive -> stringResource(R.string.not_installed)
        !ready -> stringResource(R.string.loading)
        else -> framework.apiVersion.toString()
    }
    val protection = when {
        !binderAlive -> stringResource(R.string.not_installed)
        !ready -> stringResource(R.string.loading)
        framework.apiProtected -> stringResource(R.string.enabled)
        else -> stringResource(R.string.not_enabled)
    }
    val wrapper = dex2oatWrapperText(framework, ready)
    val frameworkVersion = when {
        !binderAlive -> stringResource(R.string.not_installed)
        !ready -> stringResource(R.string.loading)
        else -> "${framework.versionName} (${framework.versionCode})"
    }
    val installUrl = stringResource(R.string.install_url)

    Scaffold(
        topBar = {
            ManagerTopBar(
                title = stringResource(R.string.app_name),
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { onNavigate(Routes.ABOUT) }) {
                        Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.About))
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            if (UpdateUtil.needUpdate()) {
                WarningCard(
                    title = stringResource(R.string.need_update),
                    message = stringResource(R.string.please_update_summary),
                    level = WarningLevel.Notice,
                )
            }

            StatusCard(
                framework = framework,
                ready = ready,
                partial = partial,
                onInstall = { context.openUrl(installUrl) },
                onClick = { onNavigate(Routes.ABOUT) },
            )

            if (sepolicyAbnormal) {
                WarningCard(
                    title = stringResource(R.string.selinux_policy_not_loaded_summary),
                    message = stringResource(R.string.selinux_policy_not_loaded).plainText(),
                )
            }
            if (systemServerAbnormal) {
                WarningCard(
                    title = stringResource(R.string.system_inject_fail_summary),
                    message = stringResource(R.string.system_inject_fail).plainText(),
                )
            }
            if (dex2oatAbnormal) {
                WarningCard(
                    title = stringResource(R.string.system_prop_incorrect_summary),
                    message = stringResource(R.string.system_prop_incorrect).plainText(),
                )
            }

            SegmentedColumn(
                modifier = Modifier.fillMaxWidth(),
            ) {
                item { InfoItem(Icons.Outlined.Tag, stringResource(R.string.info_api_version), apiValue) }
                item {
                    InfoItem(
                        Icons.Outlined.Security,
                        stringResource(R.string.settings_xposed_api_call_protection),
                        protection,
                    )
                }
                item { InfoItem(Icons.Outlined.Build, stringResource(R.string.info_dex2oat_wrapper), wrapper) }
                item {
                    InfoItem(
                        Icons.Outlined.Extension,
                        stringResource(R.string.info_framework_version),
                        frameworkVersion,
                    )
                }
                item {
                    InfoItem(
                        Icons.Outlined.Android,
                        stringResource(R.string.info_manager_package_name),
                        managerInfo.packageName,
                    )
                }
                item {
                    InfoItem(
                        Icons.Outlined.Smartphone,
                        stringResource(R.string.info_system_version),
                        managerInfo.systemVersion,
                    )
                }
                item { InfoItem(Icons.Outlined.PhoneAndroid, stringResource(R.string.info_device), managerInfo.device) }
                item { InfoItem(Icons.Outlined.Memory, stringResource(R.string.info_system_abi), managerInfo.abi) }
            }

            Spacer(Modifier.height(20.dp))

            SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    HomeLink(
                        icon = Icons.Outlined.ReceiptLong,
                        title = stringResource(R.string.Logs),
                        summary = if (framework.verboseLog) {
                            stringResource(R.string.enabled_verbose_log)
                        } else {
                            stringResource(R.string.disabled_verbose_log)
                        },
                        onClick = { onNavigate(Routes.LOGS) },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private enum class WarningLevel { Error, Notice }

@Composable
private fun WarningCard(
    title: String,
    message: String,
    level: WarningLevel = WarningLevel.Error,
) {
    val container = when (level) {
        WarningLevel.Error -> MaterialTheme.colorScheme.errorContainer
        WarningLevel.Notice -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val content = contentColorFor(container)
    TonalCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = container,
        contentColor = content,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (message.isNotBlank()) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    framework: FrameworkInfo,
    ready: Boolean,
    partial: Boolean,
    onInstall: () -> Unit,
    onClick: () -> Unit,
) {
    val binderAlive = framework.binderAlive
    val container = when {
        !binderAlive -> MaterialTheme.colorScheme.errorContainer
        !ready -> MaterialTheme.colorScheme.surfaceContainerHigh
        partial -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val content = contentColorFor(container)
    val title = when {
        !binderAlive -> stringResource(R.string.not_installed)
        !ready -> stringResource(R.string.loading)
        partial -> stringResource(R.string.partial_activated)
        else -> stringResource(R.string.activated)
    }
    val summary = when {
        !binderAlive -> stringResource(R.string.install_summary)
        !ready -> framework.versionName
        else -> "${framework.versionName} (${framework.versionCode}) · API ${framework.apiVersion}"
    }
    val icon = when {
        !binderAlive -> Icons.Outlined.ErrorOutline
        !ready -> Icons.Outlined.CheckCircle
        partial -> Icons.Outlined.Warning
        else -> Icons.Outlined.CheckCircle
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.large,
        onClick = if (!binderAlive) onInstall else onClick,
    ) {
        ListItem(
            leadingContent = {
                Icon(icon, contentDescription = title, modifier = Modifier.size(24.dp))
            },
            content = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
                    if (binderAlive && ready) {
                        Spacer(Modifier.size(8.dp))
                        StatusTagMaterial(
                            label = "API ${framework.apiVersion}",
                            backgroundColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            },
            supportingContent = {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = content,
                leadingContentColor = content,
                trailingContentColor = content,
                supportingContentColor = content.copy(alpha = 0.7f),
            ),
            verticalAlignment = Alignment.CenterVertically,
        )
    }
}

@Composable
private fun HomeLink(
    icon: ImageVector,
    title: String,
    summary: String?,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = title) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
    )
}

@Composable
private fun InfoItem(icon: ImageVector, label: String, value: String) {
    SegmentedListItem(
        headlineContent = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = { Icon(icon, contentDescription = label) },
    )
}

@Composable
private fun dex2oatWrapperText(framework: FrameworkInfo, ready: Boolean): String {
    if (!framework.binderAlive) return stringResource(R.string.not_installed)
    if (!ready) return stringResource(R.string.loading)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        return "${stringResource(R.string.unsupported)} (${stringResource(R.string.android_version_unsatisfied)})"
    }
    return when (framework.dex2OatWrapper) {
        ILSPManagerService.DEX2OAT_OK -> stringResource(R.string.supported)
        ILSPManagerService.DEX2OAT_CRASHED ->
            "${stringResource(R.string.unsupported)} (${stringResource(R.string.crashed)})"
        ILSPManagerService.DEX2OAT_MOUNT_FAILED ->
            "${stringResource(R.string.unsupported)} (${stringResource(R.string.mount_failed)})"
        ILSPManagerService.DEX2OAT_SELINUX_PERMISSIVE ->
            "${stringResource(R.string.unsupported)} (${stringResource(R.string.selinux_permissive)})"
        ILSPManagerService.DEX2OAT_SEPOLICY_INCORRECT ->
            "${stringResource(R.string.unsupported)} (${stringResource(R.string.sepolicy_incorrect)})"
        else -> stringResource(R.string.unsupported)
    }
}

private fun String.plainText(): String {
    return try {
        Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY).toString().trim()
    } catch (_: Throwable) {
        this
    }
}
