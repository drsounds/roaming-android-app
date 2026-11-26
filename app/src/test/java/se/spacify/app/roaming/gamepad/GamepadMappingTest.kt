package se.spacify.app.roaming.gamepad

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import se.spacify.app.roaming.motion.MotionType

class GamepadMappingTest {

    @Test
    fun roundTripsThroughJson() {
        val mapping = GamepadMapping.DEFAULT
            .with(MotionType.RUNNING, GamepadControl.RIGHT_TRIGGER)
            .with(MotionType.ARMS_RAISED, GamepadControl.START)
            .copy(stickFloor = 0.2f)
        val parsed = GamepadMapping.fromJson(JSONObject(mapping.toJson().toString()))
        assertEquals(GamepadControl.RIGHT_TRIGGER, parsed.controlFor(MotionType.RUNNING))
        assertEquals(GamepadControl.START, parsed.controlFor(MotionType.ARMS_RAISED))
        assertEquals(GamepadControl.A, parsed.controlFor(MotionType.JUMPING))
        assertEquals(0.2f, parsed.stickFloor, 1e-4f)
    }

    @Test
    fun missingOrUnknownEntriesFallBackToDefaults() {
        val json = JSONObject().put("bindings", JSONObject().put("jumping", "rocket").put("squatting", "x"))
        val parsed = GamepadMapping.fromJson(json)
        assertEquals(GamepadControl.A, parsed.controlFor(MotionType.JUMPING))
        assertEquals(GamepadControl.X, parsed.controlFor(MotionType.SQUATTING))
        assertEquals(GamepadControl.LEFT_STICK, parsed.controlFor(MotionType.WALKING))
        assertEquals(GamepadMapping.DEFAULT, GamepadMapping.fromJson(null))
    }

    @Test
    fun idleIsNeverBound() {
        assertEquals(GamepadControl.NONE, GamepadMapping.DEFAULT.controlFor(MotionType.IDLE))
    }
}
