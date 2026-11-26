package se.spacify.app.roaming.gamepad

import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Position of the D-pad (HID hat switch). Values are the HID hat codes, clockwise from up. */
enum class Hat(val code: Int) {
    UP(0), UP_RIGHT(1), RIGHT(2), DOWN_RIGHT(3), DOWN(4), DOWN_LEFT(5), LEFT(6), UP_LEFT(7),
    CENTER(8);

    companion object {
        /** Eight-way hat for a stick-style vector (y = up), or CENTER inside [deadZone]. */
        fun fromVector(x: Float, y: Float, deadZone: Float = 0.25f): Hat {
            if (sqrt(x * x + y * y) < deadZone) return CENTER
            // Angle clockwise from "up", in 45° sectors.
            val degrees = Math.toDegrees(atan2(x.toDouble(), y.toDouble()))
            val sector = (((degrees + 360.0 + 22.5) % 360.0) / 45.0).toInt()
            return entries[sector]
        }
    }
}

/**
 * Everything the virtual gamepad reports at one instant.
 *
 * Sticks use y = up / forward (positive), x = right (positive), both -1..1. Triggers are 0..1.
 */
data class GamepadState(
    val buttons: Int = 0,
    val hat: Hat = Hat.CENTER,
    val leftX: Float = 0f,
    val leftY: Float = 0f,
    val rightX: Float = 0f,
    val rightY: Float = 0f,
    val leftTrigger: Float = 0f,
    val rightTrigger: Float = 0f,
) {
    fun isPressed(button: GamepadButton) = buttons and button.mask != 0

    /** Packs this state into the input report described by [GamepadReport.DESCRIPTOR]. */
    fun toReport(): ByteArray = byteArrayOf(
        (buttons and 0xFF).toByte(),
        ((buttons shr 8) and 0xFF).toByte(),
        (hat.code and 0x0F).toByte(),
        axis(leftX),
        // HID Y axes grow downwards; our sticks use up = positive.
        axis(-leftY),
        axis(rightX),
        axis(-rightY),
        trigger(leftTrigger),
        trigger(rightTrigger),
    )

    companion object {
        val NEUTRAL = GamepadState()

        private fun axis(v: Float): Byte = (v.coerceIn(-1f, 1f) * 127f).roundToInt().toByte()
        private fun trigger(v: Float): Byte = (v.coerceIn(0f, 1f) * 255f).roundToInt().toByte()
    }
}

/**
 * HID report descriptor for a standard gamepad: 16 buttons, an 8-way hat, two sticks (X/Y and
 * Z/Rz, the Android / Linux convention) and two analog triggers (Brake / Accelerator, which Android
 * exposes as AXIS_BRAKE / AXIS_GAS and Linux as ABS_BRAKE / ABS_GAS).
 *
 * Input report [REPORT_ID], 9 bytes after the report ID:
 *
 * | byte | content |
 * | --- | --- |
 * | 0-1 | buttons 1-16, one bit each, little endian |
 * | 2 | hat switch in the low nibble (0 = up, clockwise, 8 = centered) |
 * | 3, 4 | left stick X, Y (signed, -127..127, Y down positive) |
 * | 5, 6 | right stick Z, Rz (signed, -127..127, Rz down positive) |
 * | 7, 8 | left trigger (Brake), right trigger (Accelerator), 0..255 |
 */
object GamepadReport {
    const val REPORT_ID = 1
    const val REPORT_SIZE = 9

    val DESCRIPTOR: ByteArray = bytes(
        0x05, 0x01,        // Usage Page (Generic Desktop)
        0x09, 0x05,        // Usage (Game Pad)
        0xA1, 0x01,        // Collection (Application)
        0x85, REPORT_ID,   //   Report ID
        // 16 buttons
        0x05, 0x09,        //   Usage Page (Button)
        0x19, 0x01,        //   Usage Minimum (1)
        0x29, 0x10,        //   Usage Maximum (16)
        0x15, 0x00,        //   Logical Minimum (0)
        0x25, 0x01,        //   Logical Maximum (1)
        0x75, 0x01,        //   Report Size (1)
        0x95, 0x10,        //   Report Count (16)
        0x81, 0x02,        //   Input (Data, Variable, Absolute)
        // Hat switch, 4 bits, out of range (8) = centered
        0x05, 0x01,        //   Usage Page (Generic Desktop)
        0x09, 0x39,        //   Usage (Hat switch)
        0x15, 0x00,        //   Logical Minimum (0)
        0x25, 0x07,        //   Logical Maximum (7)
        0x35, 0x00,        //   Physical Minimum (0)
        0x46, 0x3B, 0x01,  //   Physical Maximum (315)
        0x65, 0x14,        //   Unit (English rotation, degrees)
        0x75, 0x04,        //   Report Size (4)
        0x95, 0x01,        //   Report Count (1)
        0x81, 0x42,        //   Input (Data, Variable, Absolute, Null state)
        0x65, 0x00,        //   Unit (None)
        0x45, 0x00,        //   Physical Maximum (0)
        0x75, 0x04,        //   Report Size (4)
        0x95, 0x01,        //   Report Count (1)
        0x81, 0x03,        //   Input (Constant) padding
        // Sticks
        0x09, 0x30,        //   Usage (X)
        0x09, 0x31,        //   Usage (Y)
        0x09, 0x32,        //   Usage (Z)
        0x09, 0x35,        //   Usage (Rz)
        0x15, 0x81,        //   Logical Minimum (-127)
        0x25, 0x7F,        //   Logical Maximum (127)
        0x75, 0x08,        //   Report Size (8)
        0x95, 0x04,        //   Report Count (4)
        0x81, 0x02,        //   Input (Data, Variable, Absolute)
        // Triggers
        0x05, 0x02,        //   Usage Page (Simulation Controls)
        0x09, 0xC5,        //   Usage (Brake)
        0x09, 0xC4,        //   Usage (Accelerator)
        0x15, 0x00,        //   Logical Minimum (0)
        0x26, 0xFF, 0x00,  //   Logical Maximum (255)
        0x75, 0x08,        //   Report Size (8)
        0x95, 0x02,        //   Report Count (2)
        0x81, 0x02,        //   Input (Data, Variable, Absolute)
        0xC0,              // End Collection
    )

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
