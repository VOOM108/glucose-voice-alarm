# Status and open work

[Overview](../README.md) · [Deutsch](../README.de.md)

## Current source set

| Component | Version | Source minimum / special requirement |
|---|---|---|
| Phone | 0.20.7 Beta | Android API 26 minimum declaration; native large metric display requires Android 17/API 37 |
| GVA Watch, including tile and six complication sources | 0.19.0 Beta | API 30 minimum declaration; some dynamic-age behavior requires newer APIs |
| GVA Dots | 0.1.5 Beta | Wear OS 4+, Watch Face Format v1 |

The phone/watch application ID and signing key must match for Google Play services Data Layer communication. The face has its own package. Shared source is in `companionWatchShared/`; the two build roots are documented in [BUILDING.md](BUILDING.md).

## What has been checked

The phone suite contains 651 synthetic checks; the watch suite contains 50, including four tile checks. Native renderings cover phone themes/settings, the widget, watch data views, the tile and complication graphics. The source review checks imported xDrip rule hashes, private-file exclusions, local links, dependency/font notices, package identity and independent debug signing. Tests do not establish clinical safety or reliable alarm delivery. On 28 October 2026, the exported repository was independently rebuilt with all 651 phone checks and 50 watch checks passing (701 total). All three APKs compiled using a matching ordinary debug identity, without the private developer key. Those APKs were used only for validation and are not distributed. The installed apps were not changed during this documentation/source preparation.

Device work has focused on Pixel 7 Pro, Android 17, CamAPS FX 1.4(194).111, and Galaxy Watch5 Pro, Wear OS 6/One UI 8 Watch. The user observed value/trend speech and phone live-display behavior. The watch-face appearance, age field and worn ambient display were checked. Watch active-mode updates were observed approximately once per minute. The user accepted the phone widget’s appearance on the Pixel launcher in version 0.20.7, including its rounded font, bounded reading size and revised curve weight. These observations are not a compatibility or reliability guarantee.

## Open before broader use

- Other CamAPS variants, units and notification structures; manual trend matching after source updates or conflicts.
- Other phones, launchers, watches and Android versions; AOD/live-display placement and contention with other apps.
- Natural data gaps, process restarts, disconnection/reconnection, stale display transitions and source-clock differences on real devices.
- Perceptibility and delivery of every enabled alarm route, including watch vibration, Do Not Disturb and background restrictions. The watch has no independent alarm fallback.
- Complication/tile cadence and battery cost in everyday active and ambient use. Synthetic timing tests cannot establish these.
- Continued review of the experimental description and component notices as the project changes. The current source review retains upstream GPL notices and separately licensed dependencies; no certification or endorsement is claimed.

## Privacy and distribution

Only source, build wrappers/licenses and selected synthetic documentation images belong in this repository. No personal measurements, device captures, credentials, signing keys, APKs or build caches are included. Do not put personal readings or notification dumps in issues. Debug builds use the builder's own local key. No binary releases or Store publication are prepared.
