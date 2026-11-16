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
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private fun models(language: TextLanguage) = SpeechModels.get(applicationContext, language)
    private val db by lazy { AppDatabase.get(applicationContext) }
    private val vm: ReadingSessionViewModel by viewModels {
        ReadingSessionViewModel.factory({ models(it).installed }, { models(it).download() }, { db.readings() }, { TtsPronunciationHelper(applicationContext) }) {
            RealVoskEngine(models(it).directory)
        }
    }
    private val libraryVm: LibraryViewModel by viewModels {
        LibraryViewModel.factory({ db.texts() }, { db.readings() }, { db.children() }, { settings })
    }

    private val settings by lazy { AppSettings(applicationContext) }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withUiLanguage(AppSettings(newBase).uiLanguage))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app gets the keyboard's and system bars' insets itself (needed to know when the keyboard is open),
        // and the status bar icons follow the light or dark theme.
        enableEdgeToEdge()

        setContent {
            var syllableColors by remember { mutableStateOf(settings.syllableColors) }
            var readingTextSize by remember { mutableIntStateOf(settings.readingTextSize) }
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
                    },
                    syllableColors = syllableColors,
                    onSyllableColorsChange = {
                        settings.syllableColors = it
                        syllableColors = it
                    },
                    readingTextSize = readingTextSize,
                    onReadingTextSizeChange = {
                        settings.readingTextSize = it
                        readingTextSize = it
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
    onUiLanguageChange: (UiLanguage) -> Unit,
    syllableColors: Boolean,
    onSyllableColorsChange: (Boolean) -> Unit,
    readingTextSize: Int,
    onReadingTextSizeChange: (Int) -> Unit
) {
    val context = LocalContext.current
    val state by vm.uiState.collectAsState()
    val library by libraryVm.uiState.collectAsState()
    // Model of the language of the text being read
    val model by models(state.language).state.collectAsState()
    // Title of the saved text chosen for reading, or null while a list is shown
    var readingTitle by rememberSaveable { mutableStateOf<String?>(null) }
    // Id of that text, for its magnet colour
    var readingId by rememberSaveable { mutableStateOf(0L) }
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
        readingId = text.id
    }

    Scaffold { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .consumeWindowInsets(padding)
            .imePadding()
            .padding(16.dp)
        val result = state.result
        val editor = library.editor
        val history = library.history
        val title = readingTitle
        when {
            state.sessionState == SessionState.FINISHED && result != null ->
                ResultScreen(result, title, Magnets.forText(readingId), onClose = vm::closeResult, modifier = modifier)
            state.sessionState == SessionState.READING || state.sessionState == SessionState.PAUSED ->
                ReadingScreen(
                    state,
                    title,
                    Magnets.forText(readingId),
                    syllableColors = syllableColors,
                    onWordTap = {
                        vm.sayWord(it)
                        vm.checkWord(it)
                    },
                    onRetryWord = vm::retryWord,
                    onCloseWordCheck = vm::closeWordCheck,
                    onPause = vm::pause,
                    onResume = vm::resume,
                    onFinish = vm::finish,
                    onLeave = vm::leave,
                    modifier = modifier,
                    textSize = readingTextSize,
                    onTextSizeChange = onReadingTextSizeChange
                )
            title != null ->
                ReadyToReadScreen(
                    title,
                    Magnets.forText(readingId),
                    state,
                    model,
                    onStart = vm::start,
                    onRetryModel = vm::downloadModel,
                    onConfirmModel = { onConfirmModel(state.language) },
                    onOpenAppSettings = openAppSettings,
                    onBack = { readingTitle = null },
                    modifier = modifier,
                    textSize = readingTextSize,
                    onTextSizeChange = onReadingTextSizeChange
                )
            editor != null ->
                TextEditorScreen(
                    editor,
                    onLanguageChange = libraryVm::chooseLanguage,
                    onRawTextChange = libraryVm::updateRawText,
                    onSave = libraryVm::save,
                    onCancel = libraryVm::cancelEdit,
                    modifier = modifier
                )
            history?.shown != null -> {
                BackHandler(onBack = libraryVm::closeReading)
                // The same result the child saw at the end of that reading
                ResultScreen(
                    history.shown.result,
                    history.text.title,
                    Magnets.forText(history.text.id),
                    onClose = libraryVm::closeReading,
                    closeLabel = stringResource(R.string.back),
                    modifier = modifier
                )
            }
            history != null ->
                HistoryScreen(
                    history,
                    onShow = libraryVm::showReading,
                    onDelete = if (library.childMode) null else libraryVm::deleteReading,
                    onBack = libraryVm::closeHistory,
                    modifier = modifier
                )
            showPrivacy ->
                PrivacyPolicyScreen(onBack = { showPrivacy = false }, modifier = modifier)
            showLicenses ->
                LicensesScreen(onBack = { showLicenses = false }, modifier = modifier)
            library.settingsOpen ->
                SettingsScreen(
                    uiLanguage,
                    onLanguageChange = { if (it != uiLanguage) onUiLanguageChange(it) },
                    syllableColors = syllableColors,
                    onSyllableColorsChange = onSyllableColorsChange,
                    onPrivacy = { showPrivacy = true },
                    onLicenses = { showLicenses = true },
                    onBack = libraryVm::closeSettings,
                    modifier = modifier
                )
            else ->
                LibraryScreen(
                    library.texts,
                    library.activeChild,
                    onAdd = libraryVm::newText,
                    onRead = read,
                    onEdit = libraryVm::edit,
                    onDelete = libraryVm::delete,
                    onHistory = libraryVm::openHistory,
                    onSettings = libraryVm::openSettings,
                    modifier = modifier,
                    childMode = library.childMode,
                    textStats = library.textStats,
                    unlockQuestion = library.unlockQuestion,
                    onLock = libraryVm::lock,
                    onUnlock = libraryVm::askToUnlock,
                    onAnswerUnlock = libraryVm::answerUnlock,
                    onDismissUnlock = libraryVm::dismissUnlock,
                    children = library.children,
                    childrenOpen = library.childrenOpen,
                    onOpenChildren = libraryVm::openChildren,
                    onCloseChildren = libraryVm::closeChildren,
                    onSwitchChild = libraryVm::switchChild,
                    onAddChild = libraryVm::addChild,
                    onRenameChild = libraryVm::renameChild,
                    onDeleteChild = libraryVm::deleteChild
                )
        }
    }
}


