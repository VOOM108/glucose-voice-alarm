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
    public static String observationText(int value, NotificationParser.Trend trend, long seconds) {
        return observationText(value,trend,seconds,de.giesen.camaps.companion.prototype.alarm.TrendSpeech.defaults());
    }
    public static String observationText(int value, NotificationParser.Trend trend, long seconds,de.giesen.camaps.companion.prototype.alarm.TrendSpeech words) {
        return observationText(value,trend,seconds,words,"de");
    }
    public static String observationText(int value, NotificationParser.Trend trend, long seconds,de.giesen.camaps.companion.prototype.alarm.TrendSpeech words,String language) {
        if("en".equals(language)) return "Value received "+seconds+" seconds ago: "+value+" milligrams per deciliter."+(trend==null || trend==NotificationParser.Trend.UNKNOWN?"":" Trend: "+words.word(trend)+".");
        return "Vor " + seconds + " Sekunden empfangener Wert: " + value
                + " Milligramm pro Deziliter."
                +(trend==null || trend==NotificationParser.Trend.UNKNOWN?"":" Trend: "+words.word(trend)+".");
    }
    private SpeechPolicy() {}
}
