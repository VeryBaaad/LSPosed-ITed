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
 * Copyright (C) 2020 EdXposed Contributors
 * Copyright (C) 2021 LSPosed Contributors
 */
package org.lsposed.lspd.deopt

import android.app.Instrumentation
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.util.DisplayMetrics
import android.util.TypedValue

/**
 * Providing a whitelist of methods which are the callers of the target methods we want to hook.
 * Because the target methods are inlined into the callers, we deoptimize the callers to
 * run in intercept mode to make target methods hookable.
 *
 * Only for methods which are included in pre-compiled framework codes.
 * TODO recompile system apps and priv-apps since their original dex files are available
 */
object InlinedMethodCallers {
    const val KEY_BOOT_IMAGE = "boot_image"
    const val KEY_BOOT_IMAGE_MIUI_RES = "boot_image_miui_res"
    const val KEY_SYSTEM_SERVER = "system_server"

    private val BOOLEAN = Boolean::class.javaPrimitiveType!!
    private val INT = Int::class.javaPrimitiveType!!

    /**
     * format for each row: {className, methodName, methodSig}
     */
    private val BOOT_IMAGE = arrayOf<Array<Any?>>(
        arrayOf("android.app.LoadedApk", "makeApplication", BOOLEAN, Instrumentation::class.java),
        arrayOf(
            "android.app.LoadedApk",
            "makeApplicationInner",
            BOOLEAN,
            Instrumentation::class.java
        ),
        arrayOf(
            "android.app.LoadedApk",
            "makeApplicationInner",
            BOOLEAN,
            Instrumentation::class.java,
            BOOLEAN
        ),
        // callers of Application#attach(Context)
        arrayOf(
            "android.app.Instrumentation",
            "newApplication",
            ClassLoader::class.java,
            String::class.java,
            Context::class.java
        ),
        arrayOf(
            "android.app.Instrumentation",
            "newApplication",
            ClassLoader::class.java,
            Context::class.java
        ),
        arrayOf("android.app.ContextImpl", "getSharedPreferencesPath", String::class.java)
    )

    // TODO deprecate this
    private val BOOT_IMAGE_FOR_MIUI_RES = arrayOf<Array<Any?>>(
        // for MIUI resources hooking
        arrayOf("android.content.res.MiuiResources", "init", String::class.java),
        arrayOf("android.content.res.MiuiResources", "updateMiuiImpl"),
        arrayOf("android.content.res.MiuiResources", "setImpl", "android.content.res.ResourcesImpl"),
        arrayOf(
            "android.content.res.MiuiResources",
            "loadOverlayValue",
            TypedValue::class.java,
            INT
        ),
        arrayOf("android.content.res.MiuiResources", "getThemeString", CharSequence::class.java),
        arrayOf("android.content.res.MiuiResources", "<init>", ClassLoader::class.java),
        arrayOf("android.content.res.MiuiResources", "<init>"),
        arrayOf(
            "android.content.res.MiuiResources",
            "<init>",
            AssetManager::class.java,
            DisplayMetrics::class.java,
            Configuration::class.java
        ),
        arrayOf(
            "android.miui.ResourcesManager",
            "initMiuiResource",
            Resources::class.java,
            String::class.java
        ),
        arrayOf("android.app.LoadedApk", "getResources", Resources::class.java),
        arrayOf("android.content.res.Resources", "getSystem", Resources::class.java),
        arrayOf(
            "android.app.ApplicationPackageManager",
            "getResourcesForApplication",
            ApplicationInfo::class.java
        ),
        arrayOf("android.app.ContextImpl", "setResources", Resources::class.java),
    )

    private val SYSTEM_SERVER = emptyArray<Array<Any?>>()

    private val SYSTEM_UI = emptyArray<Array<Any?>>()

    /**
     * Key should be [KEY_BOOT_IMAGE], [KEY_SYSTEM_SERVER], or a package name
     * of system apps or priv-apps i.e. com.android.systemui
     */
    private val CALLERS = mapOf(
        KEY_BOOT_IMAGE to BOOT_IMAGE,
        KEY_BOOT_IMAGE_MIUI_RES to BOOT_IMAGE_FOR_MIUI_RES,
        KEY_SYSTEM_SERVER to SYSTEM_SERVER,
        "com.android.systemui" to SYSTEM_UI,
    )

    operator fun get(where: String): Array<Array<Any?>>? = CALLERS[where]
}
