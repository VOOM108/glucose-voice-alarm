# Glucose Voice Alarm — Telefon

[Projektübersicht](../README.de.md) · [English](README.md) · [GVA Watch](../companionWear/README.de.md) · [GVA Dots](../companionWatchFace/README.de.md)

Die Telefon-App empfängt unterstützte CamAPS-Benachrichtigungen, prüft ihre eingestellten Alarme und versorgt die anderen GVA-Anzeigen. Version **0.22.0 Beta**. Sie funktioniert ohne die beiden Uhrenkomponenten.

**Experimentelles Forschungswerkzeug; nicht für medizinische Verwendung oder Therapieentscheidungen.** Lies [SAFETY.de.md](../SAFETY.de.md). CamAPS steuert weiterhin Sensor und Pumpe.

## Beispiele

Synthetische Beispiele. Dashboard und Einstellungen sind native App-Renderings; die AOD-Grafik ist ein Schema der an Android übergebenen Informationen, kein Pixel-Screenshot. Das tatsächliche Aussehen bestimmt Android.

<table><tr><td valign="top"><a href="../docs/images/phone-dashboard-dark.png"><img src="../docs/images/phone-dashboard-dark.png" width="230" alt="Dashboard"></a><br>Dashboard</td><td valign="top"><a href="../docs/images/phone-aod-schematic.svg"><img src="../docs/images/phone-aod-schematic.svg" width="420" alt="AOD · Schema"></a><br>AOD · Schema<br><br><a href="../docs/images/phone-widget-light.png"><img src="../docs/images/phone-widget-light.png" width="420" alt="Widget · hell"></a><br>Widget · hell</td></tr></table>

<details><summary>Einstellungsbeispiele</summary>

<table><tr><td valign="top"><a href="../docs/images/phone-settings-output.png"><img src="../docs/images/phone-settings-output.png" width="260" alt="Ausgabe"></a><br>Ausgabe</td><td valign="top"><a href="../docs/images/phone-settings-levels.png"><img src="../docs/images/phone-settings-levels.png" width="260" alt="Alarmstufen"></a><br>Alarmstufen</td><td valign="top"><a href="../docs/images/phone-settings-trends.png"><img src="../docs/images/phone-settings-trends.png" width="260" alt="Trendanzeigetexte"></a><br>Trendanzeigetexte</td></tr></table>

</details>

Das Widget bietet optional eine Schalterzeile für Ton, Alarm-Sprache, Wertansagen, Vibration und Ausgabe. Die Toggles schalten bei erfüllten Voraussetzungen direkt; nötige Einrichtungsschritte öffnen die App. Je nach Breite zeigt die beschriftete Kurve 30 Minuten, eine oder zwei Stunden. Die runden Ziffern behalten ihre Größenbegrenzung, während der Pfeil bei mehr Breite freien Platz nutzt. Die Empfangsbeschriftung folgt der App-Sprache: „Empfangen: HH:mm“ auf Deutsch und „Received: HH:mm“ auf Englisch. Die übrige Fläche öffnet das Dashboard.

## Ausgabe und Einstellungen

Mehrere Alarmstufen, individuelle Ton-/Sprach-/Vibrationswege sowie Wiederholung und Snooze, soweit für die Alarmart vorgesehen. Normale Wertansagen haben ein eigenes Intervall und können nach Alarmen pausieren. Sprachvorlagen und alle sieben gesprochenen Trendwörter sind anpassbar; abgewählte Wörter bleiben stumm. Unter Darstellung → Trendtexte lassen sich die Dashboard-/AOD-Begriffe getrennt ändern. Leere Felder folgen der Oberflächensprache. Doppelpfeil-Standards sind „Sehr schnell steigend“ und „Sehr schnell fallend“; gespeicherte Sprachtexte bleiben erhalten. Fünf lokale Profile speichern beide Textarten getrennt. Details im [Telefonhandbuch](../docs/PHONE_REFERENCE.de.md).

CamAPS-Pfeil und eigene numerische Änderungsrate sind getrennt. Ein kritischer Ratenalarm kann ohne erkannten Pfeil auslösen, wenn genügend frische Werte vorliegen. Die optionale Lückenwarnung beschreibt die letzte bekannte Situation und schätzt keinen aktuellen Wert.

## Neue Hoch-/Niedrig-Episoden

Bei Hoch-/Niedrigalarmen gilt die automatische Wiederholungszeit innerhalb einer durchgehenden Alarmepisode je Stufe. Ein frischer gültiger Wert unter der Hochgrenze bzw. über der Niedriggrenze beendet diese Episode. Beim nächsten Grenzübertritt darf wieder ein erster Alarm über die eingeschalteten Sprach-, Ton- und Vibrationswege kommen. Exakt auf der Grenze bleibt die bisherige Alarmbedingung erfüllt.

