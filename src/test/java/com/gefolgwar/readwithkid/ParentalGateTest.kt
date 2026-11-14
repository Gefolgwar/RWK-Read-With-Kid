package com.gefolgwar.readwithkid

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/** The question that keeps children out of the parent area and away from external links. */
class ParentalGateTest {

    @Test
    fun `questions multiply a single digit by a number in the teens`() {
        val random = Random(42)
        repeat(200) {
            val question = GateQuestion.next(random)
            assertTrue(question.a in 3..9)
            assertTrue(question.b in 12..19)
        }
    }

    @Test
    fun `accepts only the product`() {
        val question = GateQuestion(7, 16)
        assertTrue(question.accepts("112"))
        assertTrue(question.accepts(" 112 "))
        assertFalse(question.accepts("111"))
        assertFalse(question.accepts("23"))
        assertFalse(question.accepts(""))
        assertFalse(question.accepts("abc"))
    }
}
