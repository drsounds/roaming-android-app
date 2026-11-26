package se.spacify.app.roaming.gamepad

import org.json.JSONObject
import se.spacify.app.roaming.motion.MotionType

/**
 * Buttons of the virtual gamepad, in HID button order (button 1 = bit 0).
 *
 * The order follows the Linux / Android gamepad layout (BTN_SOUTH, BTN_EAST, BTN_C, BTN_NORTH,
 * BTN_WEST, BTN_Z, BTN_TL, BTN_TR, BTN_TL2, BTN_TR2, BTN_SELECT, BTN_START, BTN_MODE, BTN_THUMBL,
 * BTN_THUMBR), so Android, Android TV, ChromeOS and Linux hosts label the buttons correctly without
 * any remapping. Windows and Steam show them as "Button 1..16" and can remap them.
 */
enum class GamepadButton(val bit: Int) {
    A(0), B(1), C(2), X(3), Y(4), Z(5), L1(6), R1(7), L2(8), R2(9),
    SELECT(10), START(11), MODE(12), L3(13), R3(14);

    val mask: Int get() = 1 shl bit
}

/**
 * What a motion type drives on the gamepad.
 *
 * Direction-driven controls ([LEFT_STICK], [RIGHT_STICK], [DPAD]) follow the motion's direction:
 * forward pushes the stick up, left/right push it sideways, and walking or running while steering
 * pushes it diagonally forward so the player keeps moving. Their deflection follows intensity.
 * Triggers follow intensity. Buttons are held while the motion lasts (at least a short tap).
 */
enum class GamepadControl(val wire: String, val button: GamepadButton? = null) {
    NONE("none"),
    LEFT_STICK("left_stick"),
    RIGHT_STICK("right_stick"),
    DPAD("dpad"),
    LEFT_TRIGGER("left_trigger"),
    RIGHT_TRIGGER("right_trigger"),
    A("a", GamepadButton.A),
    B("b", GamepadButton.B),
    X("x", GamepadButton.X),
    Y("y", GamepadButton.Y),
    L1("l1", GamepadButton.L1),
    R1("r1", GamepadButton.R1),
    L3("l3", GamepadButton.L3),
    R3("r3", GamepadButton.R3),
    SELECT("select", GamepadButton.SELECT),
    START("start", GamepadButton.START);

    companion object {
        fun fromWire(value: String?): GamepadControl? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * Which gamepad control each [MotionType] drives.
 *
 * @param stickFloor minimum stick deflection for any detected movement, so slow walking still gets
 *   past a game's dead zone. Deflection is `stickFloor + (1 - stickFloor) * intensity`.
 */
data class GamepadMapping(
    val bindings: Map<MotionType, GamepadControl>,
    val stickFloor: Float = DEFAULT_STICK_FLOOR,
) {
    fun controlFor(type: MotionType): GamepadControl = bindings[type] ?: GamepadControl.NONE

    fun with(type: MotionType, control: GamepadControl) = copy(bindings = bindings + (type to control))

    fun toJson(): JSONObject {
        val b = JSONObject()
        for ((type, control) in bindings) b.put(type.wire, control.wire)
        return JSONObject().put(KEY_BINDINGS, b).put(KEY_STICK_FLOOR, stickFloor.toDouble())
    }

    companion object {
        const val DEFAULT_STICK_FLOOR = 0.3f
        private const val KEY_BINDINGS = "bindings"
        private const val KEY_STICK_FLOOR = "stick_floor"

        /** Motion types the user can bind. IDLE always means "nothing pressed". */
        val BINDABLE: List<MotionType> = MotionType.entries.filter { it != MotionType.IDLE }

        val DEFAULT = GamepadMapping(
            mapOf(
                MotionType.STEP to GamepadControl.LEFT_STICK,
                MotionType.WALKING to GamepadControl.LEFT_STICK,
                MotionType.RUNNING to GamepadControl.LEFT_STICK,
                MotionType.LEANING to GamepadControl.LEFT_STICK,
                MotionType.JUMPING to GamepadControl.A,
                MotionType.SQUATTING to GamepadControl.B,
                MotionType.ARMS_RAISED to GamepadControl.Y,
            )
        )

        /** Parses a stored mapping; unknown or missing entries fall back to [DEFAULT]. */
        fun fromJson(json: JSONObject?): GamepadMapping {
            if (json == null) return DEFAULT
            val stored = json.optJSONObject(KEY_BINDINGS)
            val bindings = BINDABLE.associateWith { type ->
                GamepadControl.fromWire(stored?.optString(type.wire, null)) ?: DEFAULT.controlFor(type)
            }
            val floor = json.optDouble(KEY_STICK_FLOOR, DEFAULT_STICK_FLOOR.toDouble()).toFloat()
            return GamepadMapping(bindings, if (floor.isNaN()) DEFAULT_STICK_FLOOR else floor.coerceIn(0f, 0.9f))
        }
    }
}
