# Status and open work

[Overview](../README.md) · [Deutsch](../README.de.md)

## Current source set

| Component | Version | Source minimum / special requirement |
|---|---|---|
| Phone | 0.23.0 Beta | Android API 26 minimum declaration; native large metric display requires Android 17/API 37 |
| GVA Watch, including tile and six complication sources | 0.19.0 Beta | API 30 minimum declaration; some dynamic-age behavior requires newer APIs |
| GVA Dots | 0.1.5 Beta | Wear OS 4+, Watch Face Format v1 |

The phone/watch application ID and signing key must match for Google Play services Data Layer communication. The face has its own package. Shared source is in `companionWatchShared/`; the two build roots are documented in [BUILDING.md](BUILDING.md).

## What has been checked

The phone suite contains 852 synthetic checks; the unchanged watch suite contains 50, including four tile checks. The exported phone source is independently rebuilt and tested without the private developer key; watch test evidence is reused only for byte-identical sources. All three validation APKs use the same ordinary debug identity and are not distributed. Source review checks imported xDrip rule hashes, private-file exclusions, local links, dependency/font notices and package identities. Synthetic tests do not establish clinical safety or reliable alarm delivery. This source publication does not modify the installed apps.

Device work has focused on Pixel 7 Pro, Android 17, CamAPS FX 1.4(194).111, and Galaxy Watch5 Pro, Wear OS 6/One UI 8 Watch. The user observed value/trend speech and phone live-display behavior. The watch-face appearance, age field and worn ambient display were checked. Watch active-mode updates were observed approximately once per minute. The user accepted the phone widget’s appearance on the Pixel launcher in version 0.20.7, including its rounded font, bounded reading size and revised curve weight. The later 0.21.3 widget arrows and direct toggles, and the editable display trend text, are synthetically checked; their latest Pixel behavior still needs user acceptance. These observations are not a compatibility or reliability guarantee.

The dashboard pencil in phone0.21.4 also opens known arrow mappings. Synthetic checks cover correcting a wrongly labeled graphic, preserving the displayed reading and receipt time, cancelling obsolete GVA audio and queued input, and consistent normal speech for all seven directions. Arrow confirmations are local to each installation; the source project contains no preconfirmed CamAPS graphics. Correct recognition on real devices still requires checks after assignment or correction.

Phone 0.21.5 adds bounded completion of speech missed during offline-voice initialization. Fifty new synthetic cases across API 28 and 34 cover fresh-condition and schedule rechecks, the ten-second limit, current-value text, cancellation, longer tone sequences, output failures and the separate last-alarm status. They also check that sound/vibration are not replayed and routine reading speech does not overwrite that status. Real-device acceptance of this path and perceptibility of each output route remain open. A notification or accepted API call alone does not establish successful audio or vibration.

Phone 0.22.0 adds per-level high/low repeat episodes. Thirty-five new synthetic checks (19 state tests and 16 platform cases) cover recovery and recrossing, continuous excursions, exact boundaries, manual snooze, global pause, invalid/stale data, gaps, multiple levels, restarts and legacy runtime formats. Platform tests exercise speech alone and the configured phone sound/speech/vibration routes. These are synthetic acceptance checks; real-device delivery and perceptibility still need verification. The separate condition-notification/reason display remains open.

Phone 0.22.1 adds bounded processing diagnostics: 40 synthetic cases cover input/attempt correlation, silent refreshes, output failures, snooze/pause distinctions, timer ticks, cache validation and reset on service start. These snapshots do not prove physical delivery or establish the cause of a past event.

Phone 0.23.0 adds elapsed persistent-high duration across gaps. Its 42 new checks comprise 18 state cases and 12 service scenarios on API 28/34. They cover a five-hour synthetic high episode with early gaps, first high input without a rising slope, stable/falling phases, invalid/stale input, fresh boundary recovery, separate rate/episode history, speech duration, same-boot reload/restart, real clock/boot changes and retained repeat deadlines. The chart is not backfilled. Existing lost beginnings are not retrospectively restored. The latest version is installed on the development Pixel; its new duration behavior still needs everyday device acceptance. The inherited rising-only trigger, density check and one-day limit remain.

## Open before broader use

- Other CamAPS variants, units and notification structures; manual trend matching after source updates or conflicts.
- Other phones, launchers, watches and Android versions; AOD/live-display placement and contention with other apps.
- On the Pixel, GVA can show only its icon while another live chip is active. In a user check, opening the competing app restored the GVA number. The internal Android decision path was not measured.
- Very narrow widget previews can wrap the receipt/unit caption; launcher-specific sizing still needs checks.
- Natural data gaps, process restarts, disconnection/reconnection, stale display transitions and source-clock differences on real devices.
- Perceptibility and delivery of every enabled alarm route, including watch vibration, Do Not Disturb and background restrictions. The watch has no independent alarm fallback.
- Complication/tile cadence and battery cost in everyday active and ambient use. Synthetic timing tests cannot establish these.
- Continued review of the experimental description and component notices as the project changes. The current source review retains upstream GPL notices and separately licensed dependencies; no certification or endorsement is claimed.

## Privacy and distribution

Only source, build wrappers/licenses and selected synthetic documentation images belong in this repository. No personal measurements, device captures, credentials, signing keys, APKs or build caches are included. Do not put personal readings or notification dumps in issues. Debug builds use the builder's own local key. No binary releases or Store publication are prepared.
