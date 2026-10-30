# Glucose Voice Alarm

[Deutsch](../README.de.md)

Experimental Android companion for spoken glucose alarms, short sounds, vibration and a compact glucose display. It reads supported CamAPS notifications on the same phone. CamAPS continues to operate the sensor and pump.

**Beta research software. Not a certified or approved medical device. Do not use it for medical or treatment decisions, or rely on it for real-time alarms or time-critical information.** Any component can fail. Read the [safety notice](../SAFETY.md) before building or using the app.

This repository contains source code. There are no packaged APK releases or automatic release uploads. [Build the app yourself](../docs/BUILDING.md).


The native status-bar chip receives only the number in the selected unit, without an arrow or unit label. AOD and the dashboard retain the value and arrow; the AOD time is unchanged. Android controls whether and how much text is shown in the status bar.

## Home-screen widget

Phone version 0.20.0 introduced a resizable GVA widget. From 0.20.2, the large reading and CamAPS arrow share the left side, with a flat chart beside them on the right. The original receipt time, marked “Received:” (“Empfangen:” in German), unit and delta per minute appear below. Units, labels and light/dark appearance follow the app; the timestamp is not a verified sensor measurement time.

Depending on the available width, the chart shows 30 minutes, one or two hours, labeled directly below as “30 min”, “1 h” or “2 h”. GVA reserves space for the reading and arrow first and shortens the chart window as width decreases. The label describes the visible time window; missing data stays empty within it. The dashboard diagnostic chart still covers six hours.

From 0.21.3, the arrow grows with widget width. It uses spare space beside the reading while preserving the numeral size and chart area. Narrow widgets limit the arrow to the space available.

Long-press an empty area of the home screen and select Widgets → Glucose Voice Alarm → GVA · Glucose. From 0.20.3, the minimum height is 120 dp. From 0.20.5, the reading/arrow and delta are drawn as sharp, bounded images so the bundled round font survives the launcher’s restricted context. Time and history-window labels remain native text views. The widget bundles a heavy, rounded instance of Google Sans Flex for the reading and delta under SIL OFL 1.1, without relying on a particular font being installed on the phone. The watch continues to use Roboto Flex. The reading is left-aligned, with the arrow beside it. A shared size reference and a capped image height keep two- and three-digit readings at comparable sizes. The chart uses a medium-weight line and a fixed drawing height, so inaccurate launcher height hints do not change its relative weight. The history-window label is larger, and the chart uses the available height. There is no fixed 4×2 grid request. The launcher determines the grid size and required cells from the minimum dimensions. You can make an existing widget shorter there.

The toggle row remains visible at low heights: sound, alarm speech (“Alarm”), reading speech (“Reading”), vibration and the output master switch. Toggles are larger, with extra space and a subtle divider separating output from the four channels when width permits. Switch positions show on/off; during a running global pause, the master label reads “Paused”. Tapping elsewhere opens the dashboard. Profiles and other settings remain available in the app. Launchers that support widget editing can still hide the toggle row for each widget.

From 0.21.1, toggles switch directly in the widget without briefly showing the app. If a required setting is missing when the widget is built, such as the reading interval or an alarm output route, that toggle opens the existing setup screen. Missing permissions when enabling output also open setup. If a prerequisite changes after the widget is built, the switch is left unchanged and the widget refreshes; setup problems show a brief message. Stale clicks and clicks on a locked device cannot change settings.

At least one effective alarm route must remain available. If a prerequisite is missing, the app shows its existing validation and does not apply an invalid change. Old clicks from a superseded widget view cannot change newer settings. A locked device must be unlocked first. Adding, resizing or refreshing the widget does not enable output.

During a data gap, the last known reading can be used for display for up to two hours. Stale readings are drawn gray, the timestamp red, and delta is omitted. Android may defer updates and keep displaying a previously received image. Check the timestamp; the widget is not a dependable alarm or service-status display. GVA uses existing receipts without starting its own minute-by-minute polling. On the next rebuild after a process restart, history is not used to restore an old current reading. Launcher sizing and background behavior need testing on each device.

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

## New high/low alarm episodes

For high/low alarms, the automatic repeat interval applies within one continuous alarm episode per level. A fresh valid reading below the high threshold or above the low threshold ends that episode. Crossing the threshold again can trigger a new first alarm through the enabled speech, sound and vibration routes. A reading exactly on the threshold still meets the existing alarm condition.

