# Glucose Voice Alarm

[English](../README.md)

Experimentelle Android-Begleitapp für gesprochene Glukosealarme, kurze Töne, Vibration und eine kompakte Wertanzeige. Sie liest unterstützte CamAPS-Benachrichtigungen auf demselben Telefon. CamAPS bleibt für Sensor und Pumpe zuständig.

**Beta-Forschungssoftware. Kein zertifiziertes oder behördlich zugelassenes Medizinprodukt. Nicht für medizinische Entscheidungen oder Therapieentscheidungen verwenden. Verlasse Dich nicht auf Echtzeitalarme oder zeitkritische Informationen dieser App.** Jede Komponente kann ausfallen. Lies vor dem Bauen oder Verwenden die [Sicherheitshinweise](../SAFETY.de.md).

Das Repository enthält Quellcode. Es gibt keine gepackten APK-Releases und keinen automatischen Release-Upload. [So kompilierst Du die App selbst](../docs/BUILDING.de.md).


Die native Statusleistenanzeige erhält nur die Zahl in der gewählten Einheit, ohne Pfeil oder Einheitentext. AOD und Dashboard behalten Wert und Pfeil; die AOD-Uhrzeit bleibt unverändert. Ob und wie viel Text Android in der Statusleiste zeigt, bestimmt das System.

## Startbildschirm-Widget

Ab Telefonversion 0.20.0 enthält GVA ein größenanpassbares Widget. Ab 0.20.2 stehen Wert und CamAPS-Pfeil groß links, die flache Kurve rechts daneben. Darunter folgen die ursprüngliche Empfangsuhrzeit mit „Received:“, Einheit und Delta pro Minute. Einheit und Hell-/Dunkeldarstellung folgen der App; die Zeit ist kein bestätigter Sensor-Messzeitpunkt.

Die Kurve zeigt je nach verfügbarer Breite 30 Minuten, eine oder zwei Stunden. „30 min“, „1 h“ oder „2 h“ steht direkt darunter. GVA reserviert zuerst Platz für Wert und Pfeil und verkürzt bei weniger Breite den Kurvenzeitraum. Die Beschriftung bezeichnet das sichtbare Zeitfenster; fehlende Daten bleiben darin offen. Die Diagnosekurve im Dashboard zeigt weiterhin sechs Stunden.

Halte eine freie Stelle auf dem Startbildschirm gedrückt und wähle Widgets → Glucose Voice Alarm → GVA · Glukose. Ab 0.20.3 beträgt die Mindesthöhe 120 dp. Ab 0.20.5 werden Wert/Pfeil und Delta als scharf aufgelöste, begrenzte Grafiken gezeichnet. So bleibt die mitgelieferte runde Schrift auch im eingeschränkten Launcher-Kontext erhalten. Zeit und Zeitraumangabe bleiben native Textelemente. Für Hauptwert und Delta enthält das Widget eine kräftige, runde Variante von Google Sans Flex unter SIL OFL 1.1. Sie wird mitgeliefert und benötigt keine bestimmte auf dem Telefon installierte Schrift. Die Uhr verwendet weiterhin Roboto Flex. Der Wert steht linksbündig, der Pfeil daneben. Die Kurve wird etwas kräftiger gezeichnet; ihre Zeichenhöhe bleibt beim Größenwechsel fest, damit eine ungenaue Höhenangabe des Launchers die Linie nicht stark verbreitert. Die Zeitraumangabe ist größer und die Kurve nutzt die verfügbare Höhe. Eine feste 4×2-Rastervorgabe entfällt. Die Rastergröße und benötigten Zellen bestimmt der Launcher anhand der Mindestmaße. Ein vorhandenes Widget kannst Du dort selbst flacher ziehen.

Die Schalterzeile bleibt auch bei niedriger Höhe sichtbar: Ton, Alarm-Sprache („Alarm“), Wertansagen („Wert“), Vibration und der Hauptschalter „Ausgabe“. Die Toggles sind größer; bei ausreichender Breite trennt etwas Abstand mit einer dezenten Linie die Ausgabe von den vier Kanälen. Die Schalterstellung zeigt an/aus; bei laufender globaler Pause heißt der Hauptschalter „Pause“. Antippen der übrigen Fläche öffnet das Dashboard. Profile und weitere Einstellungen sind in der App erreichbar. Über die Widget-Bearbeitung des Launchers lässt sich die Schalterzeile weiterhin pro Widget ausblenden, sofern der Launcher diese Funktion anbietet.

