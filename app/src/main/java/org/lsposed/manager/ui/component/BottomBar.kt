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

package org.lsposed.manager.ui.component

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.lsposed.manager.R
import org.lsposed.manager.ui.navigation.Routes
import org.lsposed.manager.ui.viewmodel.ManagerViewModel

private data class BottomDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

private val destinations = listOf(
    BottomDestination(Routes.HOME, R.string.overview, Icons.Outlined.Home, Icons.Filled.Home),
    BottomDestination(Routes.MODULES, R.string.Modules, Icons.Outlined.Extension, Icons.Filled.Extension),
    BottomDestination(Routes.REPOSITORY, R.string.module_repo, Icons.Outlined.Storefront, Icons.Filled.Storefront),
    BottomDestination(Routes.LOGS, R.string.Logs, Icons.Outlined.ReceiptLong, Icons.Filled.ReceiptLong),
    BottomDestination(Routes.SETTINGS, R.string.Settings, Icons.Outlined.Settings, Icons.Filled.Settings),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ManagerBottomBar(
    currentRoute: String?,
    viewModel: ManagerViewModel,
    onNavigate: (String) -> Unit,
) {
    val modules by viewModel.modules.collectAsStateWithLifecycle()
    val upgradeCount by viewModel.upgradeCount.collectAsStateWithLifecycle()
    val enabledCount = modules.count { it.enabled }

    ShortNavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        windowInsets = WindowInsets.navigationBars.union(WindowInsets.displayCutout).only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
        ),
    ) {
        destinations.forEach { destination ->
            val selected = currentRoute == destination.route
            val badgeCount = when (destination.route) {
                Routes.MODULES -> enabledCount
                Routes.REPOSITORY -> upgradeCount
                else -> 0
            }
            ShortNavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onNavigate(destination.route) },
                icon = {
                    if (badgeCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge { Text(badgeCount.toString()) }
                            },
                        ) {
                            Icon(
                                if (selected) destination.selectedIcon else destination.icon,
                                contentDescription = stringResource(destination.labelRes),
                            )
                        }
                    } else {
                        Icon(
                            if (selected) destination.selectedIcon else destination.icon,
                            contentDescription = stringResource(destination.labelRes),
                        )
                    }
                },
                label = {
                    Text(
                        stringResource(destination.labelRes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}
