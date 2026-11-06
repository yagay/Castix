package me.elhizazi.Castix.xposed

import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import de.robv.android.xposed.AndroidAppHelper
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import me.elhizazi.Castix.privilege.domain.engine.LSPosedEngine
import me.elhizazi.Castix.privilege.domain.engine.LSPatchEngine

/**
 * Experimental, opt-in audio focus guard. Does not fake Activity lifecycle or
 * MediaSession playback: it only blocks an explicitly-scoped app from abandoning
 * its own audio focus while the user's Castix policy is enabled.
 *
 * An app that pauses on onStop(), releases its Surface, or deliberately calls
 * pause() will still pause. Only enable for apps that you have tested.
 */
class CastixXposedHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "CastixXposed"
        private const val MY_PACKAGE_NAME = "me.elhizazi.Castix"
        private val POLICY_URI = Uri.parse("content://me.elhizazi.Castix.hook-policy")
        private const val POLICY_CACHE_MS = 2000L
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName ?: return
        if (pkg == MY_PACKAGE_NAME) {
            val isLSPatch = runCatching {
                Class.forName("org.lsposed.lspatch.Loader", false, lpparam.classLoader)
                true
            }.getOrDefault(false)
            if (isLSPatch) LSPatchEngine.isModulePatchedActive = true
            else LSPosedEngine.isModuleActivatedInScope = true
            return
        }
        // Do not hook system_server or SystemUI. Respect the user's LSPosed scope.
        if (pkg == "android" || pkg == "com.android.systemui") return

        var cachedAllowed = false
        var lastPolicyRead = 0L
        fun shouldGuard(): Boolean {
            val now = SystemClock.elapsedRealtime()
            if (now - lastPolicyRead < POLICY_CACHE_MS) return cachedAllowed
            lastPolicyRead = now
            cachedAllowed = runCatching {
                val app = AndroidAppHelper.currentApplication() ?: return@runCatching false
                app.contentResolver.call(
                    POLICY_URI, "isAudioFocusGuardEnabled", pkg, null
                )?.getBoolean("enabled", false) == true
            }.getOrDefault(false)
            return cachedAllowed
        }

        val hook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (shouldGuard()) {
                    // AUDIOFOCUS_REQUEST_GRANTED is also the success return code for abandon.
                    // This is an opt-in compatibility experiment, NOT a general playback bypass.
                    param.result = AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                }
            }
        }
        try {
            XposedHelpers.findAndHookMethod(
                AudioManager::class.java,
                "abandonAudioFocus",
                AudioManager.OnAudioFocusChangeListener::class.java,
                hook
            )
            Log.i(TAG, "Legacy audio focus guard installed for $pkg (opt-in policy)")
        } catch (t: Throwable) {
            Log.e(TAG, "Legacy audio focus guard not installed for $pkg", t)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                XposedHelpers.findAndHookMethod(
                    AudioManager::class.java,
                    "abandonAudioFocusRequest",
                    AudioFocusRequest::class.java,
                    hook
                )
                Log.i(TAG, "AudioFocusRequest guard installed for $pkg (opt-in policy)")
            } catch (t: Throwable) {
                Log.e(TAG, "AudioFocusRequest guard not installed for $pkg", t)
            }
        }
    }
}
