package me.elhizazi.Castix.display

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/**
 * Touch-isolation safety layer for physical panel-off sessions ONLY.
 *
 * This is a fully transparent input-capturing window, NOT a black-screen
 * replacement. Root still controls the actual physical panel. The guard
 * consumes application-area touches, preventing accidental playback taps.
 * Android system bars/gesture navigation and vendor-secure input remain
 * outside the guarantee of a TYPE_APPLICATION_OVERLAY window.
 *
 * The native ColorOS wake gesture is preferred when emitted by the touch HAL;
 * this listener handles ordinary touches only as a fallback.
 */
internal class InputGuardHost(
    context: Context,
    private val onFallbackDoubleTap: () -> Unit
) {
    private val applicationContext = context.applicationContext
    private val wm = applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val gestureDetector = GestureDetector(
        applicationContext,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val enabled = applicationContext
                    .getSharedPreferences("castix_black_screen_prefs", Context.MODE_PRIVATE)
                    .getBoolean("double_tap_to_wake", true)
                if (enabled) onFallbackDoubleTap()
                return true
            }
        },
        Handler(Looper.getMainLooper())
    )

    @SuppressLint("ClickableViewAccessibility")
    private val guard = View(applicationContext).apply {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = true
        setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true // Consume: do not forward taps/swipes to the video application.
        }
    }

    fun attach() {
        if (guard.isAttachedToWindow) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        wm.addView(guard, params)
    }

    fun detach() {
        if (guard.isAttachedToWindow) wm.removeView(guard)
    }
}