Mindestens ein wirksamer Alarmweg muss erhalten bleiben. Fehlt eine Voraussetzung, zeigt die App die vorhandene Prüfung und übernimmt keinen ungültigen Wechsel. Alte Klicks aus einer inzwischen überholten Widgetansicht ändern keine neue Einstellung. Ein gesperrtes Gerät muss zuerst entsperrt werden. Hinzufügen, Vergrößern oder Aktualisieren des Widgets schaltet keine Ausgabe ein.

Bei einer Datenlücke wird der letzte bekannte Wert bis zu zwei Stunden als Anzeige verwendet; veraltet wird er grau, der Zeitstempel rot und Delta entfällt. Android kann Aktualisierungen verzögern und ein zuvor übernommenes Bild weiter anzeigen. Beachte deshalb den Zeitstempel; das Widget ist keine verlässliche Alarm- oder Betriebsanzeige. GVA verwendet vorhandene Empfangsdaten und startet keine eigene minütliche Abfrage. Beim nächsten Aufbau nach einem Prozessneustart wird kein alter Wert aus dem Verlauf zurückgeholt. Launcher, Größenwechsel und Hintergrundverhalten müssen auf dem jeweiligen Gerät geprüft werden.

## Empfang wieder verbinden

Die App fordert eine fehlende Android-Verbindung beim Öffnen, beim Start der Ausgabe und nach einer gemeldeten Trennung erneut an. Im laufenden Hintergrunddienst sind weitere Versuche auf einen je 30 Sekunden begrenzt. Empfang muss bereits eingeschaltet und Androids Benachrichtigungszugriff von Dir erlaubt sein; die App erteilt diese Freigabe nicht selbst und schaltet keine Ausgabe ein.

Unter Anzeige und Hintergrund findest Du „Empfang neu verbinden“. Die Details zeigen den Verbindungsstatus. Ein verbundener Listener wird bei einer bloßen Datenlücke nicht automatisch neu gestartet. Die App übernimmt keine bereits vorhandenen Benachrichtigungen als neue Messwerte.

Während der Erststart-Hinweise kann die Android-Verbindung bestehen bleiben. Bis beide Hinweise bestätigt sind, verarbeitet die App weiterhin keine Quellmeldungen.

## Bestätigung beim ersten Start

Vor der ersten Nutzung verlangt die App zwei getrennte Bestätigungen: zuerst den wichtigen Warnhinweis, danach die Nutzungsbedingungen mit „Ich verstehe“. Beide Checkboxen beginnen leer. Bis beide Schritte bestätigt sind, bleiben App-Funktionen, Benachrichtigungsempfang und eigene Ausgabe gesperrt. Das gilt auch für bestehende Installationen nach Einführung dieser Abfrage. Danach musst Du die aktive Ausgabe selbst einschalten.

Die Bestätigungen bleiben auf dem Gerät und gehören nicht zu Einstellungsprofilen. Normale Updates fragen nicht erneut; inhaltlich geänderte Hinweise benötigen eine neue Bestätigung. Unter Darstellung → Sicherheit und Bedingungen kannst Du die Texte jederzeit erneut lesen, einschließlich der vollständigen GPLv3-Lizenz.

## Warum es die App gibt

Wenn CamAPS bereits die Glukosedaten empfängt und die Therapie steuert, kann sich eine Begleitapp auf einstellbare Alarme und Sprachinformationen konzentrieren. Dieses Projekt untersucht diesen engeren Zweck, ohne eine zweite vollständige Sensor- und Datendrehscheibe auszuführen. Es übernimmt ausgewählte xDrip+-Alarmregeln und ergänzt eigene Android-Anbindung, Speicherung, Oberfläche und Ausgabe. Die Zuverlässigkeit oder der vollständige Funktionsumfang von xDrip+ werden damit nicht zugesagt.

## Funktionen

