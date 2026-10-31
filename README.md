# Glucose Voice Alarm

[Deutsch](README.de.md)

Glucose Voice Alarm (GVA) reads glucose values and trend arrows from **CamAPS notifications** on an Android phone. It displays them on the phone and Wear OS and provides configurable **sound, speech and vibration** for its own alarms. Routine reading announcements, alarm levels, output channels and individual spoken texts can be adjusted.

**Experimental beta. Research tool only. Not for medical use. Not a certified medical device.** Readings and alarms can be missing, delayed or wrong. Do not use this project for treatment or dosing decisions. Read [the safety information](SAFETY.md) before building or using it.

## Three APKs, one project

| Component | Current source version | Role |
|---|---|---|
| [Glucose Voice Alarm — phone](companionProbe/README.md) | 0.23.0 Beta | CamAPS receipt, configurable alarms and speech, dashboard, AOD/lock-screen notification and widget |
| [GVA Watch](companionWear/README.md) | 0.19.0 Beta | Watch display, tile, six complication sources, and optional relayed phone alarms with vibration |
| [GVA Dots](companionWatchFace/README.md) | 0.1.5 Beta | Round watch face with time dots, value/arrow, short history, age and delta |

The tile and complications are included in **GVA Watch**. They are not separate apps. The phone works without a watch; GVA Dots needs GVA Watch for its data. Shared source keeps the display and alert protocols together. Phone and watch builds must use the same application ID and signing key.

## Where information and alarms appear

| Place | Information | Alarm output |
|---|---|---|
| Phone app | Value, CamAPS arrow, delta and six-hour diagnostic chart | Alarm levels and output configuration |
| Phone audio and haptics | Optional routine value/trend speech | Short tones, spoken alarm text and vibration, separately configurable |
| Phone AOD / lock screen / status bar | Value and trend; compact status-bar number where supported | Android notifications; actual visibility depends on Android |
| Home-screen widget | Value, arrow, adaptive chart, delta and original receipt time | Optional output/channel toggles; no separate alarm engine |
| Watch app and GVA Dots | Value, arrow, one-/two-hour history, age and delta; ambient view | Optional forwarded phone alarms/data-gap warnings, with watch vibration |
| Watch tile and other faces | Tile plus combined and individual complication sources | Display only; GVA Watch handles forwarded notifications |

CamAPS remains the sensor and pump controller. GVA does not change therapy settings. It adds output and presentation choices when the source provides data.

## Examples

All examples contain **synthetic data**, not a person's measurements. The gallery distinguishes native app renderings, watch-face previews and schematics.

<table><tr><td valign="top"><img src="docs/images/phone-dashboard-dark.png" width="215" alt="Phone dashboard"><br>Phone dashboard</td><td valign="top"><img src="docs/images/watch-face-active.png" width="220" alt="GVA Dots · active preview"><br>GVA Dots · active preview</td><td valign="top"><img src="docs/images/watch-tile.png" width="220" alt="GVA Watch · tile"><br>GVA Watch · tile</td></tr></table>

[Phone AOD, widget and settings](companionProbe/README.md#examples) · [Watch tile and all six complications](companionWear/README.md#examples) · [Active and ambient watch face](companionWatchFace/README.md#examples) · [Image provenance](docs/images/README.md)

## What can be configured

- High, low, persistent-high and critical rise/fall-rate alarms; multiple levels where supported, repeat/snooze rules and optional recovery suppression.
- Tone, alarm speech, routine reading speech and vibration switches, with a separate output master. At least one effective alarm route must remain configured.
- Spoken templates, all seven spoken trend phrases and separate dashboard/AOD trend labels, plus the data-gap warning text. Individual spoken trend phrases can be muted without hiding displayed trends.
- Three bell-tone sets, custom Android sounds, vibration patterns, relative output volumes, five local settings profiles, German/English UI and default speech.
- mg/dl or mmol/l, light/dark/system appearance, watch display/alert opt-ins, and widget controls. See the [phone reference](docs/PHONE_REFERENCE.md).

An already-started live reading alarm may complete missing speech once the voice is ready, within ten seconds of alarm start and after rechecking its condition and fresh data. Sound and vibration are not repeated. A separate last-alarm output status supports diagnosis; it does not prove audibility or vibration.

A fresh valid reading back inside a high/low threshold ends that level's automatic repeat episode. The next crossing can trigger its enabled output routes immediately. Manual snooze and the global pause remain in effect; gaps do not reset the repeat. [Episode rules](docs/PHONE_REFERENCE.md#new-highlow-alarm-episodes).

Persistent-high duration includes gaps from the first received high reading; chart gaps stay visible. Fresh rising input and the imported quality checks still govern the alarm. [Duration and limits](docs/PHONE_REFERENCE.md#persistent-high-duration). [Processing diagnostics](docs/PHONE_REFERENCE.md#processing-diagnostics) distinguish alarm starts, silent refreshes and routine speech without storing readings or spoken text.

## Build and set up

This is a **source-only project**. There are no packaged APK releases or Store downloads. Follow [Build from source](docs/BUILDING.md), then the setup guide for each component. No private developer key is required. Keep your own signing key for future updates and use it for both phone and watch.

Both apps require the user to acknowledge their warning and terms of use. Notification access, background permissions and output opt-ins must be enabled deliberately. Updates interrupt phone output; enable it again yourself.

## Limits that matter

- Development and device checks have focused on a Pixel 7 Pro with Android 17 and CamAPS FX 1.4(194).111, plus a Galaxy Watch5 Pro with Wear OS 6. Other phones, launchers, watches and CamAPS versions need testing.
- The large phone AOD/live display uses Android 17 support when available. Older systems use a normal notification. Android controls promotion, placement, timing and background restrictions.
- Timestamps refer to receipt, not a verified sensor measurement time. Missing readings are not extrapolated. The CamAPS arrow and the numeric critical-rate alarms describe different things.
- Watch transfer uses Google Play services Data Layer and connected nearby devices. The watch does not calculate glucose alarms independently. A connection failure can prevent forwarded alerts; no offline alarm replay is attempted. Complication/tile refresh timing and battery use need further device testing.

The [current status and open work](docs/STATUS.md) separate synthetic tests from device observations. These tests do not establish clinical safety or dependable alarm delivery.

## License and credits

GVA source is [GPL-3.0-or-later](LICENSE), with retained xDrip authorship and provenance. Fonts and external dependencies retain their own licenses. See [Credits](CREDITS.md), [Third-party notices](THIRD_PARTY_NOTICES.md) and [xDrip-derived rules](docs/UPSTREAM.md). Product names indicate compatibility, not affiliation or endorsement.
