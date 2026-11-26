package se.spacify.app.roaming.gamepad

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONException
import org.json.JSONObject

/**
 * The user's gamepad settings, stored in app-private SharedPreferences: whether gamepad mode is on,
 * the motion-to-gamepad mapping, and the last host so Roaming can reconnect to it.
 *
 * The mapping is also exposed as a [StateFlow] so a running gamepad picks up edits immediately.
 */
object GamepadSettings {
    private const val PREFS = "gamepad"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LOCAL_ENABLED = "local_enabled"
    private const val KEY_MAPPING = "mapping"
    private const val KEY_LAST_HOST = "last_host"

    private val _mapping = MutableStateFlow(GamepadMapping.DEFAULT)
    val mapping: StateFlow<GamepadMapping> = _mapping.asStateFlow()
    private var loaded = false

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun load(context: Context): GamepadMapping {
        if (!loaded) {
            val raw = prefs(context).getString(KEY_MAPPING, null)
            _mapping.value = try {
                GamepadMapping.fromJson(raw?.let(::JSONObject))
            } catch (e: JSONException) {
                Log.w("GamepadSettings", "Ignoring unreadable gamepad mapping", e)
                GamepadMapping.DEFAULT
            }
            loaded = true
        }
        return _mapping.value
    }

    @Synchronized
    fun setMapping(context: Context, mapping: GamepadMapping) {
        _mapping.value = mapping
        loaded = true
        prefs(context).edit().putString(KEY_MAPPING, mapping.toJson().toString()).apply()
    }

    fun isEnabled(context: Context) = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()

    /** Whether the gamepad for apps on this phone (through Shizuku) is on. */
    fun isLocalEnabled(context: Context) = prefs(context).getBoolean(KEY_LOCAL_ENABLED, false)

    fun setLocalEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_LOCAL_ENABLED, enabled).apply()

    fun lastHost(context: Context): String? = prefs(context).getString(KEY_LAST_HOST, null)

    fun setLastHost(context: Context, address: String?) =
        prefs(context).edit().putString(KEY_LAST_HOST, address).apply()
}

/** State of the virtual gamepad, shown in Roaming's UI. */
sealed interface GamepadStatus {
    /** Gamepad mode is not running. */
    data object Off : GamepadStatus

    /** Gamepad mode can't run; [reason] says why. */
    data class Unavailable(val reason: Reason) : GamepadStatus

    /** Registering the gamepad with the phone's Bluetooth stack. */
    data object Starting : GamepadStatus

    /** Registered and waiting for a computer, TV or console to connect. */
    data object WaitingForHost : GamepadStatus

    /** A host is connected and receiving input. */
    data class Connected(val hostName: String) : GamepadStatus

    enum class Reason { OLD_ANDROID, NO_BLUETOOTH, BLUETOOTH_OFF, NO_PERMISSION, NOT_SUPPORTED }
}

/** State of the virtual gamepad for apps on this phone. */
sealed interface LocalGamepadStatus {
    data object Off : LocalGamepadStatus
    data object Starting : LocalGamepadStatus

    /** The virtual gamepad exists; apps on this phone receive its input. */
    data object Active : LocalGamepadStatus

    data class Unavailable(val reason: Reason) : LocalGamepadStatus

    /** Shizuku ran the service, but creating the device failed; [message] has the details. */
    data class Failed(val message: String) : LocalGamepadStatus

    enum class Reason { SHIZUKU_NOT_RUNNING, SHIZUKU_TOO_OLD, NO_PERMISSION }
}

/** In-process gamepad status, like [se.spacify.app.roaming.motion.MotionBus] for motion. */
object GamepadBus {
    private val _status = MutableStateFlow<GamepadStatus>(GamepadStatus.Off)
    val status: StateFlow<GamepadStatus> = _status.asStateFlow()

    fun setStatus(status: GamepadStatus) {
        _status.value = status
    }

    private val _localStatus = MutableStateFlow<LocalGamepadStatus>(LocalGamepadStatus.Off)
    val localStatus: StateFlow<LocalGamepadStatus> = _localStatus.asStateFlow()

    fun setLocalStatus(status: LocalGamepadStatus) {
        _localStatus.value = status
    }
}
