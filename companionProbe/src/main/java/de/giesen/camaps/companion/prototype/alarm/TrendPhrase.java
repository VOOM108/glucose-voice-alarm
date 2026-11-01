// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype.alarm;
import de.giesen.camaps.companion.prototype.AppText;
import java.util.regex.*;

/** Bounded value/unit fragments only; substituted once, before the outer template. */
public final class TrendPhrase {
    private static final Pattern TOKEN=Pattern.compile("\\{([a-z_]+)\\}");
    public static void validate(String text,boolean blank){
        if(text==null || (!blank && text.trim().isEmpty()) || text.length()>80 || text.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException(AppText.tr("Sprachtexte: 1 bis 80 Zeichen, einzeilig"));
        Matcher m=TOKEN.matcher(text);
        while(m.find())if(!SpeechTemplate.canonical(m.group(1)).equals("wert") && !SpeechTemplate.canonical(m.group(1)).equals("einheit"))
            throw new IllegalArgumentException(AppText.tr("Im Trendtext sind nur {wert} und {einheit} erlaubt"));
        String remainder=TOKEN.matcher(text).replaceAll("");
        if(remainder.contains("{") || remainder.contains("}"))throw new IllegalArgumentException(AppText.tr("Ungültige Platzhalterklammern"));
    }
    public static boolean hasValue(String text){return text.contains("{wert}") || text.contains("{value}");}
    public static String render(String text,String value,String unit){
        return text.replace("{wert}",value).replace("{value}",value).replace("{einheit}",unit).replace("{unit}",unit);
    }
    /** Remove the original value clause, retaining independent color/age clauses and the trend. */
    static String relocate(String text){
        StringBuilder result=new StringBuilder();Matcher sentences=Pattern.compile("[^.!?;]+[.!?;]?").matcher(text);
        while(sentences.find()){
            String part=sentences.group();
            if(hasValue(part)){
                part=part.replaceAll("(?i)(?:\\b(?:der Wert beträgt|der Wert ist|the value is|aktueller Wert|current value|Wert|value|aktuell|currently|bei|at)\\s*:?\\s*)?\\{(?:wert|value)\\}(?:\\s*\\{(?:einheit|unit)\\})?","");
                part=part.replace("{einheit}","").replace("{unit}","");
            }
            part=part.replaceAll("^[\\s,;:]+","").replaceAll("[,:]\\s*([.!?;])","$1").trim();
            if(!part.replaceAll("[\\s\\p{Punct}]","").isEmpty())result.append(" ").append(part);
        }
        return result.toString().trim();
    }
    private TrendPhrase(){}
}
