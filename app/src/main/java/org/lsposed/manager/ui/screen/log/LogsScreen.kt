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

package org.lsposed.manager.ui.screen.log

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WrapText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lsposed.manager.App
import org.lsposed.manager.ConfigManager
import org.lsposed.manager.R
import org.lsposed.manager.data.repository.SettingsRepository
import org.lsposed.manager.receivers.LSPManagerServiceHolder
import org.lsposed.manager.ui.component.ConfirmDialog
import org.lsposed.manager.ui.component.EmptyState
import org.lsposed.manager.ui.component.ManagerTopBar
import org.lsposed.manager.ui.viewmodel.ManagerViewModel
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader

@Composable
fun LogsScreen(viewModel: ManagerViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var tab by remember { mutableIntStateOf(0) }
    var logs by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var showClear by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    val verbose = tab == 1

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    LaunchedEffect(tab, reloadKey) {
        if (!ConfigManager.isBinderAlive()) {
            loading = false
            logs = emptyList()
            return@LaunchedEffect
        }
        loading = true
        logs = withContext(Dispatchers.IO) { readLog(verbose) }
        loading = false
    }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            showMessage(context.getString(R.string.logs_saving))
            val error = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openFileDescriptor(uri, "wt")?.use { fd ->
                        LSPManagerServiceHolder.getService().getLogs(fd)
                    }
                    null
                } catch (e: Throwable) {
                    e
                }
            }
            showMessage(
                if (error == null) context.getString(R.string.logs_saved)
                else context.getString(R.string.logs_save_failed2, error.message ?: error.toString())
            )
        }
    }

    Scaffold(
        topBar = {
            ManagerTopBar(
                title = stringResource(R.string.Logs),
                actions = {
                    IconButton(onClick = { reloadKey++ }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menuSaveToSd)) },
                            leadingIcon = { Icon(Icons.Outlined.Download, null) },
                            onClick = {
                                menu = false
                                val name = "LSPosed_logs.zip"
                                runCatching { saveLauncher.launch(name) }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.logs_wrap)) },
                            leadingIcon = { Icon(Icons.Outlined.WrapText, null) },
                            onClick = {
                                SettingsRepository.setWordWrap(!settings.wordWrap)
                                menu = false
                            },
                            trailingIcon = {
                                if (settings.wordWrap) {
                                    Text("✓")
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.menuClearLog),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error)
                            },
                            onClick = {
                                menu = false
                                showClear = true
                            },
                        )
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
                .fillMaxSize(),
        ) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text(stringResource(R.string.logs_modules)) },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text(stringResource(R.string.logs_verbose)) },
                )
            }

            when {
                loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                logs.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Outlined.WrapText,
                        title = stringResource(R.string.logs_empty),
                        summary = stringResource(R.string.logs_empty_summary),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                else -> {
                    SelectionContainer(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = 12.dp,
                                end = 12.dp,
                                top = 8.dp,
                                bottom = 24.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            items(logs) { line ->
                                val horizontal = remember(line) { ScrollState(0) }
                                Text(
                                    text = line.ifEmpty { " " },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    softWrap = settings.wordWrap,
                                    modifier = if (settings.wordWrap) {
                                        Modifier.fillMaxWidth()
                                    } else {
                                        Modifier.horizontalScroll(horizontal)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClear) {
        ConfirmDialog(
            title = stringResource(R.string.menuClearLog),
            text = stringResource(R.string.logs_clear_confirm),
            confirmText = stringResource(R.string.clear),
            dismissText = stringResource(R.string.cancel),
            destructive = true,
            onConfirm = {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { ConfigManager.clearLogs(verbose) }
                    showMessage(
                        context.getString(
                            if (ok) R.string.logs_cleared else R.string.logs_clear_failed_2
                        )
                    )
                    if (ok) reloadKey++
                }
            },
            onDismiss = { showClear = false },
        )
    }
}

private fun readLog(verbose: Boolean): List<String> {
    return try {
        ConfigManager.getLog(verbose)?.use { pfd ->
            BufferedReader(InputStreamReader(FileInputStream(pfd.fileDescriptor))).use { reader ->
                reader.readLines()
            }
        } ?: emptyList()
    } catch (e: Throwable) {
        listOf(e.stackTraceToString())
    }
}
