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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import org.lsposed.manager.ui.component.GroupItemDivider
import org.lsposed.manager.ui.component.ManagerTopBar
import org.lsposed.manager.ui.component.SettingNavigationRow
import org.lsposed.manager.ui.component.SettingSwitchRow
import org.lsposed.manager.ui.component.SettingDropdownRow
import org.lsposed.manager.ui.component.SettingsGroup
import org.lsposed.manager.ui.component.SettingsHeader
import org.lsposed.manager.ui.component.SingleChoiceDialog
import org.lsposed.manager.ui.util.findActivity
import org.lsposed.manager.ui.util.openUrl
import org.lsposed.manager.ui.viewmodel.ManagerViewModel
import org.lsposed.manager.util.BackupUtils
import org.lsposed.manager.util.LangList
import org.lsposed.manager.util.UpdateUtil
import java.util.Locale

@Composable
fun SettingsScreen(viewModel: ManagerViewModel, onAbout: () -> Unit) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val framework by viewModel.framework.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showThemeMode by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    var showChannel by remember { mutableStateOf(false) }
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

    val themeModeLabel = when (settings.themeMode) {
        ThemeMode.SYSTEM -> stringResource(R.string.theme_mode_system)
        ThemeMode.LIGHT -> stringResource(R.string.theme_mode_light)
        ThemeMode.DARK -> stringResource(R.string.theme_mode_dark)
    }
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        ) {
            item {
                SettingsHeader(stringResource(R.string.settings_appearance))
                SettingsGroup {
                    SettingDropdownRow(
                        title = stringResource(R.string.theme_mode),
                        value = themeModeLabel,
                        icon = Icons.Outlined.DarkMode,
                        onClick = { showThemeMode = true },
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        GroupItemDivider()
                        SettingSwitchRow(
                            title = stringResource(R.string.dynamic_color),
                            summary = stringResource(R.string.dynamic_color_summary),
                            icon = Icons.Outlined.Palette,
                            checked = settings.dynamicColor,
                            onCheckedChange = SettingsRepository::setDynamicColor,
                        )
                    }
                    if (!settings.dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        GroupItemDivider()
                        SettingDropdownRow(
                            title = stringResource(R.string.accent_color),
                            value = stringResource(settings.keyColor.labelRes),
                            icon = Icons.Outlined.Palette,
                            onClick = { showColorPicker = true },
                        )
                    }
                    GroupItemDivider()
                    SettingSwitchRow(
                        title = stringResource(R.string.pure_black),
                        summary = stringResource(R.string.pure_black_summary),
                        checked = settings.amoled,
                        onCheckedChange = SettingsRepository::setAmoled,
                    )
                }
            }

            item {
                SettingsHeader(stringResource(R.string.settings_language))
                SettingsGroup {
                    SettingDropdownRow(
                        title = stringResource(R.string.settings_language),
                        value = languageLabels.getOrElse(currentLanguageIndex) { settings.language },
                        icon = Icons.Outlined.Language,
                        onClick = { showLanguage = true },
                    )
                }
            }

            item {
                SettingsHeader(stringResource(R.string.settings_group_framework))
                SettingsGroup {
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_disable_verbose_log),
                        summary = stringResource(R.string.settings_disable_verbose_log_summary),
                        checked = framework.binderAlive && !framework.verboseLog,
                        enabled = framework.binderAlive,
                        onCheckedChange = { disabled ->
                            val ok = ConfigManager.setVerboseLogEnabled(!disabled)
                            if (ok) viewModel.refreshFramework() else showMessage(context.getString(R.string.failed_to_save_scope_list))
                        },
                    )
                    GroupItemDivider()
                    SettingSwitchRow(
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
                    GroupItemDivider()
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_enable_status_notification),
                        summary = stringResource(R.string.settings_enable_status_notification_summary),
                        checked = framework.statusNotificationEnabled,
                        enabled = framework.binderAlive,
                        onCheckedChange = { enabled ->
                            ConfigManager.setEnableStatusNotification(enabled)
                            viewModel.refreshFramework()
                        },
                    )
                }
            }

            item {
                SettingsHeader(stringResource(R.string.settings_network))
                SettingsGroup {
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_doh),
                        summary = stringResource(R.string.settings_doh_summary),
                        icon = Icons.Outlined.Dns,
                        checked = settings.doh,
                        onCheckedChange = SettingsRepository::setDoh,
                    )
                }
            }

            item {
                SettingsHeader(stringResource(R.string.settings_group_repo))
                SettingsGroup {
                    SettingDropdownRow(
                        title = stringResource(R.string.settings_update_channel),
                        value = channelLabels.getOrElse(channelValues.indexOf(settings.updateChannel).coerceAtLeast(0)) { "" },
                        icon = Icons.Outlined.SystemUpdate,
                        onClick = { showChannel = true },
                    )
                }
            }

            item {
                SettingsHeader(stringResource(R.string.settings_backup_and_restore))
                SettingsGroup {
                    SettingNavigationRow(
                        title = stringResource(R.string.settings_backup),
                        summary = stringResource(R.string.settings_backup_summery),
                        icon = Icons.Outlined.Backup,
                        enabled = framework.binderAlive,
                        onClick = {
                            runCatching {
                                backupLauncher.launch("LSPosed_backup.lsp")
                            }.onFailure {
                                showMessage(context.getString(R.string.enable_documentui))
                            }
                        },
                    )
                    GroupItemDivider()
                    SettingNavigationRow(
                        title = stringResource(R.string.settings_restore),
                        summary = stringResource(R.string.settings_restore_summery),
                        icon = Icons.Outlined.Restore,
                        enabled = framework.binderAlive,
                        onClick = {
                            runCatching {
                                restoreLauncher.launch(arrayOf("*/*"))
                            }.onFailure {
                                showMessage(context.getString(R.string.enable_documentui))
                            }
                        },
                    )
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                item {
                    SettingsHeader(stringResource(R.string.settings_group_system))
                    SettingsGroup {
                        SettingSwitchRow(
                            title = stringResource(R.string.settings_show_hidden_icon_apps_enabled),
                            summary = stringResource(R.string.settings_show_hidden_icon_apps_enabled_summary),
                            checked = showHiddenIcons,
                            enabled = framework.binderAlive,
                            onCheckedChange = { enabled ->
                                val ok = ConfigManager.setHiddenIcon(!enabled)
                                if (ok) {
                                    showHiddenIcons = enabled
                                } else {
                                    showMessage(context.getString(R.string.failed_to_save_scope_list))
                                }
                            },
                        )
                    }
                }
            }

            item {
                SettingsHeader(stringResource(R.string.settings_about))
                SettingsGroup {
                    SettingNavigationRow(
                        title = stringResource(R.string.About),
                        summary = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        icon = Icons.Outlined.Info,
                        onClick = onAbout,
                    )
                    GroupItemDivider()
                    SettingNavigationRow(
                        title = stringResource(R.string.about_source),
                        summary = "github.com/LSPosed/LSPosed",
                        icon = Icons.Outlined.Security,
                        onClick = {
                            context.openUrl("https://github.com/LSPosed/LSPosed")
                        },
                    )
                    GroupItemDivider()
                    SettingNavigationRow(
                        title = stringResource(R.string.about_report_issue),
                        summary = stringResource(R.string.report_issue),
                        onClick = {
                            context.openUrl("https://github.com/LSPosed/LSPosed/issues/new/choose")
                        },
                    )
                }
            }
        }
    }

    if (showThemeMode) {
        val modes = ThemeMode.entries
        SingleChoiceDialog(
            title = stringResource(R.string.theme_mode),
            items = listOf(
                stringResource(R.string.theme_mode_system),
                stringResource(R.string.theme_mode_light),
                stringResource(R.string.theme_mode_dark),
            ),
            selectedIndex = modes.indexOf(settings.themeMode),
            onSelected = { index -> SettingsRepository.setThemeMode(modes[index]) },
            onDismiss = { showThemeMode = false },
        )
    }

    if (showColorPicker) {
        ColorPickerDialog(
            selected = settings.keyColor.value,
            onSelect = {
                SettingsRepository.setKeyColor(it)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false },
        )
    }

    if (showLanguage) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_language),
            items = languageLabels,
            selectedIndex = currentLanguageIndex,
            onSelected = { index ->
                val tag = languages.getOrNull(index) ?: return@SingleChoiceDialog
                SettingsRepository.setLanguage(tag)
                App.applyLocale(tag)
                context.findActivity()?.recreate()
            },
            onDismiss = { showLanguage = false },
        )
    }

    if (showChannel) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_update_channel),
            items = channelLabels,
            selectedIndex = channelValues.indexOf(settings.updateChannel).coerceAtLeast(0),
            onSelected = { index ->
                val channel = channelValues[index]
                SettingsRepository.setUpdateChannel(channel)
                org.lsposed.manager.repo.RepoLoader.getInstance().updateLatestVersion(channel)
                viewModel.refreshRepo(remote = false)
            },
            onDismiss = { showChannel = false },
        )
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

@Composable
private fun ColorPickerDialog(
    selected: String,
    onSelect: (org.lsposed.manager.data.repository.KeyColor) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.accent_color)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                KeyColors.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { keyColor ->
                            val isSelected = keyColor.value == selected
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(keyColor.color)
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable { onSelect(keyColor) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSelected) {
                                    Text("✓", color = Color.White)
                                }
                            }
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
