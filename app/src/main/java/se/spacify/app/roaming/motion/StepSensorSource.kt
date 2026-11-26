package se.spacify.app.roaming.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import se.spacify.app.roaming.BuildConfig

/**
 * Feeds the phone's motion sensors into a [StepCadenceClassifier]. No step history is stored; only
 * a few seconds of timestamps in memory.
 *
 * Steps come from two detectors:
 * - [FastStepDetector] on linear acceleration, which reports a step within a few samples of it
 *   happening. This is what keeps the delay between moving and the game reacting short.
 * - The hardware step detector, which on many phones only starts reporting after several steps
 *   and then delivers them late. It is only used while the fast detector isn't seeing steps (for
 *   example when the phone is held in a way that damps the bounce), so steps aren't counted twice.
 *
 * All sensors are registered with no batching (`maxReportLatencyUs = 0`), so the system delivers
 * each sample as soon as it is measured instead of collecting them in the sensor hub's FIFO.
 */
class StepSensorSource(context: Context, private val emitter: MotionEmitter) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val classifier = StepCadenceClassifier()
    private val fastSteps = FastStepDetector()
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            // Re-evaluate periodically so we fall back to IDLE when the user stops moving.
            emitter.submit(classifier.classify(now()), now())
            handler.postDelayed(this, TICK_MILLIS)
        }
    }

    fun start() {
        register(Sensor.TYPE_STEP_DETECTOR, SensorManager.SENSOR_DELAY_FASTEST)
        register(Sensor.TYPE_GRAVITY, SensorManager.SENSOR_DELAY_GAME)
        register(Sensor.TYPE_LINEAR_ACCELERATION, SensorManager.SENSOR_DELAY_GAME)
        handler.postDelayed(tick, TICK_MILLIS)
    }

    fun stop() {
        handler.removeCallbacks(tick)
        sensorManager.unregisterListener(this)
        fastSteps.reset()
    }

    private fun register(type: Int, samplingPeriod: Int) {
        sensorManager.getDefaultSensor(type)?.let {
            sensorManager.registerListener(this, it, samplingPeriod, NO_BATCHING, handler)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val now = now()
        when (event.sensor.type) {
            Sensor.TYPE_STEP_DETECTOR -> {
                if (BuildConfig.DEBUG) {
                    // How old the hardware's step is when it reaches us: shows the sensor hub's delay.
                    val ageMillis = (SystemClock.elapsedRealtimeNanos() - event.timestamp) / 1_000_000
                    Log.d(LATENCY_TAG, "hardware step arrived ${ageMillis}ms after it was detected")
                }
                // The fast detector already counted this step (usually well before this arrives).
                if (fastSteps.isActive(now, FAST_DETECTOR_ACTIVE_MILLIS)) return
                emitter.submit(classifier.onStep(now), now, pulse = true)
            }
            Sensor.TYPE_GRAVITY -> classifier.onGravity(event.values[0], event.values[1], event.values[2])
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val jumped = classifier.onLinearAcceleration(x, y, z, now)
                if (jumped) {
                    emitter.submit(classifier.classify(now), now, pulse = true)
                    return
                }
                val vertical = classifier.verticalAcceleration(x, y, z) ?: return
                if (fastSteps.onVerticalAcceleration(vertical, now)) {
                    emitter.submit(classifier.onStep(now), now, pulse = true)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun now() = SystemClock.elapsedRealtime()

    companion object {
        private const val TICK_MILLIS = 100L
        private const val NO_BATCHING = 0
        /** Hardware steps are ignored while the fast detector has seen a step this recently. */
        private const val FAST_DETECTOR_ACTIVE_MILLIS = 2_000L
        const val LATENCY_TAG = "RoamingLatency"

        fun isAvailable(context: Context): Boolean {
            val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            return manager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null ||
                manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) != null
        }
    }
}
