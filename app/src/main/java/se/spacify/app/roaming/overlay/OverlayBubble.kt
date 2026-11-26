package se.spacify.app.roaming.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import se.spacify.app.roaming.R
import se.spacify.app.roaming.motion.Direction
import se.spacify.app.roaming.motion.MotionState
import se.spacify.app.roaming.motion.MotionType
import se.spacify.app.roaming.safety.SafetySettings

/** A small draggable bubble drawn over other apps while Roaming is active. */
class OverlayBubble(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: View? = null
    private var lastState: MotionState = MotionState.IDLE
    private var showingReminder = false
    private val endReminder = Runnable {
        showingReminder = false
        view?.let { bubble ->
            bubble.setBackgroundResource(R.drawable.bubble_background)
            render(bubble, lastState)
        }
    }
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 24
        y = 200
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    fun show() {
        if (view != null) return
        val bubble = LayoutInflater.from(context).inflate(R.layout.overlay_bubble, null)
        bubble.setOnTouchListener(DragListener())
        windowManager.addView(bubble, params)
        view = bubble
        update(MotionState.IDLE)
    }

    fun update(state: MotionState) {
        val bubble = view ?: return
        bubble.post {
            lastState = state
            if (!showingReminder) render(bubble, state)
        }
    }

    /**
     * Briefly turns the bubble into a reminder to keep track of the surroundings, then goes back to
     * showing the current motion. Motion updates that arrive meanwhile are kept, not lost.
     */
    fun showSafetyReminder() {
        val bubble = view ?: return
        bubble.post {
            showingReminder = true
            bubble.removeCallbacks(endReminder)
            bubble.setBackgroundResource(R.drawable.bubble_background_warning)
            bubble.alpha = 1f
            bubble.findViewById<TextView>(R.id.bubble_icon).text = "\u26A0" // ⚠
            bubble.findViewById<TextView>(R.id.bubble_label).setText(R.string.safety_reminder_bubble)
            bubble.postDelayed(endReminder, SafetySettings.REMINDER_DISPLAY_MILLIS)
        }
    }

    fun hide() {
        view?.let {
            it.removeCallbacks(endReminder)
            runCatching { windowManager.removeView(it) }
        }
        view = null
    }

    private fun render(bubble: View, state: MotionState) {
        bubble.findViewById<TextView>(R.id.bubble_icon).text = iconFor(state)
        bubble.findViewById<TextView>(R.id.bubble_label).text = state.motionType.wire.replace('_', ' ')
        bubble.alpha = 0.6f + 0.4f * state.intensity
    }

    private fun iconFor(state: MotionState): String = when (state.motionType) {
        MotionType.IDLE -> "●" // ●
        MotionType.STEP, MotionType.WALKING -> "🚶" // 🚶
        MotionType.RUNNING -> "🏃" // 🏃
        MotionType.JUMPING -> "⬆" // ⬆
        MotionType.SQUATTING -> "⬇" // ⬇
        MotionType.ARMS_RAISED -> "🙌" // 🙌
        MotionType.LEANING -> if (state.direction == Direction.LEFT) "⬅" else "➡" // ⬅ ➡
    }

    private inner class DragListener : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y
                    touchX = event.rawX; touchY = event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).toInt()
                    params.y = startY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(v, params)
                }
            }
            return true
        }
    }
}
