package com.trevio.android.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush

private val LightColorScheme = lightColorScheme(
    primary = TrevioPrimary,
    onPrimary = TrevioOnPrimary,
    primaryContainer = TrevioPrimaryLight,
    onPrimaryContainer = TrevioPrimaryDark,
    secondary = TrevioSecondary,
    onSecondary = TrevioOnPrimary,
    secondaryContainer = TrevioSecondaryLight,
    onSecondaryContainer = TrevioSecondaryDark,
    tertiary = TrevioAccent,
    onTertiary = TrevioOnPrimary,
    background = TrevioBackground,
    onBackground = TrevioOnBackground,
    surface = TrevioSurface,
    onSurface = TrevioOnSurface,
    surfaceVariant = TrevioSurfaceVariant,
    onSurfaceVariant = TrevioOnSurfaceVariant,
    error = TrevioError,
    onError = TrevioOnError
)

private val DarkColorScheme = darkColorScheme(
    primary = TrevioPrimaryDarkTheme,
    onPrimary = TrevioOnPrimary,
    primaryContainer = TrevioPrimaryDark,
    onPrimaryContainer = TrevioPrimaryLight,
    secondary = TrevioSecondaryLight,
    onSecondary = TrevioSecondaryDark,
    tertiary = TrevioAccent,
    onTertiary = TrevioOnPrimary,
    background = TrevioBackgroundDark,
    onBackground = TrevioOnBackgroundDark,
    surface = TrevioSurfaceDark,
    onSurface = TrevioOnSurfaceDark,
    surfaceVariant = TrevioSurfaceVariantDark,
    onSurfaceVariant = TrevioOnSurfaceVariantDark,
    error = TrevioError,
    onError = TrevioOnError
)

@Composable
fun TrevioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TrevioTypography,
        shapes = TrevioShapes,
        content = content
    )
}

@Composable
fun TrevioHeaderGradient(): Brush {
    val colorScheme = MaterialTheme.colorScheme
    return Brush.verticalGradient(
        colors = listOf(
            colorScheme.surface,
            colorScheme.surface
        )
    )
}
