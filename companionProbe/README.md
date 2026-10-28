# Glucose Voice Alarm — phone

[Project overview](../README.md) · [Deutsch](README.de.md) · [GVA Watch](../companionWear/README.md) · [GVA Dots](../companionWatchFace/README.md)

The phone app receives supported CamAPS notifications, evaluates its configured alarms and supplies all other GVA displays. Version **0.20.5 Beta**. The phone works without either watch component.

**Experimental research tool; not for medical use or treatment decisions.** Read [SAFETY.md](../SAFETY.md). CamAPS continues to control the sensor and pump.

## Examples

Synthetic examples. Dashboard and setting images are native app renderings; the AOD image is a schematic of the information requested from Android, not a Pixel screenshot. Android controls its actual appearance.

<table><tr><td valign="top"><a href="../docs/images/phone-dashboard-dark.png"><img src="../docs/images/phone-dashboard-dark.png" width="230" alt="Dashboard"></a><br>Dashboard</td><td valign="top"><a href="../docs/images/phone-aod-schematic.svg"><img src="../docs/images/phone-aod-schematic.svg" width="420" alt="AOD · schematic"></a><br>AOD · schematic<br><br><a href="../docs/images/phone-widget-light.png"><img src="../docs/images/phone-widget-light.png" width="420" alt="Widget · light mode"></a><br>Widget · light mode</td></tr></table>

<details><summary>Example settings</summary>

<table><tr><td valign="top"><a href="../docs/images/phone-settings-output.png"><img src="../docs/images/phone-settings-output.png" width="260" alt="Output"></a><br>Output</td><td valign="top"><a href="../docs/images/phone-settings-levels.png"><img src="../docs/images/phone-settings-levels.png" width="260" alt="Alarm levels"></a><br>Alarm levels</td></tr></table>

</details>

The widget has an optional single row of sound, alarm speech, reading speech, vibration and output toggles. Its chart switches between labeled 30-minute, one-hour and two-hour windows as width changes. The reading/arrow and delta use the bundled round font in the launcher. The curve has a slightly thicker line; “Received: HH:mm” shows the original receipt time without a date. The entire remaining surface opens the app.

## Output and settings

Configure multiple alarm levels, individual tone/speech/vibration choices, repeats and snooze where applicable. Routine value announcements have their own interval and can pause after an alarm. Seven individual trend words and complete spoken templates can be edited; unchecked trend words are omitted. Five local profiles store settings. See the [detailed phone reference](../docs/PHONE_REFERENCE.md).

The CamAPS arrow is separate from the app's numeric rate calculation. A numeric critical-rate alarm can trigger without a recognized arrow when enough fresh values arrive. The optional data-gap warning uses the last known situation; it never estimates a current value.

## Build and set up

Follow [Build from source](../docs/BUILDING.md). Build target: `:companionProbe:assembleDebug`. Minimum declared Android API: 26; large native metric/live display: Android 17/API 37 when available. A normal notification is the fallback.

1. Install your own APK and acknowledge both safety notices yourself.
2. Enable notification access and receipt; allow app notifications and review background/battery settings.
3. Configure levels, outputs and an offline speech voice. Check synthetic previews and actual sound/vibration on your device.
4. Enable **Output enabled** on the dashboard. Enable it again after an update.
5. Optional: add the widget, or pair GVA Watch and separately enable display and alarm forwarding.

The phone/watch pair requires matching package names and signing keys. The app has no Internet or direct sensor permission. Settings, mappings, alarm state and a short diagnostic history stay in private app storage. Do not upload personal readings or device dumps to issues.

See [current limitations](../docs/STATUS.md), [GPLv3](../LICENSE) and [third-party notices](../THIRD_PARTY_NOTICES.md).
