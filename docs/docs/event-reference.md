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

## `direction`

Directions are from the **player's** point of view.

| Value | Vector | Typical use |
| --- | --- | --- |
| `none` | (0, 0, 0) | `idle` |
| `forward` | (0, 0, 1) | `step`, `walking`, `running` |
| `backward` | (0, 0, -1) | reserved |
| `left` | (-1, 0, 0) | `leaning`, or steering while walking/running |
| `right` | (1, 0, 0) | `leaning`, or steering while walking/running |
| `up` | (0, 1, 0) | `jumping`, `arms_raised` |
| `down` | (0, -1, 0) | `squatting` |

While walking or running, tilting the phone (step sensor) or leaning (camera) changes the direction to `left` or `right`, so a player can steer while running in place.

## `intensity`

A float from `0.0` to `1.0`:

- `walking` / `running`: cadence divided by the maximum (200 steps/min for the step sensor, 220 for the camera). When the camera only sees the upper body, it's the larger of that and how vigorously the head and arms move, so pushing harder on an elliptical raises intensity even at the same pace
- `jumping`, `squatting`, `leaning`, `arms_raised`: how far the body moved, relative to the player's torso length (camera) or the strength of the acceleration (step sensor)
- `step`: always `0.3`
- `idle`: `0.0`

## `vector`

`vector` is the direction's unit vector multiplied by intensity, with `x` = right, `y` = up and `z` = forward. Use it when your game already reads an accelerometer or an analog stick:

```kotlin
player.velocity.x = vectorX * maxStrafeSpeed
player.velocity.z = vectorZ * maxRunSpeed
if (vectorY > 0.3f) player.jump()
```

## Values may grow

New `motion_type` and `direction` values may be added later. Treat unknown values as `idle` / `none`.
