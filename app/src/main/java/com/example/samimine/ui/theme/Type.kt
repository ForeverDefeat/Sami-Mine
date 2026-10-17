package com.example.samimine.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// SansSerif is an explicit Android adaptation, not an identified original font.
private fun style(size: Int, line: Int, weight: Int) = TextStyle(
    fontFamily = FontFamily.SansSerif, fontSize = size.sp,
    lineHeight = line.sp, fontWeight = FontWeight(weight), letterSpacing = 0.sp,
)
object SamiTextStyles {
    val BrandLarge = style(32, 38, 800)
    val BrandCompact = style(20, 24, 800)
    val InstructionTitle = style(32, 40, 800)
    val Metric = style(20, 26, 700)
    val DataValue = style(18, 24, 700)
    val Button = style(16, 22, 500)
    val SectionLabel = style(12, 18, 800).copy(letterSpacing = 1.5.sp)
}
val Typography = Typography(
    headlineLarge = style(32, 40, 800),
    headlineMedium = style(28, 36, 800),
    headlineSmall = style(24, 30, 800),
    titleLarge = style(20, 26, 700),
    titleMedium = style(16, 22, 700),
    titleSmall = style(14, 20, 700),
    bodyLarge = style(16, 26, 400),
    bodyMedium = style(14, 24, 400),
    bodySmall = style(12, 18, 500),
    labelLarge = SamiTextStyles.SectionLabel,
    labelMedium = style(12, 18, 500),
    labelSmall = style(12, 18, 500),
)
