// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

/** Process-local observations expire on process restart; no fake measurement freshness. */
final class ProbeState {
    static volatile Observation latest;
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
    private ProbeState() {}
}
