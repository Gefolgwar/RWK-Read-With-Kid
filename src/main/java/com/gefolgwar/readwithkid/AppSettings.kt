package com.gefolgwar.readwithkid

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit
import java.util.Locale

/** Interface language; [SYSTEM] follows the phone's language. */
enum class UiLanguage(val tag: String?) {
    SYSTEM(null), UK("uk"), DE("de"), EN("en")
}

/** App-wide settings kept on the phone. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var uiLanguage: UiLanguage
        get() = UiLanguage.entries.firstOrNull { it.tag == prefs.getString(KEY_UI_LANGUAGE, null) } ?: UiLanguage.SYSTEM
        set(value) = prefs.edit { putString(KEY_UI_LANGUAGE, value.tag) }

    /** Whether the reading text shows its syllables in alternating colours; off until chosen. */
    var syllableColors: Boolean
        get() = prefs.getBoolean(KEY_SYLLABLE_COLORS, false)
        set(value) = prefs.edit { putBoolean(KEY_SYLLABLE_COLORS, value) }

    private companion object {
        const val KEY_UI_LANGUAGE = "ui_language"
        const val KEY_SYLLABLE_COLORS = "syllable_colors"
    }
}

/** This context with its resources in [language]; unchanged for [UiLanguage.SYSTEM]. */
fun Context.withUiLanguage(language: UiLanguage): Context {
    val tag = language.tag ?: return this
    val config = Configuration(resources.configuration)
    config.setLocale(Locale.forLanguageTag(tag))
    return createConfigurationContext(config)
}
