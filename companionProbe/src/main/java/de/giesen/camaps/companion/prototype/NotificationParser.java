/* SPDX-License-Identifier: GPL-3.0-or-later
 * CamAPS companion probe. Parsing approach informed by xDrip UiBasedCollector
 * (JamOrHam and xDrip contributors); upstream remains in the same source tree.
 */
package de.giesen.camaps.companion.prototype;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure parser: never infer measurement time or a numeric rate from an arrow. */
public final class NotificationParser {
    public enum Trend {
        DOUBLE_UP("⇈", "doppelt aufwärts"), UP("↑", "aufwärts"),
        UP_RIGHT("↗", "schräg aufwärts"), FLAT("→", "gleichbleibend"),
        DOWN_RIGHT("↘", "schräg abwärts"), DOWN("↓", "abwärts"),
        DOUBLE_DOWN("⇊", "doppelt abwärts"), UNKNOWN("?", "unbekannt");
        public final String symbol, label;
        Trend(String symbol, String label) { this.symbol = symbol; this.label = label; }
    }

    public static final class Result {
        public final Integer mgdl;
        public final Trend trend;
        public final String reason;
        Result(Integer mgdl, Trend trend, String reason) {
            this.mgdl = mgdl; this.trend = trend; this.reason = reason;
        }
    }

    private static final Pattern VALUE = Pattern.compile("^([0-9]{1,3})\\s*(?:mg\\s*/\\s*dl)?$", Pattern.CASE_INSENSITIVE);

    public static boolean acceptsPackage(String name) {
        return "com.camdiab.fx_alert.mgdl".equals(name)
                || "com.camdiab.fx_alert.hx.mgdl".equals(name);
    }

    public static Result parse(List<String> texts) {
        return parse(texts, texts);
    }

    /** Extra trend fields must never introduce additional glucose candidates. */
    public static Result parse(List<String> valueTexts, List<String> trendTexts) {
        Set<Integer> values = new LinkedHashSet<>();
        Set<Trend> trends = new LinkedHashSet<>();
        for (String raw : trendTexts) {
            if (raw != null) readTrends(normalize(raw), trends);
        }
        for (String raw : valueTexts) {
            if (raw == null) continue;
            String s = normalize(raw);
            // Remove only understood arrow symbols, never comparison signs or units.
            String number = s.replaceAll("[↑↓↗↘→⇈⇊⬆⬇➡⬈⬊]", "").trim();
            Matcher m = VALUE.matcher(number);
            if (m.matches()) {
                int n = Integer.parseInt(m.group(1));
                if (n > 0) values.add(n);
            }
        }
        Trend trend = trends.size() == 1 ? trends.iterator().next() : Trend.UNKNOWN;
        if (values.size() != 1) return new Result(null, trend,
                values.isEmpty() ? "Kein eindeutiger mg/dl-Wert lesbar" : "Mehrere unterschiedliche Zahlen; Empfang verworfen");
        return new Result(values.iterator().next(), trend,
                trends.size() > 1 ? "Widersprüchliche Trendangaben" :
                trend == Trend.UNKNOWN ? "CamAPS-Trend nicht lesbar" : "CamAPS-Trend aus Text/Beschreibung");
    }

    private static String normalize(String raw) {
        return raw.replace('\u00a0', ' ').replace("\u2060", "")
                .replace("\ufe0f", "").replace("\ufe0e", "").trim();
    }

    private static void readTrends(String source, Set<Trend> out) {
        String s = source.replace("↑↑", "⇈").replace("↓↓", "⇊")
                .replace("⬆⬆", "⇈").replace("⬇⬇", "⇊");
        for (int i = 0; i < s.length(); i++) {
            switch (s.charAt(i)) {
                case '⇈': out.add(Trend.DOUBLE_UP); break;
                case '↑': case '⬆': out.add(Trend.UP); break;
                case '↗': case '⬈': out.add(Trend.UP_RIGHT); break;
                case '→': case '➡': out.add(Trend.FLAT); break;
                case '↘': case '⬊': out.add(Trend.DOWN_RIGHT); break;
                case '↓': case '⬇': out.add(Trend.DOWN); break;
                case '⇊': out.add(Trend.DOUBLE_DOWN); break;
                default: break;
            }
        }
        // Conservative complete descriptions for later field inspection; no substring matching.
        switch (s.toLowerCase(Locale.ROOT)) {
            case "doppelt aufwärts": case "rapidly rising": out.add(Trend.DOUBLE_UP); break;
            case "aufwärts": case "rising": out.add(Trend.UP); break;
            case "schräg aufwärts": case "slowly rising": out.add(Trend.UP_RIGHT); break;
            case "gleichbleibend": case "steady": out.add(Trend.FLAT); break;
            case "schräg abwärts": case "slowly falling": out.add(Trend.DOWN_RIGHT); break;
            case "abwärts": case "falling": out.add(Trend.DOWN); break;
            case "doppelt abwärts": case "rapidly falling": out.add(Trend.DOUBLE_DOWN); break;
            default: break;
        }
    }
}
