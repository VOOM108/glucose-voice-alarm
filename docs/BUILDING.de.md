# Selbst kompilieren

[English](BUILDING.md)

Lies zuerst [SAFETY.de.md](../SAFETY.de.md). Das Repository enthält Quellcode und bietet keine fertigen APKs an. Das Gradle-Wrapper-JAR startet den Build und ist kein App-Release.

## Voraussetzungen

- Git und Netzwerkzugriff für Quellcode, Gradle sowie Build-/Testabhängigkeiten.
- JDK 11 für den festgelegten Build mit Gradle 7.5 und Android Gradle Plugin 7.4.2.
- Android SDK Platform 34, Build Tools 30.0.3 sowie für ADB die Platform Tools. SDK-Lizenzen mit den SDK-Werkzeugen akzeptieren.
- JDK 21 für die vollständigen Robolectric-Tests. Dessen Java-Datei separat übergeben; Gradle 7.5 selbst nicht mit Java 21 starten.
- Python 3 nur für die optionale Hashprüfung der übernommenen Regeln.

Android Studio kann SDK und Editor bereitstellen. Stelle dort die Gradle-JVM auf JDK 11; neuere Studio-Voreinstellungen können eine inkompatible JVM verwenden. Nutze beim ersten Aufbau den mitgelieferten Wrapper. Die Warnung zu compileSdk 34/AGP ist bekannt: Die festgelegte Kombination ist die geprüfte Ausgangsbasis, keine Zusage zur aktuellen Store-Einreichung.

## Quellcode und Werkzeuge

```sh
git clone https://github.com/VOOM108/glucose-voice-alarm.git
cd glucose-voice-alarm
```

Solange das Repository privat ist, benötigt Dein GitHub-Konto Zugriff. Zugangstokens gehören weder in URLs noch in eingecheckte Dateien.

Setze `JAVA_HOME` auf JDK 11 und `ANDROID_HOME` auf Dein Android SDK. Alternativ kann Android Studio eine ignorierte `local.properties` mit `sdk.dir` anlegen. Die SDK-Pakete lassen sich so installieren:

```sh
sdkmanager "platforms;android-34" "build-tools;30.0.3" "platform-tools"
sdkmanager --licenses
```

## Bauen

Unter Linux/macOS mit gesetzten Variablen:

```sh
./gradlew :companionProbe:assembleDebug
```

Beispiel für Windows PowerShell, Werkzeugpfade bitte anpassen:

```powershell
$env:JAVA_HOME = 'C:\Java\jdk-11'
$env:ANDROID_HOME = 'C:\Android\Sdk'
.\gradlew.bat :companionProbe:assembleDebug
```

Die APK liegt danach unter `companionProbe/build/outputs/apk/debug/companionProbe-debug.apk`. Alle fünfzehn Glockentöne entstehen beim Build aus `companionProbe/sounddesign/ToneAssetGenerator.java`. Fremde Audiodateien, privater xDrip-Checkout, privater Signierschlüssel und entwicklerspezifische Ordner sind dafür nicht nötig.

Android erzeugt beim ersten Build einen lokalen Debug-Signierschlüssel im Android-Benutzerordner außerhalb dieses Repositorys. Behalte Deinen Schlüssel für spätere Updates derselben Installation. Eine fremd signierte bestehende Installation lässt sich mit Deiner APK nicht aktualisieren. Mit einer anderen `applicationId` in Deinem eigenen Klon ist eine separate Testinstallation möglich. Das Deinstallieren einer vorhandenen App löscht ihre privaten Daten; der Build deinstalliert nichts.

## Prüfen

```sh
python3 tools/check_upstream_rules.py
./gradlew :companionProbe:testDebugUnitTest -PprobeTestJava=/path/to/jdk-21/bin/java
```

Windows PowerShell:

```powershell
python tools/check_upstream_rules.py
.\gradlew.bat :companionProbe:testDebugUnitTest "-PprobeTestJava=C:\Java\jdk-21\bin\java.exe" "-PprobeTestTmp=C:\gva-t"
```

Wenn sqlite4java unter Windows mit langen WAL-Pfaden scheitert, wähle einen kurzen beschreibbaren Testordner. `probeTestTmp` legt ihn fest; der Standard ist `../t` relativ zum Repository. Gradle und Robolectric laden beim ersten Test Abhängigkeiten herunter, was viel Speicher benötigen kann. XML-/HTML-Berichte und synthetische native UI-Bilder bleiben im ignorierten `build/` des Moduls.

