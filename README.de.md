# Glucose Voice Alarm

[English](README.md)

Experimentelle Android-Begleitapp für gesprochene Glukosealarme, kurze Töne, Vibration und eine kompakte Wertanzeige. Sie liest unterstützte CamAPS-Benachrichtigungen auf demselben Telefon. CamAPS bleibt für Sensor und Pumpe zuständig.

**Beta-Forschungssoftware. Kein zertifiziertes oder behördlich zugelassenes Medizinprodukt. Nicht für medizinische Entscheidungen oder Therapieentscheidungen verwenden. Verlasse Dich nicht auf Echtzeitalarme oder zeitkritische Informationen dieser App.** Jede Komponente kann ausfallen. Lies vor dem Bauen oder Verwenden die [Sicherheitshinweise](SAFETY.de.md).

Das Repository enthält Quellcode. Es gibt keine gepackten APK-Releases und keinen automatischen Release-Upload. [So kompilierst Du die App selbst](docs/BUILDING.de.md).

## Warum es die App gibt

Wenn CamAPS bereits die Glukosedaten empfängt und die Therapie steuert, kann sich eine Begleitapp auf einstellbare Alarme und Sprachinformationen konzentrieren. Dieses Projekt untersucht diesen engeren Zweck, ohne eine zweite vollständige Sensor- und Datendrehscheibe auszuführen. Es übernimmt ausgewählte xDrip+-Alarmregeln und ergänzt eigene Android-Anbindung, Speicherung, Oberfläche und Ausgabe. Die Zuverlässigkeit oder der vollständige Funktionsumfang von xDrip+ werden damit nicht zugesagt.

## Funktionen

- Hoch, niedrig, schnell steigend, schnell fallend und dauerhaft hoch, bei Bedarf mit mehreren Alarmstufen.
- Eigene Sprachvorlagen und Trendwörter, Wiederholung/Snooze sowie eine Pause für normale Wertansagen nach einem Alarm.
- Getrennte Schalter für Ton, Alarm-Sprache, Wertansagen und Vibration. Vibration darf der einzige konfigurierte Alarmweg sein. Der Hauptschalter stoppt die eigenen Ausgaben.
- Drei zusammenpassende Glockentonsets aus dem mitgelieferten Generator, Android-Tonauswahl und sechs Vibrationsmuster.
- Optionale Erholungsregeln für Hoch-/Niedrig-Wiederholungen und eine einstellbare Lückenwarnung mit eigenem Text. Die Lückenwarnung beschreibt den letzten bekannten Zustand und erfindet keinen aktuellen Wert.
- Lokale Diagnosekurve über sechs Stunden, Hell/Dunkel/System, fünf lokale Einstellungsprofile sowie deutsche/englische Oberfläche und Standardansagen.
- Sieben zuordenbare Pfeilzustände einschließlich Doppelpfeilen. Unbekannte oder widersprüchliche Grafiken bleiben offen. Eine neue Quellmeldung macht die geöffnete Zuordnungsvorschau ungültig.
- Sperrbildschirm- und Statusbenachrichtigungen. Ab Android 17 wird die native große Live-Anzeige versucht. Hervorhebung, Platzierung und Konkurrenz mit anderen Apps bestimmt Android.

## Umfang und Grenzen

Aktueller Stand ist 0.11.0 Beta. Entwicklung und Geräteprüfungen konzentrierten sich auf Pixel 7 Pro mit Android 17 und CamAPS FX 1.4(194).111. Andere Geräte, Android-Varianten und CamAPS-Versionen müssen gesondert geprüft werden. Die App verarbeitet unterstützte CamAPS-Benachrichtigungen in **mg/dl**. Andere CGM-Quellen, mmol/l-Auswertung, direkte Sensor-/Bluetooth-Anbindung, Pumpensteuerung, Uhr-App und Komplikationen sind nicht enthalten.

Die deklarierte Mindestversion ist Android 8.0 (API 26); daraus folgt keine Kompatibilitätsgarantie. Ohne die native große Anzeige wird eine normale Benachrichtigung verwendet. Dauerhafte AOD-Sichtbarkeit und Alarmzustellung sind nicht garantiert. Stromsparregeln, geänderte Benachrichtigungen, Datenlücken, Audioeinstellungen und das Betriebssystem können sie unterbrechen.

Die angezeigte Zeit ist Empfangs-/Verarbeitungszeit, kein bestätigter Sensor-Messzeitpunkt. Lücken bleiben in der Kurve leer. Ein Trendpfeil allein löst keinen numerischen Ratenalarm aus. Herkunft und Anbindung der Regeln stehen in [UPSTREAM.md](docs/UPSTREAM.md).

## Erste Einrichtung

1. [Sicherheitshinweise](SAFETY.de.md) lesen und die [App selbst kompilieren](docs/BUILDING.de.md).
2. Android-Benachrichtigungszugriff erteilen, Empfang in der App einschalten, eigene Benachrichtigungen erlauben und die angezeigten Akku-/Hintergrundeinstellungen prüfen.
3. Alarmstufen und Ausgabewege konfigurieren. Synthetische Vorschauen verwenden und tatsächliche Hörbarkeit, Sprache und Vibration auf Deinem Gerät prüfen. App-Lautstärken sind relativ zur Android-Weckerlautstärke; System-stumm bleibt stumm.
4. **Ausgabe aktiv** auf dem Dashboard einschalten. Bei fehlenden Voraussetzungen erscheint darunter ein roter Link. Nach einem Update muss der Schalter erneut eingeschaltet werden.
5. Einen unbekannten Pfeil nur bestätigen, wenn die eingefrorene Vorschau dem Quellpfeil entspricht. Frühere Zuordnungen lassen sich über die Diagnose korrigieren.

Sprache benötigt eine installierte Offline-Stimme in der gewählten Ansagesprache. Eigene Texte bleiben erhalten und werden bei einem Sprachwechsel nicht übersetzt. Erholungsunterdrückung und Lückenwarnung sind optional und zunächst ausgeschaltet.

## Daten und Berechtigungen

Die App hat keine Internet-, Bluetooth- oder Sensorberechtigung. Einstellungen, bestätigte Pfeilreferenzen, Alarmzustände und der kurze Diagnoseverlauf liegen im privaten App-Speicher. Android erteilt den Nachrichtenzugriff grundsätzlich breit; der Parser akzeptiert nur die unterstützten CamAPS-Quellen. Keine persönlichen Messwerte, Benachrichtigungsdumps, Gesundheitsdaten in Screenshots, Signierschlüssel oder Zugangsdaten in Issues veröffentlichen.

## Lizenz und Credits

GPL-3.0-or-later mit vollständigem [GPLv3-Text](LICENSE). Die vier isolierten xDrip-Klassen behalten Herkunft und Hinweise. [CREDITS.md](CREDITS.md), [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) und [UPSTREAM.md](docs/UPSTREAM.md) erläutern Urheberschaft, Änderungen und gesondert lizenzierte Bauwerkzeuge. Produktnamen benennen nur die Kompatibilität; sie bedeuten keine Verbindung zu oder Unterstützung durch deren Hersteller.
