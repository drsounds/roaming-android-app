package se.spacify.app.roaming.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class UpperBodyTrackerTest {
    private val frameMillis = 33L

    /**
     * A head-and-shoulders pose with shoulder width 100 px, as seen by a front camera.
     * [armPhase] swings the arms in opposite directions (elliptical handles), [bob] moves the whole
     * upper body vertically, and the camera transform ([shiftX], [shiftY], [rotation], [scale])
     * moves every landmark the way a hand-held phone would.
     */
    private fun pose(
        armSwing: Float = 0f,
        bob: Float = 0f,
        wristY: Float = 330f,
        withArms: Boolean = true,
        jitter: Random? = null,
        jitterPx: Float = 0.5f,
        shiftX: Float = 0f,
        shiftY: Float = 0f,
        rotation: Float = 0f,
        scale: Float = 1f,
    ): UpperBodyPose {
        fun p(x: Float, y: Float): Point {
            val jx = jitter?.let { (it.nextFloat() * 2 - 1) * jitterPx } ?: 0f
            val jy = jitter?.let { (it.nextFloat() * 2 - 1) * jitterPx } ?: 0f
            // Camera transform around the image centre (200, 250).
            val rx = (x + jx - 200f) * scale
            val ry = (y + jy + bob - 250f) * scale
            return Point(
                200f + rx * cos(rotation) - ry * sin(rotation) + shiftX,
                250f + rx * sin(rotation) + ry * cos(rotation) + shiftY,
            )
        }
        return UpperBodyPose(
            nose = p(200f, 150f),
            leftShoulder = p(250f, 220f),
            rightShoulder = p(150f, 220f),
            leftElbow = if (withArms) p(265f, 280f + armSwing / 2) else null,
            rightElbow = if (withArms) p(135f, 280f - armSwing / 2) else null,
            leftWrist = if (withArms) p(255f, wristY + armSwing) else null,
            rightWrist = if (withArms) p(145f, wristY - armSwing) else null,
        )
    }

    private data class Run(val last: MotionState, val steps: Int, val types: Set<MotionType>)

    private fun run(
        tracker: UpperBodyTracker = UpperBodyTracker(),
        seconds: Float = 6f,
        steadiness: Float = 1f,
        make: (tSeconds: Float) -> UpperBodyPose,
    ): Run {
        var last = MotionState.IDLE
        var steps = 0
        val types = mutableSetOf<MotionType>()
        val frames = (seconds * 1000 / frameMillis).toInt()
        for (i in 0 until frames) {
            val now = i * frameMillis
            val (state, stepped) = tracker.update(make(now / 1000f), now, steadiness)
            if (stepped) steps++
            last = state
            if (now > 3_000) types += state.motionType
        }
        return Run(last, steps, types)
    }

    private fun swing(t: Float, hz: Float, amplitude: Float) = (amplitude * sin(2 * PI * hz * t)).toFloat()

    @Test
    fun stillUserWithLandmarkJitterIsIdle() {
        val rnd = Random(1)
        val r = run { pose(jitter = rnd) }
        assertEquals(setOf(MotionType.IDLE), r.types)
        assertEquals(0, r.steps)
    }

    @Test
    fun alternatingArmsAreWalking() {
        // One full arm cycle per second = two steps per second = 120 steps/min.
        val r = run { t -> pose(armSwing = swing(t, 1f, 40f), jitter = Random(2)) }
        assertEquals(MotionType.WALKING, r.last.motionType)
        assertEquals(Direction.FORWARD, r.last.direction)
        assertTrue("intensity ${r.last.intensity}", r.last.intensity > 0.3f)
        assertTrue("steps ${r.steps}", r.steps in 8..13)
    }

    @Test
    fun fastArmsAreRunning() {
        // 1.4 cycles per second = 168 steps/min.
        val r = run { t -> pose(armSwing = swing(t, 1.4f, 40f)) }
        assertEquals(MotionType.RUNNING, r.last.motionType)
    }

    @Test
    fun harderMovementGivesHigherIntensity() {
        val gentle = run { t -> pose(armSwing = swing(t, 1f, 20f)) }.last
        val hard = run { t -> pose(armSwing = swing(t, 1f, 60f)) }.last
        assertEquals(MotionType.WALKING, gentle.motionType)
        assertTrue("gentle ${gentle.intensity} hard ${hard.intensity}", hard.intensity > gentle.intensity)
    }

    @Test
    fun handHeldCameraMovingAloneIsIdle() {
        // The phone wanders, turns and comes closer, rhythmically, while the user sits still.
        val r = run(steadiness = 0f) { t ->
            pose(
                shiftX = swing(t, 1f, 60f),
                shiftY = swing(t, 2f, 40f),
                rotation = swing(t, 0.8f, 0.2f),
                scale = 1f + swing(t, 0.5f, 0.2f),
            )
        }
        assertEquals(setOf(MotionType.IDLE), r.types)
    }

    @Test
    fun armsAreStillDetectedWhileTheCameraMoves() {
        val r = run(steadiness = 0f) { t ->
            pose(
                armSwing = swing(t, 1f, 40f),
                shiftX = swing(t, 0.7f, 50f),
                rotation = swing(t, 0.5f, 0.15f),
                scale = 1f + swing(t, 0.3f, 0.15f),
            )
        }
        assertEquals(MotionType.WALKING, r.last.motionType)
    }

    @Test
    fun headBobWithoutHandsCountsWhenThePhoneIsMounted() {
        // Hands out of frame; the upper body bobs once per step, 2 steps per second.
        val mounted = run(steadiness = 1f) { t -> pose(withArms = false, bob = swing(t, 2f, 8f)) }
        assertEquals(MotionType.WALKING, mounted.last.motionType)
        // The same image motion from a hand-held phone can't be told apart from camera shake.
        val handHeld = run(steadiness = 0f) { t -> pose(withArms = false, bob = swing(t, 2f, 8f)) }
        assertEquals(setOf(MotionType.IDLE), handHeld.types)
    }

    @Test
    fun stoppingReturnsToIdle() {
        val tracker = UpperBodyTracker()
        run(tracker, seconds = 4f) { t -> pose(armSwing = swing(t, 1f, 40f)) }
        var state = MotionState.IDLE
        for (i in 0 until 90) {
            val now = 4_000L + i * frameMillis
            state = tracker.update(pose(), now).first
        }
        assertEquals(MotionType.IDLE, state.motionType)
    }

    @Test
    fun wristsAboveTheHeadAreArmsRaised() {
        val (state, _) = UpperBodyTracker().update(pose(wristY = 100f), 0)
        assertEquals(MotionType.ARMS_RAISED, state.motionType)
        assertEquals(Direction.UP, state.direction)
    }

    @Test
    fun heldArmOutToTheSideIsRaising() {
        val tracker = UpperBodyTracker()
        val out = pose().copy(leftWrist = Point(390f, 225f), leftElbow = Point(320f, 222f))
        var state = MotionState.IDLE
        for (i in 0 until 12) state = tracker.update(out, i * frameMillis).first
        assertEquals(MotionType.RAISING, state.motionType)
        assertEquals(Direction.LEFT, state.direction)
    }

    @Test
    fun heldOneArmUpIsRaisingUp() {
        val tracker = UpperBodyTracker()
        val up = pose().copy(rightWrist = Point(140f, 60f))
        var state = MotionState.IDLE
        for (i in 0 until 12) state = tracker.update(up, i * frameMillis).first
        assertEquals(MotionType.RAISING, state.motionType)
        assertEquals(Direction.UP, state.direction)
    }

    @Test
    fun cameraSteadinessFollowsRotationRate() {
        val s = CameraSteadiness()
        for (i in 0 until 20) s.onRotationRate(0.01f, 0.02f, 0f, i * 20L)
        assertEquals(1f, s.value, 0.001f)
        for (i in 20 until 120) s.onRotationRate(1.5f, 0.5f, 0.2f, i * 20L)
        assertEquals(0f, s.value, 0.001f)
    }
}
