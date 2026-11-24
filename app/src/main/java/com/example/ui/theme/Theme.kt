package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val TomeDarkColorScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = OnCyan,
    primaryContainer = CyanContainer,
    onPrimaryContainer = CyanAccent,
    secondary = CyanDeep,
    onSecondary = OnCyan,
    secondaryContainer = ElevatedSlate,
    onSecondaryContainer = PrimaryInk,
    tertiary = GoldAmber,
    onTertiary = ObsidianBg,
    tertiaryContainer = GoldContainer,
    onTertiaryContainer = GoldAmber,
    background = ObsidianBg,
    onBackground = PrimaryInk,
    surface = SlateSurface,
    onSurface = PrimaryInk,
    surfaceVariant = ElevatedSlate,
    onSurfaceVariant = MutedInk,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = SlateSurface,
    surfaceContainer = SlateSurface,
    surfaceContainerHigh = ElevatedSlate,
    surfaceContainerHighest = ElevatedSlate,
    outline = BorderSlate,
    outlineVariant = BorderSlate,
    error = DangerRed,
    onError = PrimaryInk,
    errorContainer = DangerContainer,
    onErrorContainer = DangerRed
)

private val TomeShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun TomeScannerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TomeDarkColorScheme,
        typography = TomeTypography,
        shapes = TomeShapes,
        content = content
    )
}
