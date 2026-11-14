package com.gefolgwar.readwithkid

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val ukModel by lazy { UkrainianModel.get(applicationContext) }
    private val vm: ReadingSessionViewModel by viewModels {
        ReadingSessionViewModel.factory(ukModel.installed) { RealVoskEngine(ukModel.directory) }
    }
    private val db by lazy { AppDatabase.get(applicationContext) }
    private val libraryVm: LibraryViewModel by viewModels {
        LibraryViewModel.factory({ db.texts() }, { db.children() })
    }
    private val profilesVm: ProfilesViewModel by viewModels {
        ProfilesViewModel.factory { db.children() }
    }
    private val childHomeVm: ChildHomeViewModel by viewModels {
        ChildHomeViewModel.factory({ db.texts() }, { db.children() })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                ReadWithKidApp(
                    vm = vm,
                    libraryVm = libraryVm,
                    profilesVm = profilesVm,
                    childHomeVm = childHomeVm,
                    models = ukModel,
                    onConfirmModel = { ukModel.confirmDownload(this) }
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // The microphone must never stay on in the background; rotation keeps reading going.
        if (!isChangingConfigurations) vm.pause()
    }
}

@Composable
fun ReadWithKidApp(
    vm: ReadingSessionViewModel,
    libraryVm: LibraryViewModel,
    profilesVm: ProfilesViewModel,
    childHomeVm: ChildHomeViewModel,
    models: ModelManager,
    onConfirmModel: () -> Unit
) {
    val state by vm.uiState.collectAsState()
    val library by libraryVm.uiState.collectAsState()
    val profiles by profilesVm.uiState.collectAsState()
    val childHome by childHomeVm.uiState.collectAsState()
    val model by models.state.collectAsState()
    // Title of the saved text chosen for reading, or null while a list is shown
    var readingTitle by rememberSaveable { mutableStateOf<String?>(null) }
    // The parent area (library, profiles, models); the child's start screen otherwise
    var parentArea by rememberSaveable { mutableStateOf(false) }
    var showModels by rememberSaveable { mutableStateOf(false) }
    var showProfiles by rememberSaveable { mutableStateOf(false) }
    val read = { text: SavedText ->
        vm.updateText(text.rawText)
        readingTitle = text.title
    }

    Scaffold { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)
        val result = state.result
        val editor = library.editor
        val profileEditor = profiles.editor
        val title = readingTitle
        when {
            state.sessionState == SessionState.FINISHED && result != null ->
                ResultScreen(result, onClose = vm::closeResult, modifier = modifier)
            state.sessionState == SessionState.READING || state.sessionState == SessionState.PAUSED ->
                ReadingScreen(state, onPause = vm::pause, onResume = vm::resume, onFinish = vm::finish, modifier = modifier)
            title != null ->
                ReadyToReadScreen(
                    title,
                    state,
                    model,
                    onStart = vm::start,
                    onDownloadModel = models::download,
                    onConfirmModel = onConfirmModel,
                    onBack = { readingTitle = null },
                    modifier = modifier
                )
            editor != null ->
                TextEditorScreen(
                    editor,
                    library.children,
                    onTitleChange = libraryVm::updateTitle,
                    onToggleChild = libraryVm::toggleChild,
                    onRawTextChange = libraryVm::updateRawText,
                    onImport = libraryVm::importTxt,
                    onSave = libraryVm::save,
                    onCancel = libraryVm::cancelEdit,
                    modifier = modifier
                )
            profileEditor != null ->
                ProfileEditorScreen(
                    profileEditor,
                    onNameChange = profilesVm::updateName,
                    onAvatarChange = profilesVm::updateAvatar,
                    onSave = profilesVm::save,
                    onCancel = profilesVm::cancelEdit,
                    modifier = modifier
                )
            showModels ->
                ModelsScreen(
                    model,
                    onDownload = models::download,
                    onConfirm = onConfirmModel,
                    onDelete = models::delete,
                    onBack = { showModels = false },
                    modifier = modifier
                )
            showProfiles ->
                ProfilesScreen(
                    profiles.children,
                    onAdd = profilesVm::newProfile,
                    onEdit = profilesVm::edit,
                    onDelete = profilesVm::delete,
                    onBack = { showProfiles = false },
                    modifier = modifier
                )
            parentArea ->
                LibraryScreen(
                    library.texts,
                    library.assignedTo,
                    onAdd = libraryVm::newText,
                    onRead = read,
                    onEdit = libraryVm::edit,
                    onDelete = libraryVm::delete,
                    onModels = { showModels = true },
                    onProfiles = { showProfiles = true },
                    onBack = { parentArea = false },
                    modifier = modifier
                )
            else ->
                ChildHomeScreen(
                    childHome,
                    onSelect = childHomeVm::select,
                    onBack = childHomeVm::back,
                    onRead = read,
                    onParents = { parentArea = true },
                    modifier = modifier
                )
        }
    }
}

