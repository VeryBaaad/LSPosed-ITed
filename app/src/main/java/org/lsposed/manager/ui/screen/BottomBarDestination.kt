package org.lsposed.manager.ui.screen

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import org.lsposed.manager.R

enum class BottomBarDestination(
    val direction: String,
    @StringRes val label: Int,
    val iconSelected: ImageVector,
    val iconNotSelected: ImageVector,
    val rootRequired: Boolean,
) {
    Home(stringResource(R.string.overview), R.string.overview, Icons.Filled.Home, Icons.Outlined.Home, false),
    Module(stringResource(R.string.Modules), R.string.Modules, Icons.Filled.Apps, Icons.Outlined.Apps, true),
    Setting(stringResource(R.string.Settings), R.string.Settings, Icons.Filled.Settings, Icons.Outlined.Settings, true)
}