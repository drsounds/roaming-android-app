package se.spacify.app.roaming.gamepad.local

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import se.spacify.app.roaming.gamepad.GamepadReport
import java.nio.ByteBuffer
import java.nio.ByteOrder

class UhidTest {
    private fun le(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    @Test
    fun eventSizeMatchesKernelStruct() {
        // sizeof(struct uhid_event) on Linux: 4 + sizeof(struct uhid_create2_req) = 4 + 4372.
        assertEquals(4376, Uhid.EVENT_SIZE)
    }

    @Test
    fun create2Layout() {
        val descriptor = GamepadReport.DESCRIPTOR
        val event = Uhid.create2("Roaming Gamepad", descriptor, Uhid.BUS_USB, 0x1234, 0x5678)
        val b = le(event)
        assertEquals(Uhid.EVENT_SIZE, event.size)
        assertEquals(Uhid.CREATE2, b.getInt(0))
        assertEquals("Roaming Gamepad", String(event, 4, 15, Charsets.UTF_8))
        assertEquals(0, event[4 + 15].toInt())
        // name[128] phys[64] uniq[64] then rd_size, bus, vendor, product, version, country, rd_data
        val off = 4 + 128 + 64 + 64
        assertEquals(descriptor.size, b.getShort(off).toInt())
        assertEquals(Uhid.BUS_USB, b.getShort(off + 2).toInt())
        assertEquals(0x1234, b.getInt(off + 4))
        assertEquals(0x5678, b.getInt(off + 8))
        assertArrayEquals(descriptor, event.copyOfRange(off + 20, off + 20 + descriptor.size))
    }

    @Test
    fun longNamesKeepATerminator() {
        val event = Uhid.create2("x".repeat(300), ByteArray(1), Uhid.BUS_USB, 0, 0)
        assertEquals(0, event[4 + 127].toInt())
        assertEquals('x'.code, event[4 + 126].toInt())
    }

    @Test
    fun input2Layout() {
        val data = byteArrayOf(1, 2, 3)
        val event = Uhid.input2(data)
        val b = le(event)
        assertEquals(Uhid.INPUT2, b.getInt(0))
        assertEquals(3, b.getShort(4).toInt())
        assertArrayEquals(data, event.copyOfRange(6, 9))
    }

    @Test
    fun repliesAndParsing() {
        val request = ByteArray(Uhid.EVENT_SIZE)
        le(request).putInt(0, Uhid.GET_REPORT).putInt(4, 77)
        request[8] = 1
        assertEquals(Uhid.GET_REPORT, Uhid.typeOf(request))
        assertEquals(77, Uhid.requestIdOf(request))
        assertEquals(1, Uhid.reportNumberOf(request))

        val reply = le(Uhid.getReportReply(77, 0, byteArrayOf(1, 9)))
        assertEquals(Uhid.GET_REPORT_REPLY, reply.getInt(0))
        assertEquals(77, reply.getInt(4))
        assertEquals(0, reply.getShort(8).toInt())
        assertEquals(2, reply.getShort(10).toInt())
        assertEquals(9, reply.get(13).toInt())

        val set = le(Uhid.setReportReply(5, Uhid.EIO))
        assertEquals(Uhid.SET_REPORT_REPLY, set.getInt(0))
        assertEquals(5, set.getInt(4))
        assertEquals(Uhid.EIO, set.getShort(8).toInt())
        assertEquals(Uhid.DESTROY, le(Uhid.destroy()).getInt(0))
    }
}
