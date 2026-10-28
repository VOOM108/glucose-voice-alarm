# Build from source

[Deutsch](BUILDING.de.md)

Read [SAFETY.md](../SAFETY.md) first. This repository supplies source only; it does not publish ready-made APKs. The Gradle wrapper JAR is a build bootstrap, not an app release.

## Requirements

- Git and network access to obtain the source, Gradle and build/test dependencies.
- JDK 11 to run the pinned Gradle 7.5 / Android Gradle Plugin 7.4.2 build.
- Android SDK Platform 34, Build Tools 30.0.3, and Platform Tools if you want ADB. Accept the SDK licenses using the SDK tools.
- JDK 21 for the complete Robolectric test suite. Pass its Java executable separately; do not run Gradle 7.5 itself with Java 21.
- Python 3 only for the optional imported-rule hash checker.

Android Studio can provide the SDK and edit the project. Configure its Gradle JVM as JDK 11; recent Studio defaults may use a newer, incompatible JVM. Use the checked-in wrapper rather than upgrading the toolchain during first setup. The existing compileSdk 34/AGP compatibility warning is known; the pinned combination is the tested baseline, not a claim of current Store submission readiness.

## Obtain and configure

```sh
git clone https://github.com/VOOM108/glucose-voice-alarm.git
cd glucose-voice-alarm
```

While the repository is private, cloning requires access through your own GitHub account. No account token belongs in a URL or committed file.

Point `JAVA_HOME` at JDK 11. Point `ANDROID_HOME` at your Android SDK, or let Android Studio create an ignored `local.properties` with `sdk.dir`. SDK tools can install the packages:

```sh
sdkmanager "platforms;android-34" "build-tools;30.0.3" "platform-tools"
sdkmanager --licenses
```

## Compile

Linux/macOS, with the variables configured:

```sh
./gradlew :companionProbe:assembleDebug
```

Windows PowerShell example (replace the tool paths):

```powershell
$env:JAVA_HOME = 'C:\Java\jdk-11'
$env:ANDROID_HOME = 'C:\Android\Sdk'
.\gradlew.bat :companionProbe:assembleDebug
```

The APK is written to `companionProbe/build/outputs/apk/debug/companionProbe-debug.apk`. The build generates all fifteen bell WAVs from `companionProbe/sounddesign/ToneAssetGenerator.java`. No downloaded sound samples, private upstream checkout, private signing key or developer-specific directory is required.

Android generates a local debug signing key on a fresh build. Keep your own key if you want later builds to update the same installation. Its normal location is your Android user directory, outside this repository. An existing installation signed by somebody else's key cannot be updated with your self-built APK. Changing `applicationId` in your own clone permits a separate test installation. Uninstalling an existing app deletes its private data; the build process does not uninstall anything.

## Validate

```sh
python3 tools/check_upstream_rules.py
./gradlew :companionProbe:testDebugUnitTest -PprobeTestJava=/path/to/jdk-21/bin/java
```

Windows PowerShell example:

```powershell
python tools/check_upstream_rules.py
.\gradlew.bat :companionProbe:testDebugUnitTest "-PprobeTestJava=C:\Java\jdk-21\bin\java.exe" "-PprobeTestTmp=C:\gva-t"
```

Use a short writable test-temp path on Windows if sqlite4java has trouble with long WAL paths. The optional `probeTestTmp` property selects that directory. By default the test directory is `../t` relative to the repository. Gradle and Robolectric download dependencies on the first test run; this can be large. Test XML/HTML reports and native synthetic UI previews remain under the ignored module `build/` directory.

`-PprobeFastCheck` runs only the pure Java subset. It is useful during development and does not replace the full suite. The phone baseline has 647 complete synthetic checks, including storage schemas, profiles, German/English UI and speech, unit conversion, first-start gates and the watch display protocol. Native synthetic renders cover both languages and themes, including large text. The watch module has 50 checks in total, including separate consent confirmations and menu actions, plus synthetic display and menu renders. These checks do not establish real-device reliability, audibility or clinical safety.

The watch app and code-free face use the separate `watch-build/` project with JDK 21 and Android SDK Platform 35. Its pinned Gradle 8.9 / AGP 8.7.2 / Kotlin 2.1.21 build supports Wear Compose Material 3. The phone toolchain above stays unchanged. In a shell configured for the watch JDK and SDK:

```sh
cd watch-build
./gradlew :companionWear:assembleDebug :companionWatchFace:assembleDebug
./gradlew :companionWear:testDebugUnitTest
./gradlew :companionWear:testTileQaUnitTest
```

Changing the phone application ID alone breaks the pair. If you customize IDs, update the watch application ID and the face default-provider package references too. The face has its own application ID. Both builds must retain the same Android user directory or explicitly use the same self-owned signing key. Google Play services is required for the phone/watch Data Layer connection.

Both phone and watch apps must retain the same application ID and signing key. See [watch setup](../companionWear/README.md).

## Install your own build

Installation is a separate action. With a compatible signing identity and your own authorized phone connected, you can transfer the generated APK manually or run:

```sh
adb install -r companionProbe/build/outputs/apk/debug/companionProbe-debug.apk
```

This updates only a matching application ID/signature and retains that app's data. Open the app and acknowledge the warning and terms of use yourself. Existing installations also see these steps once when updating to 0.15. Then grant the required Android permissions, configure the app and enable output manually. Do not automate the acknowledgements. No build task installs the app, starts live output, creates a GitHub release or uploads an APK.

## Dependency and license review

```sh
./gradlew :companionProbe:dependencies
```

The phone and watch apps include the Google Play services Wearable client; the watch also uses AndroidX complication/Compose libraries and licensed Roboto Flex. JUnit and Robolectric are test-only. See [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md) and preserve publisher notices when redistributing dependencies. Do not commit `local.properties`, keystores, credentials, personal logs, build folders or generated APKs.

The four tile checks run separately under `testTileQaUnitTest`. Only that QA variant includes the Android tile renderer and its resources; the normal debug APK does not. The remaining 46 watch checks run under `testDebugUnitTest`.
