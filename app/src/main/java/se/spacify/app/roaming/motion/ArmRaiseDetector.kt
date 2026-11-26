package se.spacify.app.roaming.motion

import kotlin.math.abs
import kotlin.math.sign

/**
 * Camera-mode signal gestures, reported as [MotionType.RAISING] with a direction:
 *
 * - **up**: one arm raised straight up (one wrist above the head; both wrists is [MotionType.ARMS_RAISED])
 * - **left / right**: one arm stretched out to that side (the player's own left or right), the
 *   wrist roughly at shoulder height
 * - **down**: both hands reaching down, wrists well below the hips (needs the hips in view)
 *
 * The pose has to be held for [holdMillis] before it is reported, so arms swinging while walking or
 * running in place don't trigger it. Distances are in "units" (torso length for the full body), so
 * the player can stand at any distance. All coordinates are image coordinates (y grows downward)
 * or any frame with the same orientation.
 */
class ArmRaiseDetector(
    /** Wrist at least this many units outward from its own shoulder for left / right. */
    private val sideReach: Float = 0.8f,
    /** ...and within this many units of shoulder height. */
    private val sideBand: Float = 0.45f,
    /** Both wrists at least this many units below the hips for down. */
    private val downReach: Float = 0.5f,
    /** Wrist at least this many units above the nose for up. */
    private val upMargin: Float = 0.1f,
    private val holdMillis: Long = 250L,
) {
    private var candidate = Direction.NONE
    private var candidateIntensity = 0f
    private var since = 0L

    /**
     * Feeds one frame. Returns a [MotionType.RAISING] state once a gesture has been held for
     * [holdMillis], or null when there is none (yet).
     *
     * @param hipY image height of the hips, or null when they are out of view (no `down` then).
     * @param unit the length one unit stands for (torso length for the full body).
     */
    fun update(
        nose: Point,
        leftShoulder: Point,
        rightShoulder: Point,
        leftWrist: Point?,
        rightWrist: Point?,
        hipY: Float?,
        unit: Float,
        nowMillis: Long,
    ): MotionState? {
        if (unit <= 0f) return clear()
        val (direction, intensity) = detect(nose, leftShoulder, rightShoulder, leftWrist, rightWrist, hipY, unit)
        if (direction == Direction.NONE) return clear()
        if (direction != candidate) {
            candidate = direction
            since = nowMillis
        }
        candidateIntensity = intensity
        if (nowMillis - since < holdMillis) return null
        return MotionState.of(MotionType.RAISING, direction, candidateIntensity)
    }

    fun reset() {
        clear()
    }

    private fun clear(): MotionState? {
        candidate = Direction.NONE
        candidateIntensity = 0f
        since = 0L
        return null
    }

    private fun detect(
        nose: Point,
        leftShoulder: Point,
        rightShoulder: Point,
        leftWrist: Point?,
        rightWrist: Point?,
        hipY: Float?,
        unit: Float,
    ): Pair<Direction, Float> {
        // Up: exactly one wrist above the head.
        val leftUp = leftWrist != null && nose.y - leftWrist.y > upMargin * unit
        val rightUp = rightWrist != null && nose.y - rightWrist.y > upMargin * unit
        if (leftUp != rightUp) {
            val wrist = if (leftUp) leftWrist!! else rightWrist!!
            return Direction.UP to ((nose.y - wrist.y) / unit / UP_FULL)
        }
        if (leftUp && rightUp) return Direction.NONE to 0f // that's ARMS_RAISED

        // Left / right: one arm stretched out sideways. Which side is anatomical (ML Kit's left
        // wrist is the player's left wrist), so mirroring doesn't matter.
        val centreX = (leftShoulder.x + rightShoulder.x) / 2f
        fun outward(wrist: Point?, shoulder: Point): Float {
            if (wrist == null) return 0f
            val away = wrist.x - shoulder.x
            if (sign(away) != sign(shoulder.x - centreX)) return 0f
            if (abs(wrist.y - shoulder.y) > sideBand * unit) return 0f
            val reach = abs(away) / unit
            return if (reach >= sideReach) reach else 0f
        }
        val leftOut = outward(leftWrist, leftShoulder)
        val rightOut = outward(rightWrist, rightShoulder)
        if (leftOut > 0f && rightOut == 0f) return Direction.LEFT to (leftOut / SIDE_FULL)
        if (rightOut > 0f && leftOut == 0f) return Direction.RIGHT to (rightOut / SIDE_FULL)
        if (leftOut > 0f && rightOut > 0f) return Direction.NONE to 0f // both arms out: ambiguous

        // Down: both hands reaching well below the hips.
        if (hipY != null && leftWrist != null && rightWrist != null) {
            val lowest = minOf(leftWrist.y, rightWrist.y) - hipY // the higher of the two wrists
            if (lowest > downReach * unit) return Direction.DOWN to (lowest / unit / DOWN_FULL)
        }
        return Direction.NONE to 0f
    }

    private companion object {
        /** Units of wrist height above the nose that give full intensity for `up`. */
        const val UP_FULL = 0.8f
        /** Units of sideways reach that give full intensity for `left` / `right`. */
        const val SIDE_FULL = 1.2f
        /** Units below the hips that give full intensity for `down`. */
        const val DOWN_FULL = 1.0f
    }
}
