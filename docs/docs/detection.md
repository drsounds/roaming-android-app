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

### Upper body only (elliptical, bike, selfie mode)

When only the head and shoulders are in view, for example with the phone on the console of an elliptical or exercise bike, or held in selfie mode, Roaming still reports `walking` and `running`. It also takes over whenever the full-body checks above see nothing, so stepping in place with the knees out of frame works too.

- **Intensity**: how fast the head, elbows and wrists move, in shoulder widths per second, smoothed over about a second. Barely moving (below 0.3) counts as nothing; 1.5 and above is full intensity.
- **Rhythm**: steps are counted from whichever signal swings most clearly: the arms moving in opposite directions (one step per swing), the elbows, the head swaying side to side, or the whole upper body bobbing (one bob per step). The cadence gives `walking`, or `running` at 150 steps/min and above.
- **Both are needed**: a rhythm of at least 3 steps within 3 seconds, and movement above the noise floor. Talking with your hands or shifting in your seat stays `idle`.
- **Arms raised**: both wrists above the nose is still `arms_raised`.

On an exercise bike the upper body moves much less than on an elliptical, so keep your arms in view or rock with the pedalling, otherwise it may stay `idle`.

Expect about two seconds of movement before the first `walking` event, and about 1.5 seconds after you stop before `idle`.

#### Hand-held phone

When the phone is held in the hand, the whole image moves with it. Arm and head movement are measured relative to the shoulders, in shoulder widths, so they are not affected by the phone moving, turning or coming closer. The whole-body bob can only be seen relative to the image, so Roaming reads the gyroscope: when the phone turns more than about 0.2 rad/s, the bob is trusted less, and above 1 rad/s it is ignored. Keep your arms in view when holding the phone, since the bob alone isn't used then.

The thresholds are first estimates and may need tuning on real devices.
