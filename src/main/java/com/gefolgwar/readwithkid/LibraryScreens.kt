package com.gefolgwar.readwithkid

import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** White cards on the enamel-white door. */
@Composable
fun cardColors() = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)

@Composable
fun LibraryScreen(
    texts: List<SavedText>,
    onAdd: () -> Unit,
    onRead: (SavedText) -> Unit,
    onEdit: (SavedText) -> Unit,
    onDelete: (SavedText) -> Unit,
    onHistory: (SavedText) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingDelete by remember { mutableStateOf<SavedText?>(null) }

    pendingDelete?.let { text ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_text_title)) },
            text = { Text(stringResource(R.string.delete_text_message, text.title)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDelete(text)
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Box(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 8.dp)) {
                Text(
                    stringResource(R.string.library_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() }
                )
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_title))
                }
            }
            Spacer(Modifier.height(16.dp))
            if (texts.isEmpty()) {
                EmptyLibrary(Modifier.weight(1f))
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    // Room under the last card for the button
                    contentPadding = PaddingValues(bottom = 96.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(texts, key = { it.id }) { text ->
                        TextCard(
                            text,
                            onRead = { onRead(text) },
                            onEdit = { onEdit(text) },
                            onDelete = { pendingDelete = text },
                            onHistory = { onHistory(text) }
                        )
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onAdd,
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.add_text), style = MaterialTheme.typography.titleMedium) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}

/** A few magnet letters on an empty door, and what to do first. */
@Composable
private fun EmptyLibrary(modifier: Modifier = Modifier) {
    val letters = stringResource(R.string.empty_magnets)
    val magnets = listOf(Magnets.Red, Magnets.Blue, Magnets.Green)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 96.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            letters.forEachIndexed { i, letter ->
                MagnetLetter(letter.toString(), magnets[i % magnets.size], size = 72.sp)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.library_empty),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TextCard(
    text: SavedText,
    onRead: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onHistory: () -> Unit
) {
    Card(onClick = onRead, colors = cardColors(), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.width(52.dp)) {
                MagnetLetter(magnetLetterOf(text.title), Magnets.forText(text.id))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(text.language.nameRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            IconButton(onClick = onHistory) {
                Icon(Icons.Default.History, contentDescription = stringResource(R.string.history_title))
            }
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_actions))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit)) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

/** Earlier readings of a text, newest first: date, stars, accuracy and words correct per minute. A reading opens its result. */
@Composable
fun HistoryScreen(
    history: TextHistory,
    onShow: (ReadingRecord) -> Unit,
    onDelete: (ReadingRecord) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    var pendingDelete by remember { mutableStateOf<ReadingRecord?>(null) }

    BackHandler(onBack = onBack)

    pendingDelete?.let { reading ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_reading_title)) },
            text = { Text(stringResource(R.string.delete_reading_message, dateFormat.format(Date(reading.readAt)))) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDelete(reading)
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Column(modifier = modifier) {
        ScreenHeader(R.string.history_title, onBack)
        TextHeading(history.text.title, Magnets.forText(history.text.id), Modifier.padding(start = 8.dp), compact = true)
        Spacer(Modifier.height(16.dp))
        if (history.readings.isEmpty()) {
            Text(
                stringResource(R.string.history_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(history.readings, key = { it.id }) { reading ->
                    Card(onClick = { onShow(reading) }, colors = cardColors(), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(dateFormat.format(Date(reading.readAt)), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(
                                        R.string.history_reading,
                                        (reading.result.accuracy * 100).roundToInt(),
                                        reading.result.wordsCorrectPerMinute
                                    ),
                                    style = MaterialTheme.typography.bodyLarge.merge(TabularNumbers),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            SmallStars(reading.result.stars)
                            IconButton(onClick = { pendingDelete = reading }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = stringResource(R.string.delete),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A text's magnet and its title, at the top of the screens about that text; [compact] under another heading. */
@Composable
fun TextHeading(title: String, magnet: Magnet, modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        MagnetLetter(magnetLetterOf(title), magnet, size = if (compact) 28.sp else 40.sp)
        Spacer(Modifier.width(if (compact) 12.dp else 16.dp))
        Text(
            title,
            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() }
        )
    }
}

/** The stars a reading earned, small, for lists. */
@Composable
private fun SmallStars(stars: Int) {
    val description = stringResource(R.string.result_stars, stars, ReadingResult.MAX_STARS)
    Row(Modifier.clearAndSetSemantics { this.contentDescription = description }) {
        repeat(ReadingResult.MAX_STARS) { i ->
            Icon(
                if (i < stars) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = null,
                tint = if (i < stars) Magnets.Yellow.edge else MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * One big field for the text; the title is made from its first words.
 * While the keyboard is open only the field and the buttons stay, so the text being typed stays visible.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextEditorScreen(
    editor: TextEditorState,
    onLanguageChange: (TextLanguage) -> Unit,
    onRawTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val typing = WindowInsets.isImeVisible

    BackHandler(onBack = onCancel)

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 8.dp)) {
            Text(
                stringResource(if (editor.id == 0L) R.string.new_text_title else R.string.edit_text_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
            )
            FilledTonalButton(onClick = {
                val clip = context.getSystemService(ClipboardManager::class.java)
                    ?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                if (!clip.isNullOrBlank()) {
                    onRawTextChange(if (editor.rawText.isBlank()) clip else editor.rawText.trimEnd() + "\n" + clip)
                }
            }) {
                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.paste))
            }
        }
        if (!typing) {
            Text(
                stringResource(R.string.private_use_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
            )
        }
        Spacer(Modifier.height(if (typing) 8.dp else 16.dp))
        OutlinedTextField(
            value = editor.rawText,
            onValueChange = onRawTextChange,
            placeholder = { Text(stringResource(R.string.input_hint)) },
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp, lineHeight = 30.sp),
            shape = MaterialTheme.shapes.large,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .heightIn(min = 120.dp)
        )
        if (!typing) {
            HardPartsHint(editor.warnings)
            Spacer(Modifier.height(16.dp))
            LanguageChoice(editor, onLanguageChange)
        }

        Spacer(Modifier.height(if (typing) 8.dp else 16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                Text(stringResource(R.string.cancel), style = MaterialTheme.typography.titleMedium)
            }
            Button(onClick = onSave, enabled = editor.canSave, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                Text(stringResource(R.string.save), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** Language of the text: suggested from its alphabet, the parent can change it. */
@Composable
private fun LanguageChoice(editor: TextEditorState, onChange: (TextLanguage) -> Unit) {
    Text(stringResource(R.string.text_language_label), style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        TextLanguage.entries.forEachIndexed { i, language ->
            SegmentedButton(
                selected = language == editor.language,
                onClick = { onChange(language) },
                shape = SegmentedButtonDefaults.itemShape(i, TextLanguage.entries.size)
            ) { Text(stringResource(language.nameRes)) }
        }
    }
    if (editor.chosenLanguage == null && editor.detectedLanguage != null) {
        Text(
            stringResource(R.string.text_language_detected),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/** Digits, abbreviations and symbols are hard to recognise: a short hint under the field. */
@Composable
private fun HardPartsHint(warnings: TextWarnings) {
    if (warnings.isEmpty) return
    Surface(
        color = Magnets.Orange.container,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = if (LocalDarkDoor.current) Magnets.Orange.face else Magnets.Orange.edge
            )
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(
                    R.string.hard_parts_hint,
                    (warnings.digits + warnings.abbreviations + warnings.symbols).joinToString(", ")
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
