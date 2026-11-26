package se.spacify.app.roaming.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Feeds the hardware step detector, gravity and linear acceleration sensors into a
 * [StepCadenceClassifier]. No step history is stored; only a few seconds of timestamps in memory.
 */
class StepSensorSource(context: Context, private val emitter: MotionEmitter) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val classifier = StepCadenceClassifier()
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            // Re-evaluate periodically so we fall back to IDLE when the user stops moving.
            emitter.submit(classifier.classify(now()), now())
            handler.postDelayed(this, TICK_MILLIS)
        }
    }

    fun start() {
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        handler.postDelayed(tick, TICK_MILLIS)
    }

    fun stop() {
        handler.removeCallbacks(tick)
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val now = now()
        when (event.sensor.type) {
            Sensor.TYPE_STEP_DETECTOR -> emitter.submit(classifier.onStep(now), now, pulse = true)
            Sensor.TYPE_GRAVITY -> classifier.onGravity(event.values[0], event.values[1], event.values[2])
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val jumped = classifier.onLinearAcceleration(event.values[0], event.values[1], event.values[2], now)
                if (jumped) emitter.submit(classifier.classify(now), now, pulse = true)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun now() = SystemClock.elapsedRealtime()

    companion object {
        private const val TICK_MILLIS = 250L

        fun isAvailable(context: Context): Boolean {
            val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            return manager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null
        }
    }
}
