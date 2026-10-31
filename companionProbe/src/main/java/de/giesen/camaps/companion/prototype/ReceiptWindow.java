// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import de.giesen.camaps.companion.prototype.alarm.AlarmStateStore.Clock;
import java.util.*;

/** Receipt-time series, deliberately not a claim about source sensor sampling times. */
public final class ReceiptWindow {
    public static final long MAX_AGE_MS=SpeechPolicy.MAX_RECEIPT_AGE_MS;
    public enum Intake { ACCEPTED, DUPLICATE, OLD_NOTIFICATION, CLOCK_RESET, INVALID }
    private final List<HistoryAnalysis.Point> points=new ArrayList<>();
    private final List<HistoryAnalysis.Point> episodePoints=new ArrayList<>();
    private final LinkedHashSet<String> identities=new LinkedHashSet<>();
    private Clock previous;
    private Long sessionStart;
    private long lastPosted;
    private boolean clockChanged;
    public boolean older(long postedMs,Clock now) {
        if(previous==null || now.boot!=previous.boot) return false;
        long elapsed=now.elapsedMs-previous.elapsedMs,wall=now.wallMs-previous.wallMs;
        return elapsed>=0 && Math.abs(wall-elapsed)<=1000 && postedMs<lastPosted;
    }
    public Intake receive(String key,long postedMs,double mgdl,Clock now) {
        if (key==null || key.length()>4096 || (!Double.isFinite(mgdl) || mgdl<=0) || postedMs<=0 || now==null) return Intake.INVALID;
        if (postedMs>now.wallMs || now.wallMs-postedMs>MAX_AGE_MS) return Intake.OLD_NOTIFICATION;
        if(older(postedMs,now)) return Intake.OLD_NOTIFICATION;
        if(previous!=null && postedMs==lastPosted && !identities.contains(key+"\u0000"+postedMs+"\u0000"+mgdl)) return Intake.INVALID;
        String identity=key+"\u0000"+postedMs+"\u0000"+mgdl;
        if (identities.contains(identity)) return Intake.DUPLICATE;
        boolean reset=false; clockChanged=false;
        if (previous!=null) {
            long elapsed=now.elapsedMs-previous.elapsedMs,wall=now.wallMs-previous.wallMs;
            clockChanged=now.boot!=previous.boot || elapsed<0 || wall<=0 || Math.abs(wall-elapsed)>1000;
            reset=clockChanged || elapsed>MAX_AGE_MS;
        }
        if (reset) { points.clear(); sessionStart=null; }
        if(clockChanged)episodePoints.clear();
        if (sessionStart==null) sessionStart=now.wallMs;
        previous=now; lastPosted=postedMs; identities.add(identity);
        while (identities.size()>500) identities.remove(identities.iterator().next());
        points.add(HistoryAnalysis.Point.received(mgdl,now.wallMs));
        episodePoints.add(HistoryAnalysis.Point.received(mgdl,now.wallMs));
        while (points.size()>500) points.remove(0);
        while(episodePoints.size()>500)episodePoints.remove(0);
        return reset?Intake.CLOCK_RESET:Intake.ACCEPTED;
    }
    public List<HistoryAnalysis.Point> points() { return Collections.unmodifiableList(new ArrayList<>(points)); }
    /** Actual receipts across gaps, for persistent-high quality only; never a rate window. */
    public List<HistoryAnalysis.Point> episodePoints(){return Collections.unmodifiableList(new ArrayList<>(episodePoints));}
    public boolean clockChanged(){return clockChanged;}
    public Long sessionStart() { return sessionStart; }
    public void clear() { points.clear(); episodePoints.clear(); identities.clear(); previous=null; sessionStart=null; lastPosted=0; clockChanged=false; }
    public static HistoryAnalysis.Limits limits() { return new HistoryAnalysis.Limits(1,MAX_AGE_MS,MAX_AGE_MS); }
}
