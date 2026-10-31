# xDrip provenance and changes

Upstream: [NightscoutFoundation/xDrip](https://github.com/NightscoutFoundation/xDrip), revision [`805ba0447cb7288c421cc898640183f8abb042fb`](https://github.com/NightscoutFoundation/xDrip/tree/805ba0447cb7288c421cc898640183f8abb042fb), tag `2026.03.01`.

The app isolates 23 method bodies from these upstream classes:

| Upstream class | Original source path | Retained methods |
| --- | --- | ---: |
| AlertType | `app/src/main/java/com/eveningoutpost/dexdrip/models/AlertType.java` | 7 |
| BgReading | `app/src/main/java/com/eveningoutpost/dexdrip/models/BgReading.java` | 10 |
| PersistentHigh | `app/src/main/java/com/eveningoutpost/dexdrip/evaluators/PersistentHigh.java` | 2 |
| ActiveBgAlert | `app/src/main/java/com/eveningoutpost/dexdrip/models/ActiveBgAlert.java` | 4 |

Their method bodies are retained verbatim and inventoried with SHA-256 hashes in `companionProbe/UPSTREAM_ALARM_IMPORT.json`. `tools/check_upstream_rules.py` checks those bodies locally without downloading upstream code. Source-file hashes in the inventory refer to the pinned original source, while the generated class hashes record the isolation step. The isolated classes use a different package, reduced class scaffolding and explicit local ports instead of the full upstream database/services. They retain their copyright and GPL notices.

## Companion behavior differs

The complete xDrip application, database, collectors, cloud integrations and watch app are not included. Notification receipt, confirmed graphical trends, alarm configuration/storage, snooze persistence, output scheduling, UI and synthetic tests are companion implementations.

Inputs use accepted receipt time instead of a verified sensor measurement time. Rate alarms require four distinct receipt minutes and additional exact-time guards. Stale/out-of-order notifications and gaps restrict evaluation. The strongest applicable rate level covers weaker levels in the same phase; a due boundary alarm can be combined with a rate alarm. Pending speech can follow a newer reading without restarting its tones. Optional recovery suppression affects repeats only; optional gap warnings do not extrapolate glucose. These changes mean that unchanged imported methods do not establish full behavioral equivalence to xDrip+.

## Threshold repeat episodes

GVA 0.22.0 resets an automatic high/low repeat when a fresh valid numerical reading returns across that level's boundary: high < threshold, low > threshold. Equality still meets the imported condition. Manual snooze and the global pause are not cancelled by recovery. Missing/invalid data and schedule closure alone do not establish numerical recovery. Same-direction levels retain their own repeat deadlines when a higher level starts; the existing priority and opposite-direction selection rules remain in place.

This is a GVA policy in `PersistentAlarmSession`, outside the 23 verbatim imported methods. Runtime storage schema 3 permits several same-direction threshold states. It reads legacy schemas 1 and 2 while retaining their single-active-threshold validation. The migrated state does not infer a recovery that was not observed in valid input. This threshold-episode policy does not modify the imported method bodies.

## Persistent-high elapsed duration

GVA 0.23.0 changes receipt-time duration bookkeeping outside the 23 imported method bodies. Its episode begins with the first fresh received reading above that level's threshold, rather than waiting for a positive slope to establish the beginning. Stable/falling high readings and gaps count toward elapsed time. A fresh value at or below the persistent-high threshold ends it. Invalid or stale input does not establish recovery.

`ReceiptWindow` keeps a contiguous rate window and a separate bounded list of actual episode receipts. `AlarmEngine`, `PersistentAlarmSession` and `AlarmOutputFlow` pass the episode history only to persistent-high evaluation. It must have the same latest timestamp and value as current input and pass structural validation. No samples are synthesized, no rate crosses a gap, and a restored verified episode beginning is not rejected solely because a new receipt window started later.

The imported `PersistentHigh` methods remain verbatim: the trigger still requires a rising fresh reading, adequate sampling density and a duration within their one-day maximum. Gap time is elapsed time, not proof of continuously high missing measurements. Reload within the same phone boot retains the beginning; real clock/boot discontinuities remain conservative. Existing runtime schema 3 stores the beginning without a format change. Lost beginnings are not reconstructed from assumptions or retrospective CamAPS history.

## Numeric dashboard rate

`ReceiptRate` computes the signed change between the first and last of four accepted receipt-minute points over the actual elapsed time. It displays a number only. CamAPS supplies all displayed and spoken arrows; this module contains no calculated-arrow classifier. Numerical alarm evaluation remains in the isolated xDrip rules and the companion's receipt-time guards described above.

## Glucose units

The conversion factor `18.0182` matches [`utilitymodels/Constants.java` at the pinned revision](https://github.com/NightscoutFoundation/xDrip/blob/805ba0447cb7288c421cc898640183f8abb042fb/app/src/main/java/com/eveningoutpost/dexdrip/utilitymodels/Constants.java). The five supported CamAPS package identifiers are listed in that revision's `services/UiBasedCollector.java`. Source units are learned from the first valid notification, checked against explicit labels and kept fixed until a confirmed reset. The companion retains fractional canonical mg/dl values and rounds only for output. This reception and presentation policy is its own implementation.

## Safety-notice source

The original research warning and warranty text are the `importanttext` and `pref_I_understand_summery` resources in [upstream internal.xml](https://github.com/NightscoutFoundation/xDrip/blob/805ba0447cb7288c421cc898640183f8abb042fb/app/src/main/res/values/internal.xml). A complete, human-readable extraction is included in [upstream/xdrip-safety-notices.txt](upstream/xdrip-safety-notices.txt). The project-specific [safety notice](../SAFETY.md) adapts their substance to this companion. The original xDrip README remains available at the pinned upstream revision.

GVA also requires two explicit first-start acknowledgements: the research warning, then the terms of use. Its own versioned local acknowledgement store gates reception, UI and output; it does not reuse xDrip preferences. Ordinary app updates retain acknowledgements of unchanged text. The notices and complete GPLv3 remain available offline under Appearance → Safety and terms.

## Listener reconnection

The companion keeps an existing Android listener binding while first-start acknowledgement is pending, but processes no source notifications until both steps are complete. It requests a missing binding on UI resume, output startup and disconnection, with attempts limited to one every 30 seconds. A manual reconnect action preserves notification access and output settings. This does not backfill active notifications, restart a connected listener merely because readings are missing, or change the glucose parser and alarm thresholds.
