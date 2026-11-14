package com.gefolgwar.readwithkid

import org.junit.Assert.*
import org.junit.Test

class TextPreparationTest {

    private fun displays(text: PreparedText) = text.sentences.map { s -> s.map { it.display } }

    @Test
    fun `splits text into sentences and words`() {
        val text = TextPreparation.prepare("Мама мила раму. Тато читав книжку! Чи ти бачив?")
        assertEquals(
            listOf(
                listOf("Мама", "мила", "раму."),
                listOf("Тато", "читав", "книжку!"),
                listOf("Чи", "ти", "бачив?")
            ),
            displays(text)
        )
        assertEquals(9, text.words.size)
    }

    @Test
    fun `sentence ends after closing quote and ellipsis`() {
        val text = TextPreparation.prepare("Він сказав «Добре.» Потім пішов… Кінець")
        assertEquals(3, text.sentences.size)
        assertEquals("«Добре.»", text.sentences[0].last().display)
    }

    @Test
    fun `blank line separates sentences, single line break does not`() {
        val text = TextPreparation.prepare("Перший рядок\nтого ж речення\n\nНовий абзац")
        assertEquals(listOf(listOf("Перший", "рядок", "того", "ж", "речення"), listOf("Новий", "абзац")), displays(text))
    }

    @Test
    fun `punctuation-only tokens are not words but stay in display`() {
        val text = TextPreparation.prepare("— Привіт — сказав кіт.")
        assertEquals(listOf("— Привіт —", "сказав", "кіт."), text.words.map { it.display })
        assertEquals(listOf("привіт", "сказав", "кіт"), text.words.map { it.normalized })
    }

    @Test
    fun `empty or blank text has no words`() {
        assertTrue(TextPreparation.prepare("").words.isEmpty())
        assertTrue(TextPreparation.prepare("  \n\n  ").sentences.isEmpty())
        assertTrue(TextPreparation.prepare("— ...").words.isEmpty())
    }

    @Test
    fun `non-breaking space separates words`() {
        assertEquals(2, TextPreparation.prepare("два слова").words.size)
    }

    @Test
    fun `normalize lowercases and strips punctuation`() {
        assertEquals("україна", TextPreparation.normalize("«Україна»,"))
        assertEquals("держава", TextPreparation.normalize("Держава."))
        assertEquals("так", TextPreparation.normalize("(Так!)"))
    }

    @Test
    fun `normalize treats all Ukrainian apostrophes the same`() {
        val expected = TextPreparation.normalize("сім'я")
        assertEquals("сімя", expected)
        assertEquals(expected, TextPreparation.normalize("сімʼя")) // U+02BC
        assertEquals(expected, TextPreparation.normalize("сім’я")) // U+2019
    }

    @Test
    fun `normalize removes stress mark`() {
        assertEquals("замок", TextPreparation.normalize("за́мок"))
        assertEquals("замок", TextPreparation.normalize("замо́к"))
    }

    @Test
    fun `normalize keeps decomposed Ukrainian letters intact`() {
        // "ї" and "й" written as base letter + combining mark
        assertEquals("їжак", TextPreparation.normalize("їжак"))
        assertEquals("мій", TextPreparation.normalize("мій"))
    }

    @Test
    fun `normalize keeps inner hyphen`() {
        assertEquals("будь-який", TextPreparation.normalize("будь-який,"))
    }
}
