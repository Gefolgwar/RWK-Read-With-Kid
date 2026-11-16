package com.gefolgwar.readwithkid

import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
    activeChild: Child?,
    onAdd: () -> Unit,
    onRead: (SavedText) -> Unit,
    onEdit: (SavedText) -> Unit,
    onDelete: (SavedText) -> Unit,
    onHistory: (SavedText) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    /** In child mode texts can only be read and their history viewed. */
    childMode: Boolean = false,
    unlockQuestion: UnlockQuestion? = null,
    onLock: () -> Unit = {},
    onUnlock: () -> Unit = {},
    onAnswerUnlock: (String) -> Unit = {},
    onDismissUnlock: () -> Unit = {}
) {
    var pendingDelete by remember { mutableStateOf<SavedText?>(null) }

    unlockQuestion?.let { UnlockDialog(it, onAnswerUnlock, onDismissUnlock) }

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
            // Whose library this is, top right
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 8.dp)
            ) {
                if (activeChild != null) ActiveChild(activeChild, Modifier.weight(1f, fill = false))
                // Open lock: parent mode, one tap locks. Closed lock: child mode, a question unlocks.
                IconButton(onClick = if (childMode) onUnlock else onLock) {
                    Icon(
                        if (childMode) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = stringResource(if (childMode) R.string.lock_child_mode else R.string.lock_parent_mode)
                    )
                }
                if (!childMode) {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                }
            }
            Text(
                stringResource(R.string.library_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .padding(start = 4.dp, top = 8.dp)
                    .semantics { heading() }
            )
            Spacer(Modifier.height(16.dp))
            if (texts.isEmpty()) {
                EmptyLibrary(childMode, Modifier.weight(1f))
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
                            childMode,
                            onRead = { onRead(text) },
                            onEdit = { onEdit(text) },
                            onDelete = { pendingDelete = text },
                            onHistory = { onHistory(text) }
                        )
                    }
                }
            }
        }
        if (!childMode) {
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
}

