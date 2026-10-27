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

`-PprobeFastCheck` runs only the pure Java subset. It is useful during development and does not replace the full suite. The Beta baseline has 552 complete synthetic checks, including legacy storage schemas, profiles, German/English UI and speech, glucose-unit conversion and the two-step first-start gate. Native synthetic renders cover both languages and themes, including large text. These checks do not establish real-device reliability, audibility or clinical safety.

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

The app uses Android platform APIs, without a bundled third-party application runtime. JUnit and Robolectric are test-only. See [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md) and preserve publisher notices when redistributing dependencies. Do not commit `local.properties`, keystores, credentials, personal logs, build folders or generated APKs.
