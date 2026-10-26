// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import java.util.List;

/** Analysis of canonical mg/dl points with explicit receipt or measurement time basis. */
public final class HistoryAnalysis {
    public enum TimeBasis { MEASUREMENT, RECEIPT }
    public enum Quality {
        NO_DATA, UNCONFIRMED_MEASUREMENT_TIME, INVALID_POINT, FUTURE_MEASUREMENT,
        OUT_OF_ORDER, CONFLICTING_MEASUREMENT, MIXED_TIME_BASIS, DATA_GAP, STALE, TOO_SHORT, VALID
    }
    public static final class Point {
        public final double mgdl;
        /** Null for notification/receipt timestamps: neither proves a measurement time. */
        public final Long confirmedMeasurementMs;
        public final Long timestampMs;
        public final TimeBasis timeBasis;
        public Point(double mgdl, Long confirmedMeasurementMs) {
            this(mgdl,confirmedMeasurementMs,TimeBasis.MEASUREMENT);
        }
        private Point(double mgdl,Long time,TimeBasis basis) {
            this.mgdl=mgdl; timestampMs=time; timeBasis=basis;
            confirmedMeasurementMs=basis==TimeBasis.MEASUREMENT?time:null;
        }
        public static Point received(double mgdl,long receivedMs) { return new Point(mgdl,receivedMs,TimeBasis.RECEIPT); }
    }
    /** Technical caller-supplied bounds, not medical thresholds or built-in alarm defaults. */
    public static final class Limits {
        public final long minimumSpanMs, maximumGapMs, maximumAgeMs;
        public Limits(long minimumSpanMs, long maximumGapMs, long maximumAgeMs) {
            if (minimumSpanMs<=0 || maximumGapMs<=0 || maximumAgeMs<0)
                throw new IllegalArgumentException("Invalid history limits");
            this.minimumSpanMs=minimumSpanMs; this.maximumGapMs=maximumGapMs; this.maximumAgeMs=maximumAgeMs;
        }
    }
    public static final class Result {
        public final Quality quality;
        public final int uniqueMeasurements;
        public final int uniquePoints;
        public final TimeBasis timeBasis;
        /** Only present for VALID, over the actual reported span; no five-minute extrapolation. */
        public final Double deltaMgdl;
        public final Long spanMs;
        public final Double rateMgdlPerMinute;
        private Result(Quality quality, int count, Double delta, Long span, Double rate,TimeBasis basis) {
            this.quality=quality; uniquePoints=count; timeBasis=basis; uniqueMeasurements=basis==TimeBasis.RECEIPT?0:count;
            deltaMgdl=delta; spanMs=span; rateMgdlPerMinute=rate;
        }
        public boolean usable() { return quality==Quality.VALID; }
    }
    private static Result rejected(Quality quality,int count) { return new Result(quality,count,null,null,null,null); }

    /**
     * Input order is oldest to newest. Exact repeated timestamp/value pairs collapse;
     * a repeated value at a later confirmed time remains a real zero-change measurement.
     * The caller selects the window and supplies its bounds. No sorting, gap filling,
     * smoothing, inferred sample times, source-arrow conversion or alarm decision.
     */
    public static Result analyze(List<Point> window, long nowMs, Limits limits) {
        return analyze(window,nowMs,limits,TimeBasis.MEASUREMENT);
    }
    /** Receipt-time operation is explicit and never relabels a receipt as a sensor measurement. */
    public static Result analyze(List<Point> window,long nowMs,Limits limits,TimeBasis acceptedBasis) {
        if (window==null || limits==null || acceptedBasis==null || nowMs<=0) throw new IllegalArgumentException("Invalid history request");
        if (window.isEmpty()) return rejected(Quality.NO_DATA,0);
        Point first=null,previous=null;
        int count=0;
        boolean gap=false;
        for (Point point:window) {
            if (point==null || !Double.isFinite(point.mgdl) || point.mgdl<=0) return rejected(Quality.INVALID_POINT,count);
            if (point.timestampMs==null) return rejected(Quality.UNCONFIRMED_MEASUREMENT_TIME,count);
            if (point.timeBasis!=acceptedBasis) return rejected(acceptedBasis==TimeBasis.MEASUREMENT?Quality.UNCONFIRMED_MEASUREMENT_TIME:Quality.MIXED_TIME_BASIS,count);
            long time=point.timestampMs;
            if (time<=0) return rejected(Quality.INVALID_POINT,count);
            if (time>nowMs) return rejected(Quality.FUTURE_MEASUREMENT,count);
            if (previous!=null) {
                long interval=time-previous.timestampMs;
                if (interval<0) return rejected(Quality.OUT_OF_ORDER,count);
                if (interval==0) {
                    if (point.mgdl!=previous.mgdl) return rejected(Quality.CONFLICTING_MEASUREMENT,count);
                    continue;
                }
                if (interval>limits.maximumGapMs) gap=true;
            }
            if (first==null) first=point;
            previous=point; count++;
        }
        if (nowMs-previous.timestampMs>limits.maximumAgeMs) return rejected(Quality.STALE,count);
        if (gap) return rejected(Quality.DATA_GAP,count);
        long span=previous.timestampMs-first.timestampMs;
        if (count<2 || span<limits.minimumSpanMs) return rejected(Quality.TOO_SHORT,count);
        double delta=previous.mgdl-first.mgdl;
        return new Result(Quality.VALID,count,delta,span,delta*60000.0/span,acceptedBasis);
    }
    private HistoryAnalysis() {}
}
