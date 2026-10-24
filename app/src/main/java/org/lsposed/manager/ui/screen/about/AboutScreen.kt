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

package org.lsposed.manager.ui.screen.about

import android.os.Build
import android.text.Html
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.lsposed.manager.BuildConfig
import org.lsposed.manager.R
import org.lsposed.manager.ui.component.AppIcon
import org.lsposed.manager.ui.component.DetailTopBar
import org.lsposed.manager.ui.component.GroupItemDivider
import org.lsposed.manager.ui.component.SettingNavigationRow
import org.lsposed.manager.ui.component.SettingRow
import org.lsposed.manager.ui.component.SettingsGroup
import org.lsposed.manager.ui.component.SettingsHeader
import org.lsposed.manager.ui.util.openUrl
import org.lsposed.manager.ui.viewmodel.ManagerViewModel

@Composable
fun AboutScreen(viewModel: ManagerViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val framework by viewModel.framework.collectAsStateWithLifecycle()
    val packageInfo = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    val translators = stringResource(R.string.translators).let {
        runCatching { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString() }.getOrDefault(it)
    }

    Scaffold(
        topBar = {
            DetailTopBar(
                title = stringResource(R.string.About),
                subtitle = stringResource(R.string.app_name),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppIcon(packageInfo, size = 72.dp)
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    if (framework.binderAlive) {
                        "${framework.versionName} (${framework.versionCode}) · API ${framework.apiVersion}"
                    } else {
                        stringResource(R.string.not_installed)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    stringResource(R.string.about_license),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            SettingsHeader(stringResource(R.string.about_source), modifier = Modifier.padding(horizontal = 16.dp))
            SettingsGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                SettingNavigationRow(
                    title = stringResource(R.string.about_source),
                    summary = "github.com/LSPosed/LSPosed",
                    icon = Icons.Outlined.Code,
                    onClick = { context.openUrl("https://github.com/LSPosed/LSPosed") },
                )
                GroupItemDivider()
                SettingNavigationRow(
                    title = stringResource(R.string.about_telegram),
                    summary = "@LSPosed",
                    icon = Icons.Outlined.Chat,
                    onClick = { context.openUrl("https://t.me/LSPosed") },
                )
                GroupItemDivider()
                SettingNavigationRow(
                    title = stringResource(R.string.about_report_issue),
                    summary = stringResource(R.string.report_issue),
                    icon = Icons.Outlined.BugReport,
                    onClick = {
                        context.openUrl("https://github.com/LSPosed/LSPosed/issues/new/choose")
                    },
                )
                GroupItemDivider()
                SettingNavigationRow(
                    title = stringResource(R.string.about_translations),
                    summary = "crowdin.com/project/lsposed",
                    icon = Icons.Outlined.Language,
                    onClick = { context.openUrl("https://lsposed.crowdin.com/lsposed") },
                )
                if (translators != "null" && translators.isNotBlank()) {
                    GroupItemDivider()
                    SettingRow(
                        title = stringResource(R.string.settings_translation_contributors),
                        summary = translators,
                        icon = Icons.Outlined.Groups,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    stringResource(R.string.copyright),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
