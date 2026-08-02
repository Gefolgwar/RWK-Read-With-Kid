package com.gefolgwar.sttperformance

class PerformanceTracker {

    data class StatsSnapshot(
        val partialsPerSecond: Double = 0.0,
        val avgUtteranceToFinalMs: Double = 0.0,
        val avgCallbackToHighlightNs: Double = 0.0,
        val avgInterWordMs: Double = 0.0
    )

    data class WordTiming(
        val wordIndex: Int,
        val timestampNs: Long
    )

    private val partialTimestamps = mutableListOf<Long>()
    private val utteranceToFinalTimes = mutableListOf<Long>()
    private var lastFinalStartMs: Long? = null
    private val callbackToHighlightTimes = mutableListOf<Long>()
    private var lastCallbackEntryNs: Long? = null
    private val wordTimings = mutableListOf<WordTiming>()

    fun recordPartial(timestampMs: Long) {
        partialTimestamps.add(timestampMs)
    }

    fun recordFinalStart(timestampMs: Long) {
        lastFinalStartMs = timestampMs
    }

    fun recordFinalEnd(timestampMs: Long) {
        lastFinalStartMs?.let { start ->
            utteranceToFinalTimes.add(timestampMs - start)
        }
        lastFinalStartMs = null
    }

    fun recordCallbackEntry(timestampNs: Long) {
        lastCallbackEntryNs = timestampNs
    }

    fun recordHighlightEmit(timestampNs: Long) {
        lastCallbackEntryNs?.let { entry ->
            callbackToHighlightTimes.add(timestampNs - entry)
        }
        lastCallbackEntryNs = null
    }

    fun recordHighlightUpdate(wordIndex: Int, timestampNs: Long) {
        wordTimings.add(WordTiming(wordIndex, timestampNs))
    }

    fun reset() {
        partialTimestamps.clear()
        utteranceToFinalTimes.clear()
        lastFinalStartMs = null
        callbackToHighlightTimes.clear()
        lastCallbackEntryNs = null
        wordTimings.clear()
    }

    fun getStats(): StatsSnapshot {
        val partialsPerSec = if (partialTimestamps.size >= 2) {
            val elapsedMs = partialTimestamps.last() - partialTimestamps.first()
            if (elapsedMs > 0) (partialTimestamps.size.toDouble() / elapsedMs) * 1000.0 else 0.0
        } else 0.0

        val avgUtterance = if (utteranceToFinalTimes.isNotEmpty()) {
            utteranceToFinalTimes.average()
        } else 0.0

        val avgCallback = if (callbackToHighlightTimes.isNotEmpty()) {
            callbackToHighlightTimes.average()
        } else 0.0

        val avgInterWord = if (wordTimings.size >= 2) {
            val intervals = wordTimings.zipWithNext { a, b -> (b.timestampNs - a.timestampNs) / 1_000_000.0 }
            intervals.average()
        } else 0.0

        return StatsSnapshot(partialsPerSec, avgUtterance, avgCallback, avgInterWord)
    }

    fun getPerWordTimings(): List<WordTiming> = wordTimings.toList()

    fun generateReport(mode: RecognitionMode, excerptTitle: String): String {
        val stats = getStats()
        return buildString {
            appendLine("=== STT Performance Report ===")
            appendLine("Mode: $mode")
            appendLine("Excerpt: $excerptTitle")
            appendLine("---")
            appendLine("Partials/sec: ${"%.2f".format(stats.partialsPerSecond)}")
            appendLine("Utterance-to-final (avg): ${"%.1f".format(stats.avgUtteranceToFinalMs)} ms")
            appendLine("Callback-to-highlight (avg): ${"%.0f".format(stats.avgCallbackToHighlightNs)} ns")
            appendLine("Inter-word (avg): ${"%.1f".format(stats.avgInterWordMs)} ms")
            appendLine("---")
            appendLine("Per-word timings:")
            wordTimings.forEach { wt ->
                appendLine("  word[${ wt.wordIndex}]: ${wt.timestampNs} ns")
            }
        }
    }
}
