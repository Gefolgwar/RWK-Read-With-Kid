package com.gefolgwar.readwithkid

import kotlin.random.Random

/**
 * Parental gate: a multiplication an adult solves at once but a child of 5–8 cannot,
 * asked before the parent area and before anything that leaves the app.
 */
data class GateQuestion(val a: Int, val b: Int) {
    fun accepts(input: String): Boolean = input.trim().toIntOrNull() == a * b

    companion object {
        fun next(random: Random = Random): GateQuestion = GateQuestion(random.nextInt(3, 10), random.nextInt(12, 20))
    }
}
