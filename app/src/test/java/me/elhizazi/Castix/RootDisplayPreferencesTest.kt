package me.elhizazi.Castix

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import me.elhizazi.Castix.display.RootDisplayPreferences
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
}
