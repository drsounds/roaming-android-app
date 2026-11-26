package se.spacify.app.roaming.gamepad

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class GamepadReportTest {

    @Test
    fun neutralReport() {
        val r = GamepadState.NEUTRAL.toReport()
        assertEquals(GamepadReport.REPORT_SIZE, r.size)
        assertArrayEquals(byteArrayOf(0, 0, 8, 0, 0, 0, 0, 0, 0), r)
    }

    @Test
    fun packsButtonsSticksAndTriggers() {
        val s = GamepadState(
            buttons = GamepadButton.A.mask or GamepadButton.START.mask,
            hat = Hat.RIGHT,
            leftX = -1f, leftY = 1f, rightX = 0.5f, rightY = -1f,
            leftTrigger = 0f, rightTrigger = 1f,
        )
        val r = s.toReport()
        assertEquals(0x01, r[0].toInt() and 0xFF)
        assertEquals(0x08, r[1].toInt() and 0xFF) // START is button 12 -> bit 11
        assertEquals(2, r[2].toInt())
        assertEquals(-127, r[3].toInt())
        assertEquals(-127, r[4].toInt()) // up is negative in HID
        assertEquals(64, r[5].toInt())
        assertEquals(127, r[6].toInt())
        assertEquals(0, r[7].toInt() and 0xFF)
        assertEquals(255, r[8].toInt() and 0xFF)
    }

    @Test
    fun descriptorInputBitsMatchReportSize() {
        // Sum Report Size x Report Count for every Input item; must equal the packed report.
        val d = GamepadReport.DESCRIPTOR.map { it.toInt() and 0xFF }
        var i = 0
        var size = 0
        var count = 0
        var bits = 0
        while (i < d.size) {
            val prefix = d[i]
            val len = when (prefix and 0x03) { 3 -> 4; else -> prefix and 0x03 }
            val value = (0 until len).fold(0) { acc, k -> acc or (d[i + 1 + k] shl (8 * k)) }
            when (prefix and 0xFC) {
                0x74 -> size = value
                0x94 -> count = value
                0x80 -> bits += size * count
            }
            i += 1 + len
        }
        assertEquals(GamepadReport.REPORT_SIZE * 8, bits)
        assertEquals(0xC0, d.last())
    }

    @Test
    fun hatFromVector() {
        assertEquals(Hat.CENTER, Hat.fromVector(0.1f, 0f))
        assertEquals(Hat.UP, Hat.fromVector(0f, 1f))
        assertEquals(Hat.RIGHT, Hat.fromVector(1f, 0f))
        assertEquals(Hat.DOWN, Hat.fromVector(0f, -1f))
        assertEquals(Hat.LEFT, Hat.fromVector(-1f, 0f))
        assertEquals(Hat.UP_LEFT, Hat.fromVector(-0.7f, 0.7f))
        assertEquals(Hat.DOWN_RIGHT, Hat.fromVector(0.7f, -0.7f))
    }
}
