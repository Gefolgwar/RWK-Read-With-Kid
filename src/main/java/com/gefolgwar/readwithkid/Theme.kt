package com.gefolgwar.readwithkid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Andika (SIL OFL): letters shaped the way children learn to write them, e.g. "a" and "I" unlike "l". */
val Andika = FontFamily(
    Font(R.font.andika_regular, FontWeight.Normal),
    Font(R.font.andika_bold, FontWeight.Bold)
)

private fun TextStyle.andika(bold: Boolean = false) =
    copy(fontFamily = Andika, fontWeight = if (bold) FontWeight.Bold else fontWeight)

private val AppTypography = Typography().run {
    Typography(
        displayLarge = displayLarge.andika(bold = true),
        displayMedium = displayMedium.andika(bold = true),
        displaySmall = displaySmall.andika(bold = true),
        headlineLarge = headlineLarge.andika(bold = true),
        headlineMedium = headlineMedium.andika(bold = true),
        headlineSmall = headlineSmall.andika(bold = true),
        titleLarge = titleLarge.andika(bold = true),
        titleMedium = titleMedium.andika(bold = true),
        titleSmall = titleSmall.andika(bold = true),
        bodyLarge = bodyLarge.andika(),
        bodyMedium = bodyMedium.andika(),
        bodySmall = bodySmall.andika(),
        labelLarge = labelLarge.andika(bold = true),
        labelMedium = labelMedium.andika(bold = true),
        labelSmall = labelSmall.andika(bold = true)
    )
}

/** Tabular figures, so numbers that change keep their width. */
val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")

/**
 * The colours of plastic fridge magnets: a glossy [face] and the darker [edge] that shows the plastic's thickness.
 * Each text keeps one magnet colour everywhere it appears.
 */
data class Magnet(val face: Color, val edge: Color, val container: Color)

object Magnets {
    val Red = Magnet(Color(0xFFE0322B), Color(0xFF9E1C17), Color(0xFFFFE3E0))
    val Blue = Magnet(Color(0xFF1D5BD8), Color(0xFF113A8F), Color(0xFFDDE7FF))
    val Green = Magnet(Color(0xFF14975A), Color(0xFF0A6339), Color(0xFFD5F2E2))
    val Orange = Magnet(Color(0xFFF06A0E), Color(0xFFA84604), Color(0xFFFFE6D3))
    val Purple = Magnet(Color(0xFF7A45D6), Color(0xFF4F2794), Color(0xFFEDE3FF))
    val Yellow = Magnet(Color(0xFFFFC20E), Color(0xFFD69A00), Color(0xFFFFF1C4))

    /** Magnets for texts; yellow is kept for the word being read. */
    private val forTexts = listOf(Red, Blue, Green, Orange, Purple)

    /** A text keeps its magnet; texts added one after another get different ones. */
    fun forText(id: Long): Magnet = forTexts[Math.floorMod(id, forTexts.size.toLong()).toInt()]
}

// A cool enamel-white door, white cards, near-black blue ink; magnet blue for actions.
internal val AppColors = lightColorScheme(
    primary = Magnets.Blue.face,
    onPrimary = Color.White,
    primaryContainer = Magnets.Blue.container,
    onPrimaryContainer = Color(0xFF0A2463),
    secondary = Magnets.Green.edge,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3E8F0),
    onSecondaryContainer = Color(0xFF161B26),
    tertiary = Magnets.Green.edge,
    onTertiary = Color.White,
    tertiaryContainer = Magnets.Green.container,
    onTertiaryContainer = Color(0xFF053A20),
    background = Color(0xFFEFF2F7),
    onBackground = Color(0xFF161B26),
    surface = Color(0xFFEFF2F7),
    onSurface = Color(0xFF161B26),
    surfaceVariant = Color(0xFFE3E8F0),
    onSurfaceVariant = Color(0xFF475062),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F9FC),
    surfaceContainer = Color(0xFFE9EDF3),
    surfaceContainerHigh = Color(0xFFE3E8F0),
    surfaceContainerHighest = Color(0xFFDCE2EB),
    outline = Color(0xFF6E7789),
    outlineVariant = Color(0xFFCCD3DE),
    error = Color(0xFFC0261C),
    errorContainer = Color(0xFFFFE3E0),
    onErrorContainer = Color(0xFF5F0D08)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/** Coloured syllables alternate these, starting each word with blue; dark enough to read on the reading highlights too. */
val SyllableColors = listOf(Magnets.Blue.edge, Magnets.Red.edge)

/**
 * A letter as a plastic fridge magnet: the glossy face over its darker edge, casting a soft shadow on the door.
 * Decoration only, so it is hidden from TalkBack.
 */
@Composable
fun MagnetLetter(letter: String, magnet: Magnet, size: TextUnit = 44.sp, modifier: Modifier = Modifier) {
    val thickness = with(LocalDensity.current) { (size.toPx() * 0.07f) }
    val style = TextStyle(fontFamily = Andika, fontWeight = FontWeight.Bold, fontSize = size, lineHeight = size)
    Box(modifier.clearAndSetSemantics { }) {
        Text(
            letter,
            style = style.copy(
                color = magnet.edge,
                shadow = Shadow(Color(0x33101828), Offset(0f, thickness * 1.6f), blurRadius = thickness * 2.5f)
            ),
            modifier = Modifier.offset(y = with(LocalDensity.current) { thickness.toDp() })
        )
        Text(letter, style = style.copy(color = magnet.face), modifier = Modifier.gloss())
    }
}

/** The shine of glossy plastic: a soft light across the top of whatever is drawn, only where it is drawn. */
fun Modifier.gloss(): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(0f to Color.White.copy(alpha = 0.38f), 0.55f to Color.Transparent),
            blendMode = BlendMode.SrcAtop
        )
    }

/** The first letter of a title, for its magnet. */
fun magnetLetterOf(title: String): String =
    title.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "•"

@Composable
fun ReadWithKidTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColors, typography = AppTypography, shapes = AppShapes, content = content)
}