This numerical recovery does not cancel manual snooze or the global pause. Data gaps, invalid or stale readings and a closed schedule alone do not reset the repeat. A missing trend arrow does not change the assessment of a valid numeric reading. Multiple levels in the same direction retain their own deadlines when a higher level starts. A previously announced lower level that remains continuously exceeded therefore does not restart early when the higher level subsides.

Updates do not retrospectively clear stored deadlines. The new policy needs a newly received valid reading back inside the relevant threshold. A separate persistent condition notification with the suppression reason and remaining time is not yet implemented.

## Speech after voice initialization

If the offline voice is still initializing when a live reading alarm starts through sound or vibration, its missing speech may follow once within ten seconds of alarm start. GVA first rechecks the current alarm condition, schedule and freshness of the available data, then renders the text with the current reading. Sound and vibration are not repeated. Speech is omitted when the deadline has passed or the condition no longer holds. Switching output off, pausing, losing the source, or changing settings or arrow mappings discards the pending speech. A service restart does not restore it. Manual and synthetic previews and data-gap warnings do not use this deferred speech path.

Under Background & AOD → Details and permissions, the last alarm status lists speech, sound and vibration separately. Routine reading announcements do not overwrite it. Its private cache contains fixed technical states without readings, spoken text, alarm identifiers or timestamps. Submission to Android or the voice engine does not confirm that an output was audible or perceptible.

## Scope and limitations

The prepared phone version is 0.22.0 Beta. Development and phone device checks have focused on a Pixel 7 Pro with Android 17 and CamAPS FX 1.4(194).111. Other devices, Android variants and CamAPS versions need separate verification. The app targets supported CamAPS notifications in **mg/dl or mmol/l**. It does not offer arbitrary CGM inputs, direct sensor communication or pump control.

Optional [GVA Watch and the GVA Dots face](../companionWear/README.en.md) have been tested in part on a Galaxy Watch5 Pro running Wear OS 6 / One UI 8 Watch. They show readings and a short chart, with optional forwarding of accepted phone alerts. Phone-to-watch transfer starts off. Complication timing, alarm delivery and battery use need further device testing.

Android 8.0 (API 26) is the minimum declared version; that is not a compatibility guarantee. On systems without the native large display, the app uses a standard notification. Persistent AOD visibility and alarm delivery are not guaranteed. Battery restrictions, notification changes, missing data, audio settings and operating-system behavior can interrupt them.

The time shown is receipt/processing time, not a verified sensor measurement timestamp. A gap stays empty in the chart. A trend arrow alone does not trigger a numeric rate alarm. The imported rules and surrounding integration are described in [UPSTREAM.md](../docs/UPSTREAM.md).

## Watch display

GVA Watch 0.19.0 Beta and the code-free GVA Dots 0.1.5 Beta face work with the prepared phone version 0.22.0 Beta. The round display uses Roboto Flex, a one- or two-hour curve and a reduced ambient view. Menus use official Wear Material 3 components. The watch app also supplies a tile with the same data layout, without time dots. Alongside the composite display and age source, separate reading, CamAPS trend, delta per minute and chart sources can be selected in compatible third-party faces. The watch displays data and can relay accepted phone alarms with vibration. It does not calculate glucose alarms independently. See [watch setup and limits](../companionWear/README.en.md). Everyday device testing continues; the new standalone complications and tile still need device acceptance.

