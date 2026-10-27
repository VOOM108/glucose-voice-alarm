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

## Numeric dashboard rate

`ReceiptRate` computes the signed change between the first and last of four accepted receipt-minute points over the actual elapsed time. It displays a number only. CamAPS supplies all displayed and spoken arrows; this module contains no calculated-arrow classifier. Numerical alarm evaluation remains in the isolated xDrip rules and the companion's receipt-time guards described above.

## Glucose units

The conversion factor `18.0182` matches [`utilitymodels/Constants.java` at the pinned revision](https://github.com/NightscoutFoundation/xDrip/blob/805ba0447cb7288c421cc898640183f8abb042fb/app/src/main/java/com/eveningoutpost/dexdrip/utilitymodels/Constants.java). The five supported CamAPS package identifiers are listed in that revision's `services/UiBasedCollector.java`. Source units are learned from the first valid notification, checked against explicit labels and kept fixed until a confirmed reset. The companion retains fractional canonical mg/dl values and rounds only for output. This reception and presentation policy is its own implementation.

## Safety-notice source

The original research warning and warranty text are the `importanttext` and `pref_I_understand_summery` resources in [upstream internal.xml](https://github.com/NightscoutFoundation/xDrip/blob/805ba0447cb7288c421cc898640183f8abb042fb/app/src/main/res/values/internal.xml). A complete, human-readable extraction is included in [upstream/xdrip-safety-notices.txt](upstream/xdrip-safety-notices.txt). The project-specific [safety notice](../SAFETY.md) adapts their substance to this companion. The original xDrip README remains available at the pinned upstream revision.

GVA also requires two explicit first-start acknowledgements: the research warning, then the terms of use. Its own versioned local acknowledgement store gates reception, UI and output; it does not reuse xDrip preferences. Ordinary app updates retain acknowledgements of unchanged text. The notices and complete GPLv3 remain available offline under Appearance → Safety and terms.

## Listener reconnection

The companion keeps an existing Android listener binding while first-start acknowledgement is pending, but processes no source notifications until both steps are complete. It requests a missing binding on UI resume, output startup and disconnection, with attempts limited to one every 30 seconds. A manual reconnect action preserves notification access and output settings. This does not backfill active notifications, restart a connected listener merely because readings are missing, or change the glucose parser and alarm thresholds.
