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
 * Copyright (C) 2021 - 2022 LSPosed Contributors
 */

import com.android.build.api.dsl.ApplicationDefaultConfig
import com.android.build.api.dsl.CommonExtension
import com.android.build.gradle.api.AndroidBasePlugin

plugins {
    alias(libs.plugins.lsplugin.cmaker)
    alias(libs.plugins.lsplugin.jgit)
    alias(libs.plugins.agp.lib) apply false
    alias(libs.plugins.agp.app) apply false
    alias(libs.plugins.nav.safeargs) apply false
}

cmaker {
    default {
        arguments.addAll(
            arrayOf(
                "-DEXTERNAL_ROOT=${File(rootDir.absolutePath, "external")}"
            )
        )
        val flags = arrayOf(
            "-DINJECTED_AID=${extra["injectedPackageUid"]}",
            "-Wno-gnu-string-literal-operator-template",
            "-Wno-c++2b-extensions",
        )
        cFlags.addAll(flags)
        cppFlags.addAll(flags)
        abiFilters("arm64-v8a", "armeabi-v7a", "x86", "x86_64", "riscv64")
    }
    buildTypes {
        if (it.name == "release" || it.name == "releaseLog") {
            arguments += "-DDEBUG_SYMBOLS_PATH=${
                layout.buildDirectory.dir("symbols").get().asFile.absolutePath
            }"
        }
    }
}

val repo = jgit.repo()
val commitCount = (repo?.commitCount("HEAD") ?: 1) + 4200
val latestTag = repo?.latestTag?.removePrefix("v")?.substringBefore("-") ?: "2.0.0-it"

extra["injectedPackageName"] = "com.android.shell"
extra["injectedPackageUid"] = 2000

extra["defaultManagerPackageName"] = "org.lsposed.manager"
extra["verCode"] = commitCount
extra["verName"] = latestTag
extra["androidTargetSdkVersion"] = 37
extra["androidMinSdkVersion"] = 27
extra["androidBuildToolsVersion"] = "37.0.0"
extra["androidCompileSdkVersion"] = 37
extra["androidCompileSdkMinorVersion"] = 1
extra["androidCompileNdkVersion"] = libs.versions.ndk.get()
extra["androidSourceCompatibility"] = JavaVersion.VERSION_21
extra["androidTargetCompatibility"] = JavaVersion.VERSION_21
extra["androidCmakeVersion"] = "3.28.0+"

tasks.register("Delete", Delete::class) {
    description = "Delete build directory"
    delete(rootProject.layout.buildDirectory)
}

subprojects {
    plugins.withType(AndroidBasePlugin::class.java) {
        extensions.configure(CommonExtension::class.java) {
            compileSdk {
                version = release(rootProject.extra["androidCompileSdkVersion"] as Int) {
                    minorApiLevel = rootProject.extra["androidCompileSdkMinorVersion"] as Int
                }
            }
            ndkVersion = rootProject.extra["androidCompileNdkVersion"] as String
            buildToolsVersion = rootProject.extra["androidBuildToolsVersion"] as String

            externalNativeBuild.cmake.version = rootProject.extra["androidCmakeVersion"] as String

            defaultConfig.minSdk = rootProject.extra["androidMinSdkVersion"] as Int
            val applicationDefaultConfig = defaultConfig as? ApplicationDefaultConfig
            if (applicationDefaultConfig != null) {
                applicationDefaultConfig.targetSdk = rootProject.extra["androidTargetSdkVersion"] as Int
                applicationDefaultConfig.minSdk = rootProject.extra["androidMinSdkVersion"] as Int
                applicationDefaultConfig.versionCode = rootProject.extra["verCode"] as Int
                applicationDefaultConfig.versionName = rootProject.extra["verName"] as String
            }

            lint.abortOnError = true
            lint.checkReleaseBuilds = false

            compileOptions.sourceCompatibility = rootProject.extra["androidSourceCompatibility"] as JavaVersion
            compileOptions.targetCompatibility = rootProject.extra["androidTargetCompatibility"] as JavaVersion
        }
    }
    plugins.withType(JavaPlugin::class.java) {
        extensions.configure(JavaPluginExtension::class.java) {
            sourceCompatibility = rootProject.extra["androidSourceCompatibility"] as JavaVersion
            targetCompatibility = rootProject.extra["androidTargetCompatibility"] as JavaVersion
        }
    }
}
