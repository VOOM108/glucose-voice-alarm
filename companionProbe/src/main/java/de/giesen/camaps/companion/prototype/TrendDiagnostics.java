// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Bounded structural summary. Never retains raw text, measurement values, keys, timestamps or images. */
public final class TrendDiagnostics {
    public enum Source {
        TITLE, TEXT, BIG_TEXT, SUB_TEXT, TEXT_LINES, INFO_TEXT, SUMMARY_TEXT, TITLE_BIG,
        OTHER_TEXT, CONTENT_VIEW, BIG_CONTENT_VIEW, HEADS_UP_VIEW
    }
    public enum ImageKind { BITMAP, VECTOR, LEVEL_LIST, STATE_LIST, OTHER }
    private static final class Field {
        int texts, descriptions, images, embeddedImages;
        boolean supplied, failed, privateFont;
        final Set<String> codes = new LinkedHashSet<>();
        final Set<String> words = new LinkedHashSet<>();
        final Set<ImageKind> kinds = new LinkedHashSet<>();
        final Set<String> graphics = new LinkedHashSet<>();
    }
    private final EnumMap<Source, Field> fields = new EnumMap<>(Source.class);
    private boolean limited, extrasFailed, smallIcon, largeIcon;
    private boolean referenceCandidate, referenceConfirmed, embeddedVector;
    private boolean shapeCandidate,exactKnown,shapeKnown,shapeConflict;

    private Field field(Source source) {
        Field value = fields.get(source);
        if (value == null) { value = new Field(); fields.put(source, value); }
        return value;
    }
    public void text(Source source, String raw, boolean description) {
        if (raw == null || raw.isEmpty()) return;
        Field f = field(source);
        if (description) f.descriptions++; else f.texts++;
        String prefix=raw.substring(0,Math.min(512,raw.length())).toLowerCase(Locale.ROOT);
        for (String word:new String[]{"trend","arrow","pfeil","up","down","doubleup","doubledown","singleup","singledown",
                "fortyfiveup","fortyfivedown","rising","falling","increasing","decreasing","rapidly","slowly","flat","steady","stable",
                "steigend","fallend","steigt","sinkt","gleichbleibend","stabil","konstant","schnell","langsam","aufwärts","abwärts","nicht","not","unbekannt"}) {
            if (java.util.regex.Pattern.compile("(?:^|[^\\p{L}])"+word+"(?:$|[^\\p{L}])").matcher(prefix).find())
                f.words.add(word.toUpperCase(Locale.ROOT));
        }
        // Only arrow/private-font code points are retained, never other characters.
        for (int offset=0, count=0; offset<raw.length() && count<512; count++) {
            int cp = raw.codePointAt(offset); offset += Character.charCount(cp);
            if (Character.getType(cp)==Character.PRIVATE_USE) f.privateFont=true;
            else if ((cp>=0x2190 && cp<=0x21ff) || (cp>=0x2794 && cp<=0x27bf)
                    || (cp>=0x27f0 && cp<=0x27ff) || (cp>=0x2900 && cp<=0x297f)
                    || (cp>=0x2b00 && cp<=0x2b11) || (cp>=0x2b30 && cp<=0x2b4c)
                    || (cp>=0x1f800 && cp<=0x1f8ff) || cp==0xfffc) {
                if (f.codes.size()<16) f.codes.add(String.format(Locale.ROOT,"U+%04X",cp));
                else limited=true;
            }
            if (offset<raw.length() && count==511) limited=true;
        }
    }
    public void remote(Source source, boolean failed) { Field f=field(source); f.supplied=true; f.failed|=failed; }
    public void image(Source source, ImageKind kind, boolean embedded) {
        Field f=field(source); if (embedded) f.embeddedImages++; else f.images++; f.kinds.add(kind);
        if (embedded && kind==ImageKind.VECTOR) embeddedVector=true;
    }
    public boolean hasEmbeddedVector() { return embeddedVector; }
    public void reference(boolean candidate, boolean confirmed) { referenceCandidate=candidate; referenceConfirmed=candidate && confirmed; }
    public void shapeReference(boolean candidate,boolean exact,boolean shape,boolean conflict) {
        shapeCandidate=candidate; exactKnown=exact; shapeKnown=shape; shapeConflict=conflict;
    }
    public void icons(boolean small, boolean large) { smallIcon=small; largeIcon=large; }
    public void imageDetails(Source source,String resourceName,ImageKind kind,int width,int height,float rotation,float matrixRotation) {
        Field f=field(source);
        if (f.graphics.size()>=16) { limited=true; return; }
        String id=resourceName!=null && resourceName.matches("[A-Za-z_][A-Za-z0-9_]{0,63}")?resourceName:"UNKNOWN_ID";
        int w=width>=0 && width<=4096?width:-1, h=height>=0 && height<=4096?height:-1;
        f.graphics.add("id="+id+"; typ="+kind.name()+"; breite="+w+"; hoehe="+h
                +"; rotation="+angle(rotation)+"; matrixRotation="+angle(matrixRotation));
    }
    private static String angle(float input) {
        if (Float.isNaN(input) || Float.isInfinite(input)) return "unbekannt";
        float normalized=input%360;
        return String.format(Locale.ROOT,"%.1f",normalized);
    }
    public void extrasFailed() { extrasFailed=true; }
    public void limitReached() { limited=true; }
    public boolean isLimited() { return limited; }
    public String summary(NotificationParser.Result result) {
        StringBuilder out=new StringBuilder("Trenddiagnose · Struktur ohne Messwerte/Rohtexte\n");
        out.append("Auswertung: ").append(result.reason).append('\n');
        out.append("Grafikreferenz: Kandidat=").append(referenceCandidate?"vorhanden":"fehlt")
                .append(", bestätigt=").append(referenceConfirmed?"ja":"nein").append('\n');
        out.append("Formvergleich: Maske=").append(shapeCandidate?"geeignet":"nicht geeignet")
                .append(", exakt=").append(exactKnown?"bekannt":"unbekannt")
                .append(", Form=").append(shapeKnown?"bekannt":"unbekannt")
                .append(", Konflikt=").append(shapeConflict?"ja":"nein").append('\n');
        for (Source source:Source.values()) {
            Field f=fields.get(source); if (f==null) continue;
            out.append(source.name()).append(": Text=").append(f.texts)
                    .append(", Beschreibung=").append(f.descriptions)
                    .append(", Bild=").append(f.images).append(", Bildspan=").append(f.embeddedImages);
            if (f.supplied) out.append(", Ansicht=").append(f.failed?"nicht lesbar":"lesbar");
            out.append(", Pfeil-/Fontcodes=").append(f.codes.isEmpty()?"keine":f.codes.toString());
            if (!f.words.isEmpty()) out.append(", Richtungstoken=").append(f.words);
            if (f.privateFont) out.append(", Privatfont=vorhanden");
            if (!f.kinds.isEmpty()) out.append(", Grafiktyp=").append(f.kinds);
            out.append('\n');
            for (String graphic:f.graphics) out.append(source.name()).append(" GRAFIK: ").append(graphic).append('\n');
        }
        out.append("Notification-Symbole: klein=").append(smallIcon?"vorhanden":"fehlt")
                .append(", groß=").append(largeIcon?"vorhanden":"fehlt").append('\n');
        if (extrasFailed) out.append("Weitere Extras nicht vollständig lesbar.\n");
        if (limited) out.append("Strukturprüfung begrenzt.\n");
        out.append("Bildfelder allein beweisen nicht, dass dort der Trendpfeil liegt.");
        return out.toString();
    }
}
