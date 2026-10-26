# Licenses and third-party notices

## Application and project resources

Glucose Voice Alarm and its modifications are provided under GNU GPL version 3 or, at your option, a later version (SPDX: GPL-3.0-or-later). See [LICENSE](LICENSE). Retain copyright, source provenance and license notices when modifying or redistributing it. The safety notices do not create an additional license or restrict GPL rights.

The four isolated xDrip classes derive from NightscoutFoundation/xDrip revision `805ba0447cb7288c421cc898640183f8abb042fb`. Their existing headers, exact method inventory and the original GPL text are retained in `companionProbe/UPSTREAM_ALARM_IMPORT.json` and `companionProbe/LICENSE-xDrip.txt`. The surrounding ports and behavioral changes are identified in [UPSTREAM.md](docs/UPSTREAM.md).

## Gradle wrapper

`gradlew`, `gradlew.bat` and `gradle/wrapper/gradle-wrapper.jar` are Gradle build-bootstrap tooling. Copyright and attribution belong to the Gradle project contributors. They are separately licensed under Apache License 2.0, not relicensed as GPL. Copies of the Gradle 7.5 distribution's LICENSE and NOTICE accompany the wrapper in `licenses/gradle/`. The Gradle distribution is downloaded by the wrapper; it is not bundled in this repository or the app.

## Downloaded build and test dependencies

The Android Gradle Plugin 7.4.2 and Android build tooling are build dependencies. JUnit 4.13.2 (EPL-1.0) and Robolectric 4.14.1 (MIT) are test dependencies. Their transitive dependencies have their own licenses. Gradle resolves these dependencies from the declared repositories; they are not vendored here, and the JUnit/Robolectric runtime is not packaged in the application. Dependency notices shipped by their publishers continue to apply.

The app uses Android platform APIs and the user's installed offline text-to-speech engine. It does not bundle a third-party voice model or external Android sound library. Generated bell WAVs are build output from the project's GPL source generator.
