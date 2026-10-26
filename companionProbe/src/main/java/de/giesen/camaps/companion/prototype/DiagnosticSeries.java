// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import java.util.*;

/** Six-hour receipt diagnostics. Drawing reduction never feeds alarm calculations. */
public final class DiagnosticSeries {
    public static final long WINDOW_MS=6*60*60000L,BUCKET_MS=5*60000L,GAP_MS=2*60000L;
    public static final class Sample {
        public final long time; public final double value;
        public Sample(long time,double value) { this.time=time; this.value=value; }
    }
    public static final class Gap {
        public final long from,to;
        Gap(long from,long to) { this.from=from; this.to=to; }
    }
    public final long from,to;
    public final int rawPoints,conflicts;
    public final List<List<Sample>> segments;
    public final List<Gap> gaps;
    private DiagnosticSeries(long from,long to,int raw,int conflicts,List<List<Sample>> segments,List<Gap> gaps) {
        this.from=from; this.to=to; rawPoints=raw; this.conflicts=conflicts;
        List<List<Sample>> copy=new ArrayList<>(); for (List<Sample> segment:segments) copy.add(Collections.unmodifiableList(new ArrayList<>(segment)));
        this.segments=Collections.unmodifiableList(copy); this.gaps=Collections.unmodifiableList(new ArrayList<>(gaps));
    }
    public static DiagnosticSeries build(List<Sample> input,long now) {
        if (input==null || now<=0 || input.size()>5000) throw new IllegalArgumentException("Invalid diagnostic series");
        long from=Math.max(1,now-WINDOW_MS); List<Sample> sorted=new ArrayList<>();
        for (Sample point:input) if (point!=null && point.time>=from && point.time<=now && Double.isFinite(point.value) && point.value>0) sorted.add(point);
        sorted.sort(Comparator.comparingLong(point -> point.time));
        List<List<Sample>> rawSegments=new ArrayList<>(); List<Sample> current=new ArrayList<>(); List<Gap> gaps=new ArrayList<>();
        Sample previous=null; boolean split=false; int conflicts=0,raw=0;
        for (int i=0;i<sorted.size();) {
            Sample point=sorted.get(i); int end=i+1; boolean conflict=false;
            while (end<sorted.size() && sorted.get(end).time==point.time) { conflict|=sorted.get(end).value!=point.value; end++; }
            i=end;
            if (conflict) { conflicts++; split=true; continue; }
            raw++;
            if (previous!=null && (split || point.time-previous.time>GAP_MS)) {
                if (!current.isEmpty()) rawSegments.add(current); current=new ArrayList<>();
                gaps.add(new Gap(previous.time,point.time));
            }
            current.add(point); previous=point; split=false;
        }
        if (!current.isEmpty()) rawSegments.add(current);
        if (previous!=null && now-previous.time>GAP_MS) gaps.add(new Gap(previous.time,now));
        List<List<Sample>> reduced=new ArrayList<>();
        for (List<Sample> segment:rawSegments) {
            List<Sample> output=new ArrayList<>(); output.add(segment.get(0));
            Sample last=segment.get(0);
            for (int i=1;i<segment.size();i++) {
                Sample next=segment.get(i);
                if (next.time/BUCKET_MS!=last.time/BUCKET_MS) {
                    if (output.get(output.size()-1)!=last) output.add(last);
                }
                last=next;
            }
            if (output.get(output.size()-1)!=last) output.add(last); reduced.add(output);
        }
        return new DiagnosticSeries(from,now,raw,conflicts,reduced,gaps);
    }
    private DiagnosticSeries() { throw new AssertionError(); }
}
