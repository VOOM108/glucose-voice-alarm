// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

public final class SpeechPolicy {
    // Technical test limit from the upstream normal speech path; no clinical freshness claim.
    public static final long MAX_RECEIPT_AGE_MS = 4 * 60_000L;
    public static boolean canSpeak(boolean enabled, long receivedElapsed, long nowElapsed,
                                   long notificationPosted, long nowWall) {
        return enabled && receivedElapsed >= 0 && nowElapsed >= receivedElapsed
                && nowElapsed - receivedElapsed <= MAX_RECEIPT_AGE_MS
                && notificationPosted > 0 && nowWall >= notificationPosted
                && nowWall - notificationPosted <= MAX_RECEIPT_AGE_MS;
    }
    public static String observationText(double value, NotificationParser.Trend trend, long seconds) {
        return observationText(value,trend,seconds,de.giesen.camaps.companion.prototype.alarm.TrendSpeech.defaults());
    }
    public static String observationText(double value, NotificationParser.Trend trend, long seconds,de.giesen.camaps.companion.prototype.alarm.TrendSpeech words) {
        return observationText(value,trend,seconds,words,"de");
    }
    public static String observationText(double value, NotificationParser.Trend trend, long seconds,de.giesen.camaps.companion.prototype.alarm.TrendSpeech words,String language) {
        return observationText(value,trend,seconds,words,language,GlucoseUnit.MGDL);
    }
    public static String observationText(double value,NotificationParser.Trend trend,long seconds,de.giesen.camaps.companion.prototype.alarm.TrendSpeech words,String language,GlucoseUnit unit) {
        return observationText(value,trend,seconds,words,language,unit,de.giesen.gva.watch.ArrowColor.NORMAL);
    }
    public static String observationText(double value,NotificationParser.Trend trend,long seconds,de.giesen.camaps.companion.prototype.alarm.TrendSpeech words,String language,GlucoseUnit unit,de.giesen.gva.watch.ArrowColor color) {
        String phrase=words.word(trend,color),number=unit.number(value,"en".equals(language)?java.util.Locale.ENGLISH:java.util.Locale.GERMAN);
        if("en".equals(language)) return "Value received "+seconds+" seconds ago: "+number+" "+unit.spokenUnit(language)+"."+(phrase.isEmpty()?"":" Trend: "+phrase+".");
        return "Vor " + seconds + " Sekunden empfangener Wert: " + number
                + " "+unit.spokenUnit(language)+"."
                +(phrase.isEmpty()?"":" Trend: "+phrase+".");
    }
    private SpeechPolicy() {}
}
