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

package org.lsposed.manager.ui.navigation

object Routes {
    const val HOME = "home"
    const val MODULES = "modules"
    const val REPOSITORY = "repository"
    const val LOGS = "logs"
    const val SETTINGS = "settings"
    const val ABOUT = "about"

    const val MODULE_SCOPE = "module/{packageName}/{userId}"
    const val REPO_DETAIL = "repository/{packageName}"

    val topLevel = setOf(HOME, MODULES, REPOSITORY, SETTINGS)

    fun scope(packageName: String, userId: Int): String = "module/$packageName/$userId"

    fun repo(packageName: String): String = "repository/$packageName"
}
