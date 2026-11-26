package se.spacify.app.roaming.gamepad.local

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Encodes and decodes Linux `uhid` events (include/uapi/linux/uhid.h), the kernel interface for
 * creating virtual HID devices from user space by reading and writing `/dev/uhid`.
 *
 * A device created this way is a real kernel input device, so Android's input system treats it
 * exactly like a USB or Bluetooth gamepad and delivers its events to the app in focus.
 *
 * Every event is a packed `struct uhid_event` of [EVENT_SIZE] bytes in host (little endian) order.
 */
object Uhid {
    /** UHID_DATA_MAX and HID_MAX_DESCRIPTOR_SIZE. */
    const val MAX_DATA = 4096

    /** sizeof(struct uhid_event): a u32 type plus the largest union member (uhid_create2_req). */
    const val EVENT_SIZE = 4 + 128 + 64 + 64 + 2 + 2 + 4 + 4 + 4 + 4 + MAX_DATA

    const val DESTROY = 1
    const val START = 2
    const val STOP = 3
    const val OPEN = 4
    const val CLOSE = 5
    const val OUTPUT = 6
    const val GET_REPORT = 9
    const val GET_REPORT_REPLY = 10
    const val CREATE2 = 11
    const val INPUT2 = 12
    const val SET_REPORT = 13
    const val SET_REPORT_REPLY = 14

    const val BUS_USB = 0x03
    const val BUS_BLUETOOTH = 0x05
    const val BUS_VIRTUAL = 0x06

    /** EIO, for get/set report replies the device can't serve. */
    const val EIO = 5

    fun create2(name: String, descriptor: ByteArray, bus: Int, vendor: Int, product: Int): ByteArray {
        require(descriptor.size <= MAX_DATA) { "descriptor too large" }
        return event(CREATE2) {
            put(fixed(name, 128))
            put(ByteArray(64)) // phys
            put(ByteArray(64)) // uniq
            putShort(descriptor.size.toShort())
            putShort(bus.toShort())
            putInt(vendor)
            putInt(product)
            putInt(0) // version
            putInt(0) // country
            put(descriptor)
        }
    }

    /** An input report; [data] includes the report ID byte when the descriptor uses report IDs. */
    fun input2(data: ByteArray): ByteArray {
        require(data.size <= MAX_DATA) { "report too large" }
        return event(INPUT2) {
            putShort(data.size.toShort())
            put(data)
        }
    }

    fun destroy(): ByteArray = event(DESTROY) {}

    /** Answers a [GET_REPORT] request: [data] on success, or an errno in [err]. */
    fun getReportReply(requestId: Int, err: Int, data: ByteArray = ByteArray(0)): ByteArray = event(GET_REPORT_REPLY) {
        putInt(requestId)
        putShort(err.toShort())
        putShort(data.size.toShort())
        put(data)
    }

    fun setReportReply(requestId: Int, err: Int): ByteArray = event(SET_REPORT_REPLY) {
        putInt(requestId)
        putShort(err.toShort())
    }

    /** The event type of a buffer read from `/dev/uhid`. */
    fun typeOf(event: ByteArray): Int = buffer(event).getInt(0)

    /** The request id of a GET_REPORT or SET_REPORT event. */
    fun requestIdOf(event: ByteArray): Int = buffer(event).getInt(4)

    /** The report number of a GET_REPORT or SET_REPORT event. */
    fun reportNumberOf(event: ByteArray): Int = event[8].toInt() and 0xFF

    private inline fun event(type: Int, body: ByteBuffer.() -> Unit): ByteArray {
        val bytes = ByteArray(EVENT_SIZE)
        buffer(bytes).apply {
            putInt(type)
            body()
        }
        return bytes
    }

    private fun buffer(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    private fun fixed(text: String, size: Int): ByteArray {
        val out = ByteArray(size)
        val raw = text.toByteArray(Charsets.UTF_8)
        raw.copyInto(out, endIndex = minOf(raw.size, size - 1)) // keep a terminating NUL
        return out
    }
}
