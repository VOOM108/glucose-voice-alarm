# Glucose Voice Alarm — phone

[Project overview](../README.md) · [Deutsch](README.de.md) · [GVA Watch](../companionWear/README.md) · [GVA Dots](../companionWatchFace/README.md)

The phone app receives supported CamAPS notifications, evaluates its configured alarms and supplies all other GVA displays. Version **0.23.0 Beta**. The phone works without either watch component.

**Experimental research tool; not for medical use or treatment decisions.** Read [SAFETY.md](../SAFETY.md). CamAPS continues to control the sensor and pump.

## Examples

Synthetic examples. Dashboard and setting images are native app renderings; the AOD image is a schematic of the information requested from Android, not a Pixel screenshot. Android controls its actual appearance.

<table><tr><td valign="top"><a href="../docs/images/phone-dashboard-dark.png"><img src="../docs/images/phone-dashboard-dark.png" width="230" alt="Dashboard"></a><br>Dashboard</td><td valign="top"><a href="../docs/images/phone-aod-schematic.svg"><img src="../docs/images/phone-aod-schematic.svg" width="420" alt="AOD · schematic"></a><br>AOD · schematic<br><br><a href="../docs/images/phone-widget-light.png"><img src="../docs/images/phone-widget-light.png" width="420" alt="Widget · light mode"></a><br>Widget · light mode</td></tr></table>

<details><summary>Example settings</summary>

<table><tr><td valign="top"><a href="../docs/images/phone-settings-output.png"><img src="../docs/images/phone-settings-output.png" width="260" alt="Output"></a><br>Output</td><td valign="top"><a href="../docs/images/phone-settings-levels.png"><img src="../docs/images/phone-settings-levels.png" width="260" alt="Alarm levels"></a><br>Alarm levels</td><td valign="top"><a href="../docs/images/phone-settings-trends.png"><img src="../docs/images/phone-settings-trends.png" width="260" alt="Display trend text"></a><br>Display trend text</td></tr></table>

</details>

The widget has an optional single row of sound, alarm speech, reading speech, vibration and output toggles. They switch directly when their prerequisites are met; required setup opens the app. Its chart shows labeled 30-minute, one-hour or two-hour windows as width changes. Rounded numerals keep their size limit while the arrow grows into available space at wider sizes. The receipt caption follows the app language: "Received: HH:mm" in English and "Empfangen: HH:mm" in German. The remaining surface opens the dashboard.

## Output and settings

Configure multiple alarm levels, individual tone/speech/vibration choices, repeats and snooze where applicable. Routine value announcements have their own interval and can pause after an alarm. Spoken templates and all seven spoken trend phrases can be edited; unchecked phrases are omitted. Appearance → Display trend text edits the dashboard/AOD labels separately. Blank labels follow the UI language. Double-arrow defaults are "Rising very fast" and "Falling very fast"; saved speech text is retained. Five local profiles store both text sets independently. See the [detailed phone reference](../docs/PHONE_REFERENCE.md).

The CamAPS arrow is separate from the app's numeric rate calculation. A numeric critical-rate alarm can trigger without a recognized arrow when enough fresh values arrive. The optional data-gap warning uses the last known situation; it never estimates a current value.

## Persistent-high duration

Duration starts with the first valid received reading above this persistent-high level's threshold. It includes stable or falling readings and time without data. A fresh reading at or below that threshold ends the episode. Missing, invalid or stale data does not confirm that it has ended.

Once GVA detects a data gap or receipt is stale, it does not issue a current reading-based alarm. The persistent-high trigger still requires a fresh rising reading and the imported xDrip data-quality check, which includes a minimum sampling density and a one-day limit. Elapsed duration alone therefore does not guarantee output. The alarm repeat deadline remains separate.

After a gap, rate calculations use only new contiguous readings. For persistent-high quality checks, GVA separately retains up to 500 actual receipts within the running service. It does not estimate missing readings. Chart gaps remain visible even if CamAPS later fills its own history; those retrospective points are not imported from the notification.

