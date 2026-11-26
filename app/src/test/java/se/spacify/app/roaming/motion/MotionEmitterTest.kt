package se.spacify.app.roaming.motion

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionEmitterTest {
    private val events = mutableListOf<MotionEvent>()
    private val emitter = MotionEmitter(MotionEvent.Source.CAMERA) { events += it }

    @Test
    fun emitsOnlyOnChange() {
        val walking = MotionState.of(MotionType.WALKING, Direction.FORWARD, 0.5f)
        emitter.submit(walking, 0)
        emitter.submit(walking, 100)
        emitter.submit(walking.copy(intensity = 0.55f), 200)
        assertEquals(1, events.size)
        assertEquals(MotionState.IDLE, events[0].prev)
        assertEquals(walking, events[0].new)
    }

    @Test
    fun pulsesAlwaysEmit() {
        val walking = MotionState.of(MotionType.WALKING, Direction.FORWARD, 0.5f)
        emitter.submit(walking, 0, pulse = true)
        emitter.submit(walking, 10, pulse = true)
        assertEquals(2, events.size)
        assertEquals(walking, events[1].prev)
    }

    @Test
    fun largeIntensityChangeEmits() {
        emitter.submit(MotionState.of(MotionType.WALKING, Direction.FORWARD, 0.3f), 0)
        emitter.submit(MotionState.of(MotionType.WALKING, Direction.FORWARD, 0.6f), 500)
        assertEquals(2, events.size)
    }
}
