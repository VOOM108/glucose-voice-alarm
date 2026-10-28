# GVA Watch

[Projektübersicht](../README.de.md) · [English](README.md) · [Telefon](../companionProbe/README.de.md) · [GVA Dots](../companionWatchFace/README.de.md)

**0.19.0 Beta.** Eine Uhren-APK enthält die GVA-Anzeige, die Kachel und alle sechs Komplikationsquellen. Sie empfängt Daten vom Telefon; sie enthält keine CamAPS-Uhren-App, direkte Sensorverbindung oder unabhängige Glukosealarmberechnung.

**Experimentelles Forschungswerkzeug, nicht für medizinische Verwendung.** Lies die [Sicherheitshinweise](../SAFETY.de.md). Bei Ausfall von Telefon oder Verbindung kann die Uhr nicht unabhängig warnen.

## Beispiele

Synthetische native Renderings. App und Kachel teilen sich den Datenaufbau. GVA Dots ergänzt die Uhrzeitpunkte und ein eigenes Altersfeld.

<table><tr><td valign="top"><img src="../docs/images/watch-face-active.png" width="230" alt="Gemeinsame runde Anzeige · Vorschau"><br>Gemeinsame runde Anzeige · Vorschau</td><td valign="top"><img src="../docs/images/watch-tile.png" width="230" alt="Kachel · ohne Uhrzeitpunkte"><br>Kachel · ohne Uhrzeitpunkte</td></tr></table>

## Sechs Komplikationsquellen

Diese Datenquellen gehören zu GVA Watch und sind keine sechs Apps. Wähle sie im Editor eines kompatiblen Zifferblatts. Die Kurztextbilder zeigen den gelieferten Inhalt; Schrift, Größe, Anordnung und Farben bestimmt das gewählte Zifferblatt. Die Grafikbilder stammen aus dem nativen GVA-Renderer.

| Quelle | Beispiel | Passender Bereich |
|---|---|---|
| Wert und Kurve | <img src="../docs/images/complication-composite.png" width="150" alt="Sammelkomplikation"> | Kleines Bild; auch Kurztext aus Wert und Pfeil |
| Alter in Minuten | <img src="../docs/images/complication-age.png" width="130" alt="Beispiel Altersinhalt"> | Kurztext |
| Glukosewert | <img src="../docs/images/complication-value.png" width="130" alt="Beispiel Wertinhalt"> | Kurztext |
| CamAPS-Trend | <img src="../docs/images/complication-trend.png" width="95" alt="Trendkomplikation"> | Kurztext oder einfarbiges Symbol |
| Delta pro Minute | <img src="../docs/images/complication-delta.png" width="130" alt="Beispiel Deltainhalt"> | Kurztext |
| Kurve | <img src="../docs/images/complication-curve.png" width="220" alt="Kurvenkomplikation"> | Kleines Bild |

GVA Dots verwendet das Sammelbild und die Altersquelle. Andere Zifferblätter können passende Quellen einzeln nutzen. Für die Kurve gilt die gemeinsame Ein-/Zwei-Stunden-Auswahl. In einem kleinen runden Bereich kann die breite Kurve schwer lesbar werden.

<a id="uhrenalarme"></a>

## Einrichtung und Alarme

Telefon und Uhr aus demselben Quellstand mit demselben Signierschlüssel bauen. Siehe [Bauanleitung](../docs/BUILDING.de.md). Uhrenziel: `:companionWear:assembleDebug` im Projekt `watch-build/` mit JDK 21/SDK 35. Deklarierte Mindestversion API 30; getestet wird auf Watch5 Pro mit Wear OS 6. Dynamische Altersanzeige benötigt passende Plattformunterstützung.

1. GVA Watch öffnen und beide Hinweise persönlich bestätigen.
2. Auf dem Telefon Darstellung → Uhr → Anzeige an Uhr senden einschalten. Anfangs aus; getrennt vom Alarm-Hauptschalter.
3. GVA-Kachel selbst hinzufügen oder Komplikationsquellen wählen. Antippen der App-Anzeige öffnet das Menü und die Ein-/Zwei-Stunden-Auswahl.
4. Für Alarme Telefonweiterleitung und Uhrenempfang getrennt aktivieren und Uhrenbenachrichtigungen erlauben. Zuerst den gekennzeichneten Test verwenden.

Weitergeleitet werden ausschließlich angenommene Telefonalarme und Lückenwarnungen. Die Uhr erstellt Benachrichtigungen mit zwei kurzen Vibrationsimpulsen. Beide Freigaben sind anfangs aus. Es gibt keine laufende Werte-Benachrichtigungsserie, keine Offline-Nachholung und keine eigene Uhren-Alarmberechnung. Wegwischen quittiert oder snoozet den Telefonalarm nicht. Nicht stören, Benachrichtigungseinstellungen und Verbindungszustand können Zustellung oder Vibration verhindern.

## Aktualität und Grenzen

Die Daten stammen aus dem angenommenen Telefon-Anzeigestand. Wiederverbinden und erneutes Senden erhalten das ursprüngliche Alter. Nach vier Minuten werden die einzelnen Trend- und Deltaquellen leer. Das Sammelbild kann den letzten bekannten Wert und alten Pfeil grau weiter zeigen, ohne Delta; das gemeinsame Anzeigefenster beträgt höchstens zwei Stunden. Fehlende Werte werden nicht geschätzt. Eine getrennte Quelle liefert verstrichene Minuten.

Wear OS bestimmt den tatsächlichen Takt von Kachel und Komplikationen, besonders im gedimmten Modus. Reguläres Intervall fünf Minuten; neue Daten fordern Aktualisierungen an, ein verlässlicher Minutentakt ist nicht für alle Ansichten belegt. Hintergrundverhalten, Alarmzustellung/Wahrnehmbarkeit und Akkuverbrauch brauchen weitere Geräteprüfungen. [Aktueller Stand](../docs/STATUS.md).

GVA-Code: [GPL-3.0-or-later](../LICENSE). Roboto Flex bleibt unter SIL OFL 1.1; AndroidX und Google Play services behalten ihre eigenen Lizenzen/Bedingungen. Siehe [Drittlizenzen](../THIRD_PARTY_NOTICES.md).
