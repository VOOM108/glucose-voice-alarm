// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype.alarm;
import de.giesen.camaps.companion.prototype.AppText;
import java.util.*;
import java.util.regex.*;

/** Gap-only templates cannot contain a current glucose value. */
public final class GapSpeechTemplate {
    private static final Pattern TOKEN=Pattern.compile("\\{([a-z_]+)\\}");
    private static final Set<String> ALLOWED=new HashSet<>(Arrays.asList("dauer_minuten","minutes","duration_minutes","trend","status"));
    public static String defaults(String language) { return "en".equals(language)?"No new readings for {minutes} minutes. {trend} {status} Current value unknown.":"Seit {dauer_minuten} Minuten keine neuen Werte. {trend} {status} Aktueller Wert unbekannt."; }
    public static void validate(String text) {
        if(text==null || text.length()>500 || text.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException(AppText.tr("Lückentext: höchstens 500 Zeichen ohne Zeilenumbruch"));
        Matcher m=TOKEN.matcher(text); while(m.find()) if(!ALLOWED.contains(m.group(1))) throw new IllegalArgumentException(AppText.tr("Unzulässige Lückenvariable: {")+m.group(1)+"}");
        String rest=TOKEN.matcher(text).replaceAll(""); if(rest.contains("{") || rest.contains("}")) throw new IllegalArgumentException(AppText.tr("Ungültige Platzhalterklammern"));
    }
    public static String render(String template,String language,long minutes,String trend,String status) {
        validate(template); String text=template.trim().isEmpty()?defaults(language):template;
        text=text.replace("{dauer_minuten}",Long.toString(minutes)).replace("{minutes}",Long.toString(minutes)).replace("{duration_minutes}",Long.toString(minutes)).replace("{trend}",trend).replace("{status}",status);
        return text.replaceAll("\\s{2,}"," ").replaceAll("\\s+([.,;:!?])","$1").trim();
    }
    private GapSpeechTemplate() {}
}
