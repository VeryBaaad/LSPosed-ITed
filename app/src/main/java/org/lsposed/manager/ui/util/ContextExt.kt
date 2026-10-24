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

package org.lsposed.manager.ui.util

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import org.lsposed.manager.App
import org.lsposed.manager.util.NavUtil
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

fun Context.findActivity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

fun Context.openUrl(url: String) {
    val activity = findActivity()
    if (activity != null) {
        NavUtil.startURL(activity, url)
    } else {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Throwable) {
            Toast.makeText(this, url, Toast.LENGTH_SHORT).show()
        }
    }
}

fun Context.copyToClipboard(label: String, text: String) {
    val manager = getSystemService(ClipboardManager::class.java)
    manager?.setPrimaryClip(ClipData.newPlainText(label, text))
}

fun formatDate(iso: String?): String? {
    if (iso.isNullOrEmpty()) return null
    return try {
        val instant = Instant.parse(iso)
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(App.getLocale())
            .withZone(ZoneId.systemDefault())
            .format(instant)
    } catch (_: Throwable) {
        null
    }
}

fun formatDateTime(iso: String?): String? {
    if (iso.isNullOrEmpty()) return null
    return try {
        val instant = Instant.parse(iso)
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withLocale(App.getLocale())
            .withZone(ZoneId.systemDefault())
            .format(instant)
    } catch (_: Throwable) {
        null
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return String.format(Locale.ROOT, if (index == 0) "%.0f %s" else "%.1f %s", value, units[index])
}
