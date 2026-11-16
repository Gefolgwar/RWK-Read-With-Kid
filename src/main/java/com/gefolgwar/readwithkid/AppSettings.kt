package com.gefolgwar.readwithkid

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit
import java.util.Locale

/** Interface language; [SYSTEM] follows the phone's language. */
enum class UiLanguage(val tag: String?) {
    SYSTEM(null), UK("uk"), DE("de"), EN("en")
}

/** What the library keeps across app starts. */
interface LibrarySession {
    /** The child whose library was open last, or null before any was. */
    var activeChildId: Long?
}

/** App-wide settings kept on the phone. */
class AppSettings(context: Context) : LibrarySession {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    override var activeChildId: Long?
        get() = if (prefs.contains(KEY_ACTIVE_CHILD)) prefs.getLong(KEY_ACTIVE_CHILD, 0) else null
        set(value) = prefs.edit { if (value == null) remove(KEY_ACTIVE_CHILD) else putLong(KEY_ACTIVE_CHILD, value) }

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
        const val KEY_ACTIVE_CHILD = "active_child"
    }
}

/** This context with its resources in [language]; unchanged for [UiLanguage.SYSTEM]. */
fun Context.withUiLanguage(language: UiLanguage): Context {
    val tag = language.tag ?: return this
    val config = Configuration(resources.configuration)
    config.setLocale(Locale.forLanguageTag(tag))
    return createConfigurationContext(config)
}