/** Whether the phone's "Remove animations" setting is on: motion then cuts instead of moving. */
@Composable
fun reduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Exponential ease-out: quick to start, soft to land. */
private val EaseOutExpo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

@Composable
fun ReadyToReadScreen(
    title: String,
    magnet: Magnet,
    state: ReadingUiState,
    model: ModelState,
    onStart: () -> Unit,
    onRetryModel: () -> Unit,
    onConfirmModel: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Reading text size in sp; the child can choose it here before starting. */
    textSize: Int = DEFAULT_READING_TEXT_SIZE,
    onTextSizeChange: (Int) -> Unit = {}
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
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Spacer(Modifier.width(8.dp))
            TextHeading(title, magnet, Modifier.weight(1f))
            TextSizeButtons(textSize, onTextSizeChange)
        }
        Spacer(Modifier.height(16.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            when {
                state.sessionState == SessionState.MODEL_MISSING ->
                    ModelDownload(model, onRetryModel, onConfirmModel)
                state.sessionState == SessionState.LOADING_MODEL -> StatusCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.model_loading), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                state.sessionState == SessionState.MODEL_ERROR -> StatusCard { ErrorText(stringResource(R.string.model_error)) }
                state.recognitionError -> StatusCard { ErrorText(stringResource(R.string.recognition_error)) }
                // Once denied, Android may no longer ask, so the parent is shown how to allow it in the phone settings.
                permissionDenied -> StatusCard {
                    ErrorText(stringResource(R.string.mic_permission_denied))
                    OutlinedButton(onClick = onOpenAppSettings) { Text(stringResource(R.string.mic_open_settings)) }
                }
            }

            // Just the text the child is about to read, on its page, at the size it will be read.
            Card(colors = cardColors(), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Text(
                    state.rawText,
                    style = readingTextStyle(textSize),
                    modifier = Modifier.padding(20.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))
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
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * A big button for the child's screens: a large icon and a short label; grows with the system font size.
 * [stacked] puts the icon above the label, for buttons that share a row.
 */
@Composable
fun ChildButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    stacked: Boolean = false
) {
    val content: @Composable RowScope.() -> Unit = {
        if (stacked) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        }
    }
    val big = modifier.heightIn(min = if (stacked) 96.dp else 72.dp)
    val shape = MaterialTheme.shapes.extraLarge
    val padding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
    if (primary) Button(onClick, big, enabled, shape = shape, contentPadding = padding, content = content)
    else FilledTonalButton(onClick, big, enabled, shape = shape, contentPadding = padding, content = content)
}