@Composable
fun ReadyToReadScreen(
    title: String,
    state: ReadingUiState,
    model: ModelState,
    onStart: () -> Unit,
    onDownloadModel: () -> Unit,
    onConfirmModel: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showRationale by remember { mutableStateOf(false) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionDenied = !granted
        if (granted) onStart()
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text(stringResource(R.string.mic_permission_title)) },
            text = { Text(stringResource(R.string.mic_permission_rationale)) },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text(stringResource(R.string.mic_permission_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))

            when {
                state.sessionState == SessionState.MODEL_MISSING -> ModelDownload(model, onDownloadModel, onConfirmModel)
                state.sessionState == SessionState.LOADING_MODEL -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.model_loading), style = MaterialTheme.typography.bodyMedium)
                }
                state.sessionState == SessionState.MODEL_ERROR -> ErrorText(stringResource(R.string.model_error))
                state.recognitionError -> ErrorText(stringResource(R.string.recognition_error))
                permissionDenied -> ErrorText(stringResource(R.string.mic_permission_denied))
            }

            TextPreview(state.text)
        }

        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.back))
            }
            Button(
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        onStart()
                    } else {
                        showRationale = true
                    }
                },
                enabled = state.sessionState == SessionState.READY && state.text.words.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.start_reading))
            }
        }
    }
}

@Composable
fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun ReadingScreen(
    state: ReadingUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val readColor = Color(0xFF4CAF50).copy(alpha = 0.3f)
    val tentativeColor = Color(0xFF4CAF50).copy(alpha = 0.15f)
    val currentColor = Color(0xFFFFEB3B).copy(alpha = 0.6f)
    val skippedColor = Color(0xFFFF9800).copy(alpha = 0.35f)
    val words = state.text.words

    val annotated = buildAnnotatedString {
        words.forEachIndexed { index, word ->
            val style = when {
                index == state.currentWordIndex -> SpanStyle(background = currentColor, fontWeight = FontWeight.Bold)
                else -> when (state.wordStatuses.getOrElse(index) { WordStatus.UNREAD }) {
                    WordStatus.CONFIRMED -> SpanStyle(background = readColor)
                    WordStatus.TENTATIVE -> SpanStyle(background = tentativeColor)
                    WordStatus.SKIPPED -> SpanStyle(background = skippedColor, textDecoration = TextDecoration.LineThrough)
                    WordStatus.UNREAD -> null
                }
            }
            if (style != null) withStyle(style) { append(word.display) } else append(word.display)
            if (index < words.lastIndex) append(" ")
        }
    }

    Column(modifier = modifier) {
        Text(
            text = annotated,
            fontSize = 30.sp,
            lineHeight = 46.sp,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.sessionState == SessionState.PAUSED) {
                OutlinedButton(onClick = onResume, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.resume))
                }
            } else {
                OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Pause, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.pause))
                }
            }
            Button(onClick = onFinish, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.finish))
            }
        }
    }
}

@Composable
fun ResultScreen(result: ReadingResult, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val totalSeconds = result.durationMs / 1000
    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(stringResource(R.string.result_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            ResultRow(
                stringResource(R.string.result_accuracy),
                stringResource(R.string.result_percent, (result.accuracy * 100).roundToInt())
            )
            ResultRow(stringResource(R.string.result_wcpm), result.wordsCorrectPerMinute.toString())
            ResultRow(stringResource(R.string.result_duration), "%d:%02d".format(totalSeconds / 60, totalSeconds % 60))
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.result_words, result.correctWords, result.totalWords),
                style = MaterialTheme.typography.bodyMedium
            )
            WordList(stringResource(R.string.result_skipped), result.skippedWords)
            WordList(stringResource(R.string.result_retried), result.retriedWords)
        }
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.result_back))
        }
    }
}

@Composable
private fun WordList(title: String, words: List<String>) {
    if (words.isEmpty()) return
    Spacer(Modifier.height(16.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
    Text(words.joinToString(", "), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}
