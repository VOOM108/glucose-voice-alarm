// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import java.util.*;

/** Four receipt-minute points; no alarm thresholds, estimated readings or sensor-time claim. */
public final class ReceiptRate {
    public static final class Result {
        public final Double rate;
        public final long lastWall;
        private Result(Double rate,long lastWall) { this.rate=rate; this.lastWall=lastWall; }
        public Double at(long now) { return rate!=null && now>=lastWall && now-lastWall<=ReceiptWindow.MAX_AGE_MS?rate:null; }
    }
    public static Result unavailable() { return new Result(null,0); }
    public static Result calculate(List<HistoryAnalysis.Point> points,long now) {
        if(points==null || points.isEmpty() || now<=0) return unavailable();
        HistoryAnalysis.TimeBasis basis=points.get(0)==null?null:points.get(0).timeBasis;
        if(basis==null || !HistoryAnalysis.analyze(points,now,ReceiptWindow.limits(),basis).usable()) return unavailable();
        List<HistoryAnalysis.Point> minutes=new ArrayList<>();
        for(HistoryAnalysis.Point point:points) {
            if(!minutes.isEmpty() && minutes.get(minutes.size()-1).timestampMs/60000==point.timestampMs/60000) minutes.set(minutes.size()-1,point);
            else minutes.add(point);
        }
        if(minutes.size()<4) return unavailable();
        HistoryAnalysis.Point last=minutes.get(minutes.size()-1),first=minutes.get(minutes.size()-4);
        double rate=(last.mgdl-first.mgdl)*60000.0/(last.timestampMs-first.timestampMs);
        return Double.isFinite(rate)?new Result(rate,last.timestampMs):unavailable();
    }
    private ReceiptRate() {}
}
