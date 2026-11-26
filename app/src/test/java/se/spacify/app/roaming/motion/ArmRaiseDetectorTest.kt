package se.spacify.app.roaming.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArmRaiseDetectorTest {
    // Unmirrored front camera: the player's left side is on the image's right (larger x).
    private val nose = Point(200f, 150f)
    private val leftShoulder = Point(240f, 200f)
    private val rightShoulder = Point(160f, 200f)
    private val hipY = 300f
    private val torso = 100f
    private val restLeft = Point(260f, 300f)
    private val restRight = Point(140f, 300f)

    private fun ArmRaiseDetector.feed(left: Point?, right: Point?, now: Long, hips: Float? = hipY) =
        update(nose, leftShoulder, rightShoulder, left, right, hips, torso, now)

    /** Holds the pose for 300 ms and returns the last state. */
    private fun held(left: Point?, right: Point?, hips: Float? = hipY): MotionState? {
        val d = ArmRaiseDetector()
        var state: MotionState? = null
        for (t in 0L..300L step 50L) state = d.feed(left, right, t, hips)
        return state
    }

    @Test
    fun armsAtRestIsNothing() {
        assertNull(held(restLeft, restRight))
    }

    @Test
    fun oneArmUpIsRaisingUp() {
        val state = held(Point(250f, 60f), restRight)!!
        assertEquals(MotionType.RAISING, state.motionType)
        assertEquals(Direction.UP, state.direction)
        assertTrue(state.intensity > 0.5f)
    }

    @Test
    fun bothArmsUpIsLeftToArmsRaised() {
        assertNull(held(Point(250f, 60f), Point(150f, 60f)))
    }

    @Test
    fun leftArmOutIsRaisingLeft() {
        val state = held(Point(340f, 205f), restRight)!!
        assertEquals(MotionType.RAISING, state.motionType)
        assertEquals(Direction.LEFT, state.direction)
    }

    @Test
    fun rightArmOutIsRaisingRight() {
        val state = held(restLeft, Point(60f, 190f))!!
        assertEquals(Direction.RIGHT, state.direction)
    }

    @Test
    fun armAcrossTheBodyIsNotSideways() {
        // Left wrist reaching over to the image's left (the player's right side): not "out".
        assertNull(held(Point(100f, 200f), restRight))
    }

    @Test
    fun bothArmsOutIsAmbiguous() {
        assertNull(held(Point(340f, 200f), Point(60f, 200f)))
    }

    @Test
    fun bothHandsLowIsRaisingDown() {
        val state = held(Point(240f, 370f), Point(160f, 380f))!!
        assertEquals(Direction.DOWN, state.direction)
    }

    @Test
    fun downNeedsTheHips() {
        assertNull(held(Point(240f, 370f), Point(160f, 380f), hips = null))
    }

    @Test
    fun gestureMustBeHeld() {
        val d = ArmRaiseDetector(holdMillis = 250L)
        val up = Point(250f, 60f)
        assertNull(d.feed(up, restRight, 0))
        assertNull(d.feed(up, restRight, 200))
        assertEquals(Direction.UP, d.feed(up, restRight, 260)!!.direction)
        // Dropping the arm ends it, and raising again starts the hold over.
        assertNull(d.feed(restLeft, restRight, 300))
        assertNull(d.feed(up, restRight, 350))
    }

    @Test
    fun switchingDirectionRestartsTheHold() {
        val d = ArmRaiseDetector(holdMillis = 250L)
        d.feed(Point(250f, 60f), restRight, 0)
        assertEquals(Direction.UP, d.feed(Point(250f, 60f), restRight, 300)!!.direction)
        assertNull(d.feed(Point(340f, 205f), restRight, 350))
        assertEquals(Direction.LEFT, d.feed(Point(340f, 205f), restRight, 650)!!.direction)
    }
}
