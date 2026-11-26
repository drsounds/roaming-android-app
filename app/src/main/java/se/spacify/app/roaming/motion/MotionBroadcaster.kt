package se.spacify.app.roaming.motion

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sends each [MotionEvent] as the `se.spacify.app.roaming.event.MOTION` broadcast.
 *
 * Extras:
 * - `data`: JSON string `{ "new": {motion_type, direction, intensity, vector}, "prev": {...}, "timestamp", "source" }`
 * - flat convenience extras: `motion_type`, `direction`, `intensity`, `vector_x`, `vector_y`,
 *   `vector_z`, `prev_motion_type`, `prev_direction`, `prev_intensity`, `timestamp`, `sent_at`, `source`.
 *
 * The same extras are delivered to apps bound to [se.spacify.app.roaming.service.MotionLinkService].
 */
class MotionBroadcaster(private val context: Context) {

    fun send(event: MotionEvent) {
        val extras = toBundle(event)
        // Apps bound to MotionLinkService get the event first: a direct one-way Binder call,
        // with no trip through the system's broadcast queue.
        MotionClients.dispatch(extras)
        val intent = Intent(ACTION_MOTION)
            .putExtras(extras)
            // Use the foreground broadcast queue. Without this flag the broadcast waits in the
            // background queue behind every other app's broadcasts, which can add seconds.
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        context.sendBroadcast(intent)
        MotionBus.publish(event)
    }

    companion object {
        const val ACTION_MOTION = "se.spacify.app.roaming.event.MOTION"
        const val EXTRA_DATA = "data"
        const val EXTRA_TIMESTAMP = "timestamp"
        const val EXTRA_SENT_AT = "sent_at"

        /** The event's extras, shared by the broadcast and the bound service. */
        fun toBundle(event: MotionEvent): Bundle = Bundle().apply {
            putString(EXTRA_DATA, event.toJson().toString())
            putString(MotionState.KEY_MOTION_TYPE, event.new.motionType.wire)
            putString(MotionState.KEY_DIRECTION, event.new.direction.wire)
            putFloat(MotionState.KEY_INTENSITY, event.new.intensity)
            putFloat("vector_x", event.new.vectorX)
            putFloat("vector_y", event.new.vectorY)
            putFloat("vector_z", event.new.vectorZ)
            putString("prev_motion_type", event.prev.motionType.wire)
            putString("prev_direction", event.prev.direction.wire)
            putFloat("prev_intensity", event.prev.intensity)
            putLong(EXTRA_TIMESTAMP, event.timestampMillis)
            // When Roaming handed the event over. Both are SystemClock.elapsedRealtime(), the same
            // clock in every app, so a receiver can measure its delivery delay.
            putLong(EXTRA_SENT_AT, SystemClock.elapsedRealtime())
            putString("source", event.source.wire)
        }
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
        MotionClients.dispatchRunning(value)
    }
}