`-PprobeFastCheck` führt nur den reinen Java-Teil aus und ersetzt nicht die vollständige Prüfung. Der Telefonstand hat 770 synthetische Prüfungen, unter anderem für Speicherformate, Profile, deutsche/englische Oberfläche und Sprache, Einheiten, Erststart-Bestätigungen, getrennte Anzeige-/Sprachtexte, direkte Widgetaktionen, responsive Pfeile, Korrekturen bekannter Pfeilzuordnungen, begrenztes Nachreichen von Live-Alarmansagen samt Ausgabestatus, die Neuarmierung von Hoch-/Niedrig-Episoden samt Zustandsmigration und das Uhren-Anzeigeformat. Native Darstellungen decken beide Sprachen und Themen sowie große Schrift ab. Das Uhrenmodul ergänzt 50 Prüfungen, darunter die getrennten Bestätigungen und Menüaktionen, sowie synthetische Anzeige- und Menüansichten. Das belegt keine Zuverlässigkeit, Hörbarkeit oder klinische Sicherheit am echten Gerät.

Uhren-App und codefreies Zifferblatt haben unter `watch-build/` einen getrennten Build mit JDK 21 und Android SDK Platform 35. Gepinnt sind Gradle 8.9, AGP 8.7.2 und Kotlin 2.1.21 für Wear Compose Material 3. Die Telefonwerkzeuge oben bleiben unverändert. In einer für das Uhren-JDK und SDK eingerichteten Shell:

```sh
cd watch-build
./gradlew :companionWear:assembleDebug :companionWatchFace:assembleDebug
./gradlew :companionWear:testDebugUnitTest
./gradlew :companionWear:testTileQaUnitTest
```

Telefon- und Uhren-App müssen dieselbe App-ID und Signatur behalten. Siehe [Uhren-Einrichtung](../companionWear/README.md).

## Eigenen Build installieren

Die Installation ist ein eigener Schritt. Bei passender App-ID/Signatur und Deinem freigegebenen verbundenen Telefon kannst Du die erzeugte APK übertragen oder so installieren:

```sh
adb install -r companionProbe/build/outputs/apk/debug/companionProbe-debug.apk
```

Der Befehl aktualisiert nur eine passende Installation mit Datenerhalt. Öffne danach die App und bestätige Warnhinweis und Nutzungsbedingungen selbst. Nach dem Update auf 0.15 gilt das auch einmalig für bestehende Installationen. Erst danach stehen Empfang und Ausgabe zur Verfügung. Erteile die Android-Freigaben, konfiguriere die App und schalte die aktive Ausgabe selbst ein. Bestätigungen dürfen nicht automatisiert werden. Kein Build-Task installiert die App, startet Live-Ausgabe, legt GitHub-Releases an oder lädt APKs hoch.

## Abhängigkeiten und Lizenzen

```sh
./gradlew :companionProbe:dependencies
```

Telefon und Uhr binden den Google-Play-services-Wearable-Client ein; die Uhr verwendet zusätzlich AndroidX-Komplikations-/Compose-Bibliotheken und die lizenzierte Roboto Flex. JUnit und Robolectric werden nur für Tests verwendet. Beachte [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md) und die Hinweise der Herausgeber beim Weitergeben von Abhängigkeiten. Keine `local.properties`, Schlüssel, Zugangsdaten, persönlichen Protokolle, Buildordner oder APKs einchecken.

Die vier Kachelprüfungen laufen getrennt unter `testTileQaUnitTest`. Nur diese QA-Variante bindet den Android-Kachel-Renderer mit seinen Ressourcen ein; die normale Debug-APK enthält ihn nicht. Die 46 übrigen Uhrenprüfungen laufen unter `testDebugUnitTest`.


Telefon- und Uhren-App müssen dieselbe App-ID und denselben Signierschlüssel behalten. Bei eigener App-ID auch die Uhren-App und die Default-Provider-Paketnamen im Zifferblatt anpassen. Das Zifferblatt hat eine eigene App-ID. Beide Builds brauchen dasselbe Android-Benutzerverzeichnis oder ausdrücklich denselben eigenen Signierschlüssel. Die Datenverbindung zwischen Telefon und Uhr benötigt Google Play services.
