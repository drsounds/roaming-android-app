---
sidebar_position: 1
title: Kotlin
---

# Kotlin

```kotlin
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

class RoamingReceiver(
    private val onMotion: (type: String, direction: String, intensity: Float) -> Unit,
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        onMotion(
            intent.getStringExtra("motion_type") ?: "idle",
            intent.getStringExtra("direction") ?: "none",
            intent.getFloatExtra("intensity", 0f),
        )
    }

    companion object {
        const val ACTION = "se.spacify.app.roaming.event.MOTION"
    }
}

class GameActivity : AppCompatActivity() {
    private val roaming = RoamingReceiver { type, direction, intensity ->
        when (type) {
            "jumping" -> player.jump(intensity)
            "walking", "running" -> player.run(speed = intensity, steer = direction)
            "idle" -> player.stop()
        }
    }

    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(
            this, roaming, IntentFilter(RoamingReceiver.ACTION), ContextCompat.RECEIVER_EXPORTED,
        )
    }

    override fun onPause() {
        unregisterReceiver(roaming)
        super.onPause()
    }
}
```

## Parsing the full JSON

```kotlin
val data = JSONObject(intent.getStringExtra("data") ?: return)
val new = data.getJSONObject("new")
val vector = new.getJSONObject("vector")
val forward = vector.getDouble("z").toFloat()
val wasType = data.getJSONObject("prev").getString("motion_type")
```

## Checking that Roaming is installed

Add a `<queries>` entry to your manifest (Android 11+ package visibility), then check:

```xml
<queries>
    <package android:name="se.spacify.app.roaming" />
</queries>
```

```kotlin
val installed = runCatching {
    packageManager.getPackageInfo("se.spacify.app.roaming", 0)
}.isSuccess

// Open Roaming so the player can start tracking:
packageManager.getLaunchIntentForPackage("se.spacify.app.roaming")?.let(::startActivity)
```
