package com.gefolgwar.readwithkid

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** Parent side: a child's readings — chart, problem words, repeated texts and the full history. */
@Composable
fun ProgressScreen(state: ProgressUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val child = state.child ?: return
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    val readings = state.readings

    BackHandler(onBack = onBack)

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            AvatarImage(child.avatar, 48.dp)
            Spacer(Modifier.width(12.dp))
            Text(child.name, style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(16.dp))
        if (readings.isEmpty()) {
            Text(stringResource(R.string.progress_empty), style = MaterialTheme.typography.bodyLarge)
            return@Column
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            item { SectionTitle(stringResource(R.string.progress_chart)) }
            item {
                val wcpm = readings.map { it.result.wordsCorrectPerMinute.toFloat() }
                LineChart(
                    stringResource(R.string.result_wcpm),
                    readings.last().result.wordsCorrectPerMinute.toString(),
                    wcpm,
                    maxValue = wcpm.max(),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item {
                LineChart(
                    stringResource(R.string.result_accuracy),
                    stringResource(R.string.result_percent, readings.last().result.percent),
                    readings.map { it.result.accuracy * 100 },
                    maxValue = 100f,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            if (state.problemWords.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.progress_problem_words)) }
                item {
                    Text(
                        state.problemWords.map { stringResource(R.string.progress_problem_word, it.word, it.count) }.joinToString(", "),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            if (state.repeatedTexts.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.progress_repeated)) }
                items(state.repeatedTexts) { text ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text.textTitle, style = MaterialTheme.typography.titleMedium)
                            text.readings.forEach { ReadingLine(dateFormat.format(Date(it.readAt)), it.result) }
                        }
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.progress_history)) }
            items(readings.asReversed(), key = { it.id }) { reading ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(reading.textTitle, style = MaterialTheme.typography.titleMedium)
                        ReadingLine(dateFormat.format(Date(reading.readAt)), reading.result)
                        if (reading.result.helpedWords.isNotEmpty()) {
                            Text(
                                stringResource(R.string.progress_helped, reading.result.helpedWords.joinToString(", ")),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

private val ReadingResult.percent: Int get() = (accuracy * 100).roundToInt()

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ReadingLine(date: String, result: ReadingResult) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(date, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            stringResource(R.string.progress_reading, result.percent, result.wordsCorrectPerMinute),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

/** [values] oldest first, drawn from 0 to [maxValue]; [latest] is shown next to the [title]. */
@Composable
private fun LineChart(title: String, latest: String, values: List<Float>, maxValue: Float, color: Color) {
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(latest, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .padding(vertical = 6.dp)
        ) {
            drawLine(axisColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            val points = values.mapIndexed { i, value ->
                val x = if (values.size == 1) size.width / 2 else size.width * i / values.lastIndex
                val y = size.height * (1 - value / maxValue.coerceAtLeast(1f))
                Offset(x, y)
            }
            val path = Path().apply {
                points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
            }
            drawPath(path, color, style = Stroke(width = 3.dp.toPx()))
            points.forEach { drawCircle(color, radius = 4.dp.toPx(), center = it) }
        }
    }
}