@Composable
fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
}

/** Where a word sits in the laid-out reading text, and the baseline of its line. */
private data class WordBox(val left: Float, val right: Float, val baseline: Float, val line: Int)

@Composable
fun ReadingScreen(
    state: ReadingUiState,
    title: String?,
    magnet: Magnet,
    syllableColors: Boolean,
    onWordTap: (Int) -> Unit,
    onRetryWord: () -> Unit,
    onCloseWordCheck: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    /** Reading text size in sp; the child steps it through [ReadingTextSizes]. */
    textSize: Int = DEFAULT_READING_TEXT_SIZE,
    onTextSizeChange: (Int) -> Unit = {}
) {
    // Leaving loses the reading, so it is confirmed first.
    var confirmLeave by remember { mutableStateOf(false) }
    BackHandler { confirmLeave = true }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.leave_reading_title)) },
            text = { Text(stringResource(R.string.leave_reading_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    onLeave()
                }) { Text(stringResource(R.string.leave_reading)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.keep_reading)) }
            }
        )
    }

    val words = state.text.words
    // Where each word starts in the shown text, to find the line of the current word
    val wordStarts = IntArray(words.size)
    val scroll = rememberScrollState()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    // Words keep their look while their state changes: the states are drawn behind the text, so lines never reflow.
    val noLinkStyle = TextLinkStyles(style = SpanStyle())

    val fontSize = textSize.sp
    val pagePalette = SyllableColors
    val annotated = buildAnnotatedString {
        words.forEachIndexed { index, word ->
            wordStarts[index] = length
            // The yellow magnet is yellow on the dark page too, so the word on it is always in dark ink (colour only, no reflow).
            val onMagnet = index == state.currentWordIndex
            val palette = if (onMagnet) SyllableColorsOnMagnet else pagePalette
            // Tapping a word says it, when the phone has a voice for the language, and shows what was heard in its place
            withLink(LinkAnnotation.Clickable("word$index", noLinkStyle) { onWordTap(index) }) {
                // With coloured syllables the letters' colour shows the syllables; progress is drawn behind them.
                if (syllableColors) {
                    Syllables.split(word.display, state.language).forEachIndexed { i, syllable ->
                        withStyle(SpanStyle(color = palette[i % palette.size])) { append(syllable) }
                    }
                } else if (onMagnet) {
                    withStyle(SpanStyle(color = InkOnMagnet)) { append(word.display) }
                } else append(word.display)
            }
            if (index < words.lastIndex) append(" ")
        }
    }

    val boxes = remember(layout) {
        val result = layout ?: return@remember emptyList()
        words.indices.map { i ->
            val start = wordStarts[i]
            val end = start + words[i].display.length
            val line = result.getLineForOffset(start)
            val bounds = result.getPathForRange(start, end).getBounds()
            WordBox(bounds.left, bounds.right, result.getLineBaseline(line), line)
        }
    }

    // Long texts follow the reading: when the current word moves to another line, that line is scrolled into view.
    val currentLine = boxes.getOrNull(state.currentWordIndex)?.line
    LaunchedEffect(currentLine) {
        val line = currentLine ?: return@LaunchedEffect
        val lineTop = layout?.getLineTop(line) ?: return@LaunchedEffect
        scroll.animateScrollTo(autoScrollTarget(lineTop, scroll.viewportSize))
    }

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            // Leaving sits alone up here, away from Pause and Done.
            IconButton(onClick = { confirmLeave = true }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            // The text's own magnet, as on the library card
            if (title != null) {
                Spacer(Modifier.width(4.dp))
                MagnetLetter(magnetLetterOf(title), magnet, size = 32.sp)
            }
            if (state.helpAvailable) {
                Spacer(Modifier.width(16.dp))
                Text(
                    stringResource(R.string.reading_help_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            } else Spacer(Modifier.weight(1f))
            TextSizeButtons(textSize, onTextSizeChange)
        }
        Spacer(Modifier.height(12.dp))
        Card(
            colors = cardColors(),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                // Inside the scroll, so the tapped word's popup stays over the word
                Box {
                    // Andika comes from the theme's typography.
                    Text(
                        text = annotated,
                        style = readingTextStyle(textSize),
                        onTextLayout = { layout = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .readingProgress(boxes, state.wordStatuses, state.currentWordIndex, fontSize)
                    )
                    val check = state.wordCheck
                    val wordBounds = check?.takeIf { it.index in wordStarts.indices }?.let {
                        val start = wordStarts[it.index]
                        layout?.getPathForRange(start, start + words[it.index].display.length)?.getBounds()
                    }
                    if (check != null && wordBounds != null) {
                        WordCheckPopup(
                            check,
                            wordBounds,
                            misread = state.wordStatuses.getOrNull(check.index) == WordStatus.SKIPPED,
                            onRetry = onRetryWord,
                            onClose = onCloseWordCheck
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.sessionState == SessionState.PAUSED) {
                ChildButton(
                    Icons.Default.PlayArrow,
                    stringResource(R.string.resume),
                    onClick = onResume,
                    primary = false,
                    stacked = true,
                    modifier = Modifier.weight(1f)
                )
            } else {
                ChildButton(
                    Icons.Default.Pause,
                    stringResource(R.string.pause),
                    onClick = onPause,
                    primary = false,
                    stacked = true,
                    modifier = Modifier.weight(1f)
                )
            }
            ChildButton(
                Icons.Default.Check,
                stringResource(R.string.finish),
                onClick = onFinish,
                stacked = true,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** The reading text at [textSize] sp; the line height keeps the 30/52 proportion, so the band and the magnet still fit. */
@Composable
private fun readingTextStyle(textSize: Int): TextStyle =
    MaterialTheme.typography.bodyLarge.copy(fontSize = textSize.sp, lineHeight = textSize.sp * (52f / 30f))

/** The child makes the text smaller or bigger, one step through [ReadingTextSizes]; the size is kept for the next reading. */
@Composable
private fun TextSizeButtons(textSize: Int, onTextSizeChange: (Int) -> Unit) {
    val smaller = ReadingTextSizes.lastOrNull { it < textSize }
    val bigger = ReadingTextSizes.firstOrNull { it > textSize }
    IconButton(onClick = { smaller?.let(onTextSizeChange) }, enabled = smaller != null) {
        Icon(Icons.Default.TextDecrease, contentDescription = stringResource(R.string.text_smaller))
    }
    IconButton(onClick = { bigger?.let(onTextSizeChange) }, enabled = bigger != null) {
        Icon(Icons.Default.TextIncrease, contentDescription = stringResource(R.string.text_bigger))
    }
}

/** Read words underlined in green, skipped ones in dashed orange, the current line banded and the current word on a yellow magnet. */
@Composable
private fun Modifier.readingProgress(boxes: List<WordBox>, statuses: List<WordStatus>, current: Int, fontSize: TextUnit): Modifier {
    val band = MaterialTheme.colorScheme.primaryContainer
    val reduceMotion = reduceMotion()
    val target = boxes.getOrNull(current)
    // The magnet springs to the next word along the line; on a new line it is simply there, never sweeping across the text.
    val tile = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    var tileLine by remember { mutableIntStateOf(-1) }
    LaunchedEffect(target) {
        val word = target ?: return@LaunchedEffect
        val rect = Rect(word.left, word.baseline, word.right, word.baseline)
        if (reduceMotion || word.line != tileLine || tile.value == Rect.Zero) tile.snapTo(rect)
        else tile.animateTo(rect, spring(dampingRatio = 0.7f, stiffness = 450f))
        tileLine = word.line
    }
    return drawBehind {
        val font = fontSize.toPx()
        if (target != null) {
            // The line being read, like a reading ruler across the page
            drawRoundRect(
                band,
                topLeft = Offset(-12.dp.toPx(), target.baseline - font * 1.05f),
                size = Size(size.width + 24.dp.toPx(), font * 1.5f),
                cornerRadius = CornerRadius(14.dp.toPx())
            )
        }
        boxes.forEachIndexed { i, box ->
            if (i == current) return@forEachIndexed
            val y = box.baseline + font * 0.28f
            when (statuses.getOrElse(i) { WordStatus.UNREAD }) {
                WordStatus.CONFIRMED -> drawProgressLine(Magnets.Green.face, box, y)
                WordStatus.TENTATIVE -> drawProgressLine(Magnets.Green.face.copy(alpha = 0.4f), box, y)
                WordStatus.SKIPPED -> drawProgressLine(Magnets.Orange.face, box, y, dashed = true)
                WordStatus.UNREAD -> Unit
            }
        }
        if (target != null && tile.value != Rect.Zero) drawMagnetTile(tile.value, font)
    }
}

private fun DrawScope.drawProgressLine(color: Color, box: WordBox, y: Float, dashed: Boolean = false) {
    val stroke = 4.dp.toPx()
    drawLine(
        color,
        Offset(box.left + stroke / 2, y),
        Offset(box.right - stroke / 2, y),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
        pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 7.dp.toPx())) else null
    )
}

/** A yellow plastic magnet lifted behind the word: a soft shadow on the page, its darker edge, and a glossy face. */
private fun DrawScope.drawMagnetTile(word: Rect, font: Float) {
    val padX = 7.dp.toPx()
    val top = word.top - font * 0.9f
    val bottom = word.top + font * 0.36f
    val topLeft = Offset(word.left - padX, top)
    val size = Size(word.width + padX * 2, bottom - top)
    val radius = 10.dp.toPx()
    val edge = 4.dp.toPx()
    // The edge casts the soft shadow, so the magnet stands off the page.
    drawIntoCanvas { canvas ->
        val paint = Paint().asFrameworkPaint().apply {
            isAntiAlias = true
            color = Magnets.Yellow.edge.toArgb()
            setShadowLayer(8.dp.toPx(), 0f, 3.dp.toPx(), Color(0x40101828).toArgb())
        }
        canvas.nativeCanvas.drawRoundRect(
            topLeft.x, topLeft.y + edge, topLeft.x + size.width, topLeft.y + edge + size.height, radius, radius, paint
        )
    }
    drawRoundRect(Magnets.Yellow.face, topLeft, size, CornerRadius(radius))
    drawRoundRect(
        Brush.verticalGradient(0f to Color.White.copy(alpha = 0.45f), 1f to Color.Transparent, startY = top, endY = top + size.height * 0.6f),
        topLeft,
        size,
        CornerRadius(radius)
    )
}

/** Above the tapped word: what the speech model heard in its place, and for a misread word the button to read it again. */
@Composable
private fun WordCheckPopup(check: WordCheck, word: Rect, misread: Boolean, onRetry: () -> Unit, onClose: () -> Unit) {
    val position = remember(word) { OverWord(word) }
    Popup(popupPositionProvider = position, onDismissRequest = onClose) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (check.heard.isEmpty()) stringResource(R.string.word_heard_nothing)
                    else stringResource(R.string.word_heard, check.heard),
                    style = MaterialTheme.typography.titleLarge
                )
                when {
                    check.retrying -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.word_retry_listening), style = MaterialTheme.typography.titleLarge)
                    }
                    misread -> ChildButton(Icons.Default.Mic, stringResource(R.string.word_retry), onClick = onRetry)
                }
            }
        }
    }
}

