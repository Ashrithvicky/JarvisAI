package com.privateai.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.privateai.R

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB39DFF),
    onPrimary = Color(0xFF1B0A4A),
    primaryContainer = Color(0xCC3D2A8C),
    onPrimaryContainer = Color(0xFFEDE7FF),
    secondary = Color(0xFF7FD6FF),
    onSecondary = Color(0xFF00334A),
    secondaryContainer = Color(0x992A3F6B),
    onSecondaryContainer = Color(0xFFD8ECFF),
    tertiary = Color(0xFFFF9AD5),
    onTertiary = Color(0xFF4A0F33),
    tertiaryContainer = Color(0x994A1F5C),
    background = CosmicColors.Void,
    onBackground = Color(0xFFEDEBFF),
    surface = Color(0xFF14102B),
    onSurface = Color(0xFFEDEBFF),
    surfaceVariant = Color(0x992B2457),
    onSurfaceVariant = Color(0xFFD6D2F2),
    outline = Color(0xFF7C74B8),
    errorContainer = Color(0x99601A3A)
)

object CosmicColors {
    val Void = Color(0xFF000000)
    val DeepIndigo = Color(0xFF030814)
    val Violet = Color(0xFF0A1744)
    val Nebula = Color(0xFF1B3A8C)
    val Glow = Color(0xFF3AA8FF)
}

private val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_semibold, FontWeight.Bold)
)

private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, spacing: Double = 0.0) =
    TextStyle(
        fontFamily = Poppins,
        fontSize = size.sp,
        lineHeight = line.sp,
        fontWeight = weight,
        letterSpacing = spacing.sp
    )

private val CosmicTypography = Typography(
    displayLarge = style(52, 62, FontWeight.SemiBold),
    headlineSmall = style(26, 34, FontWeight.SemiBold),
    titleLarge = style(22, 30, FontWeight.SemiBold),
    titleMedium = style(18, 26, FontWeight.Medium, 0.1),
    bodyLarge = style(17, 27, spacing = 0.2),
    bodyMedium = style(16, 25, spacing = 0.2),
    bodySmall = style(13, 19, spacing = 0.2),
    labelLarge = style(15, 21, FontWeight.Medium, 0.1),
    labelMedium = style(13, 18, FontWeight.Medium, 0.3),
    labelSmall = style(11, 15, FontWeight.Medium, 0.4)
)

@Composable
fun PrivateAITheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = CosmicTypography
    ) {
        Box(Modifier.fillMaxSize()) {
            CosmicBackground(Modifier.fillMaxSize())
            content()
        }
    }
}
