// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;
import static de.giesen.camaps.companion.prototype.HistoryAnalysis.Quality.*;

/** All times and values are synthetic fixtures, not copied from device data. */
public class HistoryAnalysisTest {
    private static final long MINUTE=60000, START=1000000;
    private HistoryAnalysis.Point point(int value,long offset) { return new HistoryAnalysis.Point(value,START+offset); }
    private HistoryAnalysis.Result analyze(long nowOffset,HistoryAnalysis.Point... points) {
        return HistoryAnalysis.analyze(Arrays.asList(points),START+nowOffset,new HistoryAnalysis.Limits(MINUTE,2*MINUTE,MINUTE));
    }
    private void rejectsWithoutNumbers(HistoryAnalysis.Quality expected,HistoryAnalysis.Result result) {
        assertEquals(expected,result.quality); assertFalse(result.usable());
        assertNull(result.deltaMgdl); assertNull(result.spanMs); assertNull(result.rateMgdlPerMinute);
    }
    @Test public void emptyHistoryHasNoRate() { rejectsWithoutNumbers(NO_DATA,analyze(0)); }
    @Test public void notificationAndReceiptTimesDoNotEstablishMeasurementTime() {
        rejectsWithoutNumbers(UNCONFIRMED_MEASUREMENT_TIME,analyze(MINUTE,
                new HistoryAnalysis.Point(120,null),new HistoryAnalysis.Point(123,null)));
    }
    @Test public void mixingVerifiedAndUnverifiedPointsDoesNotSkipUnknownData() {
        rejectsWithoutNumbers(UNCONFIRMED_MEASUREMENT_TIME,analyze(MINUTE,point(120,0),new HistoryAnalysis.Point(123,null)));
    }
    @Test public void risingAndFallingUseActualElapsedTime() {
        HistoryAnalysis.Result rising=analyze(90000,point(120,0),point(123,90000));
        assertTrue(rising.usable()); assertEquals(Integer.valueOf(3),rising.deltaMgdl);
        assertEquals(Long.valueOf(90000),rising.spanMs); assertEquals(2.0,rising.rateMgdlPerMinute,0.000001);
        HistoryAnalysis.Result falling=analyze(90000,point(123,0),point(120,90000));
        assertEquals(Integer.valueOf(-3),falling.deltaMgdl); assertEquals(-2.0,falling.rateMgdlPerMinute,0.000001);
    }
    @Test public void unchangedRealMeasurementsHaveZeroRate() {
        HistoryAnalysis.Result result=analyze(MINUTE,point(123,0),point(123,MINUTE));
        assertTrue(result.usable()); assertEquals(2,result.uniqueMeasurements);
        assertEquals(Integer.valueOf(0),result.deltaMgdl); assertEquals(0.0,result.rateMgdlPerMinute,0.0);
    }
    @Test public void repeatedSameMeasurementDoesNotCreateTimeOrCount() {
        HistoryAnalysis.Result result=analyze(MINUTE,point(120,0),point(120,0),point(123,MINUTE),point(123,MINUTE));
        assertTrue(result.usable()); assertEquals(2,result.uniqueMeasurements);
        assertEquals(Long.valueOf(MINUTE),result.spanMs);
        rejectsWithoutNumbers(TOO_SHORT,analyze(0,point(120,0),point(120,0)));
    }
    @Test public void contradictorySameTimeIsRejected() {
        rejectsWithoutNumbers(CONFLICTING_MEASUREMENT,analyze(MINUTE,point(120,0),point(121,0),point(123,MINUTE)));
    }
    @Test public void reversedOrLateSamplesDoNotBecomeExtremeRates() {
        rejectsWithoutNumbers(OUT_OF_ORDER,analyze(2*MINUTE,point(120,MINUTE),point(123,0)));
        rejectsWithoutNumbers(OUT_OF_ORDER,analyze(2*MINUTE,point(120,0),point(123,2*MINUTE),point(122,MINUTE)));
    }
    @Test public void evenOneInteriorGapInvalidatesTheSelectedWindow() {
        rejectsWithoutNumbers(DATA_GAP,analyze(5*MINUTE,point(120,0),point(121,MINUTE),point(122,4*MINUTE),point(123,5*MINUTE)));
    }
    @Test public void exactlyAllowedGapIsUsable() {
        assertTrue(analyze(2*MINUTE,point(120,0),point(124,2*MINUTE)).usable());
    }
    @Test public void staleNewestMeasurementDoesNotBecomeFreshThroughAnalysis() {
        rejectsWithoutNumbers(STALE,analyze(2*MINUTE+1,point(120,0),point(123,MINUTE)));
        assertTrue(analyze(2*MINUTE,point(120,0),point(123,MINUTE)).usable());
    }
    @Test public void futureTimeOrClockMovingBackIsRejected() {
        rejectsWithoutNumbers(FUTURE_MEASUREMENT,analyze(0,point(120,0),point(123,MINUTE)));
    }
    @Test public void onePointAndShortIntervalsHaveNoRate() {
        rejectsWithoutNumbers(TOO_SHORT,analyze(0,point(120,0)));
        rejectsWithoutNumbers(TOO_SHORT,analyze(MINUTE-1,point(120,0),point(123,MINUTE-1)));
    }
    @Test public void invalidPointIsNotQuietlySkipped() {
        rejectsWithoutNumbers(INVALID_POINT,analyze(MINUTE,point(120,0),null,point(123,MINUTE)));
        rejectsWithoutNumbers(INVALID_POINT,analyze(MINUTE,point(0,0),point(123,MINUTE)));
        rejectsWithoutNumbers(INVALID_POINT,analyze(MINUTE,new HistoryAnalysis.Point(120,0L),point(123,MINUTE)));
    }
    @Test public void noMedicalValueThresholdsAreBuiltIntoAnalysis() {
        assertTrue(analyze(MINUTE,point(33,0),point(450,MINUTE)).usable());
    }
    @Test public void everyLimitComesFromTheCaller() {
        HistoryAnalysis.Limits limits=new HistoryAnalysis.Limits(30000,30000,0);
        HistoryAnalysis.Result result=HistoryAnalysis.analyze(Arrays.asList(point(120,0),point(121,30000)),START+30000,limits);
        assertTrue(result.usable()); assertEquals(2.0,result.rateMgdlPerMinute,0.000001);
    }
    @Test(expected=IllegalArgumentException.class) public void zeroSpanLimitIsInvalid() { new HistoryAnalysis.Limits(0,MINUTE,MINUTE); }
    @Test(expected=IllegalArgumentException.class) public void zeroGapLimitIsInvalid() { new HistoryAnalysis.Limits(MINUTE,0,MINUTE); }
    @Test(expected=IllegalArgumentException.class) public void negativeAgeLimitIsInvalid() { new HistoryAnalysis.Limits(MINUTE,MINUTE,-1); }
    @Test(expected=IllegalArgumentException.class) public void invalidClockIsInvalidRequest() {
        HistoryAnalysis.analyze(Collections.emptyList(),0,new HistoryAnalysis.Limits(MINUTE,MINUTE,MINUTE));
    }
}
