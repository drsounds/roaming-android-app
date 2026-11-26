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
| `motion_type` | `idle`, `step`, `walking`, `running`, `jumping`, `squatting`, `leaning`, `arms_raised`, `raising` |
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

For convenience the same values are also available as flat extras: `motion_type`, `direction`, `intensity` (float), `vector_x`, `vector_y`, `vector_z`, `prev_motion_type`, `prev_direction`, `prev_intensity`, `timestamp` (long, `SystemClock.elapsedRealtime()` when the motion was detected), `sent_at` (long, same clock, when Roaming sent it) and `source` (`step_sensor` or `camera`).

For the lowest latency, games can instead bind to Roaming's motion service (action `se.spacify.app.roaming.action.BIND_MOTION`) and get each event as a direct `Messenger` callback with the same extras. See the docs page "Low-latency connection".

An event is sent when the motion type or direction changes, when the intensity changes noticeably, and once for every detected step, so a game gets one event per physical action.

| Input | What it detects |
| --- | --- |
| Step sensor | `step`, `walking` / `running` (from cadence), `jumping` (vertical acceleration spike), and `left` / `right` from tilting the phone, which also gives `leaning` when standing still |
| Camera | `jumping` and `squatting` (hip height against a baseline), `leaning` left/right, `arms_raised`, the `raising` gestures (see below), and `walking` / `running` in place (alternating knee lifts). With only the head and shoulders in view (selfie mode, or a phone on an elliptical or bike console) it reports `walking` / `running` from the rhythm and intensity of upper-body movement |

## Camera gestures: raising up, down, left and right

In camera mode, four held arm signals are sent as `motion_type` = `raising` with a `direction`, so a game can use them for things other than walking (raise or lower something, turn it, sway):

| Gesture | `direction` | How |
| --- | --- | --- |
| Raise up | `up` | One arm straight up, wrist above the head (both arms up stays `arms_raised`) |
| Raise down | `down` | Both hands reaching down, wrists well below the hips (needs the hips in view) |
| Raise left | `left` | Your left arm stretched out to your left side, about shoulder height |
| Raise right | `right` | Your right arm stretched out to your right side, about shoulder height |

The pose must be held for 0.25 s before it is sent, so arm swings while walking or running in place don't trigger it. `intensity` is how far the arm reaches, and the flat extras are the same as for every other event (`motion_type=raising`, `direction=left`, `intensity`, `vector_x`...). In gamepad mode, `raising` drives the D-pad by default.

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
    if (type == "raising" && direction == "up") player.raiseArm()
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

# Gamepad mode

Roaming can also act as a **Bluetooth gamepad** (Android 9+). A computer, Android TV, tablet or another phone pairs with the phone like any wireless controller, and your motion drives the sticks and buttons, so any game with gamepad support can be played with your body without changes. By default walking, running and leaning move the left stick (following the direction), jumping presses A, squatting B, raising both arms Y, and the `raising` arm gestures (up, down, left, right) the D-pad. Every motion type can be remapped in the app to a stick, the D-pad, an analog trigger or a button.

