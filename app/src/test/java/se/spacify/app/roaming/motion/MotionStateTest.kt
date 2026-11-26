package se.spacify.app.roaming.motion

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionStateTest {
    @Test
    fun jsonRoundTrip() {
        val state = MotionState.of(MotionType.RUNNING, Direction.LEFT, 0.75f)
        val json = state.toJson()
        assertEquals("running", json.getString("motion_type"))
        assertEquals("left", json.getString("direction"))
        assertEquals(-0.75, json.getJSONObject("vector").getDouble("x"), 1e-6)
        assertEquals(state, MotionState.fromJson(json))
    }

    @Test
    fun intensityIsClamped() {
        assertEquals(1f, MotionState.of(MotionType.JUMPING, Direction.UP, 3f).intensity)
        assertEquals(0f, MotionState.of(MotionType.JUMPING, Direction.UP, -1f).intensity)
    }

    @Test
    fun eventHasNewAndPrev() {
        val event = MotionEvent(
            new = MotionState.of(MotionType.WALKING, Direction.FORWARD, 0.5f),
            prev = MotionState.IDLE,
            timestampMillis = 42L,
            source = MotionEvent.Source.STEP_SENSOR,
        )
        val json = event.toJson()
        assertEquals("walking", json.getJSONObject("new").getString("motion_type"))
        assertEquals("idle", json.getJSONObject("prev").getString("motion_type"))
    }
}