From version 0.18.0, forwarding GVA alarms and data-gap warnings to GVA Watch can be enabled separately from display transfer. The watch creates its own notifications with vibration. Both apps require opt-in; Appearance → Watch includes a labeled test without readings. Forwarding follows accepted phone alarm outputs, without offline replay. Routine readings do not create watch notifications. [Setup and limits](../companionWear/README.en.md#watch-alerts).

Appearance → Watch shows the last send attempt separately for display data and alerts, each with its time. Test, alarm, text update and clear events have distinct labels. This status does not confirm display or vibration on the watch.

## Units

Supported CamAPS package variants supply mg/dl or mmol/l. The first unambiguous valid receipt establishes the source unit from the known package and saves it. Any supplied unit label must agree. The source unit survives app restarts. A later unit conflict rejects the notification and shows a notice; the app never infers units from the size of a number.

By default, the dashboard, chart, AOD, speech and threshold fields follow that unit. Appearance → Developer options can select a fixed mg/dl or mmol/l output after confirmation. Conversion does not change existing alarm thresholds. Saving an unchanged level preserves its original threshold even when the field shows a rounded value. Check custom names and literal units in speech templates yourself; placeholders such as `{value}` and `{unit}` are converted together. The compact AOD still omits the unit label.

Detect CamAPS unit again requires confirmation, disables live output and clears the current receipt window. The next valid notification then establishes the source unit. Profiles include the fixed output choice; the detected source unit remains a local device setting.

Values and alarm thresholds remain in canonical mg/dl with fractional precision. Conversion uses 18.0182, matching the pinned xDrip revision. Rounding happens only for output: whole mg/dl or one decimal place for mmol/l. Rates use one or two decimal places per minute respectively. mmol/l receipt has synthetic test coverage; real-device acceptance of that source variant remains outstanding.

## Trend and rate of change

The trend direction comes from CamAPS on the dashboard, AOD/lock screen and in speech. A missing arrow is not replaced with a calculated one. Under Appearance → Display trend text, you can edit the seven displayed phrases for the dashboard and AOD separately from speech. Blank fields use the default in the UI language. Keep phrases short: Android may truncate long AOD text. Stale readings still show their age there. The double-arrow defaults are “Rising very fast” and “Falling very fast”.

Edit spoken phrases under Output → Trend phrases. A checkbox beside each word controls whether it is spoken. Unchecked words are omitted like a missing trend while their text is preserved. New speech defaults also use “rising very fast” and “falling very fast”. Stored speech phrases are retained; you can replace them with defaults after confirmation in the output editor. Profiles store display and speech text separately. Neither changes arrow identity or alarm conditions.

The dashboard also shows the app's own signed rate in a separate smaller line, in the selected unit per minute. It uses the last four valid receipt-minute points, keeping the last reading in each minute, and the actual elapsed time. Insufficient or stale data hides the line. Times refer to receipt; gaps are not filled.

During a data gap, the dashboard and AOD/lock screen retain the last known reading and CamAPS arrow for up to two hours. The reading turns gray and its original time turns red; the notification also shows age in minutes. Android may override colors in the native display. This display cache remains separate from fresh input for alarms and speech. New readings, an app process restart or an explicit reset end the old display state. Stored history is never replayed as a new receipt.

Critical fall rate and Critical rise rate check their configured thresholds independently of arrows and the routine reading speech interval. The latest interval must also qualify. They can trigger without an arrow when enough fresh readings arrive. Missing readings are covered by the separately configured gap warning. Default speech is “Critical fall rate at {value}.” and “Critical rise rate at {value}.” Saved custom speech and level names are preserved.

The pencil beside the dashboard trend remains available for known arrows, so an existing mapping can be checked and corrected. The dialog holds its graphic preview fixed; after a new CamAPS notification, reload it before saving. If no current graphic is available, a message explains that the next update is needed. A correction stops current and pending GVA audio. The last displayed reading and its original receipt time remain available; output waits for new input. Arrow mappings stay in private app storage and are included in neither this source project nor settings profiles.

## Initial setup

1. Read [SAFETY.md](../SAFETY.md) and [build the app](../docs/BUILDING.md).
2. Open the app and explicitly acknowledge both the warning and terms of use. Existing installations also need these acknowledgements once after updating to 0.15.
3. Grant Android notification access, enable receipt in the app, allow its notifications and review the battery/background settings shown by the app.
4. Configure alarm levels and output channels. Use synthetic previews and verify actual sound/speech/vibration on your device. App volume is relative to Android's alarm volume; system mute stays mute.
5. Turn on **Output enabled** on the dashboard. If a prerequisite is missing, a red link appears below it. Updates require this switch to be enabled again.
6. Confirm an unknown arrow only when the frozen preview matches the source arrow. Use diagnostics to correct a prior assignment.

Speech uses an installed offline voice in the selected speech language. Existing custom text is preserved; changing language does not translate it. Recovery suppression and the data-gap warning are optional and initially disabled.

## Data and permissions

The app has no Internet, Bluetooth or sensor permission. It stores settings, confirmed arrow references, alarm state and a short diagnostic history in its private app storage. Android grants notification-listener access broadly; the parser accepts only its supported CamAPS sources. Do not post personal readings, notification dumps, screenshots containing health data, signing keys or credentials in issues.

## License and credits

GPL-3.0-or-later, with the complete [GPLv3 text](../LICENSE). The four isolated xDrip classes retain their provenance and notices. [CREDITS.md](../CREDITS.md), [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md) and [UPSTREAM.md](../docs/UPSTREAM.md) explain the original authorship, changes and separately licensed build tooling. Product names identify compatibility only; they imply no affiliation or endorsement.
