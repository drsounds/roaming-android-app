package se.spacify.app.roaming.gamepad

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import se.spacify.app.roaming.gamepad.local.LocalGamepad
import se.spacify.app.roaming.motion.MotionEvent

/**
 * Runs gamepad mode: maps motion to a [GamepadState] with a [GamepadMapper] and, 50 times a second,
 * sends a new input report to each enabled output when the state changed:
 *
 * - the Bluetooth gamepad ([GamepadHidDevice]) for other devices, and/or
 * - the virtual gamepad on this phone ([LocalGamepad], through Shizuku).
 */
class GamepadController(context: Context, useBluetooth: Boolean, useLocal: Boolean) {

    val mapper = GamepadMapper(GamepadSettings.load(context))

    private val bluetooth: GamepadHidDevice? =
        if (useBluetooth && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) GamepadHidDevice(context) else null
    private val local: LocalGamepad? = if (useLocal) LocalGamepad(context) else null
    private val handler = Handler(Looper.getMainLooper())
    private var lastReport = GamepadState.NEUTRAL.toReport()

    private val tick = object : Runnable {
        override fun run() {
            val report = mapper.state(SystemClock.elapsedRealtime()).toReport()
            if (!report.contentEquals(lastReport)) {
                lastReport = report
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) bluetooth?.send(report)
                local?.send(report)
            }
            handler.postDelayed(this, TICK_MILLIS)
        }
    }

    /** Called from the sensor or camera thread for every motion event. */
    fun onMotion(event: MotionEvent) = mapper.onEvent(event, SystemClock.elapsedRealtime())

    fun start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) bluetooth?.start()
        local?.start()
        handler.post(tick)
    }

    fun stop() {
        handler.removeCallbacks(tick)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) bluetooth?.stop()
        local?.stop()
        mapper.reset()
        lastReport = GamepadState.NEUTRAL.toReport()
    }

    /** Connects the Bluetooth gamepad to an already paired host. */
    fun connect(address: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) bluetooth?.connect(address)
    }

    private companion object {
        const val TICK_MILLIS = 20L
    }
}
