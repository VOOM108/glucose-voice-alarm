// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import de.giesen.camaps.companion.prototype.alarm.*;
import de.giesen.camaps.companion.prototype.alarm.AlarmEngine.*;
import de.giesen.camaps.companion.prototype.alarm.AlarmStateStore.Clock;
import java.io.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** The accepted receipt-time policy remains explicit; no observation becomes a sensor timestamp. */
public class ReceiptTimeTest {
    @org.junit.Rule public TemporaryFolder temporary=new TemporaryFolder();
    private static final long B=10000000,M=60000;
    private Clock clock(long offset) { return new Clock(B+offset,1000000+offset,1); }
    private List<HistoryAnalysis.Point> receipts(SyntheticAlarmScenario scenario,long offset) {
        List<HistoryAnalysis.Point> result=new ArrayList<>();
        for (HistoryAnalysis.Point point:scenario.points(offset,true)) result.add(HistoryAnalysis.Point.received(point.mgdl,point.timestampMs));
        return result;
    }
    @Test public void receiptTimelineCalculatesRatesWithoutClaimingSensorMeasurementTimes() {
        List<HistoryAnalysis.Point> data=Arrays.asList(HistoryAnalysis.Point.received(100,B),HistoryAnalysis.Point.received(106,B+2*M));
        HistoryAnalysis.Result result=HistoryAnalysis.analyze(data,B+2*M,ReceiptWindow.limits(),HistoryAnalysis.TimeBasis.RECEIPT);
        assertTrue(result.usable()); assertEquals(HistoryAnalysis.TimeBasis.RECEIPT,result.timeBasis);
        assertEquals(3,result.rateMgdlPerMinute,0.0001); assertEquals(0,result.uniqueMeasurements); assertEquals(2,result.uniquePoints);
        assertNull(data.get(0).confirmedMeasurementMs);
        assertFalse(HistoryAnalysis.analyze(data,B+2*M,ReceiptWindow.limits()).usable());
    }
    @Test public void mixedTimeBasesOldAndOutOfOrderDataStayRejected() {
        List<HistoryAnalysis.Point> mixed=Arrays.asList(HistoryAnalysis.Point.received(100,B),new HistoryAnalysis.Point(106,B+M));
        assertEquals(HistoryAnalysis.Quality.MIXED_TIME_BASIS,HistoryAnalysis.analyze(mixed,B+M,ReceiptWindow.limits(),HistoryAnalysis.TimeBasis.RECEIPT).quality);
        List<HistoryAnalysis.Point> stale=Arrays.asList(HistoryAnalysis.Point.received(100,B));
        assertEquals(HistoryAnalysis.Quality.STALE,HistoryAnalysis.analyze(stale,B+5*M,ReceiptWindow.limits(),HistoryAnalysis.TimeBasis.RECEIPT).quality);
        assertEquals(HistoryAnalysis.Quality.OUT_OF_ORDER,HistoryAnalysis.analyze(Arrays.asList(HistoryAnalysis.Point.received(106,B+M),HistoryAnalysis.Point.received(100,B)),B+M,ReceiptWindow.limits(),HistoryAnalysis.TimeBasis.RECEIPT).quality);
    }
    @Test public void originalFiveAlarmKindsAcceptExplicitReceiptDataInAnArmedLiveInstance() throws Exception {
        for (Kind kind:Kind.values()) {
            SyntheticAlarmScenario scenario=new SyntheticAlarmScenario(kind); List<AlarmOutputFlow.Plan> plans=new ArrayList<>();
            AlarmOutputFlow.Port port=new AlarmOutputFlow.Port() {
                public boolean available(AlarmOutputFlow.Plan plan) { return true; }
                public boolean start(AlarmOutputFlow.Plan plan) { plans.add(plan); return true; }
                public void stop() { }
            };
            AlarmOutputFlow live=AlarmOutputFlow.liveReceipts(new AlarmStateStore(temporary.newFolder()),Collections.singletonList(scenario.level),ReceiptWindow.limits(),OutputPreferences.defaults(),port,OutputGuard.disabled());
            assertEquals(AlarmOutputFlow.Outcome.ACCEPTED,live.evaluate(AlarmOutputFlow.Origin.LIVE,receipts(scenario,0),clock(0),720,B-10*M,scenario.trend(true)));
            long at=kind==Kind.PERSISTENT_HIGH?2*M:0;
            if (at>0) live.evaluate(AlarmOutputFlow.Origin.LIVE,receipts(scenario,at),clock(at),720,B-10*M,scenario.trend(true));
            assertEquals(kind.name(),AlarmOutputFlow.Outcome.ACCEPTED,live.alarm(scenario.level.rule.id,clock(at)));
            assertEquals(1,plans.size()); assertFalse(plans.get(0).synthetic); assertFalse(plans.get(0).speech.contains("Testansage mit synthetischen Daten"));
            assertEquals(AlarmOutputFlow.Outcome.NOT_DUE,live.alarm(scenario.level.rule.id,clock(at)));
        }
    }
    @Test public void syntheticAndLiveOriginsCannotBeMixed() throws Exception {
        SyntheticAlarmScenario scenario=new SyntheticAlarmScenario(Kind.HIGH);
        AlarmOutputFlow.Port none=new AlarmOutputFlow.Port() { public boolean available(AlarmOutputFlow.Plan p) { fail(); return false; } public boolean start(AlarmOutputFlow.Plan p) { fail(); return false; } public void stop() {} };
        AlarmOutputFlow live=AlarmOutputFlow.liveReceipts(new AlarmStateStore(temporary.newFolder()),Collections.singletonList(scenario.level),ReceiptWindow.limits(),OutputPreferences.defaults(),none,OutputGuard.disabled());
        assertEquals(AlarmOutputFlow.Outcome.LIVE_BLOCKED,live.evaluate(AlarmOutputFlow.Origin.SYNTHETIC,receipts(scenario,0),clock(0),720,null,null));
        assertEquals(AlarmOutputFlow.Outcome.DATA_BLOCKED,live.evaluate(AlarmOutputFlow.Origin.LIVE,scenario.points(0,true),clock(0),720,null,null));
    }
    @Test public void windowDeduplicatesGraphicsOnlyChangesButKeepsEqualValuesAtNewEvents() {
        ReceiptWindow window=new ReceiptWindow();
        assertEquals(ReceiptWindow.Intake.ACCEPTED,window.receive("synthetic",B,123,clock(0)));
        assertEquals(ReceiptWindow.Intake.DUPLICATE,window.receive("synthetic",B,123,clock(1)));
        assertEquals(ReceiptWindow.Intake.ACCEPTED,window.receive("synthetic",B+M,123,clock(M)));
        assertEquals(2,window.points().size()); assertEquals(Long.valueOf(B),window.sessionStart());
    }
    @Test public void oldNotificationsCannotBecomeNewReceiptsAndGapsRestartTheWindow() {
        ReceiptWindow window=new ReceiptWindow(); assertEquals(ReceiptWindow.Intake.OLD_NOTIFICATION,window.receive("s",B-5*M,123,clock(0)));
        window.receive("s",B,123,clock(0));
        assertEquals(ReceiptWindow.Intake.CLOCK_RESET,window.receive("s",B+5*M,124,clock(5*M)));
        assertEquals(1,window.points().size()); assertEquals(Long.valueOf(B+5*M),window.sessionStart());
    }
    @Test public void receiptClockOrBootChangesCannotCreateAnArtificialRate() {
        ReceiptWindow window=new ReceiptWindow(); window.receive("s",B,100,clock(0));
        assertEquals(ReceiptWindow.Intake.CLOCK_RESET,window.receive("s",B+M,106,new Clock(B+M,1000001,1)));
        assertEquals(1,window.points().size());
        assertEquals(ReceiptWindow.Intake.CLOCK_RESET,window.receive("s",B+2*M,110,new Clock(B+2*M,1000,2)));
        assertEquals(1,window.points().size());
    }
    @Test public void schedulesAreRecheckedBeforeADelayedDelivery() throws Exception {
        SyntheticAlarmScenario scenario=new SyntheticAlarmScenario(Kind.HIGH);
        AlarmEngine.Rule r=new AlarmEngine.Rule("scheduled",Kind.HIGH,true,150,0,false,720,721,new Outputs(true,false,3,5));
        List<AlarmOutputFlow.Plan> plans=new ArrayList<>();
        AlarmOutputFlow.Port port=new AlarmOutputFlow.Port() { public boolean available(AlarmOutputFlow.Plan p) { return true; } public boolean start(AlarmOutputFlow.Plan p) { plans.add(p); return true; } public void stop() {} };
        AlarmOutputFlow live=AlarmOutputFlow.liveReceipts(new AlarmStateStore(temporary.newFolder()),Collections.singletonList(new AlarmLevel(r,"Test",AlarmLevel.DEFAULT_TONE)),ReceiptWindow.limits(),OutputPreferences.defaults(),port,OutputGuard.disabled());
        live.evaluate(AlarmOutputFlow.Origin.LIVE,receipts(scenario,0),clock(0),720,null,null);
        assertEquals(AlarmOutputFlow.Outcome.CONDITION_FALSE,live.alarm("scheduled",clock(2*M))); assertTrue(plans.isEmpty());
    }
}
