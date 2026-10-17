package com.example.samimine.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SamiMineColors = lightColorScheme(
    primary = BrandBlue, onPrimary = Color.White,
    primaryContainer = SoftBlue, onPrimaryContainer = BrandShadow,
    background = PageBackground, surface = Color.White,
    onBackground = Ink, onSurface = Ink,
    onSurfaceVariant = MutedInk, secondary = BrandBlue,
    outline = FieldBorder, outlineVariant = Separator,
    error = AlertCircle, errorContainer = AlertBackground, onErrorContainer = Ink,
)

@Composable
fun SamiMineTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SamiMineColors, typography = Typography, content = content)
}