/** Places a popup centred over [word], given in the anchor's coordinates, or under it without room above; inside the window. */
private class OverWord(private val word: Rect) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val x = anchorBounds.left + word.center.x.roundToInt() - popupContentSize.width / 2
        val above = anchorBounds.top + word.top.roundToInt() - popupContentSize.height
        val y = if (above >= 0) above else anchorBounds.top + word.bottom.roundToInt()
        return IntOffset(x.coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width)), y)
    }
}

/** Scroll position that shows the line starting at [lineTop] a third of the way down a [viewportHeight] tall text. */
fun autoScrollTarget(lineTop: Float, viewportHeight: Int): Int =
    (lineTop - viewportHeight / 3f).roundToInt().coerceAtLeast(0)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultScreen(
    result: ReadingResult,
    title: String?,
    magnet: Magnet,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    closeLabel: String = stringResource(R.string.result_back)
) {
    val totalSeconds = (result.durationMs / 1000).toInt()
    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // Which text this result belongs to, with its magnet
            if (title != null) TextHeading(title, magnet, Modifier.padding(start = 8.dp, top = 8.dp), compact = true)
            Spacer(Modifier.height(16.dp))
            RewardStars(result.stars)
            Spacer(Modifier.height(12.dp))
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
            Card(colors = cardColors(), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                val percent = (result.accuracy * 100).roundToInt()
                ResultRow(stringResource(R.string.result_accuracy), percent) { stringResource(R.string.result_percent, it) }
                ResultDivider()
                ResultRow(stringResource(R.string.result_wcpm), result.wordsCorrectPerMinute) { it.toString() }
                ResultDivider()
                ResultRow(stringResource(R.string.result_duration), totalSeconds) { "%d:%02d".format(it / 60, it % 60) }
            }
            Text(
                stringResource(R.string.result_words, result.correctWords, result.totalWords),
                style = MaterialTheme.typography.bodyLarge.merge(TabularNumbers),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 12.dp)
            )
            WordList(stringResource(R.string.result_skipped), result.skippedWords, Magnets.Orange)
            WordList(stringResource(R.string.result_retried), result.retriedWords, Magnets.Yellow)
            WordList(stringResource(R.string.result_helped), result.helpedWords, Magnets.Blue)
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(16.dp))
        ChildButton(
            Icons.AutoMirrored.Filled.ArrowBack,
            closeLabel,
            onClick = onClose,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ResultDivider() =
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))

