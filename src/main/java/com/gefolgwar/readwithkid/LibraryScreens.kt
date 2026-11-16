package com.gefolgwar.readwithkid

import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
    /** Word counts of the [texts], by text id. */
    textStats: Map<Long, TextStats> = emptyMap(),
    unlockQuestion: UnlockQuestion? = null,
    onLock: () -> Unit = {},
    onUnlock: () -> Unit = {},
    onAnswerUnlock: (String) -> Unit = {},
    onDismissUnlock: () -> Unit = {},
    /** All children, listed when [childrenOpen]. */
    children: List<Child> = emptyList(),
    childrenOpen: Boolean = false,
    onOpenChildren: () -> Unit = {},
    onCloseChildren: () -> Unit = {},
    onSwitchChild: (Child) -> Unit = {},
    onAddChild: (String) -> Unit = {},
    onRenameChild: (Child, String) -> Unit = { _, _ -> },
    onDeleteChild: (Child) -> Unit = {}
) {
    var pendingDelete by remember { mutableStateOf<SavedText?>(null) }
    // A new child's name, a new name and deleting a child are asked in place of the list; cancelling goes back to the list.
    var naming by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Child?>(null) }
    var deletingChild by remember { mutableStateOf<Child?>(null) }

    unlockQuestion?.let { UnlockDialog(it, onAnswerUnlock, onDismissUnlock) }

    if (childrenOpen) {
        val toRename = renaming
        val toDelete = deletingChild
        when {
            naming -> ChildNameDialog(
                R.string.add_child,
                R.string.add_child_confirm,
                initialName = "",
                onConfirm = { name ->
                    naming = false
                    onAddChild(name)
                },
                onDismiss = { naming = false }
            )
            toRename != null -> ChildNameDialog(
                R.string.rename_child,
                R.string.save,
                initialName = toRename.name,
                onConfirm = { name ->
                    renaming = null
                    onRenameChild(toRename, name)
                },
                onDismiss = { renaming = null }
            )
            toDelete != null -> DeleteChildDialog(
                toDelete,
                onDelete = {
                    deletingChild = null
                    onDeleteChild(toDelete)
                },
                onDismiss = { deletingChild = null }
            )
            else -> ChildrenDialog(
                children,
                activeChild,
                onSwitchChild,
                onAdd = { naming = true },
                onRename = { renaming = it },
                onDelete = { deletingChild = it },
                onDismiss = onCloseChildren
            )
        }
    }

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
                if (activeChild != null) {
                    // Only a parent opens the list of children.
                    val choose = if (childMode) Modifier else Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClickLabel = stringResource(R.string.choose_child), onClick = onOpenChildren)
                    ActiveChild(activeChild, Modifier.weight(1f, fill = false).then(choose))
                }
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
                            textStats[text.id],
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

/**
 * The children to switch between, the [active] one marked, each to rename or delete, and adding a child at the end.
 * The last child cannot be deleted.
 */
@Composable
private fun ChildrenDialog(
    children: List<Child>,
    active: Child?,
    onSwitch: (Child) -> Unit,
    onAdd: () -> Unit,
    onRename: (Child) -> Unit,
    onDelete: (Child) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.children_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                children.forEach { child ->
                    val isActive = child.id == active?.id
                    val named = child.name.isNotBlank()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .selectable(selected = isActive, role = Role.RadioButton, onClick = { onSwitch(child) })
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        ChildAvatar(child, size = 40.dp)
                        Spacer(Modifier.width(16.dp))
                        Text(
                            if (named) child.name else stringResource(R.string.child_name_placeholder),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (named) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (isActive) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        IconButton(onClick = { onRename(child) }) {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = stringResource(R.string.rename_child),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (children.size > 1) {
                            IconButton(onClick = { onDelete(child) }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = stringResource(R.string.delete_child),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(role = Role.Button, onClick = onAdd)
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                ) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(
                        stringResource(R.string.add_child),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.children_close)) }
        }
    )
}

