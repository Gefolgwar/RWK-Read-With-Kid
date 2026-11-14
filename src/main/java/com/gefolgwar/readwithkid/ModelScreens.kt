package com.gefolgwar.readwithkid

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
private fun fileSize(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)

/** Offers the model download and shows its progress and errors. Shows nothing once installed. */
@Composable
fun ModelDownload(model: ModelState, onDownload: () -> Unit, onConfirm: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (model) {
            ModelState.Checking, is ModelState.Installed -> Unit
            is ModelState.NotInstalled -> {
                Text(
                    if (model.downloadBytes != null) stringResource(R.string.model_offer, fileSize(model.downloadBytes))
                    else stringResource(R.string.model_offer_unknown_size),
                    style = MaterialTheme.typography.bodyMedium
                )
                when (model.error) {
                    ModelError.NO_NETWORK -> ErrorText(stringResource(R.string.model_error_network))
                    ModelError.INSUFFICIENT_STORAGE -> ErrorText(stringResource(R.string.model_error_storage))
                    ModelError.FAILED -> ErrorText(stringResource(R.string.model_error_failed))
                    null -> Unit
                }
                Button(onClick = onDownload) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (model.error == null) R.string.model_download else R.string.model_retry))
                }
            }
            is ModelState.Downloading -> {
                if (model.totalBytes > 0) {
                    LinearProgressIndicator(
                        progress = { model.downloadedBytes.toFloat() / model.totalBytes },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        stringResource(R.string.model_downloading, fileSize(model.downloadedBytes), fileSize(model.totalBytes)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            ModelState.NeedsConfirmation -> {
                Text(stringResource(R.string.model_needs_confirmation), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onConfirm) { Text(stringResource(R.string.model_confirm)) }
            }
            ModelState.Unpacking -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.model_unpacking), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun ModelsScreen(
    model: ModelState,
    onDownload: () -> Unit,
    onConfirm: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmDelete by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.model_delete_title)) },
            text = { Text(stringResource(R.string.model_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Column(modifier = modifier) {
        Text(stringResource(R.string.models_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.model_uk_name), style = MaterialTheme.typography.titleMedium)
                if (model is ModelState.Installed) {
                    Text(
                        stringResource(R.string.model_installed, model.version, fileSize(model.sizeBytes)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.delete))
                    }
                } else {
                    ModelDownload(model, onDownload, onConfirm)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.back))
        }
    }
}
