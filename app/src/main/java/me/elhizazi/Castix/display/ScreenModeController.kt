package me.elhizazi.Castix.display

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.elhizazi.Castix.privilege.domain.engine.RootEngine
import me.elhizazi.Castix.service.BlackScreenOverlayManager
import me.elhizazi.Castix.service.CastixBackgroundService

/**
 * The only entry point for Castix's display action across UI/tile/notification/
 * floating button. Never spoof KeyguardManager or PowerManager to an app.
 */
object ScreenModeController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val root = RootEngine()
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun activate(context: Context) {
        val app = context.applicationContext
        _lastError.value = null
        try {
            when (RootDisplayPreferences.mode(app)) {
                DisplayMode.PHYSICAL_OFF -> {
                    if (BlackScreenOverlayManager.isBlackScreenActive.value) {
                        _lastError.value = "Close the AMOLED overlay before turning off the physical panel."
                        return
                    }
                    CastixBackgroundService.start(app)
                    RootDisplayManager.getInstance(app).toggle()
                }
                DisplayMode.AMOLED_OVERLAY -> {
                    if (RootDisplayManager.getInstance(app).state.value.isOff) {
                        _lastError.value = "Restore the physical panel before switching modes."
                        return
                    }
                    BlackScreenOverlayManager.getInstance(app).toggleBlackScreen()
                }
                DisplayMode.SYSTEM_LOCK -> {
                    // Android lock, not a fake "unlocked" state in the player.
                    if (RootDisplayManager.getInstance(app).state.value.isOff) {
                        _lastError.value = "Restore the physical panel before system locking."
                        return
                    }
                    if (BlackScreenOverlayManager.isBlackScreenActive.value) {
                        BlackScreenOverlayManager.getInstance(app).hideBlackScreen()
                    }
                    // Keep the controller alive during the genuine system sleep
                    // transition; the keyguard remains enforced by Android.
                    CastixBackgroundService.start(app)
                    scope.launch {
                        val result = runCatching {
                            if (!root.checkPermission(app)) {
                                error("Root is required to send the system sleep key.")
                            }
                            // KEYCODE_SLEEP (223) asks Android/ColorOS to perform its
                            // normal sleep/lock sequence; keyguard security is not bypassed.
                            val command = root.executeCommand("input keyevent 223")
                            if (!command.isSuccess) {
                                error(command.stderr.ifBlank { "System sleep key command failed" })
                            }
                        }
                        result.onFailure { failure ->
                            _lastError.value = "System lock failed: ${failure.localizedMessage}"
                        }
                    }
                }
            }
        } catch (t: Exception) {
            _lastError.value = "Screen mode action failed: ${t.localizedMessage}"
        }
    }
}
