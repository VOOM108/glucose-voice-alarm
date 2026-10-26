// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype.alarm;
import de.giesen.camaps.companion.prototype.AppText;

import de.giesen.camaps.companion.prototype.NotificationParser;
import java.util.*;
import java.util.regex.*;

/** Literal, bounded templates. No expressions, network or generated medical content. */
public final class SpeechTemplate {
    private static final Pattern TOKEN=Pattern.compile("\\{([a-z_]+)\\}");
    private static final Set<String> ALARM=new HashSet<>(Arrays.asList("wert","einheit","stufe","grenze","trend","dauer_minuten"));
    private static final Set<String> VALUE=new HashSet<>(Arrays.asList("wert","einheit","trend","alter_sekunden"));
    public static final String VALUE_DEFAULT="Der Wert beträgt {wert} {einheit}. Trend: {trend}.";
    public static String valueDefault(String language) { return "en".equals(language)?"The value is {value} {unit}. Trend: {trend}.":VALUE_DEFAULT; }
    public static String defaultAlarm(AlarmEngine.Kind kind,String language) {
        if(!"en".equals(language)) return defaultAlarm(kind);
        switch(kind) {
            case HIGH:return "The value is above your high threshold. Current value {value} {unit}.";
            case LOW:return "The value is below your low threshold. Current value {value} {unit}.";
            case PERSISTENT_HIGH:return "The value has been above your high threshold for {duration_minutes} minutes. Current value {value} {unit}.";
            case RISING:return "Critical rise rate at {value}.";
            case FALLING:return "Critical fall rate at {value}.";
            default:throw new IllegalArgumentException();
        }
    }
    public static String canonical(String token) {
        switch(token) { case "value":return "wert"; case "unit":return "einheit"; case "level":return "stufe"; case "threshold":return "grenze"; case "duration_minutes":return "dauer_minuten"; case "age_seconds":return "alter_sekunden"; default:return token; }
    }
    public static String defaultAlarm(AlarmEngine.Kind kind) {
        switch(kind) {
            case HIGH: return "Der Wert liegt über Deiner Hochgrenze. Aktuell {wert} {einheit}.";
            case LOW: return "Der Wert liegt unter Deiner Niedriggrenze. Aktuell {wert} {einheit}.";
            case PERSISTENT_HIGH: return "Der Wert liegt seit {dauer_minuten} Minuten über Deiner Hochgrenze. Aktuell {wert} {einheit}.";
            case RISING: return "Kritische Anstiegsrate bei {wert}.";
            case FALLING: return "Kritische Fallrate bei {wert}.";
            default: throw new IllegalArgumentException();
        }
    }
    public static void validate(String text,AlarmEngine.Kind kind,boolean valueOnly) {
        if (text==null || text.trim().isEmpty() || text.length()>500 || text.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException(AppText.tr("Sprachtext muss 1 bis 500 Zeichen ohne Zeilenumbruch enthalten"));
        Matcher matcher=TOKEN.matcher(text); Set<String> allowed=valueOnly?VALUE:ALARM;
        while (matcher.find()) {
            String token=canonical(matcher.group(1));
            if (!allowed.contains(token) || (!valueOnly && token.equals("dauer_minuten") && kind!=AlarmEngine.Kind.PERSISTENT_HIGH))
                throw new IllegalArgumentException(AppText.tr("Unbekannter oder hier unpassender Platzhalter: {")+token+"}");
        }
        String remainder=TOKEN.matcher(text).replaceAll("");
        if (remainder.indexOf('{')>=0 || remainder.indexOf('}')>=0) throw new IllegalArgumentException(AppText.tr("Ungültige Platzhalterklammern"));
    }
    public static List<String> variants(String lines,AlarmEngine.Kind kind) {
        List<String> result=new ArrayList<>();
        for (String line:(lines==null?"":lines).split("\\r?\\n")) if (!line.trim().isEmpty()) {
            validate(line.trim(),kind,false); result.add(line.trim());
        }
        if (result.size()>4) throw new IllegalArgumentException(AppText.tr("Höchstens vier Formulierungen, eine je Zeile"));
        return Collections.unmodifiableList(result);
    }
    public static final class Values {
        public final double mgdl;
        public final long ageSeconds,durationMinutes;
        public final NotificationParser.Trend trend;
        public final TrendSpeech words;
        public final String language;
        public final de.giesen.camaps.companion.prototype.GlucoseUnit unit;
        public Values(double mgdl,NotificationParser.Trend trend,long ageSeconds,long durationMinutes) {
            this(mgdl,trend,ageSeconds,durationMinutes,TrendSpeech.defaults());
        }
        public Values(double mgdl,NotificationParser.Trend trend,long ageSeconds,long durationMinutes,TrendSpeech words) {
            this(mgdl,trend,ageSeconds,durationMinutes,words,"de");
        }
        public Values(double mgdl,NotificationParser.Trend trend,long ageSeconds,long durationMinutes,TrendSpeech words,String language) {
            this(mgdl,trend,ageSeconds,durationMinutes,words,language,de.giesen.camaps.companion.prototype.GlucoseUnit.MGDL);
        }
        public Values(double mgdl,NotificationParser.Trend trend,long ageSeconds,long durationMinutes,TrendSpeech words,String language,de.giesen.camaps.companion.prototype.GlucoseUnit unit) {
            this.unit=java.util.Objects.requireNonNull(unit);
            if (!Double.isFinite(mgdl) || mgdl<=0 || trend==null || ageSeconds<0 || durationMinutes<0) throw new IllegalArgumentException("Invalid speech data");
            this.mgdl=mgdl; this.trend=trend; this.ageSeconds=ageSeconds; this.durationMinutes=durationMinutes;
            this.words=Objects.requireNonNull(words);
            if(!"de".equals(language) && !"en".equals(language)) throw new IllegalArgumentException("Unsupported speech language"); this.language=language;
        }
    }
    public static String render(String text,AlarmLevel level,Values values,boolean valueOnly,boolean synthetic) {
        validate(text,level==null?null:level.rule.kind,valueOnly);
        if (values.words.word(values.trend).isEmpty()) {
            text=text.replaceAll("(?i)(?:[,;]\\s*)?\\b(?:der\\s+)?(?:CamAPS-)?(?:Trend|Trendrichtung|Richtung|Entwicklung)\\s*(?::|ist|=)?\\s*\\{trend\\}\\s*[.,;!?]?","");
            text=text.replaceAll("(?:(?<=[.!?])\\s*|^)\\{trend\\}\\s*[.,;!?]?","");
        }
        Map<String,String> replacements=new HashMap<>();
        replacements.put("wert",values.unit.number(values.mgdl,"en".equals(values.language)?Locale.ENGLISH:Locale.GERMAN)); replacements.put("einheit",values.unit.spokenUnit(values.language));
        replacements.put("trend",values.words.word(values.trend)); replacements.put("alter_sekunden",Long.toString(values.ageSeconds));
        replacements.put("dauer_minuten",Long.toString(values.durationMinutes));
        if (level!=null) { replacements.put("stufe",level.name); replacements.put("grenze",values.unit.threshold(level.rule.threshold,LevelForm.isRate(level.rule.kind),"en".equals(values.language)?Locale.ENGLISH:Locale.GERMAN)); }
        Matcher matcher=TOKEN.matcher(text); StringBuffer result=new StringBuffer();
        while (matcher.find()) {
            String replacement=replacements.get(canonical(matcher.group(1))); if (replacement==null) throw new IllegalArgumentException("Missing speech value");
            matcher.appendReplacement(result,Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        String spoken=result.toString().replaceAll("\\s+([.,;:!?])","$1").replaceAll("[,:;]\\s*([.!?])","$1").replaceAll("\\s{2,}"," ").trim();
        return (synthetic?("en".equals(values.language)?"This is a test announcement with synthetic data. ":"Dies ist eine Testansage mit synthetischen Daten. "):"")+spoken;
    }
    private SpeechTemplate() {}
}
