package com.gefolgwar.readwithkid

/**
 * Short texts that come with the app, so it can be tried right after installing; the parent can delete them.
 * Written for this app (no copyrighted texts), in plain words without digits, abbreviations or symbols.
 */
object SampleTexts {
    val all: List<Pair<String, TextLanguage>> = listOf(
        "Рудий кіт сидить на вікні. Він дивиться на пташок у саду. Потім кіт заплющує очі і засинає на сонці." to TextLanguage.UK,
        "Der kleine Hund spielt im Garten. Er läuft schnell zu seinem roten Ball. Am Abend schläft er müde in seinem Korb." to TextLanguage.DE
    )
}
