package com.gefolgwar.readwithkid

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PerformanceTrackerTest {

    private lateinit var tracker: PerformanceTracker

    @Before
    fun setup() {
        tracker = PerformanceTracker()
    }

    @Test
    fun `initial state has zero metrics`() {
        val stats = tracker.getStats()
        assertEquals(0.0, stats.partialsPerSecond, 0.001)
        assertEquals(0.0, stats.avgUtteranceToFinalMs, 0.001)
        assertEquals(0.0, stats.avgCallbackToHighlightNs, 0.001)
        assertEquals(0.0, stats.avgInterWordMs, 0.001)
    }

    @Test
    fun `partial frequency computed correctly`() {
        tracker.recordPartial(1000L)
        tracker.recordPartial(1200L)
        tracker.recordPartial(1400L)
        tracker.recordPartial(1600L)
        tracker.recordPartial(1800L)
        // 5 partials over 800ms = 6.25/sec
        val stats = tracker.getStats()
        assertEquals(6.25, stats.partialsPerSecond, 0.01)
    }

    @Test
    fun `single partial gives zero frequency`() {
        tracker.recordPartial(1000L)
        assertEquals(0.0, tracker.getStats().partialsPerSecond, 0.001)
    }

    @Test
    fun `utterance to final time averaged`() {
        // First utterance: last partial at 1000, final at 1050 -> 50ms
        tracker.recordFinalStart(1000L)
        tracker.recordFinalEnd(1050L)
        // Second utterance: last partial at 2000, final at 2080 -> 80ms
        tracker.recordFinalStart(2000L)
        tracker.recordFinalEnd(2080L)
        // avg = (50 + 80) / 2 = 65ms
        assertEquals(65.0, tracker.getStats().avgUtteranceToFinalMs, 0.01)
    }

    @Test
    fun `callback to highlight time averaged`() {
        tracker.recordCallbackEntry(100_000L)
        tracker.recordHighlightEmit(100_500L)
        tracker.recordCallbackEntry(200_000L)
        tracker.recordHighlightEmit(200_300L)
        // avg = (500 + 300) / 2 = 400ns
        assertEquals(400.0, tracker.getStats().avgCallbackToHighlightNs, 0.01)
    }

    @Test
    fun `inter-word highlight time computed`() {
        tracker.recordHighlightUpdate(0, 1_000_000L) // word 0 at 1ms (in ns)
        tracker.recordHighlightUpdate(1, 1_500_000L) // word 1 at 1.5ms
        tracker.recordHighlightUpdate(2, 2_200_000L) // word 2 at 2.2ms
        // intervals: 0.5ms, 0.7ms -> avg = 0.6ms
        assertEquals(0.6, tracker.getStats().avgInterWordMs, 0.01)
    }

    @Test
    fun `single word highlight gives zero inter-word time`() {
        tracker.recordHighlightUpdate(0, 1_000_000L)
        assertEquals(0.0, tracker.getStats().avgInterWordMs, 0.001)
    }

    @Test
    fun `reset clears all state`() {
        tracker.recordPartial(1000L)
        tracker.recordPartial(2000L)
        tracker.recordFinalStart(1000L)
        tracker.recordFinalEnd(1050L)
        tracker.recordHighlightUpdate(0, 1_000_000L)
        tracker.recordHighlightUpdate(1, 2_000_000L)
        tracker.recordCallbackEntry(100_000L)
        tracker.recordHighlightEmit(100_500L)

        tracker.reset()

        val stats = tracker.getStats()
        assertEquals(0.0, stats.partialsPerSecond, 0.001)
        assertEquals(0.0, stats.avgUtteranceToFinalMs, 0.001)
        assertEquals(0.0, stats.avgCallbackToHighlightNs, 0.001)
        assertEquals(0.0, stats.avgInterWordMs, 0.001)
    }

    @Test
    fun `generate report includes all metrics`() {
        tracker.recordPartial(1000L)
        tracker.recordPartial(1500L)
        tracker.recordFinalStart(1000L)
        tracker.recordFinalEnd(1050L)
        tracker.recordHighlightUpdate(0, 1_000_000L)
        tracker.recordHighlightUpdate(1, 1_500_000L)
        tracker.recordCallbackEntry(100_000L)
        tracker.recordHighlightEmit(100_400L)

        val report = tracker.generateReport(RecognitionMode.FULL, "Test Excerpt")
        assertTrue(report.contains("FULL"))
        assertTrue(report.contains("Test Excerpt"))
        assertTrue(report.contains("Partials/sec"))
        assertTrue(report.contains("Utterance-to-final"))
        assertTrue(report.contains("Callback-to-highlight"))
        assertTrue(report.contains("Inter-word"))
    }

    @Test
    fun `per-word timings tracked`() {
        tracker.recordHighlightUpdate(0, 1_000_000L)
        tracker.recordHighlightUpdate(1, 1_500_000L)
        tracker.recordHighlightUpdate(2, 2_200_000L)

        val timings = tracker.getPerWordTimings()
        assertEquals(3, timings.size)
        assertEquals(0, timings[0].wordIndex)
        assertEquals(1, timings[1].wordIndex)
        assertEquals(2, timings[2].wordIndex)
    }
}
