package com.gefolgwar.readwithkid

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
private fun fileSize(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)

/** Name of the language, e.g. "German". */
val TextLanguage.nameRes: Int
    get() = when (this) {
        TextLanguage.UK -> R.string.language_uk
        TextLanguage.DE -> R.string.language_de
    }

/** A thick progress bar, easy to see at arm's length. */
@Composable
private fun Progress(fraction: Float? = null) {
    val modifier = Modifier
        .fillMaxWidth()
        .height(10.dp)
    if (fraction == null) LinearProgressIndicator(modifier = modifier)
    else LinearProgressIndicator(progress = { fraction }, modifier = modifier)
}

/** A card above the text for the state of the speech model or the microphone. */
@Composable
fun StatusCard(content: @Composable ColumnScope.() -> Unit) {
    Card(colors = cardColors(), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp), content = content)
    }
}

/** Progress of the automatic model download and its errors with a retry. Shows nothing once installed. */
@Composable
fun ModelDownload(model: ModelState, onRetry: () -> Unit, onConfirm: () -> Unit) {
    if (model == ModelState.Checking || model == ModelState.Installed) return
    StatusCard {
        when (model) {
            ModelState.Checking, ModelState.Installed -> Unit
            is ModelState.NotInstalled -> when (model.error) {
                // The download is about to start.
                null -> Progress()
                else -> {
                    ErrorText(
                        stringResource(
                            when (model.error) {
                                ModelError.NO_NETWORK -> R.string.model_error_network
                                ModelError.INSUFFICIENT_STORAGE -> R.string.model_error_storage
                                ModelError.FAILED -> R.string.model_error_failed
                            }
                        )
                    )
                    Button(onClick = onRetry) { Text(stringResource(R.string.model_retry)) }
                }
            }
            is ModelState.Downloading -> {
                if (model.totalBytes > 0) {
                    Text(
                        stringResource(R.string.model_downloading, fileSize(model.downloadedBytes), fileSize(model.totalBytes)),
                        style = MaterialTheme.typography.bodyLarge.merge(TabularNumbers)
                    )
                    Progress(model.downloadedBytes.toFloat() / model.totalBytes)
                } else {
                    Progress()
                }
            }
            ModelState.NeedsConfirmation -> {
                Text(stringResource(R.string.model_needs_confirmation), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onConfirm) { Text(stringResource(R.string.model_confirm)) }
            }
            ModelState.Unpacking -> {
                Text(stringResource(R.string.model_unpacking), style = MaterialTheme.typography.bodyLarge)
                Progress()
            }
        }
    }
}
