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
    fun `normalize drops hyphens so a hyphenated word matches its spoken parts`() {
        assertEquals("будьякий", TextPreparation.normalize("будь-який,"))
        assertEquals("badenwuerttemberg", TextPreparation.normalize("Baden-Württemberg."))
    }

    @Test
    fun `German closing quotes end a sentence`() {
        val text = TextPreparation.prepare("Er rief: „Halt!“ Dann lief er. Sie sagte ‚Ja.‘ Gut")
        assertEquals(4, text.sentences.size)
        assertEquals("„Halt!“", text.sentences[0].last().display)
    }

    @Test
    fun `language is detected from the alphabet`() {
        assertEquals(TextLanguage.UK, TextPreparation.detectLanguage("Мама мила раму."))
        assertEquals(TextLanguage.DE, TextPreparation.detectLanguage("Die Straße ist groß."))
        assertNull(TextPreparation.detectLanguage("123 — 456!"))
    }

    // --- hard-to-recognise parts ---

    @Test
    fun `ordinary text has no warnings`() {
        val warnings = TextPreparation.findHardParts("— Привіт, «кіт»! Сім'я за́мок, будь-який (так)… Größe: „Ja“; Я і A.")
        assertTrue(warnings.isEmpty)
    }

    @Test
    fun `digits are reported without surrounding punctuation`() {
        val warnings = TextPreparation.findHardParts("У 2024 році було 5, а стало 10.")
        assertEquals(listOf("2024", "5", "10"), warnings.digits)
        assertFalse(warnings.isEmpty)
    }

    @Test
    fun `all-caps words and dotted abbreviations are reported`() {
        val warnings = TextPreparation.findHardParts("США, ЗСУ і т.д. Die DB fährt z.B. heute. ЗСУ")
        assertEquals(listOf("США", "ЗСУ", "т.д.", "DB", "z.B."), warnings.abbreviations)
    }

    @Test
    fun `symbols are reported once each`() {
        val warnings = TextPreparation.findHardParts("50% & 20 € + 3 = № 7 @ кіт # кіт %")
        assertEquals(listOf("%", "&", "€", "+", "=", "№", "@", "#"), warnings.symbols)
    }

    // --- automatic title ---

    @Test
    fun `title is the first five words with an ellipsis when the text is longer`() {
        assertEquals("Мама мила раму. Тато читав…", TextPreparation.autoTitle("Мама мила раму. Тато читав книжку."))
        assertEquals("Жив собі кіт — дуже великий…", TextPreparation.autoTitle("Жив собі кіт — дуже великий і рудий."))
    }

    @Test
    fun `short text is the whole title without trailing punctuation`() {
        assertEquals("Мама мила раму", TextPreparation.autoTitle("  Мама мила\nраму. "))
        assertEquals("«Кіт»", TextPreparation.autoTitle("«Кіт»"))
        assertEquals("", TextPreparation.autoTitle(" — … "))
    }
}
