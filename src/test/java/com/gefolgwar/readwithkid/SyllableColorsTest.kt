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

    @Test
    fun `blue and red have at least 4_5 to 1 contrast with the background`() {
        SyllableColors.forEach { color ->
            val ratio = contrast(color, AppColors.background)
            assertTrue("$color: $ratio", ratio >= 4.5)
        }
    }
}
