package se.spacify.app.roaming.motion

import kotlin.math.abs
import kotlin.math.hypot

/** A 2D body landmark in image coordinates (y grows downward), already rotated upright. */
data class Point(val x: Float, val y: Float)

/** The handful of landmarks Roaming needs, decoupled from ML Kit so the logic is testable. */
data class BodyPose(
    val nose: Point,
    val leftShoulder: Point,
    val rightShoulder: Point,
    val leftHip: Point,
    val rightHip: Point,
    val leftWrist: Point,
    val rightWrist: Point,
    /** Knees are optional: when the user is close to the camera they are often out of frame. */
    val leftKnee: Point? = null,
    val rightKnee: Point? = null,
) {
    val shoulderCenter get() = mid(leftShoulder, rightShoulder)
    val hipCenter get() = mid(leftHip, rightHip)
    val torsoLength get() = hypot(shoulderCenter.x - hipCenter.x, shoulderCenter.y - hipCenter.y)

    private fun mid(a: Point, b: Point) = Point((a.x + b.x) / 2f, (a.y + b.y) / 2f)
}

/**
 * Classifies whole-body motion from a stream of poses seen by the front camera.
 *
 * All distances are normalised by torso length so the user can stand at any distance.
 * A slowly adapting baseline of hip height lets us tell jumps (hips above baseline) from
 * squats (hips below). Alternating knee lifts count as steps in place, giving WALKING/RUNNING.
 * Held arm gestures (one arm up, one arm out to a side, both hands down) give RAISING, see
 * [ArmRaiseDetector].
 */
class PoseClassifier(
    private val jumpThreshold: Float = 0.30f,
    private val squatThreshold: Float = 0.35f,
    private val leanThreshold: Float = 0.25f,
    private val kneeLiftThreshold: Float = 0.20f,
    private val baselineAlpha: Float = 0.05f,
    private val stepWindowMillis: Long = 3_000L,
    private val runningCadence: Float = 150f,
    private val maxCadence: Float = 220f,
    /** True when the image comes from a front camera and is not mirrored (ML Kit's default). */
    private val frontCamera: Boolean = true,
) {
    private var baselineHipY: Float? = null
    private var lastKneeSign = 0
    private val steps = ArrayDeque<Long>()
    private val raise = ArmRaiseDetector()

    /** Returns the classified state and whether a discrete step was detected on this frame. */
    fun classify(pose: BodyPose, nowMillis: Long): Pair<MotionState, Boolean> {
        val torso = pose.torsoLength
        if (torso < 1f) return MotionState.IDLE to false
        val hipY = pose.hipCenter.y
        val baseline = baselineHipY ?: hipY.also { baselineHipY = it }
        val rise = (baseline - hipY) / torso // >0 when hips are above baseline

        val stepped = trackKnees(pose, torso, nowMillis)
        val raising = raise.update(
            pose.nose, pose.leftShoulder, pose.rightShoulder, pose.leftWrist, pose.rightWrist,
            pose.hipCenter.y, torso, nowMillis,
        )

        val state = when {
            rise > jumpThreshold -> MotionState.of(MotionType.JUMPING, Direction.UP, rise)
            -rise > squatThreshold -> MotionState.of(MotionType.SQUATTING, Direction.DOWN, -rise)
            pose.leftWrist.y < pose.nose.y && pose.rightWrist.y < pose.nose.y ->
                MotionState.of(
                    MotionType.ARMS_RAISED, Direction.UP,
                    ((pose.nose.y - minOf(pose.leftWrist.y, pose.rightWrist.y)) / torso)
                )
            raising != null -> raising
            else -> {
                val lean = (pose.shoulderCenter.x - pose.hipCenter.x) / torso
                val cadence = cadence(nowMillis)
                when {
                    cadence > 0f -> MotionState.of(
                        if (cadence >= runningCadence) MotionType.RUNNING else MotionType.WALKING,
                        if (abs(lean) >= leanThreshold) leanDirection(lean) else Direction.FORWARD,
                        cadence / maxCadence,
                    )
                    abs(lean) >= leanThreshold -> MotionState.of(MotionType.LEANING, leanDirection(lean), abs(lean))
                    else -> MotionState.IDLE
                }
            }
        }

        // Only adapt the baseline while the user is roughly standing still, so a held squat
        // or a jump doesn't drag the reference with it.
        if (abs(rise) < jumpThreshold / 2) baselineHipY = baseline + (hipY - baseline) * baselineAlpha
        return state to stepped
    }

    fun reset() {
        baselineHipY = null
        lastKneeSign = 0
        steps.clear()
        raise.reset()
    }

    private fun leanDirection(lean: Float): Direction {
        // In an unmirrored front-camera image the user's right side appears on the image's left,
        // so shoulders shifting toward smaller x means the user leans to their right.
        val towardsImageLeft = lean < 0
        val userRight = if (frontCamera) towardsImageLeft else !towardsImageLeft
        return if (userRight) Direction.RIGHT else Direction.LEFT
    }

    private fun trackKnees(pose: BodyPose, torso: Float, nowMillis: Long): Boolean {
        val left = pose.leftKnee
        val right = pose.rightKnee
        if (left == null || right == null) return false
        val diff = (right.y - left.y) / torso
        val sign = when {
            diff > kneeLiftThreshold -> 1
            diff < -kneeLiftThreshold -> -1
            else -> 0
        }
        var stepped = false
        if (sign != 0 && sign != lastKneeSign) {
            steps.addLast(nowMillis)
            lastKneeSign = sign
            stepped = true
        }
        while (steps.isNotEmpty() && nowMillis - steps.first() > stepWindowMillis) steps.removeFirst()
        return stepped
    }

    private fun cadence(nowMillis: Long): Float {
        if (steps.size < 2 || nowMillis - steps.last() > 1_500L) return 0f
        val span = (steps.last() - steps.first()).coerceAtLeast(1L)
        return (steps.size - 1) * 60_000f / span
    }
}
