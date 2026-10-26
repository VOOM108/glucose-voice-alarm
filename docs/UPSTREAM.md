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

## Safety-notice source

The original research warning and warranty text are the `importanttext` and `pref_I_understand_summery` resources in [upstream internal.xml](https://github.com/NightscoutFoundation/xDrip/blob/805ba0447cb7288c421cc898640183f8abb042fb/app/src/main/res/values/internal.xml). A complete, human-readable extraction is included in [upstream/xdrip-safety-notices.txt](upstream/xdrip-safety-notices.txt). The project-specific [safety notice](../SAFETY.md) adapts their substance to this companion. The original xDrip README remains available at the pinned upstream revision.
