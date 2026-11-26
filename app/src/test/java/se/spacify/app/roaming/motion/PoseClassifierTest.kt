package se.spacify.app.roaming.motion

import org.junit.Assert.assertEquals
import org.junit.Test

class PoseClassifierTest {
    /** A neutral standing pose with torso length 100 px, offset vertically by [dy]. */
    private fun standing(
        dy: Float = 0f,
        shoulderDx: Float = 0f,
        wristY: Float = 300f,
        leftKneeY: Float = 400f,
        rightKneeY: Float = 400f,
    ) = BodyPose(
        nose = Point(200f, 150f + dy),
        leftShoulder = Point(240f + shoulderDx, 200f + dy),
        rightShoulder = Point(160f + shoulderDx, 200f + dy),
        leftHip = Point(230f, 300f + dy),
        rightHip = Point(170f, 300f + dy),
        leftWrist = Point(260f, wristY + dy),
        rightWrist = Point(140f, wristY + dy),
        leftKnee = Point(230f, leftKneeY + dy),
        rightKnee = Point(170f, rightKneeY + dy),
    )

    @Test
    fun standingStillIsIdle() {
        val c = PoseClassifier()
        assertEquals(MotionState.IDLE, c.classify(standing(), 0).first)
    }

    @Test
    fun hipsAboveBaselineIsJump() {
        val c = PoseClassifier()
        c.classify(standing(), 0)
        val (state, _) = c.classify(standing(dy = -50f), 100)
        assertEquals(MotionType.JUMPING, state.motionType)
        assertEquals(Direction.UP, state.direction)
    }

    @Test
    fun hipsBelowBaselineIsSquat() {
        val c = PoseClassifier()
        c.classify(standing(), 0)
        assertEquals(MotionType.SQUATTING, c.classify(standing(dy = 60f), 100).first.motionType)
    }

    @Test
    fun wristsAboveHeadAreArmsRaised() {
        val c = PoseClassifier()
        assertEquals(MotionType.ARMS_RAISED, c.classify(standing(wristY = 100f), 0).first.motionType)
    }

    @Test
    fun leaningUsesUserPerspective() {
        val c = PoseClassifier(frontCamera = true)
        // Shoulders shifted toward image-left: in a front-camera image that's the user's right.
        assertEquals(Direction.RIGHT, c.classify(standing(shoulderDx = -40f), 0).first.direction)
        assertEquals(Direction.LEFT, c.classify(standing(shoulderDx = 40f), 50).first.direction)
    }

    @Test
    fun alternatingKneesAreSteps() {
        val c = PoseClassifier()
        var state = MotionState.IDLE
        var steps = 0
        for (i in 0 until 8) {
            val pose = if (i % 2 == 0) standing(leftKneeY = 350f) else standing(rightKneeY = 350f)
            val (s, stepped) = c.classify(pose, i * 500L)
            state = s
            if (stepped) steps++
        }
        assertEquals(8, steps)
        assertEquals(MotionType.WALKING, state.motionType)
    }

    @Test
    fun heldOneArmOutIsRaising() {
        val c = PoseClassifier()
        val pose = standing().copy(leftWrist = Point(340f, 205f))
        c.classify(pose, 0)
        val state = c.classify(pose, 300).first
        assertEquals(MotionType.RAISING, state.motionType)
        assertEquals(Direction.LEFT, state.direction)
    }

    @Test
    fun heldOneArmUpIsRaisingUp() {
        val c = PoseClassifier()
        val pose = standing().copy(rightWrist = Point(150f, 60f))
        c.classify(pose, 0)
        assertEquals(Direction.UP, c.classify(pose, 300).first.direction)
    }
}
