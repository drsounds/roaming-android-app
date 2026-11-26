package se.spacify.app.roaming.motion

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sends each [MotionEvent] as the `se.spacify.app.roaming.event.MOTION` broadcast.
 *
 * Extras:
 * - `data`: JSON string `{ "new": {motion_type, direction, intensity, vector}, "prev": {...}, "timestamp", "source" }`
 * - flat convenience extras: `motion_type`, `direction`, `intensity`, `vector_x`, `vector_y`,
 *   `vector_z`, `prev_motion_type`, `prev_direction`, `prev_intensity`, `timestamp`, `source`.
 */
class MotionBroadcaster(private val context: Context) {

    fun send(event: MotionEvent) {
        val intent = Intent(ACTION_MOTION).apply {
            putExtra(EXTRA_DATA, event.toJson().toString())
            putExtra(MotionState.KEY_MOTION_TYPE, event.new.motionType.wire)
            putExtra(MotionState.KEY_DIRECTION, event.new.direction.wire)
            putExtra(MotionState.KEY_INTENSITY, event.new.intensity)
            putExtra("vector_x", event.new.vectorX)
            putExtra("vector_y", event.new.vectorY)
            putExtra("vector_z", event.new.vectorZ)
            putExtra("prev_motion_type", event.prev.motionType.wire)
            putExtra("prev_direction", event.prev.direction.wire)
            putExtra("prev_intensity", event.prev.intensity)
            putExtra("timestamp", event.timestampMillis)
            putExtra("source", event.source.wire)
        }
        context.sendBroadcast(intent)
        MotionBus.publish(event)
    }

    companion object {
        const val ACTION_MOTION = "se.spacify.app.roaming.event.MOTION"
        const val EXTRA_DATA = "data"
    }
}

/** In-process stream of the latest event, used by Roaming's own UI and overlay bubble. */
object MotionBus {
    private val _latest = MutableStateFlow<MotionEvent?>(null)
    val latest: StateFlow<MotionEvent?> = _latest.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun publish(event: MotionEvent) {
        _latest.value = event
    }

    fun setRunning(value: Boolean) {
        _running.value = value
        if (!value) _latest.value = null
    }
}
