// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import java.util.*;

/** Literal display overrides only. Blank entries follow the current UI language. */
public final class TrendDisplayWords {
    public static final int COUNT=7,MAX_LENGTH=40;
    public static final List<NotificationParser.Trend> TRENDS=Collections.unmodifiableList(Arrays.asList(
            NotificationParser.Trend.UP_RIGHT,NotificationParser.Trend.UP,NotificationParser.Trend.FLAT,
            NotificationParser.Trend.DOWN_RIGHT,NotificationParser.Trend.DOWN,
            NotificationParser.Trend.DOUBLE_UP,NotificationParser.Trend.DOUBLE_DOWN));
    public final List<String> overrides;
    public TrendDisplayWords(List<String> words) {
        if(words==null || words.size()!=COUNT)throw new IllegalArgumentException(AppText.tr("Sieben Anzeigetexte erforderlich"));
        List<String> copy=new ArrayList<>();
        for(String word:words) {
            if(word==null || word.length()>MAX_LENGTH || word.codePoints().anyMatch(Character::isISOControl)
                    || word.contains("{") || word.contains("}"))
                throw new IllegalArgumentException(AppText.tr("Anzeigetexte: höchstens 40 Zeichen, ohne Zeilenumbrüche oder Platzhalter"));
            copy.add(word.trim());
        }
        overrides=Collections.unmodifiableList(copy);
    }
    public static TrendDisplayWords defaults() { return new TrendDisplayWords(Collections.nCopies(COUNT,"")); }
    public String title(NotificationParser.Trend trend) {
        int index=TRENDS.indexOf(trend);
        return index>=0 && !overrides.get(index).isEmpty()?overrides.get(index):MetricNotification.trendTitle(trend);
    }
}
