package dev.cremin.gitops.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = AppPrimaryLight,
    onPrimary = AppOnPrimaryLight,
    primaryContainer = AppPrimaryContainerLight,
    onPrimaryContainer = AppOnPrimaryContainerLight,
    secondary = AppSecondaryLight,
    onSecondary = AppOnSecondaryLight,
    secondaryContainer = AppSecondaryContainerLight,
    onSecondaryContainer = AppOnSecondaryContainerLight,
    tertiary = AppTertiaryLight,
    onTertiary = AppOnTertiaryLight,
    tertiaryContainer = AppTertiaryContainerLight,
    onTertiaryContainer = AppOnTertiaryContainerLight,
    background = AppBackgroundLight,
    onBackground = AppOnBackgroundLight,
    surface = AppSurfaceLight,
    onSurface = AppOnSurfaceLight,
    surfaceVariant = AppSurfaceVariantLight,
    onSurfaceVariant = AppOnSurfaceVariantLight,
    outline = AppOutlineLight,
)

private val DarkColors = darkColorScheme(
    primary = AppPrimaryDark,
    onPrimary = AppOnPrimaryDark,
    primaryContainer = AppPrimaryContainerDark,
    onPrimaryContainer = AppOnPrimaryContainerDark,
    secondary = AppSecondaryDark,
    onSecondary = AppOnSecondaryDark,
    secondaryContainer = AppSecondaryContainerDark,
    onSecondaryContainer = AppOnSecondaryContainerDark,
    tertiary = AppTertiaryDark,
    onTertiary = AppOnTertiaryDark,
    tertiaryContainer = AppTertiaryContainerDark,
    onTertiaryContainer = AppOnTertiaryContainerDark,
    background = AppBackgroundDark,
    onBackground = AppOnBackgroundDark,
    surface = AppSurfaceDark,
    onSurface = AppOnSurfaceDark,
    surfaceVariant = AppSurfaceVariantDark,
    onSurfaceVariant = AppOnSurfaceVariantDark,
    outline = AppOutlineDark,
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
