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

package org.lsposed.manager.ui.screen.settings

import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import org.lsposed.manager.ui.component.material.SnackBarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lsposed.manager.App
import org.lsposed.manager.BuildConfig
import org.lsposed.manager.ConfigManager
import org.lsposed.manager.R
import org.lsposed.manager.data.repository.KeyColors
import org.lsposed.manager.data.repository.SettingsRepository
import org.lsposed.manager.data.repository.ThemeMode
import org.lsposed.manager.ui.component.ConfirmDialog
import org.lsposed.manager.ui.component.ManagerTopBar
import org.lsposed.manager.ui.component.material.SegmentedColumn
import org.lsposed.manager.ui.component.material.SegmentedDropdownItem
import org.lsposed.manager.ui.component.material.SegmentedListItem
import org.lsposed.manager.ui.component.material.SegmentedSwitchItem
import org.lsposed.manager.ui.util.findActivity
import org.lsposed.manager.ui.viewmodel.ManagerViewModel
import org.lsposed.manager.util.BackupUtils
import org.lsposed.manager.util.LangList
import java.util.Locale

@Composable
fun SettingsScreen(viewModel: ManagerViewModel, onAbout: () -> Unit) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val framework by viewModel.framework.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showReboot by remember { mutableStateOf(false) }
    var showHiddenIcons by remember {
        mutableStateOf(
            Settings.Global.getInt(
                context.contentResolver,
                "show_hidden_icon_apps_enabled",
                1,
            ) != 0
        )
    }

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val languages = remember { LangList.LOCALES.toList() }
    val languageLabels = remember(languages, settings.language) {
        val displayLocale = App.getLocale()
        languages.map { tag ->
            if (tag == "SYSTEM") {
                context.getString(R.string.theme_mode_system)
            } else {
                try {
                    val locale = Locale.forLanguageTag(tag)
                    if (locale.script.isNullOrEmpty()) {
                        locale.getDisplayName(displayLocale)
                    } else {
                        locale.getDisplayScript(displayLocale)
                    }
                } catch (_: Throwable) {
                    tag
                }
            }
        }
    }
    val currentLanguageIndex = languages.indexOf(settings.language).coerceAtLeast(0)

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gzip"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching { BackupUtils.backup(uri) }.exceptionOrNull()
            }
            showMessage(
                if (error == null) context.getString(R.string.settings_backup)
                else context.getString(R.string.settings_backup_failed2, error.message)
            )
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching { BackupUtils.restore(uri) }.exceptionOrNull()
            }
            showMessage(
                if (error == null) context.getString(R.string.settings_restore)
                else context.getString(R.string.settings_restore_failed2, error.message)
            )
            if (error == null) viewModel.refreshModules(force = true)
        }
    }

    val themeModeLabels = listOf(
        stringResource(R.string.theme_mode_system),
        stringResource(R.string.theme_mode_light),
        stringResource(R.string.theme_mode_dark),
    )
    val themeModes = ThemeMode.entries
    val colorLabels = KeyColors.map { stringResource(it.labelRes) }
    val channelLabels = listOf(
        stringResource(R.string.update_channel_stable),
        stringResource(R.string.update_channel_bate),
        stringResource(R.string.update_channel_nightly),
    )
    val channelValues = listOf("CHANNEL_STABLE", "CHANNEL_BETA", "CHANNEL_NIGHTLY")

    Scaffold(
        topBar = {
            ManagerTopBar(
                title = stringResource(R.string.Settings),
                scrollBehavior = scrollBehavior,
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            SegmentedColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                title = stringResource(R.string.settings_appearance),
            ) {
                item {
                    SegmentedDropdownItem(
                        icon = Icons.Outlined.DarkMode,
                        title = stringResource(R.string.theme_mode),
                        items = themeModeLabels,
                        selectedIndex = themeModes.indexOf(settings.themeMode),
                        onItemSelected = { SettingsRepository.setThemeMode(themeModes[it]) },
                    )
                }
                item(visible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SegmentedSwitchItem(
                        icon = Icons.Outlined.Palette,
                        title = stringResource(R.string.dynamic_color),
                        summary = stringResource(R.string.dynamic_color_summary),
                        checked = settings.dynamicColor,
                        onCheckedChange = SettingsRepository::setDynamicColor,
                    )
                }
                item(visible = !settings.dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    SegmentedDropdownItem(
                        icon = Icons.Outlined.Palette,
                        title = stringResource(R.string.accent_color),
                        items = colorLabels,
                        selectedIndex = KeyColors.indexOfFirst { it.value == settings.keyColor.value }
                            .coerceAtLeast(0),
                        onItemSelected = { SettingsRepository.setKeyColor(KeyColors[it]) },
                    )
                }
                item {
                    SegmentedSwitchItem(
                        title = stringResource(R.string.pure_black),
                        summary = stringResource(R.string.pure_black_summary),
                        checked = settings.amoled,
                        onCheckedChange = SettingsRepository::setAmoled,
                    )
                }
            }

            SegmentedColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                title = stringResource(R.string.settings_language),
            ) {
                item {
                    SegmentedDropdownItem(
                        icon = Icons.Outlined.Language,
                        title = stringResource(R.string.settings_language),
                        items = languageLabels,
                        selectedIndex = currentLanguageIndex,
                        onItemSelected = { index ->
                            val tag = languages.getOrNull(index) ?: return@SegmentedDropdownItem
                            SettingsRepository.setLanguage(tag)
                            App.applyLocale(tag)
                            context.findActivity()?.recreate()
                        },
                    )
                }
            }

            SegmentedColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                title = stringResource(R.string.settings_group_framework),
            ) {
                item {
                    SegmentedSwitchItem(
                        title = stringResource(R.string.settings_disable_verbose_log),
                        summary = stringResource(R.string.settings_disable_verbose_log_summary),
                        checked = framework.binderAlive && !framework.verboseLog,
                        enabled = framework.binderAlive,
                        onCheckedChange = { disabled ->
                            if (!ConfigManager.setVerboseLogEnabled(!disabled)) {
                                showMessage(context.getString(R.string.failed_to_save_scope_list))
                            }
                            viewModel.refreshFramework()
                        },
                    )
                }
                item {
                    SegmentedSwitchItem(
                        icon = Icons.Outlined.Security,
                        title = stringResource(R.string.settings_xposed_api_call_protection),
                        summary = stringResource(R.string.settings_xposed_api_call_protection_summary),
                        checked = framework.apiProtected,
                        enabled = framework.binderAlive,
                        onCheckedChange = { enabled ->
                            val ok = ConfigManager.setDexObfuscateEnabled(enabled)
                            viewModel.refreshFramework()
                            if (ok) showReboot = true
                        },
                    )
                }
                item {
                    SegmentedSwitchItem(
                        title = stringResource(R.string.settings_enable_status_notification),
                        summary = stringResource(R.string.settings_enable_status_notification_summary),
                        checked = framework.statusNotificationEnabled,
                        enabled = framework.binderAlive,
                        onCheckedChange = {
                            ConfigManager.setEnableStatusNotification(it)
                            viewModel.refreshFramework()
                        },
                    )
                }
            }

            SegmentedColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                title = stringResource(R.string.settings_network),
            ) {
                item {
                    SegmentedSwitchItem(
                        icon = Icons.Outlined.Dns,
                        title = stringResource(R.string.settings_doh),
                        summary = stringResource(R.string.settings_doh_summary),
                        checked = settings.doh,
                        onCheckedChange = SettingsRepository::setDoh,
                    )
                }
            }

            SegmentedColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                title = stringResource(R.string.settings_group_repo),
            ) {
                item {
                    SegmentedDropdownItem(
                        icon = Icons.Outlined.SystemUpdate,
                        title = stringResource(R.string.settings_update_channel),
                        items = channelLabels,
                        selectedIndex = channelValues.indexOf(settings.updateChannel).coerceAtLeast(0),
                        onItemSelected = { index ->
                            val channel = channelValues[index]
                            SettingsRepository.setUpdateChannel(channel)
                            org.lsposed.manager.repo.RepoLoader.getInstance().updateLatestVersion(channel)
                            viewModel.refreshRepo(remote = false)
                        },
                    )
                }
            }

            SegmentedColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                title = stringResource(R.string.settings_backup_and_restore),
            ) {
                item {
                    SegmentedListItem(
                        onClick = {
                            runCatching { backupLauncher.launch("LSPosed_backup.lsp") }
                                .onFailure { showMessage(context.getString(R.string.enable_documentui)) }
                        },
                        enabled = framework.binderAlive,
                        headlineContent = { Text(stringResource(R.string.settings_backup)) },
                        supportingContent = { Text(stringResource(R.string.settings_backup_summery)) },
                        leadingContent = { Icon(Icons.Outlined.Backup, null) },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    )
                }
                item {
                    SegmentedListItem(
                        onClick = {
                            runCatching { restoreLauncher.launch(arrayOf("*/*")) }
                                .onFailure { showMessage(context.getString(R.string.enable_documentui)) }
                        },
                        enabled = framework.binderAlive,
                        headlineContent = { Text(stringResource(R.string.settings_restore)) },
                        supportingContent = { Text(stringResource(R.string.settings_restore_summery)) },
                        leadingContent = { Icon(Icons.Outlined.Restore, null) },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    )
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                SegmentedColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    title = stringResource(R.string.settings_group_system),
                ) {
                    item {
                        SegmentedSwitchItem(
                            title = stringResource(R.string.settings_show_hidden_icon_apps_enabled),
                            summary = stringResource(R.string.settings_show_hidden_icon_apps_enabled_summary),
                            checked = showHiddenIcons,
                            enabled = framework.binderAlive,
                            onCheckedChange = { enabled ->
                                if (ConfigManager.setHiddenIcon(!enabled)) {
                                    showHiddenIcons = enabled
                                } else {
                                    showMessage(context.getString(R.string.failed_to_save_scope_list))
                                }
                            },
                        )
                    }
                }
            }

            SegmentedColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                title = stringResource(R.string.settings_about),
            ) {
                item {
                    SegmentedListItem(
                        onClick = onAbout,
                        headlineContent = { Text(stringResource(R.string.About)) },
                        supportingContent = { Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") },
                        leadingContent = { Icon(Icons.Outlined.Info, null) },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    )
                }
            }
        }
    }

    if (showReboot) {
        ConfirmDialog(
            title = stringResource(R.string.reboot_required),
            confirmText = stringResource(R.string.reboot),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { ConfigManager.reboot() },
            onDismiss = { showReboot = false },
        )
    }
}
