// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class DiagnosticSeriesTest {
    private static final long M=60000,B=40000000;
    @Test public void minuteDataAreReducedForDrawingWithoutChangingInputOrBridgingGaps() {
        List<DiagnosticSeries.Sample> input=new ArrayList<>();
        for (int i=0;i<=360;i++) if (i<100 || i>110) input.add(new DiagnosticSeries.Sample(B+i*M,100+i%30));
        int raw=input.size(); DiagnosticSeries result=DiagnosticSeries.build(input,B+360*M);
        assertEquals(raw,input.size()); assertEquals(raw,result.rawPoints); assertEquals(1,result.gaps.size()); assertEquals(2,result.segments.size());
        int drawn=0; for (List<DiagnosticSeries.Sample> segment:result.segments) drawn+=segment.size();
        assertTrue(drawn<90); assertTrue(result.gaps.get(0).to-result.gaps.get(0).from>10*M);
    }
    @Test public void oldAndFutureValuesStayOutsideTheSixHourView() {
        DiagnosticSeries result=DiagnosticSeries.build(Arrays.asList(new DiagnosticSeries.Sample(B-7*60*M,100),new DiagnosticSeries.Sample(B,123),new DiagnosticSeries.Sample(B+M,124)),B);
        assertEquals(1,result.rawPoints); assertEquals(B-DiagnosticSeries.WINDOW_MS,result.from); assertEquals(B,result.to);
    }
    @Test public void trailingGapIsVisibleAndAnEmptyHistoryCreatesNoInventedPoint() {
        assertTrue(DiagnosticSeries.build(Collections.emptyList(),B).segments.isEmpty());
        DiagnosticSeries result=DiagnosticSeries.build(Collections.singletonList(new DiagnosticSeries.Sample(B-5*M,123)),B);
        assertEquals(1,result.gaps.size()); assertEquals(B,result.gaps.get(0).to);
    }
    @Test public void repeatedEqualValuesAreKeptAtNewTimesButConflictingSimultaneousValuesSplitTheLine() {
        DiagnosticSeries result=DiagnosticSeries.build(Arrays.asList(new DiagnosticSeries.Sample(B,100),new DiagnosticSeries.Sample(B+M,100),
                new DiagnosticSeries.Sample(B+2*M,105),new DiagnosticSeries.Sample(B+2*M,120),new DiagnosticSeries.Sample(B+3*M,110)),B+3*M);
        assertEquals(3,result.rawPoints); assertEquals(1,result.conflicts); assertEquals(2,result.segments.size());
    }
    @Test public void aGapInsideOneFiveMinuteBucketIsNotHiddenByReduction() {
        long base=B-B%DiagnosticSeries.BUCKET_MS;
        DiagnosticSeries result=DiagnosticSeries.build(Arrays.asList(new DiagnosticSeries.Sample(base,100),new DiagnosticSeries.Sample(base+4*M,110)),base+4*M);
        assertEquals(2,result.segments.size()); assertEquals(1,result.gaps.size());
    }
}
