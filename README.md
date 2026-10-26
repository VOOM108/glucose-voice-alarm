# Glucose Voice Alarm

[Deutsch](README.de.md)

Experimental Android companion for spoken glucose alarms, short sounds, vibration and a compact glucose display. It reads supported CamAPS notifications on the same phone. CamAPS continues to operate the sensor and pump.

**Beta research software. Not a certified or approved medical device. Do not use it for medical or treatment decisions, or rely on it for real-time alarms or time-critical information.** Any component can fail. Read the [safety notice](SAFETY.md) before building or using the app.

This repository contains source code. There are no packaged APK releases or automatic release uploads. [Build the app yourself](docs/BUILDING.md).

## Why this exists

With CamAPS already collecting glucose data and controlling therapy, a companion can concentrate on configurable alarms and spoken information. This project explores that narrower role without running a second full sensor/data-hub application. It reuses selected xDrip+ alarm rules and adds its own Android notification input, storage, interface and output handling. It does not claim the reliability or complete functionality of xDrip+.

## What it does

- High, low, rising fast, falling fast and persistent-high alarm types, with multiple configurable levels where useful.
- Configurable spoken templates, trend words, repeat/snooze behavior and a pause for routine reading announcements after an alarm.
- Separate sound, alarm speech, reading speech and vibration controls. Vibration can be the sole configured alarm channel. The master switch stops the app's own output.
- Three matching sets of short bell sounds, generated from source, plus Android sound selection and six vibration patterns.
- Optional recovery rules that suppress repeat high/low alarms, and a configurable data-gap warning with its own spoken text. The gap warning describes the last known situation; it never invents a current reading.
- A six-hour local diagnostic chart, light/dark/system themes, five local settings profiles, and German/English UI and default speech.
- Seven manually confirmable trend states, including double arrows. Unknown or conflicting graphics remain unknown; a source update invalidates an open assignment preview.
- Lock-screen/status notifications. Android 17's native large live display is attempted where available; Android controls promotion, placement and competition with other apps.

## Scope and limitations

The current version is 0.11.0 Beta. Development and device checks have focused on a Pixel 7 Pro with Android 17 and CamAPS FX 1.4(194).111. Other devices, Android variants and CamAPS versions need separate verification. The app targets supported CamAPS notifications in **mg/dl**. It does not offer arbitrary CGM inputs, mmol/l parsing, direct sensor/Bluetooth communication, pump control, a watch app or watch complications.

Android 8.0 (API 26) is the minimum declared version; that is not a compatibility guarantee. On systems without the native large display, the app uses a standard notification. Persistent AOD visibility and alarm delivery are not guaranteed. Battery restrictions, notification changes, missing data, audio settings and operating-system behavior can interrupt them.

The time shown is receipt/processing time, not a verified sensor measurement timestamp. A gap stays empty in the chart. A trend arrow alone does not trigger a numeric rate alarm. The imported rules and surrounding integration are described in [UPSTREAM.md](docs/UPSTREAM.md).

## Initial setup

1. Read [SAFETY.md](SAFETY.md) and [build the app](docs/BUILDING.md).
2. Grant Android notification access, enable receipt in the app, allow its notifications and review the battery/background settings shown by the app.
3. Configure alarm levels and output channels. Use synthetic previews and verify actual sound/speech/vibration on your device. App volume is relative to Android's alarm volume; system mute stays mute.
4. Enable **Output enabled** on the dashboard. If a prerequisite is missing, a red link appears below it. Updates require this switch to be enabled again.
5. Confirm an unknown arrow only when the frozen preview matches the source arrow. Use diagnostics to correct a prior assignment.

Speech uses an installed offline voice in the selected speech language. Existing custom text is preserved; changing language does not translate it. Recovery suppression and the data-gap warning are optional and initially disabled.

## Data and permissions

The app has no Internet, Bluetooth or sensor permission. It stores settings, confirmed arrow references, alarm state and a short diagnostic history in its private app storage. Android grants notification-listener access broadly; the parser accepts only its supported CamAPS sources. Do not post personal readings, notification dumps, screenshots containing health data, signing keys or credentials in issues.

## License and credits

GPL-3.0-or-later, with the complete [GPLv3 text](LICENSE). The four isolated xDrip classes retain their provenance and notices. [CREDITS.md](CREDITS.md), [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and [UPSTREAM.md](docs/UPSTREAM.md) explain the original authorship, changes and separately licensed build tooling. Product names identify compatibility only; they imply no affiliation or endorsement.
