package com.gefolgwar.readwithkid

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private fun models(language: TextLanguage) = SpeechModels.get(applicationContext, language)
    private val db by lazy { AppDatabase.get(applicationContext) }
    private val vm: ReadingSessionViewModel by viewModels {
        ReadingSessionViewModel.factory({ models(it).installed }, { db.readings() }, { TtsPronunciationHelper(applicationContext) }) {
            RealVoskEngine(models(it).directory)
        }
    }
    private val libraryVm: LibraryViewModel by viewModels {
        LibraryViewModel.factory { db.texts() }
    }

    private val settings by lazy { AppSettings(applicationContext) }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withUiLanguage(AppSettings(newBase).uiLanguage))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ReadWithKidTheme {
                ReadWithKidApp(
                    vm = vm,
                    libraryVm = libraryVm,
                    models = ::models,
                    onConfirmModel = { models(it).confirmDownload(this) },
                    uiLanguage = settings.uiLanguage,
                    onUiLanguageChange = {
                        settings.uiLanguage = it
                        // Recreating reloads every screen's strings in the new language.
                        recreate()
                    }
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
    models: (TextLanguage) -> ModelManager,
    onConfirmModel: (TextLanguage) -> Unit,
    uiLanguage: UiLanguage,
    onUiLanguageChange: (UiLanguage) -> Unit
) {
    val context = LocalContext.current
    val state by vm.uiState.collectAsState()
    val library by libraryVm.uiState.collectAsState()
    val modelStates = TextLanguage.entries.associateWith { models(it).state.collectAsState().value }
    // Model of the language of the text being read
    val model = modelStates.getValue(state.language)
    // Title of the saved text chosen for reading, or null while a list is shown
    var readingTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var showModels by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showPrivacy by rememberSaveable { mutableStateOf(false) }
    var showLicenses by rememberSaveable { mutableStateOf(false) }
    // The only place the app leaves itself: the phone settings, to allow the microphone.
    val openAppSettings = {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        )
    }
    // Every reading of a saved text is saved for that text.
    val read = { text: SavedText ->
        vm.updateText(text.rawText, text.language, TextReading(text.id, text.title))
        readingTitle = text.title
    }

    Scaffold { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)
        val result = state.result
        val editor = library.editor
        val title = readingTitle
        when {
            state.sessionState == SessionState.FINISHED && result != null ->
                ResultScreen(result, onClose = vm::closeResult, modifier = modifier)
            state.sessionState == SessionState.READING || state.sessionState == SessionState.PAUSED ->
                ReadingScreen(
                    state,
                    onWordTap = vm::sayWord,
                    onPause = vm::pause,
                    onResume = vm::resume,
                    onFinish = vm::finish,
                    modifier = modifier
                )
            title != null ->
                ReadyToReadScreen(
                    title,
                    state,
                    model,
                    onStart = vm::start,
                    onDownloadModel = { models(state.language).download() },
                    onConfirmModel = { onConfirmModel(state.language) },
                    onOpenAppSettings = openAppSettings,
                    onBack = { readingTitle = null },
                    modifier = modifier
                )
            editor != null ->
                TextEditorScreen(
                    editor,
                    onTitleChange = libraryVm::updateTitle,
                    onLanguageChange = libraryVm::chooseLanguage,
                    onRawTextChange = libraryVm::updateRawText,
                    onImport = libraryVm::importTxt,
                    onSave = libraryVm::save,
                    onCancel = libraryVm::cancelEdit,
                    modifier = modifier
                )
            showModels ->
                ModelsScreen(
                    modelStates,
                    onDownload = { models(it).download() },
                    onConfirm = onConfirmModel,
                    onDelete = { models(it).delete() },
                    onBack = { showModels = false },
                    modifier = modifier
                )
            showPrivacy ->
                PrivacyPolicyScreen(onBack = { showPrivacy = false }, modifier = modifier)
            showLicenses ->
                LicensesScreen(onBack = { showLicenses = false }, modifier = modifier)
            showSettings ->
                SettingsScreen(
                    uiLanguage,
                    onLanguageChange = { if (it != uiLanguage) onUiLanguageChange(it) },
                    onModels = { showModels = true },
                    onPrivacy = { showPrivacy = true },
                    onLicenses = { showLicenses = true },
                    onBack = { showSettings = false },
                    modifier = modifier
                )
            else ->
                LibraryScreen(
                    library.texts,
                    onAdd = libraryVm::newText,
                    onRead = read,
                    onEdit = libraryVm::edit,
                    onDelete = libraryVm::delete,
                    onSettings = { showSettings = true },
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
    onOpenAppSettings: () -> Unit,
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
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))

            when {
                state.sessionState == SessionState.MODEL_MISSING ->
                    ModelDownload(state.language, model, onDownloadModel, onConfirmModel)
                state.sessionState == SessionState.LOADING_MODEL -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.model_loading), style = MaterialTheme.typography.bodyMedium)
                }
                state.sessionState == SessionState.MODEL_ERROR -> ErrorText(stringResource(R.string.model_error))
                state.recognitionError -> ErrorText(stringResource(R.string.recognition_error))
                // Once denied, Android may no longer ask, so the parent is shown how to allow it in the phone settings.
                permissionDenied -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ErrorText(stringResource(R.string.mic_permission_denied))
                    OutlinedButton(onClick = onOpenAppSettings) { Text(stringResource(R.string.mic_open_settings)) }
                }
            }

            // Just the text the child is about to read, without the parent's sentence breakdown.
            Spacer(Modifier.height(8.dp))
            Text(state.rawText, style = MaterialTheme.typography.titleLarge)

            if (state.history.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                ReadingHistory(state.history)
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChildButton(
                Icons.AutoMirrored.Filled.ArrowBack,
                stringResource(R.string.back),
                onClick = onBack,
                primary = false,
                modifier = Modifier.weight(1f)
            )
            ChildButton(
                Icons.Default.PlayArrow,
                stringResource(R.string.start_reading),
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        permissionDenied = false
                        onStart()
                    } else {
                        showRationale = true
                    }
                },
                enabled = state.sessionState == SessionState.READY && state.text.words.isNotEmpty(),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Earlier readings of the text, newest first: date, accuracy and words correct per minute. */
