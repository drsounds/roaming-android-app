package se.spacify.app.roaming.gamepad

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import se.spacify.app.roaming.motion.MotionEvent

/**
 * Presents the phone as a standard Bluetooth HID gamepad, using Android's [BluetoothHidDevice]
 * profile (Android 9+). Computers, TVs, tablets and other phones pair with it like any wireless
 * controller and see the [GamepadReport.DESCRIPTOR] layout.
 *
 * Motion events go into a [GamepadMapper]; a 50 Hz tick sends a new input report whenever the
 * resulting [GamepadState] changes.
 */
@RequiresApi(Build.VERSION_CODES.P)
@SuppressLint("MissingPermission") // Every entry point checks GamepadSupport.hasPermission() first.
class GamepadHidDevice(private val context: Context) {

    val mapper = GamepadMapper(GamepadSettings.load(context))

    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter
    private val handler = Handler(Looper.getMainLooper())
    private var hid: BluetoothHidDevice? = null
    private var host: BluetoothDevice? = null
    private var lastReport: ByteArray = GamepadState.NEUTRAL.toReport()
    private var started = false

    private val tick = object : Runnable {
        override fun run() {
            sendIfChanged()
            handler.postDelayed(this, TICK_MILLIS)
        }
    }

    /** Called from the sensor or camera thread for every motion event. */
    fun onMotion(event: MotionEvent) = mapper.onEvent(event, SystemClock.elapsedRealtime())

    fun start() {
        if (started) return
        started = true
        val unavailable = GamepadSupport.unavailableReason(context)
        if (unavailable != null) {
            GamepadBus.setStatus(GamepadStatus.Unavailable(unavailable))
            return
        }
        GamepadBus.setStatus(GamepadStatus.Starting)
        val ok = adapter!!.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
        if (!ok) GamepadBus.setStatus(GamepadStatus.Unavailable(GamepadStatus.Reason.NOT_SUPPORTED))
    }

    fun stop() {
        if (!started) return
        started = false
        handler.removeCallbacks(tick)
        hid?.let { proxy ->
            if (GamepadSupport.hasPermission(context)) {
                host?.let { proxy.sendReport(it, GamepadReport.REPORT_ID, GamepadState.NEUTRAL.toReport()) }
                host?.let { proxy.disconnect(it) }
                proxy.unregisterApp()
            }
            adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, proxy)
        }
        hid = null
        host = null
        mapper.reset()
        GamepadBus.setStatus(GamepadStatus.Off)
    }

    /** Connects to a host that is already paired with this phone. */
    fun connect(address: String) {
        val proxy = hid ?: return
        if (!GamepadSupport.hasPermission(context) || !BluetoothAdapter.checkBluetoothAddress(address)) return
        val device = adapter?.getRemoteDevice(address) ?: return
        host?.takeIf { it != device }?.let { proxy.disconnect(it) }
        proxy.connect(device)
    }

    private fun sendIfChanged() {
        val proxy = hid ?: return
        val target = host ?: return
        val report = mapper.state(SystemClock.elapsedRealtime()).toReport()
        if (report.contentEquals(lastReport)) return
        if (proxy.sendReport(target, GamepadReport.REPORT_ID, report)) lastReport = report
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (!started) {
                adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, proxy)
                return
            }
            val device = proxy as BluetoothHidDevice
            hid = device
            val sdp = BluetoothHidDeviceAppSdpSettings(
                SDP_NAME, SDP_DESCRIPTION, SDP_PROVIDER,
                BluetoothHidDevice.SUBCLASS2_GAMEPAD,
                GamepadReport.DESCRIPTOR,
            )
            val registered = device.registerApp(sdp, null, null, ContextCompat.getMainExecutor(context), callback)
            if (!registered) GamepadBus.setStatus(GamepadStatus.Unavailable(GamepadStatus.Reason.NOT_SUPPORTED))
        }

        override fun onServiceDisconnected(profile: Int) {
            hid = null
            host = null
            if (started) GamepadBus.setStatus(GamepadStatus.Unavailable(GamepadStatus.Reason.BLUETOOTH_OFF))
        }
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            if (!started) return
            if (!registered) {
                // Another app holds the HID device role, or Bluetooth went away.
                host = null
                handler.removeCallbacks(tick)
                GamepadBus.setStatus(GamepadStatus.Unavailable(GamepadStatus.Reason.NOT_SUPPORTED))
                return
            }
            GamepadBus.setStatus(GamepadStatus.WaitingForHost)
            handler.removeCallbacks(tick)
            handler.post(tick)
            val reconnect = pluggedDevice?.address ?: GamepadSettings.lastHost(context)
            reconnect?.let(::connect)
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            if (!started) return
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    host = device
                    lastReport = ByteArray(0) // force a fresh report to the new host
                    GamepadSettings.setLastHost(context, device.address)
                    GamepadBus.setStatus(GamepadStatus.Connected(device.name ?: device.address))
                }
                BluetoothProfile.STATE_DISCONNECTED -> if (device == host) {
                    host = null
                    GamepadBus.setStatus(GamepadStatus.WaitingForHost)
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            val proxy = hid ?: return
            if (type == BluetoothHidDevice.REPORT_TYPE_INPUT && id.toInt() == GamepadReport.REPORT_ID) {
                proxy.replyReport(device, type, id, mapper.state(SystemClock.elapsedRealtime()).toReport())
            } else {
                proxy.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID)
            }
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray?) {
            // The gamepad has no output or feature reports (no rumble, no LEDs).
            hid?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
        }
    }

    companion object {
        private const val TICK_MILLIS = 20L
        private const val SDP_NAME = "Roaming Gamepad"
        private const val SDP_DESCRIPTION = "Motion controlled gamepad"
        private const val SDP_PROVIDER = "Roaming"
    }
}

/** Checks that don't touch Android 9+ classes, so they are safe to call on any version. */
object GamepadSupport {
    /** Permissions gamepad mode needs at runtime on this Android version. */
    fun requiredPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) listOf(Manifest.permission.BLUETOOTH_CONNECT) else emptyList()

    fun hasPermission(context: Context) = requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun isSupported() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    /** Why gamepad mode can't start right now, or null if it can. */
    fun unavailableReason(context: Context): GamepadStatus.Reason? {
        if (!isSupported()) return GamepadStatus.Reason.OLD_ANDROID
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return GamepadStatus.Reason.NO_BLUETOOTH
        if (!hasPermission(context)) return GamepadStatus.Reason.NO_PERMISSION
        if (!adapter.isEnabled) return GamepadStatus.Reason.BLUETOOTH_OFF
        return null
    }
}
