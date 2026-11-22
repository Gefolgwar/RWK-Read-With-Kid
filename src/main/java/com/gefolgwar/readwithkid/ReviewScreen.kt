package com.gefolgwar.readwithkid

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * After the text, the child reads the misread, skipped and uncertain words again, one at a time: the word large in
 * the middle, a dot for each word, the microphone listening. Tapping the word says it; Next leaves it as it is.
 * Leaving shows the result.
 */
@Composable
fun ReviewScreen(
    review: Review,
    words: List<Word>,
    language: TextLanguage,
    syllableColors: Boolean,
    helpAvailable: Boolean,
    onWordTap: (Int) -> Unit,
    onNext: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onFinish)
    val index = review.wordIndex
    val word = words[index].display.trim { !it.isLetterOrDigit() }
    val palette = SyllableColors
    val shown = buildAnnotatedString {
        if (syllableColors) {
            Syllables.split(word, language).forEachIndexed { i, syllable ->
                withStyle(SpanStyle(color = palette[i % palette.size])) { append(syllable) }
            }
        } else append(word)
    }

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            IconButton(onClick = onFinish) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.review_title), style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(16.dp))
        ReviewDots(review)
        Spacer(Modifier.height(16.dp))
        Card(
            colors = cardColors(),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Andika comes from the theme's typography.
                Text(
                    shown,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 56.sp, lineHeight = 72.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clickable(enabled = helpAvailable) { onWordTap(index) }
                )
                if (!review.paused) {
                    Spacer(Modifier.height(32.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(if (review.wrongAttempts > 0) R.string.review_again else R.string.review_read),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
                if (helpAvailable) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.reading_help_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (review.paused) {
            ChildButton(Icons.Default.PlayArrow, stringResource(R.string.resume), onClick = onResume, modifier = Modifier.fillMaxWidth())
        } else {
            ChildButton(
                Icons.AutoMirrored.Filled.ArrowForward,
                stringResource(R.string.review_next),
                onClick = onNext,
                primary = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** A dot for each word to read again: green once read, orange when left misread, larger for the word being read. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewDots(review: Review) {
    val description = stringResource(R.string.review_progress, review.position + 1, review.words.size)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description }
    ) {
        review.words.indices.forEach { i ->
            val color = when {
                i in review.read -> Magnets.Green.face
                i < review.position -> Magnets.Orange.face
                i == review.position -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            Box(
                Modifier
                    .size(if (i == review.position) 16.dp else 12.dp)
                    .background(color, CircleShape)
            )
        }
    }
}
