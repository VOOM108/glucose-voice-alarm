// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype.alarm;
import de.giesen.camaps.companion.prototype.NotificationParser.Trend;
import java.util.*;
/** Five literal, user-editable trend phrases. Never changes arrow identity or alarm rules. */
public final class TrendSpeech {
    public static final int COUNT=7;
    public final List<String> words;
    public TrendSpeech(List<String> words) {
        if (words==null || (words.size()!=COUNT && words.size()!=5)) throw new IllegalArgumentException("Sieben Trendtexte erforderlich");
        List<String> copy=new ArrayList<>();
        for(String text:words) {
            if(text==null || text.trim().isEmpty() || text.length()>80 || text.codePoints().anyMatch(Character::isISOControl) || text.contains("{") || text.contains("}"))
                throw new IllegalArgumentException("Trendtexte: 1 bis 80 Zeichen, ohne Platzhalter");
            copy.add(text.trim());
        }
        if(copy.size()==5) { copy.add("doppelt aufwärts"); copy.add("doppelt abwärts"); }
        this.words=Collections.unmodifiableList(copy);
    }
    public static TrendSpeech defaults() { return defaults("de"); }
    public static TrendSpeech defaults(String language) { return "en".equals(language)?new TrendSpeech(Arrays.asList("rising","rising fast","steady","falling","falling fast","double up","double down")):new TrendSpeech(Arrays.asList("steigend","schnell steigend","stabil","fallend","schnell fallend","doppelt aufwärts","doppelt abwärts")); }
    public String word(Trend trend) {
        if(trend==null) return "";
        switch(trend) {
            case UP_RIGHT: return words.get(0); case UP: return words.get(1); case DOUBLE_UP: return words.get(5);
            case FLAT: return words.get(2); case DOWN_RIGHT: return words.get(3); case DOWN: return words.get(4); case DOUBLE_DOWN: return words.get(6); default: return "";
        }
    }
}