/** A grown-up's question to leave child mode: the product of two numbers, typed in. */
@Composable
private fun UnlockDialog(question: UnlockQuestion, onAnswer: (String) -> Unit, onDismiss: () -> Unit) {
    var answer by remember(question) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, contentDescription = null) },
        title = { Text(stringResource(R.string.unlock_title), textAlign = TextAlign.Center) },
        text = {
            Column {
                Text(
                    stringResource(R.string.unlock_question, question.a, question.b),
                    style = MaterialTheme.typography.headlineSmall.merge(TabularNumbers),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = answer,
                    onValueChange = { typed -> answer = typed.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.unlock_answer)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (answer.isNotEmpty()) onAnswer(answer) }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAnswer(answer) }, enabled = answer.isNotEmpty()) {
                Text(stringResource(R.string.unlock_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/** The child whose library is open: the name in large letters next to the avatar; a placeholder while not named. */
@Composable
private fun ActiveChild(child: Child, modifier: Modifier = Modifier) {
    val named = child.name.isNotBlank()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.semantics(mergeDescendants = true) {}) {
        Text(
            if (named) child.name else stringResource(R.string.child_name_placeholder),
            style = MaterialTheme.typography.headlineSmall,
            color = if (named) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(Modifier.width(12.dp))
        ChildAvatar(child)
    }
}

/** A round avatar in the child's own colour with the first letter of the name, or a person icon while not named. */
@Composable
fun ChildAvatar(child: Child, size: Dp = 48.dp) {
    // The letter grows with the circle, not with the font size setting, so it always fits.
    val letterSize = with(LocalDensity.current) { (size * 0.45f).toSp() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .background(Magnets.forChild(child.id).face, CircleShape)
            .clearAndSetSemantics { }
    ) {
        if (child.name.isBlank()) {
            Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.6f))
        } else {
            Text(
                magnetLetterOf(child.name),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = letterSize, lineHeight = letterSize),
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

/** A few magnet letters on an empty door, and what to do first; in [childMode] the child asks a grown-up. */
@Composable
private fun EmptyLibrary(childMode: Boolean, modifier: Modifier = Modifier) {
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
            stringResource(if (childMode) R.string.library_empty_child else R.string.library_empty),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TextCard(
    text: SavedText,
    childMode: Boolean,
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
            if (!childMode) {
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
}

/**
 * Earlier readings of a text, newest first: the score (words correct per minute) large, with date, accuracy and duration.
 * A reading opens its result.
 * [onDelete] is null in child mode, where readings cannot be deleted.
 */
@Composable
fun HistoryScreen(
    history: TextHistory,
    onShow: (ReadingRecord) -> Unit,
    onDelete: ((ReadingRecord) -> Unit)?,
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
                    onDelete?.invoke(reading)
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
                    val date = dateFormat.format(Date(reading.readAt))
                    val score = reading.result.wordsCorrectPerMinute
                    val percent = (reading.result.accuracy * 100).roundToInt()
                    val seconds = (reading.result.durationMs / 1000).toInt()
                    val duration = "%d:%02d".format(seconds / 60, seconds % 60)
                    val description = stringResource(R.string.history_reading_description, date, score, percent, duration)
                    Card(onClick = { onShow(reading) }, colors = cardColors(), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)
                        ) {
                            // Read out as one sentence; the delete button stays a separate target.
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f).clearAndSetSemantics { contentDescription = description }
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(date, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        stringResource(R.string.history_reading, percent, duration),
                                        style = MaterialTheme.typography.bodyLarge.merge(TabularNumbers),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 12.dp)) {
                                    Text(
                                        score.toString(),
                                        style = MaterialTheme.typography.headlineMedium.merge(TabularNumbers),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        stringResource(R.string.history_score_unit),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (onDelete != null) {
                                IconButton(onClick = { pendingDelete = reading }) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = stringResource(R.string.delete),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                // The score keeps its distance from the card's edge.
                                Spacer(Modifier.width(12.dp))
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
            Spacer(Modifier.height(16.dp))
            LanguageChoice(editor, onLanguageChange)
        }

        var showHardParts by remember { mutableStateOf(false) }
        if (showHardParts) {
            HardPartsDialog(
                editor.warnings,
                onFix = { showHardParts = false },
                onSaveAnyway = {
                    showHardParts = false
                    onSave()
                }
            )
        }

        Spacer(Modifier.height(if (typing) 8.dp else 16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                Text(stringResource(R.string.cancel), style = MaterialTheme.typography.titleMedium)
            }
            Button(
                onClick = { if (editor.warnings.isEmpty) onSave() else showHardParts = true },
                enabled = editor.canSave,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp)
            ) {
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

/**
 * Shown on save when the text has digits, abbreviations or symbols, which speech recognition handles poorly:
 * what was found in this text, and how to write it as words. The parent can fix the text or save it anyway.
 */
@Composable
private fun HardPartsDialog(warnings: TextWarnings, onFix: () -> Unit, onSaveAnyway: () -> Unit) {
    AlertDialog(
        onDismissRequest = onFix,
        icon = {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = if (LocalDarkDoor.current) Magnets.Orange.face else Magnets.Orange.edge
            )
        },
        title = { Text(stringResource(R.string.hard_parts_title), textAlign = TextAlign.Center) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.hard_parts_intro), style = MaterialTheme.typography.bodyMedium)
                HardPartsKind(warnings.digits, R.string.hard_parts_digits, R.string.hard_parts_digits_tip)
                HardPartsKind(warnings.abbreviations, R.string.hard_parts_abbreviations, R.string.hard_parts_abbreviations_tip)
                HardPartsKind(warnings.symbols, R.string.hard_parts_symbols, R.string.hard_parts_symbols_tip)
            }
        },
        confirmButton = {
            TextButton(onClick = onFix) { Text(stringResource(R.string.hard_parts_fix)) }
        },
        dismissButton = {
            TextButton(onClick = onSaveAnyway) { Text(stringResource(R.string.hard_parts_save_anyway)) }
        }
    )
}

/** One kind of hard part: what was found in the text, then a tip with examples. */
@Composable
private fun HardPartsKind(found: List<String>, foundRes: Int, tipRes: Int) {
    if (found.isEmpty()) return
    Spacer(Modifier.height(16.dp))
    Text(
        stringResource(foundRes, found.joinToString(", ")),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface
    )
    Text(
        stringResource(tipRes),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 4.dp)
    )
}
