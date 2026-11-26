---
sidebar_position: 3
title: Low-latency connection
---

# Low-latency connection

The [broadcast](./broadcast.md) is the simplest way to receive Roaming's events, but every broadcast goes through the system's broadcast queue, which other apps share. For a game where the player's movement should show up on screen right away, bind to Roaming's **motion service** instead. It is a standard Android [bound service](https://developer.android.com/develop/background-work/services/bound-services) using a [`Messenger`](https://developer.android.com/reference/android/os/Messenger), so there is nothing extra to install: no library, no AIDL file.

| | |
| --- | --- |
| **Package** | `se.spacify.app.roaming` |
| **Bind action** | `se.spacify.app.roaming.action.BIND_MOTION` |
| **Permission needed** | none |
| **Events flow while** | Roaming's foreground service is running (its notification is visible) |

Binding never starts tracking. The player still starts Roaming themselves, and you get events for as long as tracking runs. You can bind before tracking starts and stay bound across starts and stops.

## Messages

| Message | Direction | Content |
| --- | --- | --- |
| `1` register | your app to Roaming | set `replyTo` to your `Messenger` |
| `2` unregister | your app to Roaming | set `replyTo` to your `Messenger` |
| `100` motion | Roaming to your app | `data` holds exactly the same extras as the broadcast: `motion_type`, `direction`, `intensity`, `vector_x`/`_y`/`_z`, `prev_*`, `timestamp`, `sent_at`, `source` and the JSON `data` string |
| `101` tracking | Roaming to your app | `arg1` is `1` when tracking runs and `0` when it stops; `arg2` is the protocol version (`1`) |

Right after you register, Roaming sends a `101` with the current tracking state and, if tracking runs, the latest `100` event, so you don't have to wait for the player to move.

## Kotlin

Add Roaming to your manifest's package visibility (Android 11+):

```xml
<queries>
    <package android:name="se.spacify.app.roaming" />
</queries>
```

Then copy this class into your app:

```kotlin
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException

class RoamingClient(
    private val context: Context,
    private val onTracking: (Boolean) -> Unit = {},
    private val onMotion: (Bundle) -> Unit,
) {
    private var service: Messenger? = null
    private var bound = false

    // Runs on the main thread. Pass another Looper to receive events on your game thread.
    private val incoming = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_MOTION -> onMotion(msg.data)
                MSG_TRACKING -> onTracking(msg.arg1 == 1)
            }
        }
    })

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = Messenger(binder).also { send(it, MSG_REGISTER) }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null // Roaming was stopped or updated; Android reconnects by itself.
            onTracking(false)
        }
    }

    /** Returns false when Roaming isn't installed. */
    fun connect(): Boolean {
        val intent = Intent(ACTION_BIND).setPackage(PACKAGE)
        bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        return bound
    }

    fun disconnect() {
        service?.let { send(it, MSG_UNREGISTER) }
        if (bound) context.unbindService(connection)
        bound = false
        service = null
    }

    private fun send(to: Messenger, what: Int) {
        try {
            to.send(Message.obtain(null, what).apply { replyTo = incoming })
        } catch (e: RemoteException) {
            // Roaming went away; onServiceDisconnected follows.
        }
    }

    companion object {
        const val PACKAGE = "se.spacify.app.roaming"
        const val ACTION_BIND = "se.spacify.app.roaming.action.BIND_MOTION"
        const val MSG_REGISTER = 1
        const val MSG_UNREGISTER = 2
        const val MSG_MOTION = 100
        const val MSG_TRACKING = 101
    }
}
```

Use it from your activity:

```kotlin
private val roaming = RoamingClient(this, onTracking = { tracking -> hud.showTracking(tracking) }) { e ->
    when (e.getString("motion_type")) {
        "jumping" -> player.jump(e.getFloat("intensity"))
        "walking", "running" -> player.run(speed = e.getFloat("intensity"), steer = e.getString("direction"))
        "idle" -> player.stop()
    }
}

override fun onStart() {
    super.onStart()
    if (!roaming.connect()) showInstallRoamingHint()
}

override fun onStop() {
    roaming.disconnect()
    super.onStop()
}
```

The same class works from Java, and from a Unity or Godot Android plugin: wherever the broadcast receiver in the [Unity](./integrations/unity.md) or [Godot](./integrations/godot.md) examples calls into the engine, call it from `onMotion` instead.

## Use one or the other

Roaming sends every event both ways. If you bind, don't also register the broadcast receiver, or you'll handle each event twice.

## Measuring the delay

Both `timestamp` (when Roaming detected the motion) and `sent_at` (when it handed the event over) are `SystemClock.elapsedRealtime()` in milliseconds. That clock is the same in every app on the phone, so in your app:

```kotlin
val now = SystemClock.elapsedRealtime()
val delivery = now - e.getLong("sent_at")    // Roaming to your app
val total = now - e.getLong("timestamp")     // detected to your app
```

With the bound service, delivery is typically a millisecond or two. If `total` is small but your game still reacts late, the delay is in the game, for example smoothing or waiting for several events before moving the player.
