package se.spacify.app.roaming.motion

import android.os.Bundle
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Apps bound to [se.spacify.app.roaming.service.MotionLinkService], and the protocol they speak.
 *
 * Each client registers a [Messenger]; every motion event is sent to it as one [Message], a one-way
 * Binder call that doesn't wait for the client and doesn't go through the system's broadcast queue.
 * A client that dies or stops answering is dropped.
 */
object MotionClients {
    /** Client to Roaming: start sending events to `msg.replyTo`. */
    const val MSG_REGISTER = 1
    /** Client to Roaming: stop sending events to `msg.replyTo`. */
    const val MSG_UNREGISTER = 2
    /** Roaming to client: one motion event; `msg.data` holds the same extras as the broadcast. */
    const val MSG_MOTION = 100
    /** Roaming to client: tracking started (`arg1 = 1`) or stopped (`arg1 = 0`). */
    const val MSG_TRACKING = 101

    /** Version of this protocol, sent as `arg2` of [MSG_TRACKING]. */
    const val PROTOCOL_VERSION = 1

    private val clients = CopyOnWriteArrayList<Client>()

    private class Client(val messenger: Messenger) : IBinder.DeathRecipient {
        override fun binderDied() {
            clients.remove(this)
        }
    }

    val count: Int get() = clients.size

    fun register(messenger: Messenger) {
        if (clients.any { it.messenger == messenger }) return
        val client = Client(messenger)
        try {
            messenger.binder.linkToDeath(client, 0)
        } catch (e: RemoteException) {
            return // Already dead.
        }
        clients.add(client)
        // Tell the new client the current state straight away, so it doesn't wait for a change.
        send(client, tracking(MotionBus.running.value))
        MotionBus.latest.value?.let { send(client, motion(MotionBroadcaster.toBundle(it))) }
    }

    fun unregister(messenger: Messenger) {
        clients.filter { it.messenger == messenger }.forEach { client ->
            clients.remove(client)
            runCatching { client.messenger.binder.unlinkToDeath(client, 0) }
        }
    }

    fun dispatch(extras: Bundle) {
        if (clients.isEmpty()) return
        clients.forEach { send(it, motion(extras)) }
    }

    fun dispatchRunning(running: Boolean) {
        clients.forEach { send(it, tracking(running)) }
    }

    private fun motion(extras: Bundle) = Message.obtain(null, MSG_MOTION).apply { data = Bundle(extras) }

    private fun tracking(running: Boolean) =
        Message.obtain(null, MSG_TRACKING, if (running) 1 else 0, PROTOCOL_VERSION)

    private fun send(client: Client, message: Message) {
        try {
            client.messenger.send(message)
        } catch (e: RemoteException) {
            clients.remove(client)
        }
    }
}
