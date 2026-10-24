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

package org.lsposed.manager.ui

import android.app.ActivityManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.WindowInsets
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableStateFlow
import org.lsposed.manager.ui.component.ManagerBottomBar
import org.lsposed.manager.ui.navigation.Routes
import org.lsposed.manager.ui.screen.about.AboutScreen
import org.lsposed.manager.ui.screen.home.HomeScreen
import org.lsposed.manager.ui.screen.log.LogsScreen
import org.lsposed.manager.ui.screen.module.ModuleScopeScreen
import org.lsposed.manager.ui.screen.module.ModulesScreen
import org.lsposed.manager.ui.screen.repo.RepoDetailScreen
import org.lsposed.manager.ui.screen.repo.RepositoryScreen
import org.lsposed.manager.ui.screen.settings.SettingsScreen
import org.lsposed.manager.ui.theme.LSPosedTheme
import org.lsposed.manager.App
import org.lsposed.manager.R
import org.lsposed.manager.ui.viewmodel.ManagerViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ManagerViewModel by viewModels()
    private val pendingNavigation = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            pendingNavigation.value = parseIntent(intent)
        }

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            LSPosedTheme(settings = settings) {
                ManagerApp(
                    viewModel = viewModel,
                    pendingNavigation = pendingNavigation,
                    onNavigationConsumed = { pendingNavigation.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNavigation.value = parseIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    override fun onStart() {
        super.onStart()
        if (!App.isParasitic) return
        // When the manager runs inside another process its task is hidden by default.
        getSystemService(ActivityManager::class.java)?.appTasks?.forEach {
            it.setExcludeFromRecents(false)
        }
        val icon = taskIcon()
        setTaskDescription(
            ActivityManager.TaskDescription(
                getString(R.string.app_name),
                icon,
                getColor(R.color.ic_launcher_background),
            )
        )
    }

    private fun taskIcon(): Bitmap? {
        if (taskIconBitmap != null) return taskIconBitmap
        return try {
            val drawable = applicationInfo.loadIcon(packageManager)
            val bitmap = if (drawable is BitmapDrawable) {
                drawable.bitmap
            } else {
                val width = drawable.intrinsicWidth.coerceAtLeast(1)
                val height = drawable.intrinsicHeight.coerceAtLeast(1)
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                    val canvas = Canvas(it)
                    drawable.setBounds(0, 0, width, height)
                    drawable.draw(canvas)
                }
            }
            taskIconBitmap = bitmap
            bitmap
        } catch (_: Throwable) {
            null
        }
    }

    companion object {
        private var taskIconBitmap: Bitmap? = null
    }

    private fun parseIntent(intent: Intent?): String? {
        intent ?: return null
        if (intent.action == Intent.ACTION_APPLICATION_PREFERENCES) return Routes.SETTINGS
        val data: Uri = intent.data ?: return null
        return when (data.toString()) {
            "modules" -> Routes.MODULES
            "logs" -> Routes.LOGS
            "repo" -> Routes.REPOSITORY
            "settings" -> Routes.SETTINGS
            else -> {
                if (data.scheme == "module") {
                    val packageName = data.host ?: return null
                    val userId = data.port.takeIf { it > 0 } ?: 0
                    Routes.scope(packageName, userId)
                } else {
                    null
                }
            }
        }
    }
}

@Composable
private fun ManagerApp(
    viewModel: ManagerViewModel,
    pendingNavigation: MutableStateFlow<String?>,
    onNavigationConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in Routes.topLevel
    val pending by pendingNavigation.collectAsStateWithLifecycle()

    LaunchedEffect(pending) {
        val route = pending ?: return@LaunchedEffect
        if (route in Routes.topLevel) {
            navController.navigateTopLevel(route)
        } else {
            navController.navigate(route) { launchSingleTop = true }
        }
        onNavigationConsumed()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                ManagerBottomBar(
                    currentRoute = currentRoute,
                    viewModel = viewModel,
                    onNavigate = { navController.navigateTopLevel(it) },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = if (showBottomBar) {
                Modifier.padding(bottom = innerPadding.calculateBottomPadding())
            } else {
                Modifier
            },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigate = { route ->
                        if (route in Routes.topLevel) navController.navigateTopLevel(route)
                        else navController.navigate(route)
                    },
                )
            }
            composable(Routes.MODULES) {
                ModulesScreen(
                    viewModel = viewModel,
                    onOpenModule = { packageName, userId ->
                        navController.navigate(Routes.scope(packageName, userId))
                    },
                    onOpenRepo = { packageName -> navController.navigate(Routes.repo(packageName)) },
                )
            }
            composable(Routes.MODULE_SCOPE) { entry ->
                val packageName = entry.arguments?.getString("packageName") ?: return@composable
                val userId = entry.arguments?.getString("userId")?.toIntOrNull() ?: 0
                ModuleScopeScreen(
                    viewModel = viewModel,
                    packageName = packageName,
                    userId = userId,
                    onBack = { navController.popBackStack() },
                    onOpenRepo = { pkg -> navController.navigate(Routes.repo(pkg)) },
                )
            }
            composable(Routes.REPOSITORY) {
                RepositoryScreen(
                    viewModel = viewModel,
                    onOpenModule = { packageName -> navController.navigate(Routes.repo(packageName)) },
                )
            }
            composable(Routes.REPO_DETAIL) { entry ->
                val packageName = entry.arguments?.getString("packageName") ?: return@composable
                RepoDetailScreen(
                    viewModel = viewModel,
                    packageName = packageName,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.LOGS) {
                LogsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    viewModel = viewModel,
                    onAbout = { navController.navigate(Routes.ABOUT) },
                )
            }
            composable(Routes.ABOUT) {
                AboutScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