/** Names a new child or renames one; an empty name is allowed and shows the placeholder until the child is named. */
@Composable
fun ChildNameDialog(titleRes: Int, confirmRes: Int, initialName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.child_name_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConfirm(name) }),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) { Text(stringResource(confirmRes)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/** Asks before deleting [child], naming them, since all their texts and results go with them. */
@Composable
fun DeleteChildDialog(child: Child, onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_child_title)) },
        text = {
            Text(
                if (child.name.isBlank()) stringResource(R.string.delete_child_message_unnamed)
                else stringResource(R.string.delete_child_message, child.name)
            )
        },
        confirmButton = {
            TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
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
    stats: TextStats?,
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
                if (stats != null) {
                    // "N words · today: M", the today part only once something was read today
                    val words = pluralStringResource(R.plurals.text_word_count, stats.wordCount, stats.wordCount)
                    Text(
                        if (stats.wordsReadToday > 0) "$words · ${stringResource(R.string.text_read_today, stats.wordsReadToday)}" else words,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
 * A reading opens its result. From two readings on, a chart above the list shows how the score changed.
 * [onDelete] is null in child mode, where readings cannot be deleted.
 * The info button opens the reading norms to compare the scores with.
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
    var showNorms by remember { mutableStateOf(false) }

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

    if (showNorms) ReadingNormsDialog(onClose = { showNorms = false })

    Column(modifier = modifier) {
        // The norms are open in child mode too: they only explain the scores.
        ScreenHeader(R.string.history_title, onBack) {
            IconButton(onClick = { showNorms = true }) {
                Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.norms_title))
            }
        }
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
                // A single point shows no progress yet.
                if (history.scores.size >= 2) {
                    item(key = "chart") { ProgressChart(history.scores) }
                }
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

/**
 * [scores] of a text's readings as a line, oldest on the left, from zero up to the best score; the latest point is larger.
 * TalkBack reads the first and the latest score.
 */
@Composable
private fun ProgressChart(scores: List<Int>) {
    val description = stringResource(R.string.history_chart_description, scores.first(), scores.last())
    val line = MaterialTheme.colorScheme.primary
    val baseline = MaterialTheme.colorScheme.outlineVariant
    Card(
        colors = cardColors(),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }
    ) {
        Canvas(Modifier.fillMaxWidth().height(96.dp).padding(horizontal = 20.dp, vertical = 16.dp)) {
            val top = scores.max().coerceAtLeast(1)
            val points = scores.mapIndexed { i, score ->
                Offset(size.width * i / (scores.size - 1), size.height * (1 - score.toFloat() / top))
            }
            drawLine(baseline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            points.dropLast(1).forEach { drawCircle(line, radius = 4.dp.toPx(), center = it) }
            drawCircle(line, radius = 6.dp.toPx(), center = points.last())
        }
    }
}

/**
 * How fast and how much children read by school grade, in Ukraine and in Germany, with the sources.
 * The figures come from docs/research/reading-norms.md; the app has no links, so sources are named, not linked.
 */
@Composable
private fun ReadingNormsDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
        title = { Text(stringResource(R.string.norms_title), textAlign = TextAlign.Center) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.norms_intro), style = MaterialTheme.typography.bodyMedium)
                NormsCountry(R.string.norms_ukraine, R.string.norms_ukraine_speed, R.string.norms_ukraine_daily, R.string.norms_ukraine_source)
                NormsCountry(R.string.norms_germany, R.string.norms_germany_speed, R.string.norms_germany_daily, R.string.norms_germany_source)
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.norms_close)) }
        }
    )
}

/** One country: its name as a heading, reading speed by grade, the daily amount and the sources. */
@Composable
private fun NormsCountry(nameRes: Int, speedRes: Int, dailyRes: Int, sourceRes: Int) {
    Spacer(Modifier.height(20.dp))
    Text(
        stringResource(nameRes),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { heading() }
    )
    Text(stringResource(speedRes), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
    Text(stringResource(dailyRes), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
    Text(
        stringResource(sourceRes),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 6.dp)
    )
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
