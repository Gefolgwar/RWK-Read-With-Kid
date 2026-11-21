package com.gefolgwar.readwithkid

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** The syllable colours stay readable on the reading background. */
class SyllableColorsTest {

    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    private fun assertReadable(colors: List<Color>, background: Color) = colors.forEach { color ->
        val ratio = contrast(color, background)
        assertTrue("$color on $background: $ratio", ratio >= 4.5)
    }

    @Test
    fun `blue and red have at least 4_5 to 1 contrast with the background`() {
        assertReadable(SyllableColorsOnMagnet, AppColors.background)
        assertReadable(SyllableColorsOnMagnet, AppColors.surfaceContainerLowest)
    }

    @Test
    fun `on the dark page blue and red have at least 4_5 to 1 contrast`() {
        assertReadable(SyllableColorsOnDark, AppDarkColors.surfaceContainerLowest)
        assertReadable(SyllableColorsOnDark, AppDarkColors.primaryContainer)
    }
}
