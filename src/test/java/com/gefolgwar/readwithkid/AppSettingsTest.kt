package com.gefolgwar.readwithkid

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Interface language, coloured syllables and reading text size, kept across app starts. */
@RunWith(RobolectricTestRunner::class)
class AppSettingsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `interface language follows the system until chosen`() {
        assertEquals(UiLanguage.SYSTEM, AppSettings(context).uiLanguage)
        AppSettings(context).uiLanguage = UiLanguage.DE
        assertEquals(UiLanguage.DE, AppSettings(context).uiLanguage)
        AppSettings(context).uiLanguage = UiLanguage.SYSTEM
        assertEquals(UiLanguage.SYSTEM, AppSettings(context).uiLanguage)
    }

    @Test
    fun `coloured syllables are off until chosen`() {
        assertFalse(AppSettings(context).syllableColors)
        AppSettings(context).syllableColors = true
        assertTrue(AppSettings(context).syllableColors)
        AppSettings(context).syllableColors = false
        assertFalse(AppSettings(context).syllableColors)
    }

    @Test
    fun `reading text size is the usual one until chosen`() {
        assertEquals(30, AppSettings(context).readingTextSize)
        AppSettings(context).readingTextSize = 38
        assertEquals(38, AppSettings(context).readingTextSize)
    }

    @Test
    fun `active child is remembered once chosen`() {
        assertNull(AppSettings(context).activeChildId)
        AppSettings(context).activeChildId = 2
        assertEquals(2L, AppSettings(context).activeChildId)
    }

    @Test
    fun `first launch is in parent mode and the mode is remembered`() {
        assertFalse(AppSettings(context).childMode)
        AppSettings(context).childMode = true
        assertTrue(AppSettings(context).childMode)
        AppSettings(context).childMode = false
        assertFalse(AppSettings(context).childMode)
    }

    private val Context.language get() = resources.configuration.locales[0].language

    @Test
    @Config(qualifiers = "fr")
    fun `chosen language overrides the system language`() {
        assertEquals("de", context.withUiLanguage(UiLanguage.DE).language)
        assertEquals("uk", context.withUiLanguage(UiLanguage.UK).language)
        assertEquals("en", context.withUiLanguage(UiLanguage.EN).language)
        assertEquals("fr", context.withUiLanguage(UiLanguage.SYSTEM).language)
    }
}
