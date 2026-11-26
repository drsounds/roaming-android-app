---
sidebar_position: 1
title: Gamepad mode
---

# Gamepad mode

Gamepad mode lets the phone act as a **standard Bluetooth gamepad**. A computer, TV, tablet or another phone pairs with it like any wireless controller, and your motion moves the sticks and presses the buttons. Games don't need to know anything about Roaming: if they support a gamepad, they work.

```text
 step sensor / camera ──▶ Roaming ──▶ motion-to-gamepad mapping ──▶ Bluetooth HID gamepad ──▶ PC / TV / tablet
                                 └──▶ broadcast se.spacify.app.roaming.event.MOTION ──▶ apps on this phone
```

The broadcast keeps working while gamepad mode is on, so apps on the phone and a paired device can both follow your motion.

## Requirements

- Android 9 or later, with Bluetooth.
- The phone must allow apps to use the Bluetooth HID device role. Most phones do. A few manufacturers turn it off; Roaming then shows *"This phone doesn't allow apps to act as a Bluetooth gamepad"*.
- The **Nearby devices** permission (Android 12 and later).

## Set it up

1. In Roaming, turn on **Act as a Bluetooth gamepad**, choose your inputs (step sensor, camera or both) and tap **Start tracking**.
2. The status line says *Waiting for a device* and shows the phone's Bluetooth name.
3. Pair the first time:
   - Tap **Make this phone discoverable** and allow it.
   - On the other device, open its Bluetooth settings, look for the phone's name and pair. It appears as a gamepad / game controller.
4. Later sessions: Roaming reconnects to the last device on its own. To pick another one that is already paired, tap **Connect to a paired device**.
5. The status changes to *Connected to …*. Start moving.

Stopping tracking (in the app or with **Stop** in the notification) releases all buttons, disconnects and unregisters the gamepad.

## Where it works

| Host | Works? | Notes |
| --- | --- | --- |
| Windows 10 / 11 | Yes | Shows up as a game controller (DirectInput). Games that only accept Xbox (XInput) controllers need [Steam Input](https://partner.steamgames.com/doc/features/steam_controller) or a similar mapper. |
| Linux, SteamOS | Yes | Handled by the generic HID driver; the button layout matches Linux's gamepad layout. |
| Android, Android TV / Google TV, ChromeOS | Yes | Buttons, sticks and triggers are labelled correctly without remapping. |
| Web browsers | Yes | Visible through the [Gamepad API](https://developer.mozilla.org/docs/Web/API/Gamepad_API) (as a non-"standard" mapping on some platforms). |
| Steam on any OS | Yes | Steam Input can remap it like any generic controller. |
| macOS, iOS, iPadOS | Partly | Apps built on SDL, Steam or the browser see it. Apps that use Apple's Game Controller framework (for example Apple Arcade) only accept the controllers Apple supports. |
| PlayStation, Xbox, Nintendo Switch | No | Consoles only accept their own (or licensed) controllers. |
| Games on the same phone | No | See below. |

### Why not games on the same phone?

Android doesn't let an app inject gamepad input into other apps (that needs root or system privileges), and a phone can't pair with itself over Bluetooth. Games on the phone itself should listen to the [broadcast](../broadcast.md) instead; see the [integration guides](../integrations/kotlin.md).

## Battery and privacy

Gamepad mode only uses classic Bluetooth while tracking runs. Reports are sent only when the gamepad state changes (checked 50 times a second). Nothing leaves the phone except those gamepad reports to the device you paired: buttons, stick positions and trigger values. No camera frames or sensor data are sent. See [Privacy](../privacy.md).

Next: [mapping motion to the gamepad](./mapping.md).
