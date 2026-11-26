package se.spacify.app.roaming.motion

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max

/**
 * The upper-body landmarks the front camera sees when only the head and shoulders are in frame:
 * a phone held in selfie mode, or mounted on the console of an elliptical, bike or rowing machine.
 * Elbows and wrists are optional because hands are often out of frame or holding the phone.
 */
data class UpperBodyPose(
    val nose: Point,
    val leftShoulder: Point,
    val rightShoulder: Point,
    val leftElbow: Point? = null,
    val rightElbow: Point? = null,
    val leftWrist: Point? = null,
    val rightWrist: Point? = null,
)

/**
 * Detects rhythmic exercise (elliptical, cross trainer, stepper, walking or running in place) from
 * the upper body alone, so the camera input works without hips, knees or feet in view.
 *
 * Two things are measured:
 * - **Intensity**: how fast the head, elbows and wrists move, smoothed over about a second.
 * - **Rhythm**: steps, counted from the side-to-side alternation of the arms, the head swaying,
 *   or the whole upper body bobbing. Whichever signal is currently strongest is used.
 *
 * Arm and head movement are measured in a body frame (origin between the shoulders, x along the
 * shoulder line, one unit = shoulder width). That frame moves, turns and scales with the image, so
 * it cancels out a hand-held camera moving, rotating or coming closer. The whole-body bob can only
 * be seen in image coordinates, where it can't be told apart from the camera moving, so it is
 * weighted by `cameraSteadiness` (1 = phone held still or mounted, 0 = phone moving a lot, see
 * [CameraSteadiness]).
 *
 * WALKING / RUNNING is only reported when there is both a rhythm and movement above the noise
 * floor, so talking with your hands or shifting in your seat stays IDLE.
 */