/** The reward: earned stars pop in one after another as yellow magnets, the rest stay as outlines. */
@Composable
private fun RewardStars(stars: Int) {
    val description = stringResource(R.string.result_stars, stars, ReadingResult.MAX_STARS)
    val reduceMotion = reduceMotion()
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description }
    ) {
        repeat(ReadingResult.MAX_STARS) { i ->
            val earned = i < stars
            val scale = remember { Animatable(if (earned && !reduceMotion) 0f else 1f) }
            if (earned && !reduceMotion) {
                LaunchedEffect(Unit) {
                    delay(300L * i)
                    scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessLow))
                }
            }
            Box(
                // Scaled while drawing only, so the animation does not recompose the screen every frame.
                Modifier.graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
            ) {
                if (earned) {
                    Icon(Icons.Default.Star, null, tint = Magnets.Yellow.edge, modifier = Modifier.size(72.dp).offset(y = 4.dp))
                    Icon(Icons.Default.Star, null, tint = Magnets.Yellow.face, modifier = Modifier.size(72.dp).gloss())
                } else {
                    Icon(Icons.Default.StarBorder, null, tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(72.dp))
                }
            }
        }
    }
}

/** A result number that counts up to [value] in place, read out by TalkBack as its final text. */
@Composable
private fun ResultRow(label: String, value: Int, format: @Composable (Int) -> String) {
    val reduceMotion = reduceMotion()
    val shown = remember(value) { Animatable(if (reduceMotion) value.toFloat() else 0f) }
    LaunchedEffect(value) {
        if (!reduceMotion) {
            delay(400)
            shown.animateTo(value.toFloat(), tween(900, easing = EaseOutExpo))
        }
    }
    val finalText = format(value)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clearAndSetSemantics { contentDescription = "$label: $finalText" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))
        Text(
            format(shown.value.roundToInt()),
            style = MaterialTheme.typography.headlineSmall.merge(TabularNumbers)
        )
    }
}

/** The words of one kind as small tiles in the reading font, tinted like their marks while reading. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordList(title: String, words: List<String>, magnet: Magnet) {
    if (words.isEmpty()) return
    Spacer(Modifier.height(24.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp))
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        words.forEach { word ->
            Surface(color = magnet.container, shape = MaterialTheme.shapes.small) {
                Text(
                    word,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 22.sp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}
