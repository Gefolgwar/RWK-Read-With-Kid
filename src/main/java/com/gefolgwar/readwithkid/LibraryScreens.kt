package com.gefolgwar.readwithkid

import android.content.ClipboardManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun LibraryScreen(
    texts: List<SavedText>,
    onAdd: () -> Unit,
    onRead: (SavedText) -> Unit,
    onEdit: (SavedText) -> Unit,
    onDelete: (SavedText) -> Unit,
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
        Text(stringResource(R.string.library_title), style = MaterialTheme.typography.titleLarge)
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
                    TextCard(text, onRead = { onRead(text) }, onEdit = { onEdit(text) }, onDelete = { pendingDelete = text })
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
private fun TextCard(text: SavedText, onRead: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(text.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text.rawText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = onRead) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.read))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                }
            }
        }
    }
}

@Composable
fun TextEditorScreen(
    editor: TextEditorState,
    onTitleChange: (String) -> Unit,
    onRawTextChange: (String) -> Unit,
    onImport: (fileName: String?, content: String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var importFailed by remember { mutableStateOf(false) }

    BackHandler(onBack = onCancel)

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val content = runCatching {
            context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }.toString(Charsets.UTF_8).removePrefix("﻿")
        }.getOrNull()
        importFailed = content == null
        if (content != null) {
            val fileName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            onImport(fileName, content)
        }
    }

    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                stringResource(if (editor.id == 0L) R.string.new_text_title else R.string.edit_text_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.private_use_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = editor.title,
                onValueChange = onTitleChange,
                label = { Text(stringResource(R.string.text_title_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("text/plain")) }) {
                    Icon(Icons.Default.FileOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.import_txt))
                }
            }
            if (importFailed) ErrorText(stringResource(R.string.import_error))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = editor.rawText,
                onValueChange = onRawTextChange,
                placeholder = { Text(stringResource(R.string.input_hint)) },
                minLines = 5,
                modifier = Modifier.fillMaxWidth()
            )

            if (!editor.warnings.isEmpty) {
                Spacer(Modifier.height(8.dp))
                HardPartsWarning(editor.warnings)
            }
            TextPreview(editor.preview)
        }

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

@Composable
private fun HardPartsWarning(warnings: TextWarnings) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.hard_parts_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            listOf(
                R.string.hard_parts_digits to warnings.digits,
                R.string.hard_parts_abbreviations to warnings.abbreviations,
                R.string.hard_parts_symbols to warnings.symbols
            ).filter { it.second.isNotEmpty() }.forEach { (label, parts) ->
                Text(
                    stringResource(label, parts.joinToString(", ")),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/** How the text was split into sentences and words. */
@Composable
fun TextPreview(text: PreparedText) {
    if (text.words.isEmpty()) return
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.input_summary, text.sentences.size, text.words.size),
        style = MaterialTheme.typography.titleSmall
    )
    text.sentences.forEachIndexed { i, sentence ->
        Text(
            "${i + 1}. " + sentence.joinToString(" · ") { it.display },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
