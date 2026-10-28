// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

/** Process-local observations expire on process restart; no fake measurement freshness. */
final class ProbeState {
    static volatile Observation latest;
    private static volatile Observation lastDisplay;
    static final long DISPLAY_MAX_AGE_MS=2*60*60*1000L;
    static volatile VectorReference.Candidate vectorCandidate;
    static volatile String status = "Empfang noch nicht verbunden";
    static volatile String diagnostics = "Noch kein CamAPS-Update für die Trenddiagnose vorhanden.";
    static final class Observation {
        final double value;
        final NotificationParser.Trend trend;
        final ReceiptRate.Result receiptRate;
        final long receivedElapsed, postedWall,receivedWall;
        final String notificationKey;
        Observation(double value, NotificationParser.Trend trend, long receivedElapsed, long postedWall, String key) {
            this(value,trend,receivedElapsed,postedWall,key,ReceiptRate.unavailable());
        }
        Observation(double value, NotificationParser.Trend trend, long receivedElapsed, long postedWall, String key,ReceiptRate.Result receiptRate) {
            this.receiptRate=receiptRate;
            this.value = value; this.trend = trend; this.receivedElapsed = receivedElapsed;
            this.receivedWall=System.currentTimeMillis();
            this.notificationKey = key;
            this.postedWall = postedWall;
        }
    }
    static void observed(Observation observation) { lastDisplay=observation; latest=observation; }
    /** A missing source is never fresh input, but does not erase the last known display. */
    static void unavailable() { Observation old=latest; if(old!=null)lastDisplay=old; latest=null; }
    static void clear() { latest=null; lastDisplay=null; }
    static boolean displayAllowed(android.content.Context c) {
        return SafetyConsent.ready(c)&&c.getSharedPreferences(CamApsListener.PREFS,0).getBoolean(CamApsListener.ENABLED,false)&&!UnitPreferences.conflict(c);
    }
    static Observation display(android.content.Context c) {
        Observation current=latest,shown=current!=null?current:lastDisplay;
        return displayAllowed(c)&&displayClockValid(shown,android.os.SystemClock.elapsedRealtime(),System.currentTimeMillis())?shown:null;
    }
    static boolean displayClockValid(Observation shown,long elapsed,long wall) {
        if(shown==null||elapsed<shown.receivedElapsed||wall<shown.receivedWall)return false;
        long age=elapsed-shown.receivedElapsed;
        return age<=DISPLAY_MAX_AGE_MS&&Math.abs((wall-shown.receivedWall)-age)<=60000;
    }
    static boolean displayFresh(android.content.Context c,Observation shown) {
        return shown!=null&&shown==latest&&SpeechPolicy.canSpeak(displayAllowed(c),shown.receivedElapsed,
                android.os.SystemClock.elapsedRealtime(),shown.postedWall,System.currentTimeMillis());
    }
    private ProbeState() {}
}
