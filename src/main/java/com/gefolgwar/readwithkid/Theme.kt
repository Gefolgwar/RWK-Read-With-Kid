package com.gefolgwar.readwithkid

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** Andika (SIL OFL): letters shaped the way children learn to write them, e.g. "a" and "I" unlike "l". */
val Andika = FontFamily(
    Font(R.font.andika_regular, FontWeight.Normal),
    Font(R.font.andika_bold, FontWeight.Bold)
)

private fun TextStyle.andika() = copy(fontFamily = Andika)

private val AppTypography = Typography().run {
    Typography(
        displayLarge = displayLarge.andika(),
        displayMedium = displayMedium.andika(),
        displaySmall = displaySmall.andika(),
        headlineLarge = headlineLarge.andika(),
        headlineMedium = headlineMedium.andika(),
        headlineSmall = headlineSmall.andika(),
        titleLarge = titleLarge.andika(),
        titleMedium = titleMedium.andika(),
        titleSmall = titleSmall.andika(),
        bodyLarge = bodyLarge.andika(),
        bodyMedium = bodyMedium.andika(),
        bodySmall = bodySmall.andika(),
        labelLarge = labelLarge.andika(),
        labelMedium = labelMedium.andika(),
        labelSmall = labelSmall.andika()
    )
}

// High contrast: near-black text on a warm light background, a deep blue for actions.
internal val AppColors = lightColorScheme(
    primary = Color(0xFF1546A0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001A40),
    tertiary = Color(0xFF00695C),
    onTertiary = Color.White,
    background = Color(0xFFFFFBF2),
    onBackground = Color(0xFF111111),
    surface = Color(0xFFFFFBF2),
    onSurface = Color(0xFF111111),
    onSurfaceVariant = Color(0xFF3D3D45),
    outline = Color(0xFF5E5E66)
)

/** Coloured syllables alternate these, starting each word with blue; dark enough to read on the reading highlights too. */
val SyllableColors = listOf(Color(0xFF1546A0), Color(0xFFB71C1C))

@Composable
fun ReadWithKidTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColors, typography = AppTypography, content = content)
}
