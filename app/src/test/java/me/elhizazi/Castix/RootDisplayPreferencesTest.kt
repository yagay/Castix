package me.elhizazi.Castix

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import me.elhizazi.Castix.display.DisplayMode
import me.elhizazi.Castix.display.RootDisplayPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RootDisplayPreferencesTest {

    @Test
    fun rootModeIsPreferredByDefault() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ctx.getSharedPreferences("castix_display_mode", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        assertTrue(RootDisplayPreferences.preferRoot(ctx))
        assertTrue(RootDisplayPreferences.safetyTestMode(ctx))
    }

    @Test
    fun explicitOverlayFallbackAndSafetySettingsPersist() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        RootDisplayPreferences.setPreferRoot(ctx, false)
        RootDisplayPreferences.setSafetyTestMode(ctx, false)
        assertFalse(RootDisplayPreferences.preferRoot(ctx))
        assertFalse(RootDisplayPreferences.safetyTestMode(ctx))
        RootDisplayPreferences.setPreferRoot(ctx, true)
        RootDisplayPreferences.setSafetyTestMode(ctx, true)
    }

    @Test
    fun physicalOffIsDefaultAndModesAreMutuallyExclusive() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("castix_display_mode", Context.MODE_PRIVATE)
            .edit().clear().commit()
        assertEquals(DisplayMode.PHYSICAL_OFF, RootDisplayPreferences.mode(ctx))

        RootDisplayPreferences.setMode(ctx, DisplayMode.SYSTEM_LOCK)
        assertEquals(DisplayMode.SYSTEM_LOCK, RootDisplayPreferences.mode(ctx))
        assertFalse(RootDisplayPreferences.preferRoot(ctx))

        RootDisplayPreferences.setMode(ctx, DisplayMode.AMOLED_OVERLAY)
        assertEquals(DisplayMode.AMOLED_OVERLAY, RootDisplayPreferences.mode(ctx))

        RootDisplayPreferences.setMode(ctx, DisplayMode.PHYSICAL_OFF)
        assertTrue(RootDisplayPreferences.preferRoot(ctx))
    }

    @Test
    fun legacyBooleanPreferenceWorksWhenNoExplicitModeIsSaved() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ctx.getSharedPreferences("castix_display_mode", Context.MODE_PRIVATE)
        prefs.edit().clear().putBoolean("prefer_root_physical_display", false).commit()
        assertEquals(DisplayMode.AMOLED_OVERLAY, RootDisplayPreferences.mode(ctx))
        prefs.edit().putBoolean("prefer_root_physical_display", true).commit()
        assertEquals(DisplayMode.PHYSICAL_OFF, RootDisplayPreferences.mode(ctx))
        prefs.edit().clear().commit()
    }
}
