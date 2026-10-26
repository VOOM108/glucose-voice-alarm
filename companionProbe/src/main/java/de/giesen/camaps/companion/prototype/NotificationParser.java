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
        public final Double mgdl;
        public final GlucoseUnit sourceUnit;
        public final Trend trend;
        public final String reason;
        Result(Number mgdl,Trend trend,String reason) { this(mgdl==null?null:mgdl.doubleValue(),trend,reason,GlucoseUnit.MGDL); }
        Result(Double mgdl,Trend trend,String reason,GlucoseUnit unit) { this.mgdl=mgdl;this.trend=trend;this.reason=reason;this.sourceUnit=unit; }
        Result withTrend(Trend trend,String reason) { return new Result(mgdl,trend,reason,sourceUnit); }
    }
    private static final Pattern VALUE=Pattern.compile("^([0-9]{1,3}(?:[.,][0-9]{1,2})?)\\s*(?:(mg\\s*/\\s*dl|mmol\\s*/\\s*l))?$",Pattern.CASE_INSENSITIVE);
    private static final Pattern UNIT=Pattern.compile("(mg\\s*/\\s*dl|mmol\\s*/\\s*l)",Pattern.CASE_INSENSITIVE);
    public static GlucoseUnit packageUnit(String name) {
        if("com.camdiab.fx_alert.mgdl".equals(name) || "com.camdiab.fx_alert.hx.mgdl".equals(name))return GlucoseUnit.MGDL;
        if("com.camdiab.fx_alert.mmoll".equals(name) || "com.camdiab.fx_alert.hx.mmoll".equals(name) || "com.camdiab.fx_alert.mmoll.ca".equals(name))return GlucoseUnit.MMOL;
        return null;
    }
    public static boolean acceptsPackage(String name) { return packageUnit(name)!=null; }
    public static Result parse(List<String> texts) { return parse(texts,texts); }
    public static Result parse(List<String> values,List<String> trends) { return parse(values,trends,GlucoseUnit.MGDL); }
    /** The allowlisted package declares the source unit; decimal magnitude never guesses it. */
    public static Result parse(List<String> valueTexts,List<String> trendTexts,GlucoseUnit source) {
        if(source==null)return new Result(null,Trend.UNKNOWN,"CamAPS-Einheit nicht erkannt",null);
        Set<Double> values=new LinkedHashSet<>();Set<Trend> trends=new LinkedHashSet<>();boolean conflict=false;
        for(String raw:trendTexts)if(raw!=null)readTrends(normalize(raw),trends);
        for(String raw:valueTexts) {
            if(raw==null)continue;
            String text=normalize(raw);Matcher units=UNIT.matcher(text);
            while(units.find())if((units.group(1).toLowerCase(Locale.ROOT).startsWith("mmol")?GlucoseUnit.MMOL:GlucoseUnit.MGDL)!=source)conflict=true;
            String number=text.replaceAll("[↑↓↗↘→⇈⇊⬆⬇➡⬈⬊]","").trim();Matcher m=VALUE.matcher(number);
            if(m.matches()) {
                String lexical=m.group(1);
                if(source==GlucoseUnit.MGDL && (lexical.contains(".") || lexical.contains(",")))continue;
                double n=new java.math.BigDecimal(lexical.replace(',','.')).doubleValue();
                if(n>0 && (source==GlucoseUnit.MGDL || n<100))values.add(source.canonical(n));
            }
        }
        Trend trend=trends.size()==1?trends.iterator().next():Trend.UNKNOWN;
        if(conflict)return new Result(null,trend,"CamAPS-Einheiten widersprechen sich; Empfang verworfen",source);
        if(values.size()!=1)return new Result(null,trend,values.isEmpty()?"Kein eindeutiger Glukosewert lesbar":"Mehrere unterschiedliche Zahlen; Empfang verworfen",source);
        return new Result(values.iterator().next(),trend,trends.size()>1?"Widersprüchliche Trendangaben":trend==Trend.UNKNOWN?"CamAPS-Trend nicht lesbar":"CamAPS-Trend aus Text/Beschreibung",source);
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
