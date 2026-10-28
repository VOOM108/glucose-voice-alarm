# GVA Dots

[Projektübersicht](../README.de.md) · [English](README.md) · [GVA Watch](../companionWear/README.de.md) · [Telefon](../companionProbe/README.de.md)

**0.1.5 Beta.** Ein codefreies Zifferblatt im Watch Face Format v1 für runde Wear-OS-Anzeigen. Es nutzt Sammelbild und Alterskomplikation von GVA Watch. Das Zifferblatt enthält keinen eigenen Glukoseempfang oder Alarmkern.

**Experimentelles Forschungswerkzeug, nicht für medizinische Verwendung.** Lies [SAFETY.de.md](../SAFETY.de.md). Fehlende oder alte Anzeigedaten sind keine verlässliche Ausfallwarnung für Telefon oder Verbindung.

## Beispiele

Synthetische Vorschauen aus dem GVA-Renderer mit der Uhrzeit-/Altersanordnung des Zifferblatts. Keine Fotos oder Aufnahmen einer persönlichen Uhr.

<table><tr><td valign="top"><img src="../docs/images/watch-face-active.png" width="260" alt="Aktiv · Vorschau"><br>Aktiv · Vorschau</td><td valign="top"><img src="../docs/images/watch-face-ambient.png" width="260" alt="Gedimmt · Vorschau"><br>Gedimmt · Vorschau</td></tr></table>

- Großer Wert und CamAPS-Pfeil über einer Ein-/Zwei-Stunden-Kurve.
- Unten links verstrichene Minuten seit Empfang, unten rechts numerisches Delta pro Minute in der am Telefon gewählten Einheit. Ein oder zwei kleine Punkte kennzeichnen den Verlauf.
- Größerer blauer Stundenpunkt und kleinerer orangefarbener Minutenpunkt am Rand. Bei Überlagerung wird Orange über Blau gezeichnet.
- Die gedimmte Ansicht reduziert Daten-/Punkthelligkeit und blendet den Kurvenhintergrund aus. Aktualisierung und Sichtbarkeit bestimmt die Uhr.

## Bauen und auswählen

Ziel `:companionWatchFace:assembleDebug` im Projekt `watch-build/` mit JDK 21/SDK 35. WFF v1 erfordert Wear OS 4 oder neuer. Siehe [Bauanleitung](../docs/BUILDING.de.md).

Zuerst GVA Watch installieren und mit dem Telefon verbinden. Zifferblatt-APK installieren, GVA Dots selbst auswählen und beide Bereiche prüfen: **GVA · Wert und Kurve** sowie **GVA · Alter (Min.)**. Die Quellen kommen aus GVA Watch. Falls nicht automatisch gesetzt, im Zifferblatteditor zuweisen. Eine oder zwei Stunden wählst Du im Menü von GVA Watch; das Zifferblatt übernimmt diese Einstellung.

Das Aussehen auf der Watch5 Pro wurde in der Entwicklung abgenommen; Aktualisierungstakt und Akkuverbrauch benötigen weitere Alltagstests. AOD wurde am Handgelenk bestätigt; Tisch/Ladegerät sind kein verlässlicher Test des gedimmten Modus. [Aktuelle Grenzen](../docs/STATUS.md).

Zifferblattquellen: [GPL-3.0-or-later](../LICENSE). Roboto Flex: SIL OFL 1.1 mit vollständigen mitgelieferten Hinweisen. [Credits und Lizenzen](../THIRD_PARTY_NOTICES.md).
