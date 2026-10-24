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

package org.lsposed.manager.data.model

import org.lsposed.lspd.models.Application
import java.util.Objects

/**
 * Parcelable scope entry with value semantics, used by the scope editor and backups.
 */
class ApplicationWithEquals : Application {
    constructor() : super()

    constructor(packageName: String, userId: Int) : super() {
        this.packageName = packageName
        this.userId = userId
    }

    constructor(application: Application) : super() {
        packageName = application.packageName
        userId = application.userId
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Application) return false
        return packageName == other.packageName && userId == other.userId
    }

    override fun hashCode(): Int = Objects.hash(packageName, userId)

    override fun toString(): String = "$packageName!$userId"
}
