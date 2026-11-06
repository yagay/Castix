package me.elhizazi.Castix.display

import android.content.Context

/**
 * Physical panel-off is the preferred behavior. Secure system locking remains an
 * explicit independent mode, not an LSPosed-spoofed keyguard state.
 */
enum class DisplayMode {
    PHYSICAL_OFF,
    SYSTEM_LOCK,
    AMOLED_OVERLAY
}

object RootDisplayPreferences {
    private const val FILE = "castix_display_mode"
    private const val MODE = "selected_display_mode"
    private const val LEGACY_ROOT_MODE = "prefer_root_physical_display"
    private const val SAFETY_TEST_MODE = "root_display_safety_test_mode"

    fun mode(context: Context): DisplayMode {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val stored = prefs.getString(MODE, null)
        if (stored != null) {
            return runCatching { DisplayMode.valueOf(stored) }.getOrDefault(DisplayMode.PHYSICAL_OFF)
        }
        // Preserve existing installs' original root/overlay choice.
        return if (prefs.getBoolean(LEGACY_ROOT_MODE, true))
            DisplayMode.PHYSICAL_OFF else DisplayMode.AMOLED_OVERLAY
    }

    fun setMode(context: Context, mode: DisplayMode) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(MODE, mode.name)
            .putBoolean(LEGACY_ROOT_MODE, mode == DisplayMode.PHYSICAL_OFF)
            .apply()
    }

    /** Compatibility for callers/tests from the original Root integration. */
    fun preferRoot(context: Context): Boolean = mode(context) == DisplayMode.PHYSICAL_OFF

    fun setPreferRoot(context: Context, enabled: Boolean) {
        setMode(context, if (enabled) DisplayMode.PHYSICAL_OFF else DisplayMode.AMOLED_OVERLAY)
    }

    /** An independent root watchdog restores the screen after 30 seconds by default. */
    fun safetyTestMode(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getBoolean(SAFETY_TEST_MODE, true)

    fun setSafetyTestMode(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(SAFETY_TEST_MODE, enabled).apply()
    }
}
