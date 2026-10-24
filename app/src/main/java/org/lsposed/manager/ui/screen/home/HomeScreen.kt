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
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.lsposed.lspd.ILSPManagerService
import org.lsposed.manager.BuildConfig
import org.lsposed.manager.R
import org.lsposed.manager.data.model.FrameworkInfo
import org.lsposed.manager.data.repository.ManagerRepository
import org.lsposed.manager.ui.component.ConfirmDialog
import org.lsposed.manager.ui.component.GroupItemDivider
import org.lsposed.manager.ui.component.ManagerTopBar
import org.lsposed.manager.ui.component.SettingNavigationRow
import org.lsposed.manager.ui.component.SettingRow
import org.lsposed.manager.ui.component.SettingsGroup
import org.lsposed.manager.ui.component.SettingsHeader
import org.lsposed.manager.ui.navigation.Routes
import org.lsposed.manager.ui.util.copyToClipboard
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
    val modules by viewModel.modules.collectAsStateWithLifecycle()
    val upgradeCount by viewModel.upgradeCount.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showRebootDialog by remember { mutableStateOf(false) }

    val binderAlive = framework.binderAlive
    val ready = !binderAlive || framework.versionCode >= 0
    val dex2oatAbnormal = ready && binderAlive &&
        framework.dex2OatWrapper != ILSPManagerService.DEX2OAT_OK &&
        !framework.dex2OatFlagsLoaded
    val sepolicyAbnormal = ready && binderAlive && !framework.sepolicyLoaded
    val systemServerAbnormal = ready && binderAlive && !framework.systemServerRequested
    val partial = ready && binderAlive &&
        (dex2oatAbnormal || sepolicyAbnormal || systemServerAbnormal)

    val enabledModules = modules.count { it.enabled }
    val latestUrl = stringResource(R.string.latest_url)
    val installUrl = stringResource(R.string.install_url)

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    Scaffold(
        topBar = {
            ManagerTopBar(
                title = stringResource(R.string.app_name),
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { onNavigate(Routes.ABOUT) }) {
                        Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.About))
                    }
                    IconButton(onClick = {
                        context.openUrl("https://github.com/LSPosed/LSPosed/issues/new/choose")
                    }) {
                        Icon(Icons.Outlined.BugReport, contentDescription = stringResource(R.string.report_issue))
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            if (UpdateUtil.needUpdate()) {
                WarningCard(
                    title = stringResource(R.string.need_update),
                    summary = stringResource(R.string.please_update_summary),
                    level = WarningLevel.Error,
                    action = stringResource(R.string.install),
                    onAction = { context.openUrl(latestUrl) },
                )
            }

            StatusCard(
                framework = framework,
                managerVersion = "${managerInfo.versionName} (${managerInfo.versionCode})",
                partial = partial,
                ready = ready,
                onInstall = { context.openUrl(installUrl) },
            )

            if (sepolicyAbnormal) {
                WarningCard(
                    title = stringResource(R.string.selinux_policy_not_loaded_summary),
                    summary = stringResource(R.string.selinux_policy_not_loaded).plainText(),
                )
            }
            if (systemServerAbnormal) {
                WarningCard(
                    title = stringResource(R.string.system_inject_fail_summary),
                    summary = stringResource(R.string.system_inject_fail).plainText(),
                )
            }
            if (dex2oatAbnormal) {
                WarningCard(
                    title = stringResource(R.string.system_prop_incorrect_summary),
                    summary = stringResource(R.string.system_prop_incorrect).plainText(),
                )
            }

            SettingsHeader(stringResource(R.string.quick_actions))
            SettingsGroup {
                SettingNavigationRow(
                    title = stringResource(R.string.Modules),
                    summary = when {
                        !binderAlive -> stringResource(R.string.not_installed_summary)
                        enabledModules > 0 -> pluralStringResource(R.plurals.modules_enabled_count, enabledModules, enabledModules)
                        else -> stringResource(R.string.no_module_enabled)
                    },
                    icon = Icons.Outlined.Extension,
                    onClick = { onNavigate(Routes.MODULES) },
                )
                GroupItemDivider()
                SettingNavigationRow(
                    title = stringResource(R.string.module_repo),
                    summary = when {
                        upgradeCount > 0 -> pluralStringResource(R.plurals.module_repo_upgradable, upgradeCount, upgradeCount)
                        else -> stringResource(R.string.module_repo_up_to_date)
                    },
                    icon = Icons.Outlined.Storefront,
                    onClick = { onNavigate(Routes.REPOSITORY) },
                )
                GroupItemDivider()
                SettingNavigationRow(
                    title = stringResource(R.string.Logs),
                    summary = when {
                        framework.verboseLog -> stringResource(R.string.enabled_verbose_log)
                        else -> stringResource(R.string.disabled_verbose_log)
                    },
                    icon = Icons.Outlined.ReceiptLong,
                    onClick = { onNavigate(Routes.LOGS) },
                )
                GroupItemDivider()
                SettingNavigationRow(
                    title = stringResource(R.string.Settings),
                    summary = stringResource(R.string.settings_group_theme),
                    icon = Icons.Outlined.Settings,
                    onClick = { onNavigate(Routes.SETTINGS) },
                )
                if (binderAlive) {
                    GroupItemDivider()
                    SettingRow(
                        title = stringResource(R.string.home_reboot),
                        icon = Icons.Outlined.RestartAlt,
                        onClick = { showRebootDialog = true },
                    )
                }
                GroupItemDivider()
                SettingRow(
                    title = stringResource(R.string.report_issue),
                    icon = Icons.Outlined.BugReport,
                    onClick = {
                        context.openUrl("https://github.com/LSPosed/LSPosed/issues/new/choose")
                    },
                )
            }

            SettingsHeader(stringResource(R.string.home_info))
            SettingsGroup {
                val apiValue = if (binderAlive) framework.apiVersion.toString() else stringResource(R.string.not_installed)
                val protection = if (!binderAlive) {
                    stringResource(R.string.not_installed)
                } else if (framework.apiProtected) {
                    stringResource(R.string.enabled)
                } else {
                    stringResource(R.string.not_enabled)
                }
                val wrapper = dex2oatWrapperText(framework)
                val version = if (binderAlive) {
                    "${framework.versionName} (${framework.versionCode})"
                } else {
                    stringResource(R.string.not_installed)
                }

                HomeInfoRow(stringResource(R.string.info_api_version), apiValue)
                GroupItemDivider()
                HomeInfoRow(stringResource(R.string.settings_xposed_api_call_protection), protection)
                GroupItemDivider()
                HomeInfoRow(stringResource(R.string.info_dex2oat_wrapper), wrapper)
                GroupItemDivider()
                HomeInfoRow(stringResource(R.string.info_framework_version), version)
                GroupItemDivider()
                HomeInfoRow(stringResource(R.string.info_manager_package_name), managerInfo.packageName)
                GroupItemDivider()
                HomeInfoRow(stringResource(R.string.info_system_version), managerInfo.systemVersion)
                GroupItemDivider()
                HomeInfoRow(stringResource(R.string.info_device), managerInfo.device)
                GroupItemDivider()
                HomeInfoRow(stringResource(R.string.info_system_abi), managerInfo.abi)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = {
                        val info = buildString {
                            appendLine("${context.getString(R.string.app_name)} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                            appendLine()
                            appendLine("${context.getString(R.string.info_api_version)}: $apiValue")
                            appendLine("${context.getString(R.string.settings_xposed_api_call_protection)}: $protection")
                            appendLine("${context.getString(R.string.info_dex2oat_wrapper)}: $wrapper")
                            appendLine("${context.getString(R.string.info_framework_version)}: $version")
                            appendLine("${context.getString(R.string.info_manager_package_name)}: ${managerInfo.packageName}")
                            appendLine("${context.getString(R.string.info_system_version)}: ${managerInfo.systemVersion}")
                            appendLine("${context.getString(R.string.info_device)}: ${managerInfo.device}")
                            appendLine("${context.getString(R.string.info_system_abi)}: ${managerInfo.abi}")
                            appendLine("${context.getString(R.string.dark_mode)}: ${settings.themeMode}")
                        }
                        context.copyToClipboard("LSPosed", info)
                        showMessage(context.getString(R.string.info_copied))
                    }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            stringResource(R.string.copy_info),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            Text(
                text = "${stringResource(R.string.app_name)} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 24.dp),
            )
        }
    }

    if (showRebootDialog) {
        ConfirmDialog(
            title = stringResource(R.string.home_reboot),
            text = stringResource(R.string.home_reboot_message),
            confirmText = stringResource(R.string.reboot),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { ManagerRepository.reboot() },
            onDismiss = { showRebootDialog = false },
        )
    }
}

