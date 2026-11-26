---
sidebar_position: 2
title: Gamepad for apps on this phone
---

# Gamepad for apps on this phone

**Act as a gamepad for apps on this phone** adds a virtual gamepad to the phone itself. Games and emulators running on the phone see it like a USB controller you plugged in, so you can play them with your body while Roaming tracks you in the background. It uses the same [mapping](./mapping.md) as the Bluetooth gamepad, and both can be on at once.

It needs **[Shizuku](https://shizuku.rikka.app)** (free, open source) or a rooted phone, because Android doesn't let normal apps do this.

## Why Shizuku is needed

Android keeps apps from sending input to each other: only the system, ADB (the developer shell) and root may do it. Roaming asks for the smallest step above a normal app:

| Route | Works? | Why or why not |
| --- | --- | --- |
| **Shizuku (ADB rights)** | **Yes, used** | Shizuku runs a helper with the same rights as ADB, which may create virtual input devices through the kernel's `uhid` interface. Android then sees a real gamepad, so every game that supports controllers works. No root and no computer needed on Android 11 and later. |
| **Root (Magisk, KernelSU…)** | **Yes, used** | Start Shizuku in root mode, or use [Sui](https://github.com/RikkaApps/Sui), which offers the same interface. Root also survives restarts. |
| Accessibility service | No | It can only tap and swipe the screen, not send gamepad buttons or sticks. Games that support controllers wouldn't see a gamepad. |
| `INJECT_EVENTS` permission | No | Only apps signed by the phone maker can hold it. |
| Bluetooth to itself | No | A phone can't pair with itself. |
| Android's virtual device APIs | No | Only system apps or apps that manage a companion device (such as a watch or car) may use them. |

## Set it up

1. Install **Shizuku** from Google Play or [GitHub](https://github.com/RikkaApps/Shizuku/releases).
2. Start Shizuku. On Android 11 and later you can do this on the phone with **Wireless debugging** (Shizuku walks you through pairing). On rooted phones, tap **Start** with root. See the [Shizuku setup guide](https://shizuku.rikka.app/guide/setup/).
3. In Roaming, turn on **Act as a gamepad for apps on this phone**, choose your inputs and tap **Start tracking**.
4. The first time, Shizuku asks whether Roaming may use it. Tap **Allow**.
5. The status says *Phone gamepad is active*. Open your game.

Stopping tracking releases all buttons and removes the virtual gamepad.

### After the phone restarts

Without root, Shizuku stops when the phone restarts. Start it again in the Shizuku app (step 2), then start tracking. Roaming says *Shizuku isn't running* when it needs this.

## Tips

- **Camera input:** prop the phone up so the front camera sees you, then switch to the game. Roaming keeps tracking in the background while its notification is shown.
- **Step sensor input:** works well for games you watch on the phone while walking or running in place with it in your hand. Cast or mirror the screen to a TV for a bigger view.
- If a game shows controller prompts, it has found the gamepad. Some games only read controllers that are connected before they start, so start tracking first, then open the game.
- Turn off the overlay bubble if it covers game controls.

## Things to know

- Roaming uses Shizuku only for the gamepad: Shizuku starts a small helper process (`se.spacify.app.roaming:gamepad`) that creates the device and passes on button and stick values. It accepts no other commands and stops when tracking stops.
- A few phone makers restrict ADB further. If the status says *Couldn't create the phone gamepad* with a permission error, the phone blocks it without root.
- Emulators and games that only accept specific controllers (by name or ID) may need you to map the "Roaming Gamepad" controller in their settings once.
