package se.spacify.app.roaming.service

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import se.spacify.app.roaming.motion.MotionClients

/**
 * The low-latency way for other apps to receive motion events: bind to this service and get a
 * direct callback for every event, instead of waiting for a broadcast.
 *
 * Bind with action [ACTION_BIND] and package `se.spacify.app.roaming`, then send
 * [MotionClients.MSG_REGISTER] with `replyTo` set to your own [Messenger]. See the docs page
 * "Low-latency connection" for the full client code.
 *
 * Binding does not start tracking: events only flow while the user has started Roaming and its
 * notification is showing, exactly like the broadcast.
 */
class MotionLinkService : Service() {

    private val incoming = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            val client = msg.replyTo ?: return
            when (msg.what) {
                MotionClients.MSG_REGISTER -> MotionClients.register(client)
                MotionClients.MSG_UNREGISTER -> MotionClients.unregister(client)
                else -> super.handleMessage(msg)
            }
        }
    })

    override fun onBind(intent: Intent): IBinder = incoming.binder

    companion object {
        const val ACTION_BIND = "se.spacify.app.roaming.action.BIND_MOTION"
    }
}