Reloading settings or restarting the service within the same phone boot does not discard the saved beginning solely because receipt was interrupted. A real clock or boot discontinuity starts the check again conservatively. Changed alarm conditions are reconciled with stored state as before. An update does not reconstruct a beginning already discarded by an older version.

## New high/low alarm episodes

For high/low alarms, the automatic repeat interval applies within one continuous alarm episode per level. A fresh valid reading below the high threshold or above the low threshold ends that episode. Crossing the threshold again can trigger a new first alarm through the enabled speech, sound and vibration routes. A reading exactly on the threshold still meets the existing alarm condition.

This numerical recovery does not cancel manual snooze or the global pause. Data gaps, invalid or stale readings and a closed schedule alone do not reset the repeat. A missing trend arrow does not change the assessment of a valid numeric reading. Multiple levels in the same direction retain their own deadlines when a higher level starts. A previously announced lower level that remains continuously exceeded therefore does not restart early when the higher level subsides.

Updates do not retrospectively clear stored deadlines. The new policy needs a newly received valid reading back inside the relevant threshold. A separate persistent condition notification with the suppression reason and remaining time is not yet implemented.

## Processing diagnostics

Under Background & AOD → Details and permissions, the last new input, last alarm attempt and last output attempt are shown separately. The input snapshot distinguishes a new alarm start from a silent notification refresh. For a threshold alarm that is not yet due, it identifies the repeat interval, snooze or another level's deadline where readable. The last output attempt distinguishes routine value speech, an alarm and a data-gap warning, with the requested channels and acceptance or rejection.

The separate last-alarm status lists speech, sound and vibration. Input and attempt numbers link it to the processing records within one service run. Timer checks do not overwrite the last input snapshot, and routine value speech does not replace the last alarm attempt. The bounded private caches contain only fixed technical states and counters, without readings, spoken text, alarm-level identifiers or timestamps. They reset when the service starts and do not form an event history. Acceptance or a completed callback does not prove that output was audible or perceptible.

## Speech after voice initialization

If the offline voice is still initializing when a live reading alarm starts through sound or vibration, its missing speech may follow once within ten seconds of alarm start. GVA first rechecks the current alarm condition, schedule and freshness of the available data, then renders the text with the current reading. Sound and vibration are not repeated. Speech is omitted when the deadline has passed or the condition no longer holds. Switching output off, pausing, losing the source, or changing settings or arrow mappings discards the pending speech. A service restart does not restore it. Manual and synthetic previews and data-gap warnings do not use this deferred speech path.

## Arrow mappings

The pencil beside the dashboard trend remains available for known arrows, so an existing mapping can be checked and corrected. The dialog holds its graphic preview fixed; after a new CamAPS notification, reload it before saving. If no current graphic is available, a message explains that the next update is needed. A correction stops current and pending GVA audio. The last displayed reading and its original receipt time remain available; output waits for new input. Arrow mappings stay in private app storage and are included in neither this source project nor settings profiles.

## Build and set up

Follow [Build from source](../docs/BUILDING.md). Build target: `:companionProbe:assembleDebug`. Minimum declared Android API: 26; large native metric/live display: Android 17/API 37 when available. A normal notification is the fallback.

1. Install your own APK and acknowledge both safety notices yourself.
2. Enable notification access and receipt; allow app notifications and review background/battery settings.
3. Configure levels, outputs and an offline speech voice. Check synthetic previews and actual sound/vibration on your device.
4. Enable **Output enabled** on the dashboard. Enable it again after an update.
5. Optional: add the widget, or pair GVA Watch and separately enable display and alarm forwarding.

The phone/watch pair requires matching package names and signing keys. The app has no Internet or direct sensor permission. Settings, mappings, alarm state and a short diagnostic history stay in private app storage. Do not upload personal readings or device dumps to issues.

See [current limitations](../docs/STATUS.md), [GPLv3](../LICENSE) and [third-party notices](../THIRD_PARTY_NOTICES.md).
