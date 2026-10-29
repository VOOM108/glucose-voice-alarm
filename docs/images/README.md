# Documentation images

All numbers and histories in this folder are synthetic fixtures. No personal phone/watch captures or measurements are used.

- Phone dashboard, settings and widget images are native Android/Robolectric renderings from the phone UI tests. `phone-settings-trends.png` shows the separate display-text editor; phone images reflect source0.21.3.
- Watch active/ambient pictures are previews from the shared GVA renderer with the face's clock and age arrangement. They illustrate GVA Dots; they are not captures of the Wear OS watch-face host.
- The tile image is produced by the native tile renderer. The composite and curve complication graphics come from the actual GVA providers/rendering code.
- Age, value, trend and delta examples contain the actual providers' synthetic preview text, laid out for documentation. A third-party face determines their real typography and placement.
- `phone-aod-schematic.svg` is an explicitly labeled schematic based on the phone notification fields. Its layout is not a promised Pixel or Samsung appearance. Android controls notification promotion, AOD visibility and placement.

`provenance.json` records hashes and source categories for the selected images. Native examples may differ from real-device font rendering or system chrome. See [current limitations](../STATUS.md).

Deutsch: Alle Beispiele sind synthetisch. Die AOD-Grafik ist ein Schema; bei den Kurztext-Komplikationen zeigt das Bild den gelieferten Inhalt, nicht das Layout eines bestimmten Zifferblatts. Persönliche Gerätebilder und Messwerte sind nicht enthalten.
