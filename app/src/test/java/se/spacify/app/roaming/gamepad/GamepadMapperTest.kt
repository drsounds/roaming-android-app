package se.spacify.app.roaming.gamepad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import se.spacify.app.roaming.motion.Direction
import se.spacify.app.roaming.motion.MotionEvent
import se.spacify.app.roaming.motion.MotionState
import se.spacify.app.roaming.motion.MotionType

class GamepadMapperTest {
    private val mapper = GamepadMapper(GamepadMapping.DEFAULT, holdMillis = 150)

    private fun event(
        type: MotionType,
        direction: Direction,
        intensity: Float,
        source: MotionEvent.Source = MotionEvent.Source.STEP_SENSOR,
    ) = MotionEvent(MotionState.of(type, direction, intensity), MotionState.IDLE, 0, source)

    @Test
    fun idleIsNeutral() {
        assertEquals(GamepadState.NEUTRAL, mapper.state(0))
        mapper.onEvent(event(MotionType.IDLE, Direction.NONE, 0f), 0)
        assertEquals(GamepadState.NEUTRAL, mapper.state(10))
    }

    @Test
    fun walkingForwardPushesLeftStickUp() {
        mapper.onEvent(event(MotionType.WALKING, Direction.FORWARD, 0.5f), 0)
        val s = mapper.state(10)
        assertEquals(0f, s.leftX, 1e-4f)
        assertEquals(0.3f + 0.7f * 0.5f, s.leftY, 1e-4f)
        assertEquals(0, s.buttons)
    }

    @Test
    fun runningWhileSteeringGoesDiagonallyForward() {
        mapper.onEvent(event(MotionType.RUNNING, Direction.LEFT, 1f), 0)
        val s = mapper.state(10)
        assertTrue(s.leftX < -0.5f)
        assertTrue(s.leftY > 0.5f)
    }

    @Test
    fun leaningStandingStillSteersSideways() {
        mapper.onEvent(event(MotionType.LEANING, Direction.RIGHT, 0.5f), 0)
        val s = mapper.state(10)
        assertTrue(s.leftX > 0.5f)
        assertEquals(0f, s.leftY, 1e-4f)
    }

    @Test
    fun jumpPressesAAndIsHeldBriefly() {
        mapper.onEvent(event(MotionType.JUMPING, Direction.UP, 0.8f), 0)
        mapper.onEvent(event(MotionType.IDLE, Direction.NONE, 0f), 20)
        assertTrue(mapper.state(100).isPressed(GamepadButton.A))
        assertFalse(mapper.state(200).isPressed(GamepadButton.A))
    }

    @Test
    fun inputsAreCombined() {
        mapper.onEvent(event(MotionType.WALKING, Direction.FORWARD, 0.4f), 0)
        mapper.onEvent(event(MotionType.SQUATTING, Direction.DOWN, 0.6f, MotionEvent.Source.CAMERA), 0)
        val s = mapper.state(500)
        assertTrue(s.isPressed(GamepadButton.B))
        assertTrue(s.leftY > 0f)
    }

    @Test
    fun customMappingDrivesTriggersAndDpad() {
        mapper.mapping = GamepadMapping.DEFAULT
            .with(MotionType.RUNNING, GamepadControl.RIGHT_TRIGGER)
            .with(MotionType.LEANING, GamepadControl.DPAD)
        mapper.onEvent(event(MotionType.RUNNING, Direction.FORWARD, 0.8f), 0)
        mapper.onEvent(event(MotionType.LEANING, Direction.LEFT, 0.5f, MotionEvent.Source.CAMERA), 0)
        val s = mapper.state(500)
        assertEquals(0.8f, s.rightTrigger, 1e-4f)
        assertEquals(0f, s.leftY, 1e-4f)
        assertEquals(Hat.LEFT, s.hat)
    }

    @Test
    fun unboundMotionDoesNothing() {
        mapper.mapping = GamepadMapping.DEFAULT.with(MotionType.JUMPING, GamepadControl.NONE)
        mapper.onEvent(event(MotionType.JUMPING, Direction.UP, 1f), 0)
        assertEquals(GamepadState.NEUTRAL, mapper.state(10))
    }
}
