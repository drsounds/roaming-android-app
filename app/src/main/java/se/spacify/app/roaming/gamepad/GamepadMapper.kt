package se.spacify.app.roaming.gamepad

import se.spacify.app.roaming.motion.MotionEvent
import se.spacify.app.roaming.motion.MotionState
import se.spacify.app.roaming.motion.MotionType
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Turns Roaming's [MotionEvent]s into a [GamepadState] using a [GamepadMapping].
 *
 * - Each input (step sensor, camera) keeps its own current motion; the gamepad shows both at once
 *   (buttons are combined, the strongest stick / trigger value wins).
 * - Every event is also held for [holdMillis], so short actions such as a single step or a quick
 *   jump always reach the host as at least one press, even if the motion has already ended.
 *
 * Pure Kotlin and thread safe, so it can be unit tested and fed from sensor and camera threads.
 */
class GamepadMapper(
    mapping: GamepadMapping = GamepadMapping.DEFAULT,
    private val holdMillis: Long = 150L,
) {
    @Volatile
    var mapping: GamepadMapping = mapping

    private val current = mutableMapOf<MotionEvent.Source, MotionState>()
    private val held = ArrayDeque<Pair<MotionState, Long>>()

    @Synchronized
    fun onEvent(event: MotionEvent, nowMillis: Long) {
        current[event.source] = event.new
        if (event.new.motionType != MotionType.IDLE) held.addLast(event.new to nowMillis + holdMillis)
    }

    @Synchronized
    fun reset() {
        current.clear()
        held.clear()
    }

    @Synchronized
    fun state(nowMillis: Long): GamepadState {
        while (held.isNotEmpty() && held.first().second < nowMillis) held.removeFirst()
        val active = current.values + held.map { it.first }
        val m = mapping

        var buttons = 0
        var left = Vec.ZERO
        var right = Vec.ZERO
        var dpad = Vec.ZERO
        var leftTrigger = 0f
        var rightTrigger = 0f
        for (motion in active) {
            if (motion.motionType == MotionType.IDLE) continue
            val control = m.controlFor(motion.motionType)
            control.button?.let { buttons = buttons or it.mask }
            when (control) {
                GamepadControl.LEFT_STICK -> left = strongest(left, stickVector(motion, m.stickFloor))
                GamepadControl.RIGHT_STICK -> right = strongest(right, stickVector(motion, m.stickFloor))
                GamepadControl.DPAD -> dpad = strongest(dpad, stickVector(motion, 1f))
                GamepadControl.LEFT_TRIGGER -> leftTrigger = max(leftTrigger, motion.intensity)
                GamepadControl.RIGHT_TRIGGER -> rightTrigger = max(rightTrigger, motion.intensity)
                else -> Unit
            }
        }
        return GamepadState(
            buttons = buttons,
            hat = Hat.fromVector(dpad.x, dpad.y),
            leftX = left.x, leftY = left.y,
            rightX = right.x, rightY = right.y,
            leftTrigger = leftTrigger, rightTrigger = rightTrigger,
        )
    }

    internal data class Vec(val x: Float, val y: Float) {
        val length: Float get() = sqrt(x * x + y * y)

        companion object {
            val ZERO = Vec(0f, 0f)
        }
    }

    companion object {
        private val LOCOMOTION = setOf(MotionType.STEP, MotionType.WALKING, MotionType.RUNNING)

        private fun strongest(a: Vec, b: Vec) = if (b.length > a.length) b else a

        /**
         * Stick deflection for a motion: its direction on the floor plane (y = forward), scaled to
         * `floor + (1 - floor) * intensity`. Up / down (jump, squat) count as forward / back, and
         * walking or running while steering left / right goes diagonally forward.
         */
        internal fun stickVector(motion: MotionState, floor: Float): Vec {
            if (motion.intensity <= 0f) return Vec.ZERO
            val d = motion.direction
            var x = d.x
            var y = d.z + d.y
            // Moving your legs always means "go": forward, or diagonally forward while steering.
            if (motion.motionType in LOCOMOTION && y == 0f) y = 1f
            val len = sqrt(x * x + y * y)
            if (len == 0f) return Vec.ZERO
            x /= len
            y /= len
            val magnitude = (floor + (1f - floor) * motion.intensity).coerceIn(0f, 1f)
            return Vec(x * magnitude, y * magnitude)
        }
    }
}
