# Glucose Voice Alarm

[Deutsch](README.de.md)

Experimental Android companion for spoken glucose alarms, short sounds, vibration and a compact glucose display. It reads supported CamAPS notifications on the same phone. CamAPS continues to operate the sensor and pump.

**Beta research software. Not a certified or approved medical device. Do not use it for medical or treatment decisions, or rely on it for real-time alarms or time-critical information.** Any component can fail. Read the [safety notice](SAFETY.md) before building or using the app.

This repository contains source code. There are no packaged APK releases or automatic release uploads. [Build the app yourself](docs/BUILDING.md).

## Reconnecting reception

The app requests a missing Android listener connection when opened, when output starts and after a reported disconnection. While the background service is running, further attempts are limited to one every 30 seconds. Reception must already be enabled and notification access granted by you; the app does not grant access or enable output itself.

Display and background contains “Reconnect reception”. Its details show the connection status. A connected listener is not restarted merely because readings are missing. Existing notifications are never imported as new readings.

The Android connection may remain bound while the first-start notices are open. The app still processes no source notifications until both notices have been acknowledged.

## First-start acknowledgement

Before first use, the app requires two separate acknowledgements: the important warning, followed by the terms of use with “I understand”. Both checkboxes start unchecked. App features, notification reception and all own output remain blocked until both steps are acknowledged. Existing installations also see these steps when this check is introduced. Afterwards, you must enable active output yourself.

Acknowledgements stay on the device and are excluded from settings profiles. Ordinary updates do not ask again; a change to the meaning of the notices requires a new acknowledgement. Appearance → Safety and terms lets you reread the notices and the complete GPLv3 license at any time.

## Why this exists

With CamAPS already collecting glucose data and controlling therapy, a companion can concentrate on configurable alarms and spoken information. This project explores that narrower role without running a second full sensor/data-hub application. It reuses selected xDrip+ alarm rules and adds its own Android notification input, storage, interface and output handling. It does not claim the reliability or complete functionality of xDrip+.

## What it does

- High, low, critical rise rate, critical fall rate and persistent-high alarm types, with multiple configurable levels where useful.
- Configurable spoken templates, trend words, repeat/snooze behavior and a pause for routine reading announcements after an alarm.
- Separate sound, alarm speech, reading speech and vibration controls. Vibration can be the sole configured alarm channel. The master switch stops the app's own output.
- Three matching sets of short bell sounds, generated from source, plus Android sound selection and six vibration patterns.
- Optional recovery rules that suppress repeat high/low alarms, and a configurable data-gap warning with its own spoken text. The gap warning describes the last known situation; it never invents a current reading.
- A six-hour local diagnostic chart, light/dark/system themes, five local settings profiles, and German/English UI and default speech.
- Seven manually confirmable trend states, including double arrows. Unknown or conflicting graphics remain unknown; a source update invalidates an open assignment preview.
- Lock-screen/status notifications. Android 17's native large live display is attempted where available; Android controls promotion, placement and competition with other apps.

## Scope and limitations

The current version is 0.15.1 Beta. Development and device checks have focused on a Pixel 7 Pro with Android 17 and CamAPS FX 1.4(194).111. Other devices, Android variants and CamAPS versions need separate verification. The app targets supported CamAPS notifications in **mg/dl or mmol/l**. It does not offer arbitrary CGM inputs, direct sensor/Bluetooth communication, pump control, a watch app or watch complications.

Android 8.0 (API 26) is the minimum declared version; that is not a compatibility guarantee. On systems without the native large display, the app uses a standard notification. Persistent AOD visibility and alarm delivery are not guaranteed. Battery restrictions, notification changes, missing data, audio settings and operating-system behavior can interrupt them.

The time shown is receipt/processing time, not a verified sensor measurement timestamp. A gap stays empty in the chart. A trend arrow alone does not trigger a numeric rate alarm. The imported rules and surrounding integration are described in [UPSTREAM.md](docs/UPSTREAM.md).

