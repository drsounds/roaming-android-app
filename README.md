# roaming-android-app
An Android stepwatcher and motion sensing app which can send broadcasts about motion to allow for creating new apps that react on physical activity.

# Background

Obesity has grown into one of the largest public health challenges of our time. According to the World Health Organization, worldwide adult obesity has more than doubled since 1990 and adolescent obesity has quadrupled; in 2022 about 1 in 8 people in the world were living with obesity, and WHO has long described it as a global epidemic ([WHO fact sheet](https://www.who.int/news-room/fact-sheets/detail/obesity-and-overweight)). The causes are many, including diet and environment, but physical inactivity is an important one: WHO estimates that around 31% of adults (1.8 billion people) did not get enough physical activity in 2022 ([WHO fact sheet](https://www.who.int/news-room/fact-sheets/detail/physical-activity)). Much of our free time is spent sitting, and a lot of it is spent playing games.

Roaming wants to turn some of that screen time into movement. Instead of asking every game developer to build their own motion detection, Roaming does the hard part once, on the device, and publishes what the player's body is doing as simple broadcast events. Any game or app can listen for those events with a few lines of code and let players walk, run, jump, squat, lean or raise their arms to control it. The goal is an easy integration that makes it cheap for games to incorporate physical activity.

# Inputs
1. Stepwatcher (hardware step detector, plus device tilt and acceleration)
2. On device identification of body motion by analyzing the device camera with an on-device model ([ML Kit Pose Detection](https://developers.google.com/ml-kit/vision/pose-detection), bundled model)

# Output
A stream of steps / motion detected as a stream of events

````json
{
motion_type,
direction,
intensity
}
````

which are sent as broadcast intents

````se.spacify.app.roaming.event.MOTION````

with data

````json
{
new: { motion_type,
direction,
intensity},
prev: {
motion_type,
direction,
intensity
}
}
````

When active, Roaming also shows an overlay on the screen: a small draggable bubble showing the current motion.

## Event reference

| Field | Values |
| --- | --- |
| `motion_type` | `idle`, `step`, `walking`, `running`, `jumping`, `squatting`, `leaning`, `arms_raised` |
| `direction` | `none`, `forward`, `backward`, `left`, `right`, `up`, `down` (from the user's point of view) |
| `intensity` | `0.0` to `1.0` |
| `vector` | `{ x, y, z }`: the direction scaled by intensity, so it can be used like an accelerometer or joystick (x = right, y = up, z = forward) |

The intent carries the full JSON as the `data` string extra, for example:

```json
{
  "new":  { "motion_type": "running", "direction": "forward", "intensity": 0.82, "vector": { "x": 0, "y": 0, "z": 0.82 } },
  "prev": { "motion_type": "walking", "direction": "forward", "intensity": 0.55, "vector": { "x": 0, "y": 0, "z": 0.55 } },
  "timestamp": 123456789,
  "source": "step_sensor"
}
```

For convenience the same values are also available as flat extras: `motion_type`, `direction`, `intensity` (float), `vector_x`, `vector_y`, `vector_z`, `prev_motion_type`, `prev_direction`, `prev_intensity`, `timestamp` (long, `SystemClock.elapsedRealtime()`) and `source` (`step_sensor` or `camera`).

An event is sent when the motion type or direction changes, when the intensity changes noticeably, and once for every detected step, so a game gets one event per physical action.

| Input | What it detects |
| --- | --- |
| Step sensor | `step`, `walking` / `running` (from cadence), `jumping` (vertical acceleration spike), and `left` / `right` from tilting the phone, which also gives `leaning` when standing still |
| Camera | `jumping` and `squatting` (hip height against a baseline), `leaning` left/right, `arms_raised`, and `walking` / `running` in place (alternating knee lifts) |

## Listening from your game

Since Android 8, implicit broadcasts are not delivered to receivers declared in the manifest, so register the receiver at runtime while your game is in the foreground:

```kotlin
class MotionReceiver(private val onMotion: (type: String, direction: String, intensity: Float) -> Unit) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        onMotion(
            intent.getStringExtra("motion_type") ?: "idle",
            intent.getStringExtra("direction") ?: "none",
            intent.getFloatExtra("intensity", 0f),
        )
        // Or parse everything: JSONObject(intent.getStringExtra("data")!!)
    }
}

val receiver = MotionReceiver { type, direction, intensity ->
    if (type == "jumping") player.jump(intensity)
    player.speed = if (type == "running" || type == "walking") intensity else 0f
}
ContextCompat.registerReceiver(
    context, receiver,
    IntentFilter("se.spacify.app.roaming.event.MOTION"),
    ContextCompat.RECEIVER_EXPORTED,
)
// ...and context.unregisterReceiver(receiver) in onPause/onStop.
```

Unity, Godot and other engines can do the same through their Android plugin layer.

# Using the app

1. Install the debug APK from the latest CI run (or build it, see below).
2. Choose the inputs: Step sensor, Camera, or both.
3. Optionally allow the overlay bubble ("display over other apps").
4. Tap **Start tracking**. Roaming shows an ongoing notification with a **Stop** action; broadcasts are only sent while it is there.
5. Switch to your game.

For the camera, prop the phone up so your whole body (at least head to hips, ideally to the knees) is in view of the front camera.

# Privacy

* Only local. The app has no network permission (it is explicitly removed from the merged manifest).
* No GPS coordinate
* No health data. Nothing is stored; only a few seconds of step timestamps are kept in memory to compute cadence. Camera frames are analyzed in memory and dropped immediately.
* Open Source
* Use a foreground service with an ongoing notification so it only broadcasts while that notification (and its Stop pending intent) is live, so the user always knows it is tracking

# Building

Requirements: JDK 17 and the Android SDK (API 35).

```sh
./gradlew testDebugUnitTest   # unit tests for the motion classifiers
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

Project layout:

* `app/src/main/java/se/spacify/app/roaming/motion`: event model, classifiers (pure Kotlin, unit tested), sensor and camera sources, broadcaster
* `.../service/MotionService.kt`: the foreground service
* `.../overlay/OverlayBubble.kt`: the on-screen bubble
* `.../ui/MainActivity.kt`: start/stop screen and live event view

# License

Apache License 2.0, see [LICENSE](LICENSE).

# CI
A GitHub Actions workflow (`.github/workflows/android.yml`) runs the unit tests and builds the debug APK on every push and pull request, and uploads the APK as a build artifact.
