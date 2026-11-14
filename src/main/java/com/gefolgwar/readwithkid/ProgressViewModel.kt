package com.gefolgwar.readwithkid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** A word the child skipped or needed several attempts for, and how many times. */
data class ProblemWord(val word: String, val count: Int)

/** Readings of one text, oldest first. */
data class TextReadings(val textTitle: String, val readings: List<ReadingRecord>)

data class ProgressUiState(
    /** The child whose progress is shown, or null when none is open. */
    val child: ChildProfile? = null,
    /** The child's readings, oldest first: the history and the chart. */
    val readings: List<ReadingRecord> = emptyList(),
    /** Texts the child read more than once, the most recently read first. */
    val repeatedTexts: List<TextReadings> = emptyList(),
    /** The child's most frequent problem words, most frequent first. */
    val problemWords: List<ProblemWord> = emptyList()
)

/** Parent side: a child's reading history and progress. */
class ProgressViewModel(children: ChildRepository, readings: ReadingRepository) : ViewModel() {

    private val selectedId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<ProgressUiState> = combine(
        children.observeChildren(),
        readings.observeReadings(),
        selectedId
    ) { allChildren, allReadings, id ->
        val child = allChildren.find { it.id == id } ?: return@combine ProgressUiState()
        val own = allReadings.filter { it.childId == child.id }
        ProgressUiState(child, own, repeatedTexts(own), problemWords(own))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ProgressUiState())

    fun open(child: ChildProfile) {
        selectedId.value = child.id
    }

    fun close() {
        selectedId.value = null
    }

    private fun repeatedTexts(readings: List<ReadingRecord>): List<TextReadings> =
        readings.groupBy { it.textId }.values
            .filter { it.size > 1 }
            .sortedByDescending { it.last().readAt }
            .map { TextReadings(it.last().textTitle, it) }

    private fun problemWords(readings: List<ReadingRecord>): List<ProblemWord> =
        readings.flatMap { (it.result.skippedWords + it.result.retriedWords).map(String::lowercase) }
            .groupingBy { it }.eachCount()
            .map { (word, count) -> ProblemWord(word, count) }
            .sortedWith(compareByDescending<ProblemWord> { it.count }.thenBy { it.word })
            .take(MAX_PROBLEM_WORDS)

    companion object {
        const val MAX_PROBLEM_WORDS = 10

        fun factory(createChildren: () -> ChildRepository, createReadings: () -> ReadingRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ProgressViewModel(createChildren(), createReadings()) }
            }
    }
}
