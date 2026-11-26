---
sidebar_position: 6
title: Privacy
---

# Privacy

- **Only local.** Roaming has no internet permission. It is removed from the app's manifest even if a library tries to add it.
- **No location.** Roaming never reads GPS.
- **No health data stored.** Only a few seconds of step timestamps stay in memory. Camera frames are never saved.
- **Always visible.** Broadcasts are only sent while the foreground service runs. Its notification is always shown and has a **Stop** button.
- **Gamepad mode is opt in.** When you turn it on, Roaming sends gamepad reports (button, stick and trigger values) over Bluetooth to the one device you paired. No camera frames, sensor data or anything else are sent.
- **Open source** under the Apache License 2.0.

Note that the broadcast itself can be received by any installed app while tracking is on. It only contains the current motion state, not raw sensor data or images.