private enum class WarningLevel { Error, Notice }

@Composable
private fun WarningCard(
    title: String,
    summary: String?,
    level: WarningLevel = WarningLevel.Error,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val container = if (level == WarningLevel.Error) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = container,
        contentColor = contentColorFor(container),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Outlined.Warning,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (!summary.isNullOrBlank()) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (action != null && onAction != null) {
                    TextButton(onClick = onAction, modifier = Modifier.padding(top = 4.dp)) {
                        Text(action)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    framework: FrameworkInfo,
    managerVersion: String,
    partial: Boolean,
    ready: Boolean,
    onInstall: () -> Unit,
) {
    val binderAlive = framework.binderAlive
    val container = when {
        !binderAlive -> MaterialTheme.colorScheme.errorContainer
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
        !binderAlive -> stringResource(R.string.not_install_summary)
        !ready -> ""
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
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    stringResource(R.string.manager_version, managerVersion),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (!binderAlive) {
                Button(onClick = onInstall) {
                    Text(stringResource(R.string.install))
                }
            }
        }
    }
}

@Composable
private fun HomeInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1.2f),
        )
    }
}

@Composable
private fun dex2oatWrapperText(framework: FrameworkInfo): String {
    if (!framework.binderAlive) return stringResource(R.string.not_installed)
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