class UpperBodyTracker(
    /** Average body-frame speed (shoulder widths per second) that counts as no movement. */
    private val noiseFloor: Float = 0.3f,
    /** Average body-frame speed that counts as full intensity. */
    private val fullActivity: Float = 1.5f,
    private val activityTauMillis: Float = 800f,
    private val smoothingTauMillis: Float = 50f,
    private val stepWindowMillis: Long = 3_000L,
    private val minStepIntervalMillis: Long = 250L,
    private val runningCadence: Float = 150f,
    private val maxCadence: Float = 220f,
    /** Below this steadiness the whole-body bob is ignored (it's probably the camera moving). */
    private val minSteadinessForBob: Float = 0.5f,
) {
    private var lastAt: Long? = null
    private var activity = 0f
    private var width: Float? = null
    private val smoothed = HashMap<Feature, Point>()
    private var centre: Point? = null
    private var roll: Float? = null
    private val steps = ArrayDeque<Long>()

    private val armSwing = RhythmDetector(minHysteresis = 0.08f, stepOnBothCrossings = true)
    private val elbowSwing = RhythmDetector(minHysteresis = 0.06f, stepOnBothCrossings = true)
    private val headSway = RhythmDetector(minHysteresis = 0.03f, stepOnBothCrossings = true)
    private val bob = RhythmDetector(minHysteresis = 0.02f, stepOnBothCrossings = false)
    private val detectors = listOf(armSwing, elbowSwing, headSway, bob)

    /**
     * Feeds one frame. Returns the state and whether a step was detected on this frame.
     *
     * @param cameraSteadiness 0..1, how still the phone is (1 when mounted).
     */
    fun update(pose: UpperBodyPose, nowMillis: Long, cameraSteadiness: Float = 1f): Pair<MotionState, Boolean> {
        val dx = pose.leftShoulder.x - pose.rightShoulder.x
        val dy = pose.leftShoulder.y - pose.rightShoulder.y
        val w = hypot(dx, dy)
        if (w < 1f) return MotionState.IDLE to false

        val previous = lastAt
        if (previous != null && (nowMillis <= previous || nowMillis - previous > MAX_GAP_MILLIS)) {
            // Clock went backwards or we lost the body for a while: start over.
            reset()
        }
        val dt = lastAt?.let { (nowMillis - it).toFloat() }
        lastAt = nowMillis
        val steadiness = cameraSteadiness.coerceIn(0f, 1f)

        // Body frame: origin between the shoulders, ux along the shoulder line, vy perpendicular
        // (pointing down the image for an upright user), one unit = shoulder width.
        val ux = dx / w
        val uy = dy / w
        val ox = (pose.leftShoulder.x + pose.rightShoulder.x) / 2f
        val oy = (pose.leftShoulder.y + pose.rightShoulder.y) / 2f
        fun body(p: Point?): Point? = p?.let {
            val rx = it.x - ox
            val ry = it.y - oy
            Point((rx * ux + ry * uy) / w, (-rx * uy + ry * ux) / w)
        }

        val alpha = dt?.let { alpha(it, smoothingTauMillis) } ?: 1f
        val raw = mapOf(
            Feature.NOSE to body(pose.nose),
            Feature.LEFT_ELBOW to body(pose.leftElbow),
            Feature.RIGHT_ELBOW to body(pose.rightElbow),
            Feature.LEFT_WRIST to body(pose.leftWrist),
            Feature.RIGHT_WRIST to body(pose.rightWrist),
        )
        val speeds = ArrayList<Float>(raw.size)
        for ((feature, point) in raw) {
            if (point == null) {
                smoothed.remove(feature)
                continue
            }
            val before = smoothed[feature]
            val now = if (before == null) point else lerp(before, point, alpha)
            smoothed[feature] = now
            if (before != null && dt != null) speeds += dist(before, now) / dt * 1000f
        }

        // Whole-body movement in the image: translation and roll of the shoulder line.
        val smoothW = width?.let { it + (w - it) * alpha } ?: w
        width = smoothW
        val prevCentre = centre
        val newCentre = prevCentre?.let { lerp(it, Point(ox, oy), alpha) } ?: Point(ox, oy)
        centre = newCentre
        val angle = atan2(dy, dx)
        val prevRoll = roll
        val newRoll = prevRoll?.let { it + wrapAngle(angle - it) * alpha } ?: angle
        roll = newRoll
        var globalSpeed = 0f
        if (dt != null && prevCentre != null && prevRoll != null) {
            globalSpeed = (dist(prevCentre, newCentre) / smoothW + abs(wrapAngle(newRoll - prevRoll))) / dt * 1000f
        }

        if (dt != null && (speeds.isNotEmpty() || globalSpeed > 0f)) {
            val bodySpeed = if (speeds.isEmpty()) 0f else speeds.sum() / speeds.size
            val energy = bodySpeed + globalSpeed * steadiness
            activity += (energy - activity) * alpha(dt, activityTauMillis)
        }

        // Rhythm signals.
        val lw = smoothed[Feature.LEFT_WRIST]
        val rw = smoothed[Feature.RIGHT_WRIST]
        val le = smoothed[Feature.LEFT_ELBOW]
        val re = smoothed[Feature.RIGHT_ELBOW]
        val nose = smoothed[Feature.NOSE]
        val stepCandidates = HashMap<RhythmDetector, Boolean>()
        fun feed(d: RhythmDetector, value: Float?) {
            if (value == null) d.clear() else stepCandidates[d] = d.update(value, nowMillis, dt)
        }
        feed(armSwing, if (lw != null && rw != null) lw.y - rw.y else null)
        feed(elbowSwing, if (le != null && re != null) le.y - re.y else null)
        feed(headSway, nose?.x)
        feed(bob, if (steadiness >= minSteadinessForBob) newCentre.y / smoothW else null)

        val dominant = detectors.filter { it.score >= MIN_SCORE }.maxByOrNull { it.score }
        var stepped = false
        if (dominant != null && stepCandidates[dominant] == true) {
            val last = steps.lastOrNull()
            if (last == null || nowMillis - last >= minStepIntervalMillis) {
                steps.addLast(nowMillis)
                stepped = true
            }
        }
        while (steps.isNotEmpty() && nowMillis - steps.first() > stepWindowMillis) steps.removeFirst()

        val intensity = ((activity - noiseFloor) / (fullActivity - noiseFloor)).coerceIn(0f, 1f)
        val cadence = cadence(nowMillis)

        // Both wrists above the nose: the same gesture the full-body classifier reports.
        if (lw != null && rw != null && nose != null && lw.y < nose.y && rw.y < nose.y) {
            val lift = (nose.y - minOf(lw.y, rw.y)) / ARMS_RAISED_FULL
            return MotionState.of(MotionType.ARMS_RAISED, Direction.UP, lift) to false
        }
        if (cadence <= 0f || intensity <= 0f) return MotionState.IDLE to false
        val type = if (cadence >= runningCadence) MotionType.RUNNING else MotionType.WALKING
        return MotionState.of(type, Direction.FORWARD, max(intensity, cadence / maxCadence)) to stepped
    }

    /** Current smoothed movement level, 0..1 (exposed for the UI and tests). */
    val intensity: Float
        get() = ((activity - noiseFloor) / (fullActivity - noiseFloor)).coerceIn(0f, 1f)

    fun reset() {
        lastAt = null
        activity = 0f
        width = null
        smoothed.clear()
        centre = null
        roll = null
        steps.clear()
        detectors.forEach { it.clear() }
    }

    private fun cadence(nowMillis: Long): Float {
        if (steps.size < MIN_STEPS_FOR_RHYTHM || nowMillis - steps.last() > MAX_STEP_GAP_MILLIS) return 0f
        val span = (steps.last() - steps.first()).coerceAtLeast(1L)
        return (steps.size - 1) * 60_000f / span
    }

    private enum class Feature { NOSE, LEFT_ELBOW, RIGHT_ELBOW, LEFT_WRIST, RIGHT_WRIST }

    /**
     * Counts oscillations of one signal: subtracts a slowly moving baseline, then counts crossings
     * with hysteresis that scales with the signal's own amplitude. [score] is how far the signal
     * swings compared with its minimum hysteresis, i.e. how clearly it oscillates.
     */
    private class RhythmDetector(
        private val minHysteresis: Float,
        /** True when each half cycle is a step (alternating arms); false for one step per cycle (bob). */
        private val stepOnBothCrossings: Boolean,
        private val baselineTauMillis: Float = 1_500f,
        private val amplitudeTauMillis: Float = 1_000f,
    ) {
        private var baseline: Float? = null
        private var amplitude = 0f
        private var sign = 0

        val score: Float get() = amplitude / minHysteresis

        fun update(value: Float, nowMillis: Long, dt: Float?): Boolean {
            val base = baseline
            if (base == null || dt == null) {
                baseline = value
                return false
            }
            val newBase = base + (value - base) * alpha(dt, baselineTauMillis)
            baseline = newBase
            val d = value - newBase
            amplitude += (abs(d) - amplitude) * alpha(dt, amplitudeTauMillis)
            val hysteresis = max(minHysteresis, amplitude * 0.5f)
            val newSign = when {
                d > hysteresis -> 1
                d < -hysteresis -> -1
                else -> return false
            }
            val previous = sign
            sign = newSign
            if (previous == 0 || previous == newSign) return false
            return stepOnBothCrossings || newSign > 0
        }

        fun clear() {
            baseline = null
            amplitude = 0f
            sign = 0
        }
    }

    private companion object {
        const val MAX_GAP_MILLIS = 1_000L
        const val MAX_STEP_GAP_MILLIS = 1_500L
        const val MIN_STEPS_FOR_RHYTHM = 3
        const val MIN_SCORE = 1.5f

        /** Wrists this many shoulder widths above the nose give full intensity for ARMS_RAISED. */
        const val ARMS_RAISED_FULL = 1.5f

        fun alpha(dtMillis: Float, tauMillis: Float) = 1f - exp(-dtMillis / tauMillis)

        fun lerp(a: Point, b: Point, t: Float) = Point(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

        fun dist(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)

        fun wrapAngle(a: Float): Float {
            var r = a
            while (r > Math.PI) r -= (2 * Math.PI).toFloat()
            while (r < -Math.PI) r += (2 * Math.PI).toFloat()
            return r
        }
    }
}

/**
 * How still the phone is, from the gyroscope: 1 when mounted or held still, falling to 0 when it
 * turns faster than [shakyRadPerSec]. Turning the phone is what moves the image the most, so this
 * tells [UpperBodyTracker] when whole-body movement in the image can't be trusted.
 */
class CameraSteadiness(
    private val stillRadPerSec: Float = 0.2f,
    private val shakyRadPerSec: Float = 1.0f,
    private val tauMillis: Float = 400f,
) {
    private var speed = 0f
    private var lastAt: Long? = null

    @Volatile
    var value: Float = 1f
        private set

    @Synchronized
    fun onRotationRate(x: Float, y: Float, z: Float, nowMillis: Long) {
        val rate = kotlin.math.sqrt(x * x + y * y + z * z)
        val last = lastAt
        lastAt = nowMillis
        speed = if (last == null || nowMillis <= last) rate
        else speed + (rate - speed) * (1f - exp(-(nowMillis - last) / tauMillis))
        value = (1f - (speed - stillRadPerSec) / (shakyRadPerSec - stillRadPerSec)).coerceIn(0f, 1f)
    }

    @Synchronized
    fun reset() {
        speed = 0f
        lastAt = null
        value = 1f
    }
}
