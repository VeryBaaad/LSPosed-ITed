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

package org.lsposed.manager.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import org.lsposed.manager.data.repository.SettingsState
import org.lsposed.manager.data.repository.ThemeMode

val LocalDarkTheme = staticCompositionLocalOf { false }

val LSPosedTypography = Typography()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LSPosedTheme(
    settings: SettingsState,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }

    val context = LocalContext.current
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dynamicColor = settings.dynamicColor && dynamicAvailable

    val seed = if (dynamicColor) {
        if (dark) dynamicDarkColorScheme(context).primary else dynamicLightColorScheme(context).primary
    } else {
        settings.keyColor.color
    }

    val scheme = rememberDynamicColorScheme(
        seedColor = seed,
        isDark = dark,
        isAmoled = settings.amoled && dark,
        style = settings.paletteStyle,
        specVersion = settings.colorSpec.effectiveFor(settings.paletteStyle),
    ).let { if (settings.amoled && dark) it.amoledBackground() else it }
    val animatedScheme = scheme.animateAsState()

    val window = (context as? Activity)?.window
    SideEffect {
        if (window != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialExpressiveTheme(
            colorScheme = animatedScheme,
            motionScheme = MotionScheme.expressive(),
            typography = LSPosedTypography,
            content = content,
        )
    }
}

private fun ColorScheme.amoledBackground(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainer = Color.Black,
    surfaceContainerHigh = Color.Black,
    surfaceContainerHighest = Color.Black,
)

fun PaletteStyle.supportsSpec2025(): Boolean = when (this) {
    PaletteStyle.TonalSpot,
    PaletteStyle.Neutral,
    PaletteStyle.Vibrant,
    PaletteStyle.Expressive,
    -> true
    else -> false
}

fun ColorSpec.SpecVersion.effectiveFor(style: PaletteStyle): ColorSpec.SpecVersion =
    if (this == ColorSpec.SpecVersion.SPEC_2025 && !style.supportsSpec2025()) {
        ColorSpec.SpecVersion.SPEC_2021
    } else {
        this
    }

@Composable
fun ColorScheme.animateAsState(): ColorScheme {
    @Composable
    fun animate(color: Color): Color = animateColorAsState(
        targetValue = color,
        animationSpec = spring(),
        label = "theme_color_animation",
    ).value

    return ColorScheme(
        primary = animate(primary),
        onPrimary = animate(onPrimary),
        primaryContainer = animate(primaryContainer),
        onPrimaryContainer = animate(onPrimaryContainer),
        inversePrimary = animate(inversePrimary),
        secondary = animate(secondary),
        onSecondary = animate(onSecondary),
        secondaryContainer = animate(secondaryContainer),
        onSecondaryContainer = animate(onSecondaryContainer),
        tertiary = animate(tertiary),
        onTertiary = animate(onTertiary),
        tertiaryContainer = animate(tertiaryContainer),
        onTertiaryContainer = animate(onTertiaryContainer),
        background = animate(background),
        onBackground = animate(onBackground),
        surface = animate(surface),
        onSurface = animate(onSurface),
        surfaceVariant = animate(surfaceVariant),
        onSurfaceVariant = animate(onSurfaceVariant),
        surfaceTint = animate(surfaceTint),
        inverseSurface = animate(inverseSurface),
        inverseOnSurface = animate(inverseOnSurface),
        error = animate(error),
        onError = animate(onError),
        errorContainer = animate(errorContainer),
        onErrorContainer = animate(onErrorContainer),
        outline = animate(outline),
        outlineVariant = animate(outlineVariant),
        scrim = animate(scrim),
        surfaceBright = animate(surfaceBright),
        surfaceDim = animate(surfaceDim),
        surfaceContainer = animate(surfaceContainer),
        surfaceContainerHigh = animate(surfaceContainerHigh),
        surfaceContainerHighest = animate(surfaceContainerHighest),
        surfaceContainerLow = animate(surfaceContainerLow),
        surfaceContainerLowest = animate(surfaceContainerLowest),
        primaryFixed = animate(primaryFixed),
        primaryFixedDim = animate(primaryFixedDim),
        onPrimaryFixed = animate(onPrimaryFixed),
        onPrimaryFixedVariant = animate(onPrimaryFixedVariant),
        secondaryFixed = animate(secondaryFixed),
        secondaryFixedDim = animate(secondaryFixedDim),
        onSecondaryFixed = animate(onSecondaryFixed),
        onSecondaryFixedVariant = animate(onSecondaryFixedVariant),
        tertiaryFixed = animate(tertiaryFixed),
        tertiaryFixedDim = animate(tertiaryFixedDim),
        onTertiaryFixed = animate(onTertiaryFixed),
        onTertiaryFixedVariant = animate(onTertiaryFixedVariant),
    )
}
