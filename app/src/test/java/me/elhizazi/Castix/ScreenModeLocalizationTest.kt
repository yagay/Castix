package me.elhizazi.Castix

import me.elhizazi.Castix.privilege.ui.AppStrings
import me.elhizazi.Castix.privilege.ui.LanguageRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Ensures all new Root/Extinguish/ColorOS display labels and error messages
 * have a native Simplified Chinese translation rather than silently showing
 * an English fallback in the Chinese UI.
 */
class ScreenModeLocalizationTest {

    @Test
    fun chineseAndEnglishAreAvailable() {
        val supported = LanguageRegistry.supportedLanguages.map { it.code }
        assertTrue(supported.contains("en"))
        assertTrue(supported.contains("zh"))
        assertEquals("简体中文", LanguageRegistry.findLanguage("zh").nativeName)
    }

    @Test
    fun everyScreenModeKeyHasIndependentEnglishAndChineseText() {
        val keys = AppStrings.en.keys.filter { it.startsWith("screen_") }
        assertTrue("Expected the full mode/error translation group", keys.size >= 50)
        for (key in keys) {
            val english = AppStrings.get(key, "en")
            val chinese = AppStrings.get(key, "zh")
            assertNotEquals("Missing English for $key", key, english)
            assertNotEquals("Missing Chinese for $key", key, chinese)
            assertTrue("Empty English for $key", english.isNotBlank())
            assertTrue("Empty Chinese for $key", chinese.isNotBlank())
            assertNotEquals("Chinese fallback to English for $key", english, chinese)
        }
    }

    @Test
    fun explicitChineseAndEnglishLanguageTagsAreAccepted() {
        assertEquals(
            AppStrings.get("screen_mode_physical_title", "zh"),
            AppStrings.get("screen_mode_physical_title", "zh-CN")
        )
        assertEquals(
            AppStrings.get("screen_mode_physical_title", "en"),
            AppStrings.get("screen_mode_physical_title", "en-GB")
        )
    }

    @Test
    fun autoLanguageReadsDeviceLocaleNotLastManuallySelectedLocale() {
        val deviceLanguage = android.content.res.Resources.getSystem()
            .configuration.locales[0].language
        val expected = AppStrings.get("screen_mode_physical_title", deviceLanguage)
        val previous = Locale.getDefault()
        try {
            // A manually changed default locale must not override the OS locale.
            Locale.setDefault(Locale(if (deviceLanguage == "zh") "en" else "zh"))
            assertEquals(
                expected,
                AppStrings.get("screen_mode_physical_title", "auto")
            )
        } finally {
            Locale.setDefault(previous)
        }
    }
}