@Composable
private fun ReadingHistory(history: List<ReadingRecord>) {
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    Text(
        stringResource(R.string.history_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() }
    )
    history.forEach { reading ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            Text(dateFormat.format(Date(reading.readAt)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                stringResource(
                    R.string.history_reading,
                    (reading.result.accuracy * 100).roundToInt(),
                    reading.result.wordsCorrectPerMinute
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** A big button for the child's screens: a large icon and a short label; grows with the system font size. */
@Composable
fun ChildButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true
) {
    val content: @Composable RowScope.() -> Unit = {
        Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    }
    val big = modifier.heightIn(min = 72.dp)
    if (primary) Button(onClick, big, enabled, content = content)
    else OutlinedButton(onClick, big, enabled, content = content)
}

@Composable
fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun ReadingScreen(
    state: ReadingUiState,
    onWordTap: (Int) -> Unit,
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
    // Where each word starts in the shown text, to find the line of the current word
    val wordStarts = IntArray(words.size)
    val scroll = rememberScrollState()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    val annotated = buildAnnotatedString {
        words.forEachIndexed { index, word ->
            wordStarts[index] = length
            val style = when {
                index == state.currentWordIndex -> SpanStyle(background = currentColor, fontWeight = FontWeight.Bold)
                else -> when (state.wordStatuses.getOrElse(index) { WordStatus.UNREAD }) {
                    WordStatus.CONFIRMED -> SpanStyle(background = readColor)
                    WordStatus.TENTATIVE -> SpanStyle(background = tentativeColor)
                    WordStatus.SKIPPED -> SpanStyle(background = skippedColor, textDecoration = TextDecoration.LineThrough)
                    WordStatus.UNREAD -> null
                }
            }
            val appendWord = { if (style != null) withStyle(style) { append(word.display) } else append(word.display) }
            // Tapping a word says it, when the phone has a voice for the language
            if (state.helpAvailable) withLink(LinkAnnotation.Clickable("word$index") { onWordTap(index) }) { appendWord() } else appendWord()
            if (index < words.lastIndex) append(" ")
        }
    }

    // Long texts follow the reading: when the current word moves to another line, that line is scrolled into view.
    val currentLine = layout?.takeIf { state.currentWordIndex in wordStarts.indices }
        ?.getLineForOffset(wordStarts[state.currentWordIndex])
    LaunchedEffect(currentLine) {
        val line = currentLine ?: return@LaunchedEffect
        val lineTop = layout?.getLineTop(line) ?: return@LaunchedEffect
        scroll.animateScrollTo(autoScrollTarget(lineTop, scroll.viewportSize))
    }

    Column(modifier = modifier) {
        if (state.helpAvailable) {
            Text(stringResource(R.string.reading_help_hint), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
        }
        // Andika comes from the theme's typography.
        Text(
            text = annotated,
            fontSize = 30.sp,
            lineHeight = 46.sp,
            onTextLayout = { layout = it },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.sessionState == SessionState.PAUSED) {
                ChildButton(
                    Icons.Default.PlayArrow,
                    stringResource(R.string.resume),
                    onClick = onResume,
                    primary = false,
                    modifier = Modifier.weight(1f)
                )
            } else {
                ChildButton(
                    Icons.Default.Pause,
                    stringResource(R.string.pause),
                    onClick = onPause,
                    primary = false,
                    modifier = Modifier.weight(1f)
                )
            }
            ChildButton(Icons.Default.Check, stringResource(R.string.finish), onClick = onFinish, modifier = Modifier.weight(1f))
        }
    }
}

/** Scroll position that shows the line starting at [lineTop] a third of the way down a [viewportHeight] tall text. */
fun autoScrollTarget(lineTop: Float, viewportHeight: Int): Int =
    (lineTop - viewportHeight / 3f).roundToInt().coerceAtLeast(0)

@Composable
fun ResultScreen(result: ReadingResult, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val totalSeconds = result.durationMs / 1000
    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            RewardStars(result.stars)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(
                    when (result.stars) {
                        3 -> R.string.result_praise_3
                        2 -> R.string.result_praise_2
                        else -> R.string.result_praise_1
                    }
                ),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
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
            WordList(stringResource(R.string.result_helped), result.helpedWords)
        }
        Spacer(Modifier.height(8.dp))
        ChildButton(
            Icons.AutoMirrored.Filled.ArrowBack,
            stringResource(R.string.result_back),
            onClick = onClose,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** The reward: earned stars pop in one after another, the rest stay as outlines. */
@Composable
private fun RewardStars(stars: Int) {
    val description = stringResource(R.string.result_stars, stars, ReadingResult.MAX_STARS)
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description }
    ) {
        repeat(ReadingResult.MAX_STARS) { i ->
            val earned = i < stars
            val scale = remember { Animatable(if (earned) 0f else 1f) }
            if (earned) {
                LaunchedEffect(Unit) {
                    delay(300L * i)
                    scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessLow))
                }
            }
            Icon(
                if (earned) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = null,
                tint = if (earned) Color(0xFFFFA000) else MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .size(80.dp)
                    // Scaled while drawing only, so the animation does not recompose the screen every frame.
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                    }
            )
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
