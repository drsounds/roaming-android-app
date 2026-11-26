package se.spacify.app.roaming.motion

import kotlin.math.abs

/**
 * Turns a stream of raw [MotionState] samples into [MotionEvent]s.
 *
 * An event is emitted when the motion type or direction changes, when intensity moves by more than
 * [intensityThreshold], or when the caller reports a discrete [pulse] (a single step or jump), so
 * consumers get one event per physical action without being flooded at sensor rate.
 */
class MotionEmitter(
    private val source: MotionEvent.Source,
    private val intensityThreshold: Float = 0.15f,
    private val minIntervalMillis: Long = 50L,
    private val sink: (MotionEvent) -> Unit,
) {
    var current: MotionState = MotionState.IDLE
        private set
    private var lastEmitAt = Long.MIN_VALUE / 2

    @Synchronized
    fun submit(state: MotionState, nowMillis: Long, pulse: Boolean = false) {
        val changed = state.motionType != current.motionType || state.direction != current.direction
        val intensityMoved = abs(state.intensity - current.intensity) >= intensityThreshold
        if (!changed && !pulse && !intensityMoved) return
        // Throttle pure intensity jitter; always deliver real transitions and pulses.
        if (!changed && !pulse && nowMillis - lastEmitAt < minIntervalMillis) return
        val event = MotionEvent(new = state, prev = current, timestampMillis = nowMillis, source = source)
        current = state
        lastEmitAt = nowMillis
        sink(event)
    }

    @Synchronized
    fun reset(nowMillis: Long) {
        if (current != MotionState.IDLE) submit(MotionState.IDLE, nowMillis)
    }
}
