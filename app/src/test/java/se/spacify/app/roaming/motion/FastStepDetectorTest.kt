package se.spacify.app.roaming.motion

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class FastStepDetectorTest {
    /** Feeds a vertical acceleration signal at 50 Hz and returns the times of detected steps. */
    private fun run(d: FastStepDetector, millis: Long, signal: (Long) -> Float): List<Long> {
        val steps = ArrayList<Long>()
        var t = 0L
        while (t <= millis) {
            if (d.onVerticalAcceleration(signal(t), t)) steps += t
            t += 20
        }
        return steps
    }

    private fun bounce(t: Long, hz: Float, amplitude: Float) =
        (amplitude * sin(2 * PI * hz * t / 1000.0)).toFloat()

    @Test
    fun walkingBounceGivesOneStepPerCycle() {
        // 1.8 Hz, about 108 steps/min, a typical walking bounce.
        val steps = run(FastStepDetector(), 5_000) { bounce(it, 1.8f, 3f) }
        assertNear(9, steps.size, 1)
    }

    @Test
    fun firstStepIsReportedWithinHalfASecond() {
        val steps = run(FastStepDetector(), 3_000) { bounce(it, 1.8f, 3f) }
        assertTrue("first step at ${steps.firstOrNull()}", steps.first() <= 500)
    }

    @Test
    fun holdingThePhoneIsNotAStep() {
        // Hand tremor: small and fast.
        val steps = run(FastStepDetector(), 5_000) { bounce(it, 8f, 0.4f) + bounce(it, 3f, 0.3f) }
        assertTrue("steps $steps", steps.isEmpty())
    }

    @Test
    fun vibrationFasterThanStepsIsLimited() {
        val steps = run(FastStepDetector(), 2_000) { bounce(it, 10f, 4f) }
        for (i in 1 until steps.size) assertTrue(steps[i] - steps[i - 1] >= 250)
    }

    @Test
    fun activeOnlyRightAfterAStep() {
        val d = FastStepDetector()
        val steps = run(d, 1_000) { bounce(it, 1.8f, 3f) }
        val last = steps.last()
        assertTrue(d.isActive(last + 100, 2_000))
        assertFalse(d.isActive(last + 3_000, 2_000))
    }

    private fun assertNear(expected: Int, actual: Int, tolerance: Int) {
        assertTrue("expected $expected ± $tolerance, was $actual", kotlin.math.abs(expected - actual) <= tolerance)
    }
}
