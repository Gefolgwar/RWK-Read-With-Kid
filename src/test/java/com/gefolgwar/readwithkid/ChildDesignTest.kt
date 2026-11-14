package com.gefolgwar.readwithkid

import org.junit.Assert.assertEquals
import org.junit.Test

/** The reward after reading and the automatic scrolling of the reading text. */
class ChildDesignTest {

    private fun result(correct: Int, total: Int = 10) = ReadingResult(
        totalWords = total,
        correctWords = correct,
        durationMs = 60_000,
        skippedWords = emptyList(),
        retriedWords = emptyList()
    )

    @Test
    fun `every reading earns at least one star`() {
        assertEquals(1, result(correct = 0).stars)
        assertEquals(1, result(correct = 6).stars)
        assertEquals(1, result(correct = 0, total = 0).stars)
    }

    @Test
    fun `more accurate reading earns more stars`() {
        assertEquals(2, result(correct = 7).stars)
        assertEquals(2, result(correct = 8).stars)
        assertEquals(3, result(correct = 9).stars)
        assertEquals(3, result(correct = 10).stars)
    }

    @Test
    fun `the current line is kept a third of the way down the text`() {
        assertEquals(400, autoScrollTarget(lineTop = 700f, viewportHeight = 900))
    }

    @Test
    fun `the first lines need no scrolling`() {
        assertEquals(0, autoScrollTarget(lineTop = 0f, viewportHeight = 900))
        assertEquals(0, autoScrollTarget(lineTop = 200f, viewportHeight = 900))
    }
}
