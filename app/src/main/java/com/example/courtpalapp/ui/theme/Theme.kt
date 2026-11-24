
package com.example.courtpalapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = PrimaryGreen,
    onPrimary = White,
    primaryContainer = LightGreen,
    onPrimaryContainer = DarkGreen,

    secondary = DarkGreen,
    onSecondary = White,

    background = Background,
    onBackground = PrimaryText,

    secondaryContainer = LightBlue,
    onSecondaryContainer = PrimaryText,

    surfaceVariant = LightBlue,
    surfaceContainerLow = LightBlue,
    surfaceContainer = LightBlue,
    surfaceContainerHigh = LightBlue,
    surfaceContainerHighest = LightBlue,

    surface = Surface,
    onSurface = PrimaryText,
    onSurfaceVariant = SecondaryText,

    error = ErrorRed
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryGreen,
    onPrimary = White,
    primaryContainer = DarkGreen,
    onPrimaryContainer = DarkPrimaryText,

    secondary = LightGreen,
    onSecondary = DarkGreen,

    background = DarkBackground,
    onBackground = DarkPrimaryText,

    surface = DarkSurface,
    onSurface = DarkPrimaryText,
    onSurfaceVariant = DarkSecondaryText,

    error = ErrorRed
)

@Composable
fun CourtPalAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
