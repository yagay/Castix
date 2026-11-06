package me.elhizazi.Castix.display

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.elhizazi.Castix.privilege.domain.engine.RootEngine
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Root-first Extinguish physical screen power implementation.
 *
 * No Shizuku, no fake black window. The 1x1 overlay keeps Android interactive,
 * as in upstream Extinguish's AwakeHost. An evdev listener provides a
 * best-effort double-tap and hardware-key recovery path.
 *
 * Vendor native DT2W may be unavailable in POWER_MODE_OFF; in that case the
 * hardware key recovery and detached root watchdog are essential.
 */
class RootDisplayManager private constructor(private val context: Context) {

    enum class Status { ON, TURNING_OFF, OFF, RESTORING, ERROR }

    data class DisplayState(
        val status: Status = Status.ON,
        val message: String = "",
    ) {
        val isOff: Boolean get() = status == Status.OFF || status == Status.RESTORING
    }

    companion object {
        private const val TAG = "CastixRootDisplay"
        private const val WATCHDOG_SECONDS = 2 * 60 * 60 // Hard recovery if app dies.
        private const val DOUBLE_TAP_WINDOW_MS = 400L
        private const val VOLUME_RECOVERY_WINDOW_MS = 700L

        @Volatile private var instance: RootDisplayManager? = null

        fun getInstance(context: Context): RootDisplayManager =
            instance ?: synchronized(this) {
                instance ?: RootDisplayManager(context.applicationContext)
                    .also { instance = it }
            }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val root = RootEngine()
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val keepAwakeView = View(context)
    private val monitoring = AtomicBoolean(false)
    private var monitorProcess: Process? = null
    private var sessionMarker: String? = null
    private val markerPath = "/data/local/tmp/castix_display_${android.os.Process.myUid()}.session"

    private val _state = MutableStateFlow(DisplayState())
    val state: StateFlow<DisplayState> = _state.asStateFlow()

    fun toggle() {
        scope.launch {
            mutex.withLock {
                when (_state.value.status) {
                    Status.OFF, Status.ERROR -> restoreLocked()
                    Status.ON -> turnOffLocked()
                    Status.TURNING_OFF, Status.RESTORING -> Unit
                }
            }
        }
    }

    fun restore() {
        scope.launch { mutex.withLock { restoreLocked() } }
    }

    private suspend fun turnOffLocked() {
        _state.value = DisplayState(Status.TURNING_OFF)
        if (!Settings.canDrawOverlays(context)) {
            _state.value = DisplayState(
                Status.ERROR, "Grant Display over other apps for the 1x1 keep-awake host.")
            return
        }
        if (!root.checkPermission(context)) {
            _state.value = DisplayState(Status.ERROR, "Root (su) access is required.")
            return
        }

        try {
            withContext(Dispatchers.Main) { attachKeepAwakeView() }

            // Always arrange an out-of-process recovery BEFORE switching off.
            val marker = "castix_${System.currentTimeMillis()}_${SystemClock.elapsedRealtime()}"
            val watchdog = installWatchdog(marker)
            if (!watchdog) {
                throw IllegalStateException("Root recovery watchdog could not be installed")
            }
            sessionMarker = marker
            startInputWatcher()

            val result = root.executeCommand(displayCommand("off"))
            if (!result.isSuccess || !result.stdout.contains("CASTIX_DISPLAY_OFF_OK")) {
                throw IllegalStateException(
                    "Extinguish display power OFF failed: " +
                        result.stderr.ifBlank { result.stdout.takeLast(400) })
            }
            _state.value = DisplayState(Status.OFF)
            Log.i(TAG, "Physical panel power set to OFF; Android remains interactive")
        } catch (e: Exception) {
            Log.e(TAG, "Cannot enter physical display-off mode", e)
            // The display call may have partially succeeded. Always attempt ON.
            val restored = runPowerOn()
            stopInputWatcher()
            if (restored) {
                clearWatchdog()
                withContext(Dispatchers.Main) { detachKeepAwakeView() }
            }
            _state.value = DisplayState(Status.ERROR, e.localizedMessage ?: "Root display OFF failed")
        }
    }

    private suspend fun restoreLocked() {
        if (_state.value.status == Status.ON) return
        _state.value = DisplayState(Status.RESTORING)
        val restored = runPowerOn()
        if (restored) {
            stopInputWatcher()
            clearWatchdog()
            withContext(Dispatchers.Main) { detachKeepAwakeView() }
            _state.value = DisplayState(Status.ON)
        } else {
            _state.value = DisplayState(
                Status.ERROR,
                "Root display restore failed. Use adb shell su -c with the recovery command in docs/ROOT_DISPLAY.md.")
        }
    }

    private suspend fun runPowerOn(): Boolean {
        repeat(3) {
            val result = root.executeCommand(displayCommand("on"))
            if (result.isSuccess && result.stdout.contains("CASTIX_DISPLAY_ON_OK")) {
                return true
            }
            kotlinx.coroutines.delay(250)
        }
        return false
    }

    private fun shellQuote(input: String): String =
        "'" + input.replace("'", "'\\''") + "'"

    private fun displayCommand(mode: String): String {
        val apk = context.applicationInfo.sourceDir
        return "CLASSPATH=${shellQuote(apk)} /system/bin/app_process /system/bin " +
            "me.elhizazi.Castix.display.RootDisplayCli $mode"
    }

    private suspend fun installWatchdog(marker: String): Boolean {
        // Detached root shell restores normal display power after two hours.
        // Marker invalidation prevents outdated watchdogs from restoring a new session.
        val id = shellQuote(marker)
        val file = shellQuote(markerPath)
        val on = displayCommand("on")
        val command = "printf %s $id > $file || exit 1; " +
            "(sleep $WATCHDOG_SECONDS; " +
            "if [ \"\$(cat $file 2>/dev/null)\" = $id ]; then " +
            "$on >/dev/null 2>&1; rm -f $file; fi) " +
            "</dev/null >/dev/null 2>&1 &"
        return root.executeCommand(command).isSuccess
    }

    private suspend fun clearWatchdog() {
        sessionMarker = null
        root.executeCommand("rm -f ${shellQuote(markerPath)}")
    }

    private fun attachKeepAwakeView() {
        if (keepAwakeView.isAttachedToWindow) return
        val params = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        wm.addView(keepAwakeView, params)
    }

    private fun detachKeepAwakeView() {
        if (!keepAwakeView.isAttachedToWindow) return
        runCatching { wm.removeView(keepAwakeView) }
    }

    private fun startInputWatcher() {
        if (!monitoring.compareAndSet(false, true)) return
        Thread({
            var lastTouchUp = 0L
            var lastVolumeUp = 0L
            try {
                // Root is required to listen to /dev/input. This is read-only.
                val proc = ProcessBuilder("su", "-c", "exec getevent -l")
                    .redirectErrorStream(true).start()
                monitorProcess = proc
                BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                    while (monitoring.get()) {
                        val line = reader.readLine() ?: break
                        val now = SystemClock.elapsedRealtime()
                        if (_state.value.status != Status.OFF || !line.contains("EV_KEY")) continue
                        val eventUp = line.contains(" UP") || line.endsWith("00000000")
                        val eventDown = line.contains(" DOWN") || line.endsWith("00000001")

                        if (eventDown && (
                                line.contains("KEY_WAKEUP") ||
                                line.contains("KEY_GESTURE") ||
                                line.contains("KEY_POWER"))) {
                            restore()
                            continue
                        }
                        if (eventUp && line.contains("BTN_TOUCH")) {
                            val doubleTapEnabled = context
                                .getSharedPreferences("castix_black_screen_prefs", Context.MODE_PRIVATE)
                                .getBoolean("double_tap_to_wake", true)
                            if (doubleTapEnabled && now - lastTouchUp in 80L..DOUBLE_TAP_WINDOW_MS) {
                                restore()
                            }
                            lastTouchUp = now
                        }
                        if (eventUp && line.contains("KEY_VOLUMEUP")) {
                            if (now - lastVolumeUp in 80L..VOLUME_RECOVERY_WINDOW_MS) restore()
                            lastVolumeUp = now
                        }
                    }
                }
            } catch (t: Exception) {
                Log.w(TAG, "Root evdev wake monitor unavailable: ${t.localizedMessage}")
            } finally {
                monitoring.set(false)
                monitorProcess?.destroy()
                monitorProcess = null
            }
        }, "Castix-root-evdev-wake").apply {
            isDaemon = true
            start()
        }
    }

    private fun stopInputWatcher() {
        monitoring.set(false)
        monitorProcess?.destroy()
        monitorProcess = null
    }
}
