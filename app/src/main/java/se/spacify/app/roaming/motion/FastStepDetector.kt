package se.spacify.app.roaming.motion

import kotlin.math.exp

/**
 * Detects individual steps straight from the vertical component of linear acceleration, so a step
 * is reported within a few sensor samples of the foot landing.
 *
 * The hardware step detector (`TYPE_STEP_DETECTOR`) is accurate but slow to react: many phones
 * only start reporting after several consistent steps, and then deliver them late, which made the
 * first `walking` event arrive seconds after the user started moving. This detector reacts to the
 * first step and is used ahead of the hardware one (see [StepSensorSource]).
 *
 * Each step is one bounce: the smoothed vertical acceleration has to rise above [riseThreshold]
 * after having dropped below [fallThreshold] since the previous step, and steps closer than [minIntervalMillis] are
 * ignored. Holding the phone still, or the small shake of a hand, stays well below the thresholds.
 * Pure Kotlin so it can be unit tested without a device.
 */
class FastStepDetector(
    private val riseThreshold: Float = 1.2f,
    private val fallThreshold: Float = -0.4f,
    private val minIntervalMillis: Long = 250L,
    private val smoothingTauMillis: Float = 30f,
) {
    private var smoothed = 0f
    private var lastSampleAt: Long? = null
    // Starts armed so the very first bounce counts: waiting for a dip first would delay it a cycle.
    private var armed = true
    private var lastStepAt = Long.MIN_VALUE / 2

    /** Time of the last step this detector reported, or null if it hasn't seen one. */
    val lastStepMillis: Long?
        get() = lastStepAt.takeIf { it > Long.MIN_VALUE / 2 }

    /**
     * Feeds one sample of vertical linear acceleration in m/s² (positive = up).
     * Returns true when this sample completes a step.
     */
    fun onVerticalAcceleration(vertical: Float, nowMillis: Long): Boolean {
        val last = lastSampleAt
        lastSampleAt = nowMillis
        smoothed = if (last == null || nowMillis <= last) {
            vertical
        } else {
            smoothed + (vertical - smoothed) * (1f - exp(-(nowMillis - last) / smoothingTauMillis))
        }
        if (smoothed < fallThreshold) {
            armed = true
            return false
        }
        if (armed && smoothed > riseThreshold && nowMillis - lastStepAt >= minIntervalMillis) {
            armed = false
            lastStepAt = nowMillis
            return true
        }
        return false
    }

    /** True when this detector has reported a step within [withinMillis] of [nowMillis]. */
    fun isActive(nowMillis: Long, withinMillis: Long): Boolean = nowMillis - lastStepAt <= withinMillis

    fun reset() {
        smoothed = 0f
        lastSampleAt = null
        armed = true
        lastStepAt = Long.MIN_VALUE / 2
    }
}
