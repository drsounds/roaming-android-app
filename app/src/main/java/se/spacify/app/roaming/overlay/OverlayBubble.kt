package se.spacify.app.roaming.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import se.spacify.app.roaming.R
import se.spacify.app.roaming.motion.Direction
import se.spacify.app.roaming.motion.MotionState
import se.spacify.app.roaming.motion.MotionType
import se.spacify.app.roaming.safety.SafetySettings
import kotlin.math.abs

/**
 * A small draggable bubble drawn over other apps while Roaming is active.
 *
 * Tapping it calls [onTap] (the service opens Roaming). While it is dragged a bin appears at the
 * bottom of the screen; dropping the bubble on the bin calls [onDropOnTrash] (the service stops).
 * The surroundings reminder pops up in its own box under the bubble, so the motion stays visible.
 */
class OverlayBubble(
    private val context: Context,
    private val onTap: () -> Unit = {},
    private val onDropOnTrash: () -> Unit = {},
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var root: View? = null
    private var trash: View? = null
    private var lastState: MotionState = MotionState.IDLE
    private val endReminder = Runnable {
        root?.findViewById<View>(R.id.bubble_warning)?.visibility = View.GONE
    }
    private val params = overlayParams(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 24
        y = 200
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    fun show() {
        if (root != null) return
        val view = LayoutInflater.from(context).inflate(R.layout.overlay_bubble, null)
        view.setOnTouchListener(DragListener())
        // Lets accessibility services "tap" the bubble too.
        view.setOnClickListener { onTap() }
        windowManager.addView(view, params)
        root = view
        update(MotionState.IDLE)
    }

    fun update(state: MotionState) {
        val view = root ?: return
        view.post {
            lastState = state
            if (root === view) render(view, state)
        }
    }

    /**
     * Shows the reminder to keep track of the surroundings in its own box under the bubble for a
     * few seconds. The bubble keeps showing the current motion meanwhile.
     */
    fun showSafetyReminder() {
        val view = root ?: return
        view.post {
            if (root !== view) return@post
            view.removeCallbacks(endReminder)
            view.findViewById<View>(R.id.bubble_warning).visibility = View.VISIBLE
            view.postDelayed(endReminder, SafetySettings.REMINDER_DISPLAY_MILLIS)
        }
    }

    fun hide() {
        hideTrash()
        root?.let {
            it.removeCallbacks(endReminder)
            runCatching { windowManager.removeView(it) }
        }
        root = null
    }

    private fun render(view: View, state: MotionState) {
        view.findViewById<TextView>(R.id.bubble_icon).text = iconFor(state)
        view.findViewById<TextView>(R.id.bubble_label).text = state.motionType.wire.replace('_', ' ')
        view.findViewById<View>(R.id.bubble).alpha = 0.6f + 0.4f * state.intensity
    }

    @SuppressLint("InflateParams")
    private fun showTrash() {
        if (trash != null) return
        val view = LayoutInflater.from(context).inflate(R.layout.overlay_trash, null)
        val trashParams = overlayParams(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (context.resources.displayMetrics.density * TRASH_BOTTOM_MARGIN_DP).toInt()
        }
        runCatching { windowManager.addView(view, trashParams) }.onSuccess { trash = view }
    }

    private fun hideTrash() {
        trash?.let { runCatching { windowManager.removeView(it) } }
        trash = null
    }

    /** Whether the finger at screen position ([rawX], [rawY]) is over the bin, with some slack. */
    private fun isOverTrash(rawX: Float, rawY: Float): Boolean {
        val view = trash ?: return false
        if (view.width == 0) return false
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val slack = (context.resources.displayMetrics.density * TRASH_SLACK_DP).toInt()
        val bounds = Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
        bounds.inset(-slack, -slack)
        return bounds.contains(rawX.toInt(), rawY.toInt())
    }

    private fun iconFor(state: MotionState): String = when (state.motionType) {
        MotionType.IDLE -> "●" // ●
        MotionType.STEP, MotionType.WALKING -> "🚶" // 🚶
        MotionType.RUNNING -> "🏃" // 🏃
        MotionType.JUMPING -> "⬆" // ⬆
        MotionType.SQUATTING -> "⬇" // ⬇
        MotionType.ARMS_RAISED -> "🙌" // 🙌
        MotionType.RAISING -> when (state.direction) {
            Direction.UP -> "☝" // ☝
            Direction.DOWN -> "👇" // 👇
            Direction.LEFT -> "👈" // 👈
            else -> "👉" // 👉
        }
        MotionType.LEANING -> if (state.direction == Direction.LEFT) "⬅" else "➡" // ⬅ ➡
    }

    private inner class DragListener : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f
        private var dragging = false
        private var overTrash = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y
                    touchX = event.rawX; touchY = event.rawY
                    dragging = false
                    overTrash = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                        showTrash()
                    }
                    if (dragging) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        runCatching { windowManager.updateViewLayout(v, params) }
                        val over = isOverTrash(event.rawX, event.rawY)
                        if (over != overTrash) {
                            overTrash = over
                            trash?.setBackgroundResource(
                                if (over) R.drawable.trash_background_active else R.drawable.trash_background
                            )
                            trash?.scaleX = if (over) TRASH_HOVER_SCALE else 1f
                            trash?.scaleY = if (over) TRASH_HOVER_SCALE else 1f
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val dropped = dragging && isOverTrash(event.rawX, event.rawY)
                    hideTrash()
                    when {
                        // Posted: stopping removes this view, which shouldn't happen mid-dispatch.
                        dropped -> mainHandler.post { onDropOnTrash() }
                        !dragging -> v.performClick()
                    }
                    dragging = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    hideTrash()
                    dragging = false
                }
            }
            return true
        }
    }

    private companion object {
        const val TRASH_BOTTOM_MARGIN_DP = 48
        const val TRASH_SLACK_DP = 24
        const val TRASH_HOVER_SCALE = 1.2f

        fun overlayParams(flags: Int) = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
    }
}
