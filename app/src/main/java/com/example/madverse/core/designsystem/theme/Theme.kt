package com.example.madverse.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = CinematicCrimson,
    onPrimary = ContentHighEmphasis,
    primaryContainer = CinematicCrimsonLight,
    onPrimaryContainer = ContentHighEmphasis,
    secondary = CinematicAmber,
    onSecondary = ObsidianBackground,
    secondaryContainer = ObsidianSurfaceVariant,
    onSecondaryContainer = CinematicAmber,
    tertiary = CinematicViolet,
    onTertiary = ContentHighEmphasis,
    background = ObsidianBackground,
    onBackground = ContentHighEmphasis,
    surface = ObsidianSurface,
    onSurface = ContentHighEmphasis,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = ContentMediumEmphasis,
    outline = ObsidianCardBorder,
    error = ErrorRed,
    onError = ContentHighEmphasis
)

private val LightColorScheme = lightColorScheme(
    primary = CinematicCrimson,
    onPrimary = LightSurface,
    primaryContainer = CinematicCrimsonLight,
    onPrimaryContainer = LightSurface,
    secondary = CinematicAmberDark,
    onSecondary = LightSurface,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = LightTextPrimary,
    tertiary = CinematicViolet,
    onTertiary = LightSurface,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightCardBorder,
    error = ErrorRed,
    onError = LightSurface
)

/**
 * MADVerse theme with premier cinematic styling.
 * Defaults to dark theme for maximum cinematic visual impact.
 */
@Composable
fun MADVerseTheme(
    darkTheme: Boolean = true, // Defaulting to dark cinematic theme for wow factor
    dynamicColor: Boolean = false, // Keep tailored brand colors by default
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MADVerseTypography,
        content = content
    )
}
