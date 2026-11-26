---
sidebar_position: 2
title: Mapping and HID layout
---

# Mapping motion to the gamepad

Each [`motion_type`](../event-reference.md#motion_type) drives one gamepad control. You can change the mapping in Roaming under **Gamepad → Motion to gamepad**; changes apply immediately, even while a device is connected, and are kept on the phone.

## Default mapping

| Motion | Gamepad control | Typical use in games |
| --- | --- | --- |
| Single step | Left stick (follows direction) | move |
| Walking | Left stick (follows direction) | move |
| Running | Left stick (follows direction) | move faster |
| Leaning | Left stick (follows direction) | steer / strafe |
| Jumping | A | jump |
| Squatting | B | crouch / dodge |
| Arms raised | Y | action / interact |
| Idle | nothing | |

## Controls you can choose

| Control | What it does |
| --- | --- |
| **Left stick / Right stick** | Follows the motion's direction. `forward` pushes the stick up, `backward` down, `left` / `right` sideways. Walking or running while steering left or right pushes it diagonally forward, so you keep moving while you turn. A jump (`up`) counts as forward and a squat (`down`) as back. |
| **D-pad** | Like a stick, but snapped to the 8 D-pad directions and always fully pressed. |
| **Left trigger / Right trigger** | Analog, pressed as far as the motion's intensity (0 to 1). Good for racing games: map running to the right trigger to accelerate. |
| **A, B, X, Y, L1, R1, L3, R3, Select, Start** | Held down while the motion lasts. |
| **Nothing** | The motion is ignored by the gamepad (it is still broadcast). |

### How intensity becomes stick deflection

Stick deflection is `0.3 + 0.7 × intensity`, so slow walking (intensity around 0.4) already pushes the stick about 60% and gets past the dead zone most games use, while sprinting pushes it all the way.

### Short motions

Every motion is held for at least 150 ms, so a single step or a quick jump always reaches the game as a full button press, even if the motion was over before the next report.

### Step sensor and camera together

When both inputs are on, the gamepad shows both: buttons from either input are pressed together, and for each stick or trigger the stronger value wins. For example you can run in place in front of the camera while leaning, or hold the phone and squat.

## HID layout

For developers writing their own mapping (Steam Input, SDL `gamecontrollerdb`, a game's input settings), the gamepad reports this layout. It follows the Linux / Android gamepad convention, so those hosts need no mapping at all.

**Bluetooth:** HID device, subclass *gamepad*, service name `Roaming Gamepad`. The host sees the phone's own Bluetooth name.

### Buttons

| HID button | Bit | Control | Linux / Android code |
| --- | --- | --- | --- |
| 1 | 0 | A | `BTN_SOUTH` / `KEYCODE_BUTTON_A` |
| 2 | 1 | B | `BTN_EAST` / `KEYCODE_BUTTON_B` |
| 3 | 2 | (C, unused) | `BTN_C` |
| 4 | 3 | X | `BTN_NORTH` / `KEYCODE_BUTTON_X` |
| 5 | 4 | Y | `BTN_WEST` / `KEYCODE_BUTTON_Y` |
| 6 | 5 | (Z, unused) | `BTN_Z` |
| 7 | 6 | L1 | `BTN_TL` / `KEYCODE_BUTTON_L1` |
| 8 | 7 | R1 | `BTN_TR` / `KEYCODE_BUTTON_R1` |
| 9, 10 | 8, 9 | (L2, R2, unused; triggers are analog) | `BTN_TL2`, `BTN_TR2` |
| 11 | 10 | Select | `BTN_SELECT` / `KEYCODE_BUTTON_SELECT` |
| 12 | 11 | Start | `BTN_START` / `KEYCODE_BUTTON_START` |
| 13 | 12 | (Mode, unused) | `BTN_MODE` |
| 14 | 13 | L3 | `BTN_THUMBL` / `KEYCODE_BUTTON_THUMBL` |
| 15 | 14 | R3 | `BTN_THUMBR` / `KEYCODE_BUTTON_THUMBR` |

### Axes

| HID usage | Control | Android axis |
| --- | --- | --- |
| X, Y | Left stick (Y grows downwards) | `AXIS_X`, `AXIS_Y` |
| Z, Rz | Right stick (Rz grows downwards) | `AXIS_Z`, `AXIS_RZ` |
| Brake | Left trigger | `AXIS_BRAKE` |
| Accelerator | Right trigger | `AXIS_GAS` |
| Hat switch | D-pad | `AXIS_HAT_X`, `AXIS_HAT_Y` |

### Input report

Report ID `1`, 9 bytes after the ID:

| Byte | Content |
| --- | --- |
| 0-1 | Buttons 1-16, one bit each, little endian |
| 2 | Hat switch in the low nibble: `0` up, clockwise to `7` up-left, `8` centered |
| 3, 4 | Left stick X, Y: signed, -127 to 127 |
| 5, 6 | Right stick Z, Rz: signed, -127 to 127 |
| 7, 8 | Left trigger, right trigger: 0 to 255 |

The full report descriptor is in [`GamepadReport.kt`](https://github.com/drsounds/roaming-android-app/blob/main/app/src/main/java/se/spacify/app/roaming/gamepad/GamepadReport.kt). The gamepad has no output reports (no rumble or player LEDs).
