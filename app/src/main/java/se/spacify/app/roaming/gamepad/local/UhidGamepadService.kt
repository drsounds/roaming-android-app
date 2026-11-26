package se.spacify.app.roaming.gamepad.local

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import java.io.FileDescriptor
import kotlin.system.exitProcess

/**
 * Creates the virtual gamepad through `/dev/uhid`.
 *
 * This class does **not** run in Roaming's own process. Shizuku starts it as a "user service" in a
 * separate process with shell (ADB) or root privileges, because ordinary apps may not open
 * `/dev/uhid`. It only ever creates the one gamepad device Roaming describes and forwards its input
 * reports; it accepts no other commands.
 */
class UhidGamepadService : IUhidGamepad.Stub() {

    private val lock = Any()
    private var fd: FileDescriptor? = null
    private var reader: Thread? = null
    @Volatile private var latest: ByteArray = ByteArray(0)

    override fun create(name: String, descriptor: ByteArray, bus: Int, vendor: Int, product: Int): String? {
        close()
        return try {
            val opened = Os.open(DEVICE, OsConstants.O_RDWR or OsConstants.O_CLOEXEC, 0)
            writeEvent(opened, Uhid.create2(name, descriptor, bus, vendor, product))
            synchronized(lock) { fd = opened }
            reader = Thread({ readLoop(opened) }, "uhid-reader").apply {
                isDaemon = true
                start()
            }
            null
        } catch (e: ErrnoException) {
            "${e.functionName ?: "open"} $DEVICE failed: ${OsConstants.errnoName(e.errno) ?: e.errno}"
        } catch (e: Exception) {
            e.toString()
        }
    }

    override fun send(reportId: Int, report: ByteArray) {
        val data = byteArrayOf(reportId.toByte()) + report
        latest = data
        synchronized(lock) {
            val current = fd ?: return
            try {
                writeEvent(current, Uhid.input2(data))
            } catch (_: ErrnoException) {
                // The device is gone; the next create() starts over.
            }
        }
    }

    override fun close() {
        val old = synchronized(lock) { fd.also { fd = null } } ?: return
        try {
            writeEvent(old, Uhid.destroy())
        } catch (_: ErrnoException) {
        }
        reader?.join(POLL_MILLIS * 2L)
        reader = null
        try {
            Os.close(old)
        } catch (_: ErrnoException) {
        }
    }

    override fun destroy() {
        close()
        exitProcess(0)
    }

    /**
     * Drains events the kernel sends (start, open, close...) and answers report requests, so the
     * kernel never waits for a reply. Stops once [close] clears [fd].
     */
    private fun readLoop(device: FileDescriptor) {
        val buffer = ByteArray(Uhid.EVENT_SIZE)
        val poll = StructPollfd().apply {
            this.fd = device
            events = OsConstants.POLLIN.toShort()
        }
        while (synchronized(lock) { fd === device }) {
            try {
                poll.revents = 0
                if (Os.poll(arrayOf(poll), POLL_MILLIS) <= 0) continue
                if (Os.read(device, buffer, 0, buffer.size) <= 0) continue
                val reply = when (Uhid.typeOf(buffer)) {
                    Uhid.GET_REPORT -> {
                        val data = latest
                        if (data.isNotEmpty() && Uhid.reportNumberOf(buffer) == (data[0].toInt() and 0xFF)) {
                            Uhid.getReportReply(Uhid.requestIdOf(buffer), 0, data)
                        } else {
                            Uhid.getReportReply(Uhid.requestIdOf(buffer), Uhid.EIO)
                        }
                    }
                    Uhid.SET_REPORT -> Uhid.setReportReply(Uhid.requestIdOf(buffer), Uhid.EIO)
                    else -> null
                }
                if (reply != null) synchronized(lock) { if (fd === device) writeEvent(device, reply) }
            } catch (e: ErrnoException) {
                if (e.errno != OsConstants.EINTR) return
            }
        }
    }

    private fun writeEvent(device: FileDescriptor, event: ByteArray) {
        Os.write(device, event, 0, event.size)
    }

    private companion object {
        const val DEVICE = "/dev/uhid"
        const val POLL_MILLIS = 200
    }
}
