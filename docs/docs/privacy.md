---
sidebar_position: 6
title: Privacy policy
---

# Privacy policy

_Last updated: 26 November 2026_

**In short: Roaming does not send any data from your phone to the publisher or to anyone else.** The app has no internet permission, no accounts, no analytics and no advertising. The publisher does not collect, receive or store any personal data about you.

## 1. Who is responsible

The publisher of Roaming, and the controller under the EU General Data Protection Regulation (GDPR) for any processing described in this policy, is:

> **Alexander Forselius**
> Floragatan 21D
> 531 37 Lidköping
> Sweden

Because no personal data leaves your device, the publisher has no access to data about you. You can still contact the publisher at the address above, or by opening an issue on the [GitHub repository](https://github.com/drsounds/roaming-android-app), with any privacy question.

## 2. No data is sent from your phone

- **No internet access.** Roaming's manifest removes the `INTERNET` and `ACCESS_NETWORK_STATE` permissions, even if a library tries to add them. The app cannot connect to a server.
- **No accounts, analytics, crash reporting, advertising or tracking.** There is no third-party SDK that collects data.
- **No cloud backup.** Backup of the app's data is turned off.
- **Everything is processed on your device.** The publisher never receives any of the information below.

## 3. What the app processes on your device

Roaming turns your movement into input for other apps and games. To do this it uses the following, only while its foreground service is running (its notification is visible and has a **Stop** button):

| Data | Permission | Purpose | What happens to it |
| --- | --- | --- | --- |
| Step events from the step detector | Physical activity (`ACTIVITY_RECOGNITION`) | Detect walking and running and their pace | Only a few seconds of step timestamps are kept in memory. Nothing is saved. |
| Camera frames (optional upper-body detection) | Camera (`CAMERA`) | Detect body pose, for example for elliptical or arm-based workouts | Frames are analysed on the device and discarded immediately. They are never saved, recorded or sent anywhere. |
| Bluetooth connection (optional gamepad mode) | Bluetooth (`BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`) | Act as a Bluetooth gamepad for a device you choose | Only gamepad reports (button, stick and trigger values) are sent, to the one device you pair. No camera frames, sensor data or anything else. |
| Notifications and overlay | Notifications, display over other apps | Show the always-visible service notification and the optional on-screen bubble | Local display only. |

Roaming never reads your location (GPS) and does not store health data.

Your chosen settings, such as the gamepad mapping, are stored locally on your device. You can remove them at any time by clearing the app's data or uninstalling it.

## 4. The motion broadcast

While tracking is on, Roaming sends a local Android broadcast that contains the current motion state (for example "walking" or "running"). Any app installed on your device can receive it, and that is the purpose of the feature. It contains no raw sensor data and no images, and it never leaves your device. See [The broadcast](broadcast.md).

Apps that receive the broadcast are separate from Roaming. Their handling of data is governed by their own privacy policies, not this one.

## 5. Gamepad mode and Bluetooth

Gamepad mode is opt-in. When you turn it on, your phone appears as a Bluetooth gamepad and sends input to the single device you pair with. That transmission is made at your request, directly to your own paired device, and not to the publisher. The receiving device is not controlled by the publisher.

## 6. Legal basis

Since the publisher does not receive or process personal data, there is no processing by the publisher that needs a legal basis. The on-device processing in section 3 happens only because you granted the relevant permission and started the service, and you can stop it or revoke the permission at any time.

## 7. Retention

The publisher stores nothing. On your device, only a few seconds of step timestamps are kept in memory while the service runs. Camera frames are not kept at all.

## 8. Your rights

Under the GDPR you have the right to access, rectify, erase and restrict the processing of your personal data, to data portability, and to object to processing. Because the publisher holds no personal data about you, there is nothing to access, correct or delete on the publisher's side. You keep full control on your device: you can stop the service, revoke any permission in Android settings, clear the app's data or uninstall the app.

You also have the right to lodge a complaint with a supervisory authority. In Sweden this is the Swedish Authority for Privacy Protection (Integritetsskyddsmyndigheten, IMY), [www.imy.se](https://www.imy.se).

## 9. Children

Roaming does not knowingly collect data from anyone, including children, because it collects no data at all.

## 10. Open source

Roaming is open source under the Apache License 2.0. You can verify everything stated here in the [source code](https://github.com/drsounds/roaming-android-app), including the manifest that removes the network permissions.

## 11. Changes to this policy

If the app changes in a way that affects privacy, this policy will be updated and the date at the top changed. The full history is in the repository.
