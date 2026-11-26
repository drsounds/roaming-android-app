package se.spacify.app.roaming.gamepad.local

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.util.Log
import rikka.shizuku.Shizuku
import se.spacify.app.roaming.BuildConfig
import se.spacify.app.roaming.gamepad.GamepadBus
import se.spacify.app.roaming.gamepad.GamepadReport
import se.spacify.app.roaming.gamepad.GamepadState
import se.spacify.app.roaming.gamepad.LocalGamepadStatus

/**
 * A virtual gamepad for apps on this phone.
 *
 * Android doesn't let an app send gamepad input to other apps, but the kernel can host a virtual
 * HID device. Roaming asks [Shizuku](https://shizuku.rikka.app) (which runs with ADB shell or root
 * privileges) to start [UhidGamepadService], which creates a gamepad through `/dev/uhid` with the
 * same HID layout as the Bluetooth gamepad. Android then sees a real gamepad and sends its button
 * and stick events to whichever app is in the foreground.
 */
class LocalGamepad(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private var service: IUhidGamepad? = null
    private var started = false
    private var bound = false

    private val args = Shizuku.UserServiceArgs(ComponentName(context.packageName, UhidGamepadService::class.java.name))
        .daemon(false)
        .tag("roaming-gamepad")
        .processNameSuffix("gamepad")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val binderReceived = Shizuku.OnBinderReceivedListener { if (started) bind() }
    private val binderDead = Shizuku.OnBinderDeadListener {
        service = null
        bound = false
        if (started) GamepadBus.setLocalStatus(LocalGamepadStatus.Unavailable(LocalGamepadStatus.Reason.SHIZUKU_NOT_RUNNING))
    }
    private val notRunning = Runnable {
        if (started && !Shizuku.pingBinder()) {
            GamepadBus.setLocalStatus(LocalGamepadStatus.Unavailable(LocalGamepadStatus.Reason.SHIZUKU_NOT_RUNNING))
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (!started || binder == null || !binder.pingBinder()) return
            val remote = IUhidGamepad.Stub.asInterface(binder)
            val error = try {
                remote.create(DEVICE_NAME, GamepadReport.DESCRIPTOR, Uhid.BUS_USB, VENDOR_ID, PRODUCT_ID)
            } catch (e: RemoteException) {
                e.toString()
            }
            if (error != null) {
                Log.w(TAG, "Could not create the virtual gamepad: $error")
                GamepadBus.setLocalStatus(LocalGamepadStatus.Failed(error))
                return
            }
            service = remote
            GamepadBus.setLocalStatus(LocalGamepadStatus.Active)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
            if (started) GamepadBus.setLocalStatus(LocalGamepadStatus.Failed("The gamepad service stopped"))
        }
    }

    fun start() {
        if (started) return
        started = true
        GamepadBus.setLocalStatus(LocalGamepadStatus.Starting)
        Shizuku.addBinderDeadListener(binderDead)
        // Called right away if Shizuku is already connected, or later when it starts.
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        handler.postDelayed(notRunning, SHIZUKU_WAIT_MILLIS)
    }

    fun stop() {
        if (!started) return
        started = false
        handler.removeCallbacks(notRunning)
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        try {
            service?.send(GamepadReport.REPORT_ID, GamepadState.NEUTRAL.toReport())
            service?.close()
        } catch (_: RemoteException) {
        }
        service = null
        if (bound && Shizuku.pingBinder()) {
            try {
                Shizuku.unbindUserService(args, connection, true)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Could not stop the gamepad service", e)
            }
        }
        bound = false
        GamepadBus.setLocalStatus(LocalGamepadStatus.Off)
    }

    /** Sends a new input report to the virtual gamepad. Main thread; never blocks (oneway). */
    fun send(report: ByteArray) {
        try {
            service?.send(GamepadReport.REPORT_ID, report)
        } catch (_: RemoteException) {
            service = null
        }
    }

    private fun bind() {
        if (bound) return
        handler.removeCallbacks(notRunning)
        val reason = LocalGamepadSupport.unavailableReason()
        if (reason != null) {
            GamepadBus.setLocalStatus(LocalGamepadStatus.Unavailable(reason))
            return
        }
        try {
            Shizuku.bindUserService(args, connection)
            bound = true
        } catch (e: RuntimeException) {
            GamepadBus.setLocalStatus(LocalGamepadStatus.Failed(e.message ?: e.toString()))
        }
    }

    companion object {
        private const val TAG = "LocalGamepad"
        private const val SHIZUKU_WAIT_MILLIS = 3_000L
        const val DEVICE_NAME = "Roaming Gamepad"

        // Not a registered USB ID: Android falls back to its generic gamepad key layout, which
        // matches GamepadReport's button order.
        const val VENDOR_ID = 0x0000
        const val PRODUCT_ID = 0x0000
    }
}

/** Shizuku checks for the "this phone" gamepad. Only call after Shizuku's binder is received. */
object LocalGamepadSupport {
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    fun isShizukuRunning(): Boolean = Shizuku.pingBinder()

    fun isShizukuInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun hasPermission(): Boolean =
        isShizukuRunning() && !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    fun unavailableReason(): LocalGamepadStatus.Reason? = when {
        !isShizukuRunning() -> LocalGamepadStatus.Reason.SHIZUKU_NOT_RUNNING
        Shizuku.isPreV11() -> LocalGamepadStatus.Reason.SHIZUKU_TOO_OLD
        Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> LocalGamepadStatus.Reason.NO_PERMISSION
        else -> null
    }
}
