package se.spacify.app.roaming.motion

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Classifies motion from the phone's own sensors: the hardware step detector plus gravity and
 * linear acceleration. Pure Kotlin so it can be unit tested without a device.
 *
 * - Steps per minute over a sliding window decide WALKING vs RUNNING; intensity follows cadence.
 * - Device tilt (gravity's x axis, phone held upright in portrait) steers LEFT / RIGHT.
 * - A strong vertical spike in linear acceleration is a JUMP.
 */
class StepCadenceClassifier(
    private val windowMillis: Long = 4_000L,
    /** Longest time without a step before going IDLE (used until there is a step rhythm). */
    private val idleAfterMillis: Long = 2_000L,
    /** Shortest time without a step before going IDLE, for fast cadences. */
    private val minIdleAfterMillis: Long = 700L,
    /** With a rhythm, go IDLE after this many missed step intervals. */
    private val idleAfterIntervals: Float = 2f,
    private val runningCadence: Float = 140f,
    private val maxCadence: Float = 200f,
    private val tiltThreshold: Float = 0.35f,
    private val jumpThreshold: Float = 6f,
    private val jumpHoldMillis: Long = 400L,
) {
    private val steps = ArrayDeque<Long>()
    private var gravity = floatArrayOf(0f, 9.81f, 0f)
    private var jumpUntil = Long.MIN_VALUE
    private var jumpIntensity = 0f

    /** Steps per minute over the recent window. */
    fun cadence(nowMillis: Long): Float {
        prune(nowMillis)
        if (steps.size < 2) return if (steps.isEmpty()) 0f else 60_000f / windowMillis
        val span = (steps.last() - steps.first()).coerceAtLeast(1L)
        return (steps.size - 1) * 60_000f / span
    }

    fun onStep(nowMillis: Long): MotionState {
        steps.addLast(nowMillis)
        return classify(nowMillis)
    }

    fun onGravity(x: Float, y: Float, z: Float) {
        gravity = floatArrayOf(x, y, z)
    }

    /**
     * Component of linear acceleration along "up", in m/s², or null before gravity is known.
     * Up is the axis of the gravity sensor's reading, which points up when the device is at rest.
     */
    fun verticalAcceleration(x: Float, y: Float, z: Float): Float? {
        val g = gravity
        val norm = sqrt(g[0] * g[0] + g[1] * g[1] + g[2] * g[2]).takeIf { it > 0.1f } ?: return null
        return (x * g[0] + y * g[1] + z * g[2]) / norm
    }

    /** Returns true when this sample starts a jump. */
    fun onLinearAcceleration(x: Float, y: Float, z: Float, nowMillis: Long): Boolean {
        val vertical = verticalAcceleration(x, y, z) ?: return false
        if (vertical > jumpThreshold && nowMillis > jumpUntil) {
            jumpUntil = nowMillis + jumpHoldMillis
            jumpIntensity = ((vertical - jumpThreshold) / (jumpThreshold * 2) + 0.4f).coerceIn(0f, 1f)
            return true
        }
        return false
    }

    fun classify(nowMillis: Long): MotionState {
        if (nowMillis <= jumpUntil) return MotionState.of(MotionType.JUMPING, Direction.UP, jumpIntensity)
        prune(nowMillis)
        val lastStep = steps.lastOrNull()
        if (lastStep == null || nowMillis - lastStep > idleAfter()) {
            val tilt = tiltDirection()
            return if (tilt != Direction.NONE) {
                MotionState.of(MotionType.LEANING, tilt, tiltAmount())
            } else {
                MotionState.IDLE
            }
        }
        val direction0 = tiltDirection().takeIf { it != Direction.NONE } ?: Direction.FORWARD
        if (steps.size < 2) return MotionState.of(MotionType.STEP, direction0, 0.3f)
        val cadence = cadence(nowMillis)
        val type = if (cadence >= runningCadence) MotionType.RUNNING else MotionType.WALKING
        return MotionState.of(type, direction0, cadence / maxCadence)
    }

    /**
     * How long without a step means the user stopped. Once there is a rhythm this follows the
     * cadence (two missed steps), so stopping is reported quickly when walking or running, while
     * a single step or a very slow walk still gets the full [idleAfterMillis].
     */
    private fun idleAfter(): Long {
        if (steps.size < 2) return idleAfterMillis
        val interval = steps.last() - steps[steps.size - 2]
        return (interval * idleAfterIntervals).toLong().coerceIn(minIdleAfterMillis, idleAfterMillis)
    }

    private fun tiltAmount(): Float {
        val g = gravity
        val norm = sqrt(g[0] * g[0] + g[1] * g[1] + g[2] * g[2]).coerceAtLeast(0.1f)
        return abs(g[0] / norm)
    }

    private fun tiltDirection(): Direction {
        val g = gravity
        val norm = sqrt(g[0] * g[0] + g[1] * g[1] + g[2] * g[2]).coerceAtLeast(0.1f)
        val tx = g[0] / norm
        return when {
            // Tilting the top of the phone to the right makes gravity's x reading negative.
            tx <= -tiltThreshold -> Direction.RIGHT
            tx >= tiltThreshold -> Direction.LEFT
            else -> Direction.NONE
        }
    }

    private fun prune(nowMillis: Long) {
        while (steps.isNotEmpty() && nowMillis - steps.first() > windowMillis) steps.removeFirst()
    }
}
