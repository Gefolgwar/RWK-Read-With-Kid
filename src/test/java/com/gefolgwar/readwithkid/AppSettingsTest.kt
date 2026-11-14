package com.gefolgwar.readwithkid

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Onboarding flag and interface language, kept across app starts. */
@RunWith(RobolectricTestRunner::class)
class AppSettingsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `first launch shows onboarding once`() {
        assertFalse(AppSettings(context).onboardingDone)
        AppSettings(context).onboardingDone = true
        assertTrue(AppSettings(context).onboardingDone)
    }

    @Test
    fun `interface language follows the system until chosen`() {
        assertEquals(UiLanguage.SYSTEM, AppSettings(context).uiLanguage)
        AppSettings(context).uiLanguage = UiLanguage.DE
        assertEquals(UiLanguage.DE, AppSettings(context).uiLanguage)
        AppSettings(context).uiLanguage = UiLanguage.SYSTEM
        assertEquals(UiLanguage.SYSTEM, AppSettings(context).uiLanguage)
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
