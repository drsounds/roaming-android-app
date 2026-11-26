---
sidebar_position: 5
title: How motion is detected
---

# How motion is detected

## Step sensor

Uses the phone's hardware step detector, gravity sensor and linear acceleration sensor. The phone can be held in the hand or kept in a pocket.

- **Steps and cadence**: steps over the last 4 seconds give steps per minute. Below 140 is `walking`, 140 and above is `running`.
- **Steering**: tilting the top of the phone to the left or right (held upright) sets the direction to `left` / `right`. Standing still while tilting gives `leaning`.
- **Jumps**: upward acceleration (relative to gravity) above 6 m/s² is a jump.

Requires the **Physical activity** permission and a phone with a step detector.

## Camera

Uses [ML Kit Pose Detection](https://developers.google.com/ml-kit/vision/pose-detection) with its bundled model, so it works offline. The front camera runs at about 640×480. Each frame is analyzed in memory and then dropped.

All distances are measured relative to the player's torso length, so the player can stand at any distance.

- **Jump / squat**: hip height compared with a slowly adapting standing baseline.
- **Lean**: horizontal offset between shoulders and hips.
- **Arms raised**: both wrists above the nose.
- **Walking / running in place**: alternating knee lifts, counted like steps.

Prop the phone up so the player is visible at least from the head to the hips (to the knees for running in place).