Diese numerische Erholung hebt manuelles Snooze oder die globale Pause nicht auf. Datenlücken, ungültige oder veraltete Werte sowie ein bloß geschlossenes Zeitfenster setzen die Wiederholung nicht zurück. Ein fehlender Trendpfeil ändert die Bewertung eines gültigen Zahlenwerts nicht. Mehrere gleichgerichtete Stufen behalten ihre eigenen Fristen beim Wechsel zu einer höheren Stufe. Dadurch wird eine bereits gemeldete, durchgehend überschrittene untere Stufe beim Abklingen der höheren Stufe nicht vorzeitig neu alarmiert.

Bestehende Fristen werden beim Update nicht rückwirkend gelöscht. Die neue Regel benötigt einen neu empfangenen gültigen Wert zurück innerhalb der jeweiligen Grenze. Eine zusätzliche dauerhafte Bedingungsbenachrichtigung mit Sperrgrund und Restzeit ist noch nicht umgesetzt.

## Alarmansage nach Stimmenstart

Wird die Offline-Stimme erst vorbereitet, während ein Live-Messwertalarm bereits über Ton oder Vibration startet, darf die fehlende Ansage einmal innerhalb von zehn Sekunden nach Alarmstart folgen. GVA prüft vorher die aktuelle Alarmbedingung, das Zeitfenster und die Frische der vorhandenen Daten erneut und setzt den Text mit dem aktuellen Wert zusammen. Ton und Vibration werden nicht wiederholt. Bei abgelaufener Frist oder nicht mehr gültiger Bedingung entfällt die Ansage. Ausschalten, Pause, Quellenverlust sowie Änderungen an Einstellungen oder Pfeilzuordnungen verwerfen die wartende Ansage. Ein Dienstneustart stellt sie nicht wieder her. Manuelle und synthetische Vorschauen sowie Datenlückenwarnungen verwenden diese Nachreichung nicht.

Unter „Hintergrund & AOD → Details und Systemfreigaben“ steht der letzte Alarmstatus getrennt für Sprache, Ton und Vibration. Normale Wertansagen überschreiben ihn nicht. Der private Cache enthält nur feste technische Zustände, keine Messwerte, Ansagetexte, Alarmkennungen oder Zeiten. Eine an Android oder die Stimme übergebene Ausgabe bestätigt nicht, dass sie hörbar oder spürbar war.

## Pfeilzuordnungen

Der Stift neben dem Dashboard-Trend bleibt auch bei bekannten Pfeilen erreichbar. Damit lässt sich eine bestehende Zuordnung prüfen und korrigieren. Der Dialog zeigt eine feste Grafikvorschau; nach einer neuen CamAPS-Meldung muss sie vor dem Speichern neu geladen werden. Ohne aktuelle Grafik erklärt ein Hinweis, dass die nächste Meldung abgewartet werden muss. Eine Korrektur beendet laufende und ausstehende GVA-Audioausgaben. Der letzte angezeigte Wert und seine ursprüngliche Empfangszeit bleiben erhalten; weitere Ausgabe wartet auf einen neuen Empfang. Pfeilzuordnungen liegen nur im privaten App-Speicher und sind weder im Quellprojekt noch in den Einstellungsprofilen enthalten.

## Bauen und einrichten

Siehe [Bauanleitung](../docs/BUILDING.de.md). Ziel: `:companionProbe:assembleDebug`. Deklarierte Mindestversion Android API 26; große native Messwert-/Live-Anzeige ab Android 17/API 37, soweit verfügbar. Sonst normale Benachrichtigung.

1. Eigene APK installieren und beide Hinweise selbst bestätigen.
2. Benachrichtigungszugriff und Empfang einschalten; Benachrichtigungen erlauben sowie Hintergrund-/Stromspareinstellungen prüfen.
3. Stufen, Ausgabe und Offline-Stimme konfigurieren. Synthetische Vorschauen und echte Hörbarkeit/Vibration am Gerät prüfen.
4. **Ausgabe aktiv** im Dashboard einschalten; nach einem Update erneut einschalten.
5. Optional Widget hinzufügen oder GVA Watch koppeln und Anzeige-/Alarmweiterleitung getrennt erlauben.

Telefon und Uhren-App benötigen dieselbe App-ID und denselben Signierschlüssel. Die App hat keine Internet- oder direkte Sensorberechtigung. Einstellungen, Zuordnungen, Alarmzustände und kurzer Diagnoseverlauf liegen im privaten App-Speicher. Keine persönlichen Werte oder Geräte-Dumps in Issues hochladen.

Siehe [aktuelle Grenzen](../docs/STATUS.md), [GPLv3](../LICENSE) und [Drittlizenzen](../THIRD_PARTY_NOTICES.md).
