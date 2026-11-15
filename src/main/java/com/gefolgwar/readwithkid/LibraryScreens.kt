package com.gefolgwar.readwithkid

import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

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

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.library_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
            )
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_title))
            }
        }
        Spacer(Modifier.height(8.dp))
        if (texts.isEmpty()) {
            Text(
                stringResource(R.string.library_empty),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
        Spacer(Modifier.height(8.dp))
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.add_text))
        }
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
    Card(onClick = onRead, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
            ) {
                Text(text.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text.rawText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
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

/** Earlier readings of a text, newest first: date, accuracy and words correct per minute. A reading opens its result. */
@Composable
fun HistoryScreen(
    history: TextHistory,
    onShow: (ReadingRecord) -> Unit,
    onDelete: (ReadingRecord) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
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
        Text(
            stringResource(R.string.history_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )
        Text(history.text.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(8.dp))
        if (history.readings.isEmpty()) {
            Text(
                stringResource(R.string.history_empty),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(history.readings, key = { it.id }) { reading ->
                    Card(onClick = { onShow(reading) }, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            Text(
                                dateFormat.format(Date(reading.readAt)),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                stringResource(
                                    R.string.history_reading,
                                    (reading.result.accuracy * 100).roundToInt(),
                                    reading.result.wordsCorrectPerMinute
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { pendingDelete = reading }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.back))
        }
    }
}

/** One big field for the text; the title is made from its first words. */
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

    BackHandler(onBack = onCancel)

    Column(modifier = modifier) {
        Text(
            stringResource(if (editor.id == 0L) R.string.new_text_title else R.string.edit_text_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.private_use_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = {
            val clip = context.getSystemService(ClipboardManager::class.java)
                ?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
            if (!clip.isNullOrBlank()) {
                onRawTextChange(if (editor.rawText.isBlank()) clip else editor.rawText.trimEnd() + "\n" + clip)
            }
        }) {
            Icon(Icons.Default.ContentPaste, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.paste))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = editor.rawText,
            onValueChange = onRawTextChange,
            placeholder = { Text(stringResource(R.string.input_hint)) },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
        HardPartsHint(editor.warnings)
        Spacer(Modifier.height(8.dp))
        LanguageChoice(editor, onLanguageChange)

        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.cancel))
            }
            Button(onClick = onSave, enabled = editor.canSave, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

/** Language of the text: suggested from its alphabet, the parent can change it. */
@Composable
private fun LanguageChoice(editor: TextEditorState, onChange: (TextLanguage) -> Unit) {
    Text(stringResource(R.string.text_language_label), style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextLanguage.entries.forEach { language ->
            FilterChip(
                selected = language == editor.language,
                onClick = { onChange(language) },
                label = { Text(stringResource(language.nameRes)) }
            )
        }
    }
    if (editor.chosenLanguage == null && editor.detectedLanguage != null) {
        Text(
            stringResource(R.string.text_language_detected),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Digits, abbreviations and symbols are hard to recognise: a short hint under the field. */
@Composable
private fun HardPartsHint(warnings: TextWarnings) {
    if (warnings.isEmpty) return
    Text(
        stringResource(
            R.string.hard_parts_hint,
            (warnings.digits + warnings.abbreviations + warnings.symbols).joinToString(", ")
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 4.dp)
    )
}
