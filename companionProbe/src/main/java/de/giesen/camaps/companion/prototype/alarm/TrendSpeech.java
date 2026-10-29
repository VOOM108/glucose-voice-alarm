// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype.alarm;
import de.giesen.camaps.companion.prototype.NotificationParser.Trend;
import java.util.*;
/** Seven literal phrases and speech switches. Never changes arrow identity or alarm rules. */
public final class TrendSpeech {
    public static final int COUNT=7;
    public final List<String> words;
    public final List<Boolean> enabled;
    public TrendSpeech(List<String> words) {
        this(words,Collections.nCopies(COUNT,true));
    }
    public TrendSpeech(List<String> words,List<Boolean> enabled) {
        if (words==null || (words.size()!=COUNT && words.size()!=5)) throw new IllegalArgumentException("Sieben Trendtexte erforderlich");
        if(enabled==null || enabled.size()!=COUNT || enabled.contains(null)) throw new IllegalArgumentException("Seven trend speech switches required");
        List<String> copy=new ArrayList<>();
        for(String text:words) {
            if(text==null || text.trim().isEmpty() || text.length()>80 || text.codePoints().anyMatch(Character::isISOControl) || text.contains("{") || text.contains("}"))
                throw new IllegalArgumentException("Trendtexte: 1 bis 80 Zeichen, ohne Platzhalter");
            copy.add(text.trim());
        }
        if(copy.size()==5) { copy.add("doppelt aufwärts"); copy.add("doppelt abwärts"); }
        this.words=Collections.unmodifiableList(copy);
        this.enabled=Collections.unmodifiableList(new ArrayList<>(enabled));
    }
    public static TrendSpeech defaults() { return defaults("de"); }
    public static TrendSpeech defaults(String language) { return "en".equals(language)?new TrendSpeech(Arrays.asList("rising","rising fast","steady","falling","falling fast","rising very fast","falling very fast")):new TrendSpeech(Arrays.asList("steigend","schnell steigend","stabil","fallend","schnell fallend","sehr schnell steigend","sehr schnell fallend")); }
    public String word(Trend trend) {
        if(trend==null) return "";
        int index;
        switch(trend) {
            case UP_RIGHT: index=0; break; case UP: index=1; break; case DOUBLE_UP: index=5; break;
            case FLAT: index=2; break; case DOWN_RIGHT: index=3; break; case DOWN: index=4; break; case DOUBLE_DOWN: index=6; break; default: return "";
        }
        return enabled.get(index)?words.get(index):"";
    }
}