It works with Windows, Linux / SteamOS, Android / Android TV / ChromeOS, browsers and Steam. Consoles (PlayStation, Xbox, Switch) only accept their own controllers, and games on the same phone can't receive it (Android doesn't let apps inject gamepad input into other apps), so they should use the broadcast. See the [gamepad docs](https://drsounds.github.io/roaming-android-app/gamepad/overview) for pairing, compatibility and the HID layout.

# Safety and disclaimer

**Roaming is provided "as is", without any warranty. You are responsible for all use of the app.** Moving while looking at a screen can lead to falls and collisions, so only use Roaming in a safe environment (a clear, flat area away from traffic, stairs, water, furniture and other people) and keep track of your surroundings at all times.

In the app, the safety warning and disclaimer must be accepted on first launch before tracking can start, and can be read again from **Safety and disclaimer** on the main screen. While tracking, the motion bubble regularly turns into a "Watch your surroundings" reminder (every 5 minutes by default; 2 to 30 minutes can be chosen in the app). See the [safety docs](https://drsounds.github.io/roaming-android-app/safety).

# Using the app

1. Install the debug APK from the latest CI run (or build it, see below).
2. Read and accept the safety warning and disclaimer.
3. Choose the inputs: Step sensor, Camera, or both.
4. Optionally turn on **Act as a Bluetooth gamepad**, and allow the overlay bubble ("display over other apps").
5. Tap **Start tracking**. Roaming shows an ongoing notification with a **Stop** action; broadcasts are only sent while it is there.
6. Switch to your game, in a safe place with room to move.

For the camera, prop the phone up so your whole body (at least head to hips, ideally to the knees) is in view of the front camera. For an elliptical, exercise bike or stepper, it's enough to have your head and shoulders in view: mount the phone on the console, or hold it in selfie mode. Arms in view help, as their swing is the clearest rhythm.

# Documentation

Developer docs for the broadcast, with integration guides for Kotlin, Java, Unity and Godot, are published at **https://drsounds.github.io/roaming-android-app/**. The source is a [Docusaurus](https://docusaurus.io) site in [`docs/`](docs); run `npm ci && npm start` there to preview it. The `Docs (GitHub Pages)` workflow deploys it on every push to `main` that touches `docs/`.

# Privacy

* Only local. The app has no network permission (it is explicitly removed from the merged manifest).
* No GPS coordinate
* No health data. Nothing is stored; only a few seconds of step timestamps are kept in memory to compute cadence. The camera input also reads the gyroscope, only to tell whether the phone itself is moving. Camera frames are analyzed in memory and dropped immediately.
* Open Source
* Use a foreground service with an ongoing notification so it only broadcasts while that notification (and its Stop pending intent) is live, so the user always knows it is tracking

# Building

Requirements: JDK 17 and the Android SDK (API 36).

```sh
./gradlew testDebugUnitTest   # unit tests for the motion classifiers and gamepad mapping
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

Project layout:

* `app/src/main/java/se/spacify/app/roaming/motion`: event model, classifiers (pure Kotlin, unit tested), sensor and camera sources, broadcaster
* `app/src/main/java/se/spacify/app/roaming/gamepad`: motion-to-gamepad mapping and HID report (pure Kotlin, unit tested) and the Bluetooth HID gamepad
* `.../service/MotionService.kt`: the foreground service
* `.../overlay/OverlayBubble.kt`: the on-screen bubble, including the surroundings reminder
* `.../safety/SafetySettings.kt`: disclaimer acceptance and the reminder interval
* `.../ui/MainActivity.kt`: start/stop screen and live event view

# License

Apache License 2.0, see [LICENSE](LICENSE).

# CI
Three GitHub Actions workflows live in `.github/workflows`:

- **`android-ci.yml`** runs automatically on every push and pull request: it runs the unit tests, builds the debug APK and uploads it as the `roaming-debug-apk` artifact.
- **`android-build.yml`** (manual, or called by the release workflow) builds and signs the release AAB and uploads it as the `release-aab` artifact (kept 14 days).
- **`android-play-release.yml`** (manual) calls `android-build.yml` as its `build` job, then a separate `publish` job uploads that AAB to a Google Play track (defaults to **internal**). Because build and publish are separate jobs, "Re-run failed jobs" re-runs only `publish` and reuses the AAB already built.

The release builds are started from the **Actions** tab via "Run workflow". `versionCode` comes from `GITHUB_RUN_NUMBER`, so every CI-built AAB has a code Google Play hasn't seen before.

**Signing model**: Google Play manages the app signing key ([Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756)). CI only needs an **upload key**, a keystore used to sign the bundle you hand to Google, which Google then re-signs. Treat it as a long-lived credential.

Configure these repository secrets before running the release workflows:

| Secret | Description |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | The upload keystore file, base64-encoded (`base64 -w0 upload-keystore.jks`). |
| `ANDROID_KEYSTORE_PASSWORD` | Store password for the keystore. |
| `ANDROID_KEY_ALIAS` | Alias of the upload key inside the keystore. |
| `ANDROID_KEY_PASSWORD` | Password for the key itself (same as the store password for a PKCS12 keystore). |
| `PLAY_SERVICE_ACCOUNT_JSON` | JSON key of a Google Play service account with "Release Manager" access to this app, pasted as plain text. |

The service account must be linked in Google Play Console under **Setup → API access**, with permission to manage releases on the `se.spacify.app.roaming` package.

For local release builds, put `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD` in `local.properties` (not committed).

**One-time setup before the first `android-play-release.yml` run:**
1. The Play Developer API cannot create an app's very first release. Run `android-build.yml`, download the `release-aab` artifact, and upload it manually via **Play Console → Release → Internal testing → Create release** to create the app listing and enroll it in Play App Signing.
2. After that first manual release exists, later releases can go through `android-play-release.yml`.
