package com.gefolgwar.readwithkid

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val engine = RealVoskEngine()
        val vm = SttViewModel(engine)
        engine.init(this)

        setContent {
            MaterialTheme {
                SttScreen(vm = vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SttScreen(vm: SttViewModel) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.start()
        else Toast.makeText(context, "Microphone permission required", Toast.LENGTH_SHORT).show()
    }

    val sheetState = rememberBottomSheetScaffoldState()

    BottomSheetScaffold(
        scaffoldState = sheetState,
        sheetPeekHeight = 56.dp,
        sheetContent = {
            BottomSheetContent(state, vm, context)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TopControlBar(state, vm, context, permissionLauncher)
            StatsBar(state)
            TextDisplay(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

@Composable
fun TopControlBar(
    state: SttUiState,
    vm: SttViewModel,
    context: Context,
    permissionLauncher: androidx.activity.result.ActivityResultLauncher<String>
) {
    val isRunning = state.appState == AppState.LISTENING || state.appState == AppState.PAUSED
    val isLoading = state.appState == AppState.LOADING_MODEL

    Column(modifier = Modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RecognitionMode.entries.forEach { mode ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    RadioButton(
                        selected = state.mode == mode,
                        onClick = { vm.selectMode(mode) },
                        enabled = !isRunning && !isLoading
                    )
                    Text(
                        text = if (mode == RecognitionMode.FULL) "Full" else "Grammar",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Text(
            text = if (state.mode == RecognitionMode.FULL) {
                "Full: Recognizes any word from the entire vocabulary."
            } else {
                "Grammar: Vocabulary limited to current and next sentence."
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            var expanded by remember { mutableStateOf(false) }
            val excerpts = TextExcerpts.excerpts

            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    enabled = !isRunning && !isLoading
                ) {
                    Text(
                        excerpts[state.selectedExcerptIndex].title,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    excerpts.forEachIndexed { index, excerpt ->
                        DropdownMenuItem(
                            text = { Text(excerpt.title) },
                            onClick = {
                                vm.selectExcerpt(index)
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            when (state.appState) {
                AppState.IDLE, AppState.STOPPED -> {
                    IconButton(onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                            == PackageManager.PERMISSION_GRANTED
                        ) {
                            vm.start()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Start")
                    }
                }
                AppState.LISTENING -> {
                    IconButton(onClick = { vm.pause() }) {
                        Icon(Icons.Default.Pause, contentDescription = "Pause")
                    }
                    IconButton(onClick = { vm.stop() }) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop")
                    }
                }
                AppState.PAUSED -> {
                    IconButton(onClick = { vm.resume() }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                    }
                    IconButton(onClick = { vm.stop() }) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop")
                    }
                }
                AppState.LOADING_MODEL -> {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
fun StatsBar(state: SttUiState) {
    val avgMs = "%.0f".format(state.stats.avgInterWordMs)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "${state.wordsMatched}/${state.totalWords} words | ${avgMs}ms avg",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun TextDisplay(state: SttUiState, modifier: Modifier = Modifier) {
    val excerpt = TextExcerpts.excerpts[state.selectedExcerptIndex]
    val words = excerpt.words

    val confirmedColor = Color(0xFF4CAF50).copy(alpha = 0.3f)
    val tentativeColor = Color(0xFFFFEB3B).copy(alpha = 0.4f)

    val annotated = buildAnnotatedString {
        words.forEachIndexed { index, word ->
            val status = state.wordStatuses.getOrElse(index) { WordStatus.UNREAD }
            when (status) {
                WordStatus.CONFIRMED -> {
                    withStyle(SpanStyle(
                        background = confirmedColor,
                        textDecoration = TextDecoration.Underline
                    )) {
                        append(word)
                    }
                }
                WordStatus.TENTATIVE -> {
                    withStyle(SpanStyle(background = tentativeColor)) {
                        append(word)
                    }
                }
                WordStatus.UNREAD -> {
                    append(word)
                }
            }
            if (index < words.lastIndex) append(" ")
        }
    }

    Box(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = annotated,
            fontSize = 22.sp,
            lineHeight = 36.sp
        )
    }
}

@Composable
fun BottomSheetContent(state: SttUiState, vm: SttViewModel, context: Context) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Details",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                val report = vm.generateReport()
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("STT Report", report))
                Toast.makeText(context, "Report copied", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Report")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val stats = state.stats
        Text("Partials/sec: ${"%.2f".format(stats.partialsPerSecond)}", style = MaterialTheme.typography.bodySmall)
        Text("Utterance→final: ${"%.1f".format(stats.avgUtteranceToFinalMs)} ms", style = MaterialTheme.typography.bodySmall)
        Text("Callback→highlight: ${"%.0f".format(stats.avgCallbackToHighlightNs)} ns", style = MaterialTheme.typography.bodySmall)
        Text("Inter-word: ${"%.1f".format(stats.avgInterWordMs)} ms", style = MaterialTheme.typography.bodySmall)

        Spacer(modifier = Modifier.height(12.dp))

        Text("Raw Output", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(4.dp))

        val listState = rememberLazyListState()
        LaunchedEffect(state.rawLog.size) {
            if (state.rawLog.isNotEmpty()) {
                listState.animateScrollToItem(state.rawLog.lastIndex)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
        ) {
            items(state.rawLog) { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp
                )
            }
        }
    }
}
