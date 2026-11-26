---
sidebar_position: 2
title: The broadcast
---

# The broadcast

| | |
| --- | --- |
| **Action** | `se.spacify.app.roaming.event.MOTION` |
| **Sent by** | package `se.spacify.app.roaming` |
| **Sent while** | Roaming's foreground service is running (its notification is visible) |
| **Permission needed to receive** | none |

## When events are sent

Roaming does not stream at sensor rate. An event is sent when:

- the **motion type** changes, for example `idle` → `walking`;
- the **direction** changes, for example `forward` → `left`;
- the **intensity** moves by 0.15 or more (at most once every 50 ms);
- a **discrete action** is detected: every step (step sensor or knee lift on camera) and every jump.

So you can treat each event as "something happened". The `prev` state tells you what the player was doing before.

When the step sensor stops seeing steps, it sends an event back to `idle`: after two missed steps at your current pace (between 0.7 and 2 seconds), or after 2 seconds following a single step. When the camera loses the player's body, it sends `idle` too.

## Payload

The main extra is `data`, a JSON string:

```json
{
  "new":  { "motion_type": "running", "direction": "forward", "intensity": 0.82,
            "vector": { "x": 0, "y": 0, "z": 0.82 } },
  "prev": { "motion_type": "walking", "direction": "forward", "intensity": 0.55,
            "vector": { "x": 0, "y": 0, "z": 0.55 } },
  "timestamp": 123456789,
  "source": "step_sensor"
}
```

The same values are also available as flat extras, so you don't have to parse JSON:

| Extra | Type | Example |
| --- | --- | --- |
| `data` | String (JSON) | see above |
| `motion_type` | String | `running` |
| `direction` | String | `forward` |
| `intensity` | float | `0.82` |
| `vector_x`, `vector_y`, `vector_z` | float | `0.0`, `0.0`, `0.82` |
| `prev_motion_type` | String | `walking` |
| `prev_direction` | String | `forward` |
| `prev_intensity` | float | `0.55` |
| `timestamp` | long | `SystemClock.elapsedRealtime()` in ms when the motion was detected |
| `sent_at` | long | `SystemClock.elapsedRealtime()` in ms when Roaming sent the event |
| `source` | String | `step_sensor` or `camera` |

When both inputs are on, each one sends its own events. Use `source` to tell them apart, or to prefer one.

## Receiving on Android 8 and later

Since Android 8 (API 26), implicit broadcasts are **not** delivered to receivers declared in `AndroidManifest.xml`. Register the receiver in code while your game is in the foreground, and unregister it when it isn't. On Android 13 and later, a receiver for another app's broadcast must be registered as **exported**. `ContextCompat.registerReceiver(..., RECEIVER_EXPORTED)` handles that on all versions.

See [Kotlin](./integrations/kotlin.md) or [Java](./integrations/java.md) for full code.

## Latency

Roaming sends the broadcast on the system's foreground broadcast queue, so it normally arrives within a few milliseconds. When the phone is busy it can still be held up. If your game needs every event as fast as possible, use the [low-latency connection](./low-latency.md) instead: a bound service that calls your app directly. It sends the same extras.

`timestamp` and `sent_at` use `SystemClock.elapsedRealtime()`, which is the same clock in every app, so `SystemClock.elapsedRealtime() - intent.getLongExtra("sent_at", 0)` in your receiver is the delivery delay.