## Units

Supported CamAPS package variants supply mg/dl or mmol/l. The first unambiguous valid receipt establishes the source unit from the known package and saves it. Any supplied unit label must agree. The source unit survives app restarts. A later unit conflict rejects the notification and shows a notice; the app never infers units from the size of a number.

By default, the dashboard, chart, AOD, speech and threshold fields follow that unit. Appearance → Developer options can select a fixed mg/dl or mmol/l output after confirmation. Conversion does not change existing alarm thresholds. Saving an unchanged level preserves its original threshold even when the field shows a rounded value. Check custom names and literal units in speech templates yourself; placeholders such as `{value}` and `{unit}` are converted together. The compact AOD still omits the unit label.

Detect CamAPS unit again requires confirmation, disables live output and clears the current receipt window. The next valid notification then establishes the source unit. Profiles include the fixed output choice; the detected source unit remains a local device setting.

Values and alarm thresholds remain in canonical mg/dl with fractional precision. Conversion uses 18.0182, matching the pinned xDrip revision. Rounding happens only for output: whole mg/dl or one decimal place for mmol/l. Rates use one or two decimal places per minute respectively. mmol/l receipt has synthetic test coverage; real-device acceptance of that source variant remains outstanding.

## Trend and rate of change

The arrow and trend word come from CamAPS on the dashboard, AOD/lock screen and in speech. A missing arrow is not replaced with a calculated one. All seven spoken trend words can be edited. A checkbox beside each word controls whether it is spoken. Unchecked words are omitted like a missing trend while their text is preserved. Profiles include these switches; displayed arrows and alarm conditions are unchanged.

The dashboard also shows the app's own signed rate in a separate smaller line, in the selected unit per minute. It uses the last four valid receipt-minute points, keeping the last reading in each minute, and the actual elapsed time. Insufficient or stale data hides the line. Times refer to receipt; gaps are not filled.

Critical fall rate and Critical rise rate check their configured thresholds independently of arrows and the routine reading speech interval. The latest interval must also qualify. They can trigger without an arrow when enough fresh readings arrive. Missing readings are covered by the separately configured gap warning. Default speech is “Critical fall rate at {value}.” and “Critical rise rate at {value}.” Saved custom speech and level names are preserved.

## Initial setup

1. Read [SAFETY.md](SAFETY.md) and [build the app](docs/BUILDING.md).
2. Open the app and explicitly acknowledge both the warning and terms of use. Existing installations also need these acknowledgements once after updating to 0.15.
3. Grant Android notification access, enable receipt in the app, allow its notifications and review the battery/background settings shown by the app.
4. Configure alarm levels and output channels. Use synthetic previews and verify actual sound/speech/vibration on your device. App volume is relative to Android's alarm volume; system mute stays mute.
5. Turn on **Output enabled** on the dashboard. If a prerequisite is missing, a red link appears below it. Updates require this switch to be enabled again.
6. Confirm an unknown arrow only when the frozen preview matches the source arrow. Use diagnostics to correct a prior assignment.

Speech uses an installed offline voice in the selected speech language. Existing custom text is preserved; changing language does not translate it. Recovery suppression and the data-gap warning are optional and initially disabled.

## Data and permissions

The app has no Internet, Bluetooth or sensor permission. It stores settings, confirmed arrow references, alarm state and a short diagnostic history in its private app storage. Android grants notification-listener access broadly; the parser accepts only its supported CamAPS sources. Do not post personal readings, notification dumps, screenshots containing health data, signing keys or credentials in issues.

## License and credits

GPL-3.0-or-later, with the complete [GPLv3 text](LICENSE). The four isolated xDrip classes retain their provenance and notices. [CREDITS.md](CREDITS.md), [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and [UPSTREAM.md](docs/UPSTREAM.md) explain the original authorship, changes and separately licensed build tooling. Product names identify compatibility only; they imply no affiliation or endorsement.
