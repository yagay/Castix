package me.elhizazi.Castix.display

import android.content.Context

/**
 * Explicit user preference: physical panel off (Root) is preferred over
 * an AMOLED black overlay. The overlay remains an opt-in fallback.
 *
 * The preference is NOT evidence that the display bridge succeeded.
 */
object RootDisplayPreferences {
    private const val FILE = "castix_display_mode"
    private const val ROOT_MODE = "prefer_root_physical_display"
    private const val SAFETY_TEST_MODE = "root_display_safety_test_mode"

    fun preferRoot(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getBoolean(ROOT_MODE, true)

    fun setPreferRoot(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(ROOT_MODE, enabled).apply()
    }
    /** Test mode restores the screen after 30 seconds, even without a touch event. */
    fun safetyTestMode(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getBoolean(SAFETY_TEST_MODE, true)

    fun setSafetyTestMode(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(SAFETY_TEST_MODE, enabled).apply()
    }
}