- Hoch, niedrig, kritische Anstiegsrate, kritische Fallrate und dauerhaft hoch, bei Bedarf mit mehreren Alarmstufen.
- Eigene Sprachvorlagen und Trendwörter, Wiederholung/Snooze sowie eine Pause für normale Wertansagen nach einem Alarm.
- Getrennte Schalter für Ton, Alarm-Sprache, Wertansagen und Vibration. Vibration darf der einzige konfigurierte Alarmweg sein. Der Hauptschalter stoppt die eigenen Ausgaben.
- Drei zusammenpassende Glockentonsets aus dem mitgelieferten Generator, Android-Tonauswahl und sechs Vibrationsmuster.
- Optionale Erholungsregeln für Hoch-/Niedrig-Wiederholungen und eine einstellbare Lückenwarnung mit eigenem Text. Die Lückenwarnung beschreibt den letzten bekannten Zustand und erfindet keinen aktuellen Wert.
- Lokale Diagnosekurve über sechs Stunden, Hell/Dunkel/System, fünf lokale Einstellungsprofile sowie deutsche/englische Oberfläche und Standardansagen.
- Sieben zuordenbare Pfeilzustände einschließlich Doppelpfeilen. Unbekannte oder widersprüchliche Grafiken bleiben offen. Eine neue Quellmeldung macht die geöffnete Zuordnungsvorschau ungültig.
- Sperrbildschirm- und Statusbenachrichtigungen. Ab Android 17 wird die native große Live-Anzeige versucht. Hervorhebung, Platzierung und Konkurrenz mit anderen Apps bestimmt Android.

## Umfang und Grenzen

Vorbereitet ist die Telefonversion 0.20.5 Beta. Entwicklung und Telefonprüfungen konzentrierten sich auf Pixel 7 Pro mit Android 17 und CamAPS FX 1.4(194).111. Andere Geräte, Android-Varianten und CamAPS-Versionen müssen gesondert geprüft werden. Die App verarbeitet unterstützte CamAPS-Benachrichtigungen in **mg/dl oder mmol/l**. Andere CGM-Quellen, direkte Sensoranbindung und Pumpensteuerung sind nicht enthalten.

Optional sind [GVA Watch und das Zifferblatt GVA Dots](../companionWear/README.de.md) teilweise auf der Galaxy Watch5 Pro mit Wear OS 6 / One UI 8 Watch erprobt. Sie zeigen Werte und einen kurzen Verlauf; angenommene Telefonalarme lassen sich zusätzlich weiterleiten. Die Telefonübertragung ist zunächst aus. Aktualisierung der Komplikationen, Alarmzustellung und Akkuverbrauch benötigen weitere Geräteprüfungen.

Die deklarierte Mindestversion ist Android 8.0 (API 26); daraus folgt keine Kompatibilitätsgarantie. Ohne die native große Anzeige wird eine normale Benachrichtigung verwendet. Dauerhafte AOD-Sichtbarkeit und Alarmzustellung sind nicht garantiert. Stromsparregeln, geänderte Benachrichtigungen, Datenlücken, Audioeinstellungen und das Betriebssystem können sie unterbrechen.

Die angezeigte Zeit ist Empfangs-/Verarbeitungszeit, kein bestätigter Sensor-Messzeitpunkt. Lücken bleiben in der Kurve leer. Ein Trendpfeil allein löst keinen numerischen Ratenalarm aus. Herkunft und Anbindung der Regeln stehen in [UPSTREAM.md](../docs/UPSTREAM.md).

## Anzeige auf der Uhr

GVA Watch 0.19.0 Beta und das codefreie Zifferblatt GVA Dots 0.1.5 Beta passen zur vorbereiteten Telefonversion 0.20.5 Beta. Die runde Anzeige verwendet Roboto Flex, eine Kurve über eine oder zwei Stunden und eine reduzierte gedimmte Ansicht. Menüs nutzen die offiziellen Wear-Material-3-Komponenten. Die Uhren-App bietet zusätzlich eine Kachel im selben Aufbau ohne Uhrzeit-Punkte. Neben Sammelfeld und Alter lassen sich Wert, CamAPS-Trend, Delta pro Minute und Kurve einzeln in passenden fremden Zifferblättern auswählen. Die Uhr zeigt Daten und kann angenommene Telefonalarme mit Vibration weitergeben. Eine eigene Glukosealarmberechnung ist nicht enthalten. [Einrichtung und Grenzen](../companionWear/README.de.md). Die Alltagserprobung läuft; neue Einzelkomplikationen und Kachel müssen noch am Gerät geprüft werden.

