package se.spacify.app.roaming.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StepCadenceClassifierTest {
    @Test
    fun firstStepIsStep() {
        val c = StepCadenceClassifier()
        assertEquals(MotionType.STEP, c.onStep(0).motionType)
    }

    @Test
    fun slowStepsAreWalking() {
        val c = StepCadenceClassifier()
        var state = MotionState.IDLE
        for (i in 0..6) state = c.onStep(i * 600L) // 100 steps/min
        assertEquals(MotionType.WALKING, state.motionType)
        assertEquals(Direction.FORWARD, state.direction)
    }

    @Test
    fun fastStepsAreRunning() {
        val c = StepCadenceClassifier()
        var state = MotionState.IDLE
        for (i in 0..10) state = c.onStep(i * 350L) // ~171 steps/min
        assertEquals(MotionType.RUNNING, state.motionType)
    }

    @Test
    fun becomesIdleAfterSteppingStops() {
        val c = StepCadenceClassifier()
        for (i in 0..5) c.onStep(i * 500L)
        assertEquals(MotionState.IDLE, c.classify(10_000L))
    }

    @Test
    fun tiltSteers() {
        val c = StepCadenceClassifier()
        c.onGravity(-5f, 8.4f, 0f) // top of the phone tilted to the right
        for (i in 0..5) c.onStep(i * 500L)
        assertEquals(Direction.RIGHT, c.classify(2_600L).direction)
    }

    @Test
    fun verticalSpikeIsJump() {
        val c = StepCadenceClassifier()
        assertTrue(c.onLinearAcceleration(0f, 12f, 0f, 1_000L))
        assertEquals(MotionType.JUMPING, c.classify(1_100L).motionType)
        assertEquals(Direction.UP, c.classify(1_100L).direction)
    }

    @Test
    fun stoppingIsReportedAfterTwoMissedSteps() {
        val c = StepCadenceClassifier()
        for (i in 0..6) c.onStep(i * 500L) // last step at 3000, 500 ms apart
        assertEquals(MotionType.WALKING, c.classify(3_900L).motionType)
        assertEquals(MotionState.IDLE, c.classify(4_100L))
    }

    @Test
    fun aSingleStepWaitsLongerBeforeIdle() {
        val c = StepCadenceClassifier()
        c.onStep(0L)
        assertEquals(MotionType.STEP, c.classify(1_500L).motionType)
        assertEquals(MotionState.IDLE, c.classify(2_100L))
    }

    @Test
    fun verticalAccelerationFollowsGravity() {
        val c = StepCadenceClassifier()
        c.onGravity(0f, 0f, 9.81f) // phone flat on its back
        assertEquals(2f, c.verticalAcceleration(0f, 0f, 2f)!!, 0.001f)
        assertEquals(0f, c.verticalAcceleration(2f, 0f, 0f)!!, 0.001f)
    }
}
