package com.gefolgwar.readwithkid

import org.junit.Assert.assertEquals
import org.junit.Test

/** Splitting words into syllables for the coloured-syllables option. */
class SyllablesTest {

    private fun assertSyllables(language: TextLanguage, vararg expected: String) {
        expected.forEach { hyphenated ->
            assertEquals(hyphenated, Syllables.split(hyphenated.replace("-", ""), language).joinToString("-"))
        }
    }

    @Test
    fun `ukrainian words follow the school syllable rules`() {
        assertSyllables(
            TextLanguage.UK,
            "ма-ма", "ко-ло-бок", "ді-ти", "ша-пка", "ка-зка", "мор-ква", "лій-ка", "зай-чик",
            "сі-м'я", "ві-кон-це", "со-неч-ко", "пі-се-нька"
        )
    }

    @Test
    fun `german words split as in first reading books`() {
        assertSyllables(
            TextLanguage.DE,
            "Ma-ries", "Ran-zen", "ver-hext", "Hef-ten", "Bü-chern", "gras-grü-ne", "Frö-sche", "da-rin",
            "sei-nen", "Haus-geist", "Hen-ry", "Schu-le", "gro-ßes", "Cha-os", "rich-tig", "Spaß", "und", "ist",
            "O-ma", "a-ber"
        )
    }

    @Test
    fun `punctuation stays with the neighbouring syllable`() {
        assertEquals(listOf("«Ма", "ма!»"), Syllables.split("«Мама!»", TextLanguage.UK))
        assertEquals(listOf("— Ma", "ries,"), Syllables.split("— Maries,", TextLanguage.DE))
        assertEquals(listOf("сі", "м’я"), Syllables.split("сім’я", TextLanguage.UK))
    }

    @Test
    fun `parts of a hyphenated word are split separately`() {
        assertEquals(listOf("будь-", "я", "кий"), Syllables.split("будь-який", TextLanguage.UK))
    }

    @Test
    fun `a word without vowels stays whole`() {
        assertEquals(listOf("з"), Syllables.split("з", TextLanguage.UK))
        assertEquals(listOf("2024"), Syllables.split("2024", TextLanguage.DE))
    }
}