Die optionale Weiterleitung eigener Alarme und Datenlückenwarnungen an GVA Watch ist ab 0.18.0 getrennt von den Anzeigedaten einschaltbar. Auf der Uhr erscheinen eigene Benachrichtigungen mit Vibration. Beide Apps benötigen eine Freigabe; unter Darstellung → Uhr gibt es einen ausdrücklich bezeichneten Test ohne Messwerte. Die Weiterleitung folgt angenommenen Telefonalarm-Ausgaben und arbeitet ohne Offline-Nachholung. Normale Werte erzeugen keine Uhrenbenachrichtigung. [Einrichtung und Grenzen](../companionWear/README.de.md#einrichtung-und-alarme).

Unter Darstellung → Uhr steht der letzte Versandversuch getrennt bei Anzeige und Alarmen, jeweils mit Uhrzeit. Test, Alarm, Textaktualisierung und Löschen sind unterscheidbar. Diese Anzeige bestätigt weder die Darstellung noch die Vibration auf der Uhr.

## Einheiten

Unterstützte CamAPS-Paketvarianten liefern mg/dl oder mmol/l. Beim ersten eindeutigen gültigen Empfang wird die Quelleinheit aus dem bekannten Paket erkannt und gespeichert. Eine mitgelieferte Einheit muss dazu passen. Die Erkennung bleibt über App-Neustarts erhalten. Ein späterer Einheitenwiderspruch verwirft die Meldung und zeigt einen Hinweis; Zahlen werden nicht nach ihrer Größenordnung eingeordnet.

Standardmäßig folgen Dashboard, Kurve, AOD, Ansagen und Grenzfelder dieser Einheit. Unter Darstellung → Entwickleroptionen kann eine feste Ausgabe in mg/dl oder mmol/l gewählt werden. Dafür ist eine Bestätigung nötig. Die Umrechnung ändert keine vorhandene Alarmgrenze. Unverändertes Speichern einer Stufe erhält deren ursprünglichen Grenzwert auch bei gerundeter Anzeige. Eigene Namen und fest ausgeschriebene Einheiten in Textvorlagen musst Du selbst prüfen; Variablen wie `{wert}` und `{einheit}` werden passend umgerechnet. Die kompakte AOD-Anzeige enthält weiterhin keine Einheit.

„CamAPS-Einheit neu erkennen“ verlangt eine Bestätigung, schaltet Live-Ausgabe aus und leert das aktuelle Empfangsfenster. Danach legt die nächste gültige Meldung die Quelleinheit fest. Die feste Ausgabeauswahl gehört zu den Profilen; die erkannte Quelleinheit bleibt eine lokale Geräteeinstellung.

Intern bleiben Werte und Alarmgrenzen in mg/dl mit erhaltenen Nachkommastellen. Verwendet wird derselbe Faktor 18,0182 wie im gepinnten xDrip-Stand. Erst die Ausgabe rundet: ganze mg/dl oder eine Nachkommastelle bei mmol/l. Die Ratenzeile verwendet eine beziehungsweise zwei Nachkommastellen pro Minute. mmol/l-Empfang ist synthetisch geprüft; eine reale Abnahme dieser Quellvariante steht aus.

## Trend und Änderungsrate

Pfeil und Trendwort stammen von CamAPS: im Dashboard, auf AOD/Sperrbildschirm und in Ansagen. Ein fehlender Pfeil wird nicht durch einen berechneten ersetzt. Die sieben gesprochenen Trendwörter sind anpassbar. Eine Checkbox je Wort bestimmt, ob es angesagt wird. Abgewählte Wörter werden wie ein fehlender Trend ausgelassen; ihr Text bleibt gespeichert. Die Auswahl gehört auch zu den Profilen und ändert weder sichtbare Pfeile noch Alarmbedingungen.

Das Dashboard zeigt daneben die eigene Änderungsrate in einer separaten kleinen Zeile mit Vorzeichen und der gewählten Einheit pro Minute. Sie verwendet die letzten vier gültigen Empfangs-Minutenpunkte, jeweils den letzten Wert je Minute, und die tatsächliche Zeitspanne. Bei unzureichenden oder alten Daten bleibt die Zeile verborgen. Die Zeitbasis ist der Empfang; Lücken werden nicht aufgefüllt.

Bei einer Datenlücke bleiben der letzte bekannte Wert und der CamAPS-Pfeil im Dashboard sowie auf AOD/Sperrbildschirm bis zu zwei Stunden sichtbar. Der Wert wird grau, die ursprüngliche Uhrzeit rot; die Benachrichtigung nennt zusätzlich das Alter in Minuten. Android kann die Farben der nativen Anzeige selbst festlegen. Diese Anzeige bleibt vom frischen Eingang für Alarme und Ansagen getrennt. Neue Werte, ein Neustart der App oder ein ausdrückliches Zurücksetzen beenden den alten Anzeigestand; es werden keine alten Verlaufspunkte als neuer Empfang eingelesen.

„Kritische Fallrate“ und „Kritische Anstiegsrate“ prüfen ihre eingestellten Schwellen unabhängig von Pfeilen und vom Intervall normaler Wertansagen. Zusätzlich zur Gesamtrate muss das jüngste Intervall passen. Sie können bei fehlendem Pfeil auslösen, solange ausreichend frische Werte vorliegen. Fehlen neue Werte, bleibt die getrennt konfigurierbare Lückenwarnung. Standardtexte sind „Kritische Fallrate bei {wert}.“ und „Kritische Anstiegsrate bei {wert}.“ Gespeicherte eigene Texte und Stufennamen bleiben erhalten.

## Erste Einrichtung

1. [Sicherheitshinweise](../SAFETY.de.md) lesen und die [App selbst kompilieren](../docs/BUILDING.de.md).
2. App öffnen und Warnhinweis sowie Nutzungsbedingungen ausdrücklich bestätigen. Das gilt nach dem Update auf 0.15 auch einmalig für bestehende Installationen.
3. Android-Benachrichtigungszugriff erteilen, Empfang in der App einschalten, eigene Benachrichtigungen erlauben und die angezeigten Akku-/Hintergrundeinstellungen prüfen.
4. Alarmstufen und Ausgabewege konfigurieren. Synthetische Vorschauen verwenden und tatsächliche Hörbarkeit, Sprache und Vibration auf Deinem Gerät prüfen. App-Lautstärken sind relativ zur Android-Weckerlautstärke; System-stumm bleibt stumm.
5. **Ausgabe aktiv** auf dem Dashboard einschalten. Bei fehlenden Voraussetzungen erscheint darunter ein roter Link. Nach einem Update muss der Schalter erneut eingeschaltet werden.
6. Einen unbekannten Pfeil nur bestätigen, wenn die eingefrorene Vorschau dem Quellpfeil entspricht. Frühere Zuordnungen lassen sich über die Diagnose korrigieren.

Sprache benötigt eine installierte Offline-Stimme in der gewählten Ansagesprache. Eigene Texte bleiben erhalten und werden bei einem Sprachwechsel nicht übersetzt. Erholungsunterdrückung und Lückenwarnung sind optional und zunächst ausgeschaltet.

## Daten und Berechtigungen

Die App hat keine Internet-, Bluetooth- oder Sensorberechtigung. Einstellungen, bestätigte Pfeilreferenzen, Alarmzustände und der kurze Diagnoseverlauf liegen im privaten App-Speicher. Android erteilt den Nachrichtenzugriff grundsätzlich breit; der Parser akzeptiert nur die unterstützten CamAPS-Quellen. Keine persönlichen Messwerte, Benachrichtigungsdumps, Gesundheitsdaten in Screenshots, Signierschlüssel oder Zugangsdaten in Issues veröffentlichen.

## Lizenz und Credits

GPL-3.0-or-later mit vollständigem [GPLv3-Text](../LICENSE). Die vier isolierten xDrip-Klassen behalten Herkunft und Hinweise. [CREDITS.md](../CREDITS.md), [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md) und [UPSTREAM.md](../docs/UPSTREAM.md) erläutern Urheberschaft, Änderungen und gesondert lizenzierte Bauwerkzeuge. Produktnamen benennen nur die Kompatibilität; sie bedeuten keine Verbindung zu oder Unterstützung durch deren Hersteller.
