# Glucose Voice Alarm — Telefon

[Projektübersicht](../README.de.md) · [English](README.md) · [GVA Watch](../companionWear/README.de.md) · [GVA Dots](../companionWatchFace/README.de.md)

Die Telefon-App empfängt unterstützte CamAPS-Benachrichtigungen, prüft ihre eingestellten Alarme und versorgt die anderen GVA-Anzeigen. Version **0.20.4 Beta**. Sie funktioniert ohne die beiden Uhrenkomponenten.

**Experimentelles Forschungswerkzeug; nicht für medizinische Verwendung oder Therapieentscheidungen.** Lies [SAFETY.de.md](../SAFETY.de.md). CamAPS steuert weiterhin Sensor und Pumpe.

## Beispiele

Synthetische Beispiele. Dashboard und Einstellungen sind native App-Renderings; die AOD-Grafik ist ein Schema der an Android übergebenen Informationen, kein Pixel-Screenshot. Das tatsächliche Aussehen bestimmt Android.

<table><tr><td valign="top"><a href="../docs/images/phone-dashboard-dark.png"><img src="../docs/images/phone-dashboard-dark.png" width="230" alt="Dashboard"></a><br>Dashboard</td><td valign="top"><a href="../docs/images/phone-aod-schematic.svg"><img src="../docs/images/phone-aod-schematic.svg" width="420" alt="AOD · Schema"></a><br>AOD · Schema<br><br><a href="../docs/images/phone-widget-light.png"><img src="../docs/images/phone-widget-light.png" width="420" alt="Widget · hell"></a><br>Widget · hell</td></tr></table>

<details><summary>Einstellungsbeispiele</summary>

<table><tr><td valign="top"><a href="../docs/images/phone-settings-output.png"><img src="../docs/images/phone-settings-output.png" width="260" alt="Ausgabe"></a><br>Ausgabe</td><td valign="top"><a href="../docs/images/phone-settings-levels.png"><img src="../docs/images/phone-settings-levels.png" width="260" alt="Alarmstufen"></a><br>Alarmstufen</td></tr></table>

</details>

Das Widget bietet optional eine Schalterzeile für Ton, Alarm-Sprache, Wertansagen, Vibration und Ausgabe. Je nach Breite zeigt die beschriftete Kurve 30 Minuten, eine oder zwei Stunden. Die ursprüngliche Empfangszeit bleibt sichtbar. Die übrige Fläche öffnet die App.

## Ausgabe und Einstellungen

Mehrere Alarmstufen, individuelle Ton-/Sprach-/Vibrationswege sowie Wiederholung und Snooze, soweit für die Alarmart vorgesehen. Normale Wertansagen haben ein eigenes Intervall und können nach Alarmen pausieren. Sieben einzelne Trendwörter und ganze Sprachvorlagen sind anpassbar; abgewählte Trendwörter bleiben stumm. Fünf lokale Profile speichern Einstellungen. Details im [Telefonhandbuch](../docs/PHONE_REFERENCE.de.md).

CamAPS-Pfeil und eigene numerische Änderungsrate sind getrennt. Ein kritischer Ratenalarm kann ohne erkannten Pfeil auslösen, wenn genügend frische Werte vorliegen. Die optionale Lückenwarnung beschreibt die letzte bekannte Situation und schätzt keinen aktuellen Wert.

## Bauen und einrichten

Siehe [Bauanleitung](../docs/BUILDING.de.md). Ziel: `:companionProbe:assembleDebug`. Deklarierte Mindestversion Android API 26; große native Messwert-/Live-Anzeige ab Android 17/API 37, soweit verfügbar. Sonst normale Benachrichtigung.

1. Eigene APK installieren und beide Hinweise selbst bestätigen.
2. Benachrichtigungszugriff und Empfang einschalten; Benachrichtigungen erlauben sowie Hintergrund-/Stromspareinstellungen prüfen.
3. Stufen, Ausgabe und Offline-Stimme konfigurieren. Synthetische Vorschauen und echte Hörbarkeit/Vibration am Gerät prüfen.
4. **Ausgabe aktiv** im Dashboard einschalten; nach einem Update erneut einschalten.
5. Optional Widget hinzufügen oder GVA Watch koppeln und Anzeige-/Alarmweiterleitung getrennt erlauben.

Telefon und Uhren-App benötigen dieselbe App-ID und denselben Signierschlüssel. Die App hat keine Internet- oder direkte Sensorberechtigung. Einstellungen, Zuordnungen, Alarmzustände und kurzer Diagnoseverlauf liegen im privaten App-Speicher. Keine persönlichen Werte oder Geräte-Dumps in Issues hochladen.

Siehe [aktuelle Grenzen](../docs/STATUS.md), [GPLv3](../LICENSE) und [Drittlizenzen](../THIRD_PARTY_NOTICES.md).
