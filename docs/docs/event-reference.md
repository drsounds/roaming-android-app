---
sidebar_position: 4
title: Event reference
---

# Event reference

Each state has the shape `{ motion_type, direction, intensity, vector }`.

## `motion_type`

| Value | Meaning | Detected by |
| --- | --- | --- |
| `idle` | Not moving (or the body is not visible to the camera) | both |
| `step` | A single step after standing still; becomes `walking` / `running` with the next steps | step sensor |
| `walking` | Steady steps below the running cadence. On camera, from knee lifts or, with only the upper body in view, from the rhythm of the arms, head and shoulders (elliptical, bike, stepper) | both |
| `running` | Fast steps: 140+ steps/min on the step sensor, 150+ knee lifts or upper-body strides/min on camera | both |
| `jumping` | A jump. On the step sensor, a strong upward acceleration; on camera, hips well above the standing height | both |
| `squatting` | Hips well below the standing height | camera |
| `leaning` | Leaning (camera) or tilting the phone (step sensor) left or right while not stepping | both |
| `arms_raised` | Both wrists above the head | camera |
| `raising` | A held arm gesture: one arm up (`up`), one arm stretched out to the player's left or right (`left` / `right`), or both hands reaching down below the hips (`down`). See [camera gestures](#camera-gestures-raising) | camera |

## `direction`

Directions are from the **player's** point of view.

| Value | Vector | Typical use |
| --- | --- | --- |
| `none` | (0, 0, 0) | `idle` |
| `forward` | (0, 0, 1) | `step`, `walking`, `running` |
| `backward` | (0, 0, -1) | reserved |
| `left` | (-1, 0, 0) | `leaning`, `raising`, or steering while walking/running |
| `right` | (1, 0, 0) | `leaning`, `raising`, or steering while walking/running |
| `up` | (0, 1, 0) | `jumping`, `arms_raised`, `raising` |
| `down` | (0, -1, 0) | `squatting`, `raising` |

While walking or running, tilting the phone (step sensor) or leaning (camera) changes the direction to `left` or `right`, so a player can steer while running in place.

## `intensity`

A float from `0.0` to `1.0`:

- `walking` / `running`: cadence divided by the maximum (200 steps/min for the step sensor, 220 for the camera). When the camera only sees the upper body, it's the larger of that and how vigorously the head and arms move, so pushing harder on an elliptical raises intensity even at the same pace
- `raising`: how far the arm reaches: for `up`, wrist height above the nose (0.8 torso lengths = 1.0); for `left` / `right`, how far the wrist is from its shoulder (1.2 torso lengths = 1.0); for `down`, how far the higher wrist is below the hips (1 torso length = 1.0)
- `jumping`, `squatting`, `leaning`, `arms_raised`: how far the body moved, relative to the player's torso length (camera) or the strength of the acceleration (step sensor)
- `step`: always `0.3`
- `idle`: `0.0`

## Camera gestures (`raising`)

Four held arm signals, made for controlling things other than walking: raising or lowering something, turning it, or swaying. A game can treat them like a four-way D-pad.

| Gesture | Event | How |
| --- | --- | --- |
| Raise up | `raising` / `up` | One arm straight up, wrist above the head (both arms up is `arms_raised`) |
| Raise down | `raising` / `down` | Both hands reaching down, wrists well below the hips (needs the hips in view) |
| Raise left | `raising` / `left` | Your left arm stretched out to your left side, about shoulder height |
| Raise right | `raising` / `right` | Your right arm stretched out to your right side, about shoulder height |

The gesture has to be held for a quarter of a second before it is sent, so arms swinging while walking or running in place don't trigger it. It is sent once when it starts (and again when its intensity changes noticeably), and `idle` or the next motion follows when you drop the arm. Both arms out to the sides at once is ignored. Jumps and squats win over a gesture; a gesture wins over walking, running and leaning.

```kotlin
if (type == "raising") when (direction) {
    "up" -> waves.height += 0.1f
    "down" -> waves.height -= 0.1f
    "left" -> waves.turn(-15f)
    "right" -> waves.turn(15f)
}
```

## `vector`

`vector` is the direction's unit vector multiplied by intensity, with `x` = right, `y` = up and `z` = forward. Use it when your game already reads an accelerometer or an analog stick:

```kotlin
player.velocity.x = vectorX * maxStrafeSpeed
player.velocity.z = vectorZ * maxRunSpeed
if (vectorY > 0.3f) player.jump()
```

## Values may grow

New `motion_type` and `direction` values may be added later. Treat unknown values as `idle` / `none`.
