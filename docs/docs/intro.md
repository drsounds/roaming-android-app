---
slug: /
sidebar_position: 1
title: Introduction
---

# Roaming

Roaming is an open source Android app that detects how your body moves and broadcasts it to other apps. Games and apps listen for one broadcast intent, `se.spacify.app.roaming.event.MOTION`, and let players walk, run, jump, squat, lean or raise their arms to control them.

## Why

Obesity is one of the largest public health challenges of our time. According to the World Health Organization, worldwide adult obesity has more than doubled since 1990, and around 31% of adults did not get enough physical activity in 2022 ([WHO: obesity](https://www.who.int/news-room/fact-sheets/detail/obesity-and-overweight), [WHO: physical activity](https://www.who.int/news-room/fact-sheets/detail/physical-activity)). A lot of our sitting time is spent playing games.

Roaming does the motion detection once, on the device, so any game can add physical activity with a few lines of code instead of building its own sensor or camera pipeline.

## Safety first

Roaming makes people move while they look at a screen. Only use it in a safe environment and keep track of your surroundings; the app asks players to accept a safety warning and reminds them regularly while tracking. Roaming is provided without any warranty and the user is responsible for all use. See [Safety and disclaimer](./safety.md).

## How it works

1. The player opens Roaming, picks the inputs (step sensor, camera, or both) and taps **Start tracking**.
2. Roaming runs a foreground service with an ongoing notification. It turns sensor readings or camera frames into motion states such as `running` or `jumping`.
3. Every change is sent as a broadcast. Your app registers a receiver and reacts.
4. Optionally, Roaming also acts as a Bluetooth gamepad, so games on a computer, TV or tablet can be played with your body without any changes. See [Gamepad mode](./gamepad/overview.md).

```text
 step sensor / camera ──▶ Roaming classifier ──▶ broadcast se.spacify.app.roaming.event.MOTION ──▶ your game
```

## Next steps

- [The broadcast](./broadcast.md): what is sent and when
- [Event reference](./event-reference.md): every field and value
- Integrations: [Kotlin](./integrations/kotlin.md), [Java](./integrations/java.md), [Unity](./integrations/unity.md), [Godot](./integrations/godot.md)
