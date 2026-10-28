# Licenses and third-party notices

## Application and project resources

Glucose Voice Alarm and its modifications are provided under GNU GPL version 3 or, at your option, a later version (SPDX: GPL-3.0-or-later). See [LICENSE](LICENSE). Retain copyright, source provenance and license notices when modifying or redistributing it. The safety notices do not create an additional license or restrict GPL rights.

The four isolated xDrip classes derive from NightscoutFoundation/xDrip revision `805ba0447cb7288c421cc898640183f8abb042fb`. Their existing headers, exact method inventory and the original GPL text are retained in `companionProbe/UPSTREAM_ALARM_IMPORT.json` and `companionProbe/LICENSE-xDrip.txt`. The surrounding ports and behavioral changes are identified in [UPSTREAM.md](docs/UPSTREAM.md).

## Gradle wrapper

`gradlew`, `gradlew.bat` and `gradle/wrapper/gradle-wrapper.jar` are Gradle build-bootstrap tooling. Copyright and attribution belong to the Gradle project contributors. They are separately licensed under Apache License 2.0, not relicensed as GPL. Copies of the Gradle 7.5 distribution's LICENSE and NOTICE accompany the wrapper in `licenses/gradle/`. The Gradle distribution is downloaded by the wrapper; it is not bundled in this repository or the app.

## Downloaded build and test dependencies

The Android Gradle Plugin 7.4.2 and Android build tooling are build dependencies. JUnit 4.13.2 (EPL-1.0) and Robolectric 4.14.1 (MIT) are test dependencies. Their transitive dependencies have their own licenses. Gradle resolves these dependencies from the declared repositories; they are not vendored here, and the JUnit/Robolectric runtime is not packaged in the application. Dependency notices shipped by their publishers continue to apply.

The app uses Android platform APIs and the user's installed offline text-to-speech engine. It does not bundle a third-party voice model or external Android sound library. Generated bell WAVs are build output from the project's GPL source generator.

## Watch display dependencies

The phone and watch apps use `com.google.android.gms:play-services-wearable:18.2.0` and its resolved dependencies. Google Play services is an external SDK governed by Google's terms; it is not relicensed as GPL. Communication requires Google Play services on the paired Android devices. The source code does not contain Google credentials or private service keys.

The watch app uses `androidx.wear.watchface:watchface-complications-data-source:1.1.1` and its AndroidX dependencies, under their Apache-2.0 licenses. Preserve the publishers' notices. A complete Apache-2.0 text is included in `companionWear/licenses/Apache-2.0.txt` and in the watch APK.

Roboto Flex is distributed under SIL Open Font License 1.1. Copyright 2017 The Roboto Flex Project Authors; see [Roboto Flex](https://github.com/TypeNetwork/Roboto-Flex). The unmodified variable font is in `companionWear/src/main/res/font/roboto_flex.ttf`; its full license is in `companionWear/licenses/RobotoFlex-OFL.txt` and in both watch APKs. The code-free face also includes the same unmodified font under `companionWatchFace/src/main/res/font/roboto_flex.ttf` and its OFL in `companionWatchFace/src/main/assets/RobotoFlex-OFL.txt`. Both watch modules additionally include `res/font/roboto_flex_footer.ttf`, a static instance derived from Roboto Flex with weight 900, width 100 and optical size 32; the other axes use upstream defaults. Its internal family is renamed “GVA Roboto Flex Footer”. The derivation coordinates, source hash and fontTools version are in `companionWear/licenses/RobotoFlex-Footer.json`. The same SIL OFL 1.1 applies to this instance. It is not relicensed as GPL. Nunito was used in an earlier prototype and is no longer packaged.

The watch menu uses official Wear Compose Material 3 / Foundation 1.5.6 and AndroidX Activity Compose 1.10.1, under their Apache-2.0 licenses. The watch-only Gradle 8.9 wrapper retains its own license and notice in `watch-build/licenses/gradle/`. Kotlin/Compose compiler 2.1.21 and AGP 8.7.2 are build dependencies; test libraries are excluded from the APK.

GVA Dots is an original GPL-3.0-or-later WFF resource definition. The official Google/Samsung WFF XSD files are used as external validation tooling and are not bundled in the application.

The watch age source uses AndroidX watchface complication data source 1.2.0 and ProtoLayout Expression 1.2.0, under Apache-2.0. The expression pipeline 1.2.0 is an explicit test dependency for evaluating the native counter; dependency resolution may also provide pipeline classes used internally by AndroidX. No sensor operation is requested by the age expression.

The watch tile uses AndroidX Wear Tiles 1.6.2, ProtoLayout Material 3 and ProtoLayout Expression 1.4.2 under Apache-2.0. Tiles Renderer and ProtoLayout/Tile protocol classes are test-only. The watchface complication data-source library remains 1.2.0. No additional phone dependency is introduced.

## Phone widget font

The widget uses a static, rounded Latin and punctuation subset derived from [Google Sans Flex](https://github.com/googlefonts/googlesans-flex), distributed under SIL Open Font License 1.1. The official source is pinned to google/fonts commit `bd8f81ddb5c74d5c8897b36ad88b440266245103`. Copyright and license text are preserved in `companionProbe/src/main/assets/licenses/GoogleSansFlex-OFL.txt`, alongside the upstream trademark notice. The internal font family is renamed “GVA Rounded Widget”. Weight is 900, roundedness 100, width 100, optical size 48 and slant 0. Source and output hashes, coordinates and the fontTools version are in `GoogleSansFlex-Widget.json` in the same directory. The bundled font is `companionProbe/src/main/res/font/gva_rounded_widget.ttf` and remains under OFL 1.1; it is not relicensed as GPL. No affiliation with or sponsorship by Google is implied.

The phone APK also retains the earlier GVA Roboto Flex Footer font and its OFL/derivation notices. Watch typography is unchanged. The widget uses Android platform RemoteViews and Canvas APIs without adding a runtime library.
