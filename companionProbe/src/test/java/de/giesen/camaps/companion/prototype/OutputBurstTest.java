// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import de.giesen.camaps.companion.prototype.alarm.*;
import de.giesen.camaps.companion.prototype.alarm.AlarmEngine.*;
import de.giesen.camaps.companion.prototype.alarm.AlarmStateStore.Clock;
import java.util.*;
import java.io.File;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class OutputBurstTest {
    @org.junit.Rule public TemporaryFolder folder=new TemporaryFolder();
    private final List<AlarmOutputFlow.Plan> plans=new ArrayList<>();
    private Clock clock(long offset) { return new SyntheticAlarmScenario(Kind.LOW).clock(offset); }
    private List<HistoryAnalysis.Point> points(long offset,int... values) { List<HistoryAnalysis.Point> p=new ArrayList<>(); for(int i=0;i<values.length;i++) p.add(new HistoryAnalysis.Point(values[i],clock(offset).wallMs-(values.length-1-i)*60000L)); return p; }
    private AlarmLevel rate(String id,double threshold) { return new AlarmLevel(new AlarmEngine.Rule(id,Kind.FALLING,true,threshold,0,true,0,1440,new Outputs(true,true,0,0)),id,AlarmLevel.DEFAULT_TONE); }
    private AlarmOutputFlow flow(File directory,List<AlarmLevel> levels) { return new AlarmOutputFlow(new AlarmStateStore(directory),levels,new HistoryAnalysis.Limits(1,240000,240000),OutputPreferences.defaults(),new AlarmOutputFlow.Port() {
        public boolean available(AlarmOutputFlow.Plan p) { return true; } public boolean start(AlarmOutputFlow.Plan p) { plans.add(p); return true; } public void stop() { }
    }); }
    private void evaluate(AlarmOutputFlow flow,long at,int... values) { assertEquals(AlarmOutputFlow.Outcome.ACCEPTED,flow.evaluate(AlarmOutputFlow.Origin.SYNTHETIC,points(at,values),clock(at),720,null,NotificationParser.Trend.DOWN)); }
    @Test public void weakerRateLevelsCannotCreateFollowupToneTrainsOrStepDownAlerts() throws Exception {
        AlarmLevel a=rate("rate_two",2),b=rate("rate_four",4),c=rate("rate_six",6),low=new SyntheticAlarmScenario(Kind.LOW).level;
        File file=folder.newFolder(); AlarmOutputFlow flow=flow(file,Arrays.asList(low,a,b,c)); evaluate(flow,0,110,98,86,74);
        assertEquals(AlarmOutputFlow.Outcome.ACCEPTED,flow.alarm(low.rule.id,clock(0))); assertEquals(Arrays.asList(low.rule.id,c.rule.id),plans.get(0).ruleIds);
        assertEquals(AlarmOutputFlow.Outcome.CONDITION_FALSE,flow.alarm(a.rule.id,clock(2000))); assertEquals(AlarmOutputFlow.Outcome.CONDITION_FALSE,flow.alarm(b.rule.id,clock(2000)));
        assertEquals(AlarmOutputFlow.Outcome.NOT_DUE,flow.alarm(c.rule.id,clock(2000))); assertEquals(1,plans.size());
        evaluate(flow,60000,110,107,104,101); assertEquals(AlarmOutputFlow.Outcome.NOT_DUE,flow.alarm(a.rule.id,clock(60000))); assertEquals(1,plans.size());
        AlarmStateStore.Snapshot state=new AlarmStateStore(file).load(); assertTrue(state.entries.get(a.rule.id).active); assertFalse(state.entries.get(a.rule.id).acceptedInEpisode);
    }
    @Test public void aStrongerNewRateStageStillEscalatesAndARealNewEpisodeCanRearm() throws Exception {
        AlarmLevel a=rate("moderate",2),b=rate("strong",6); AlarmOutputFlow flow=flow(folder.newFolder(),Arrays.asList(a,b)); evaluate(flow,0,120,117,114,111);
        assertEquals(AlarmOutputFlow.Outcome.ACCEPTED,flow.alarm(a.rule.id,clock(0)));
        evaluate(flow,60000,120,112,104,96); assertEquals(AlarmOutputFlow.Outcome.ACCEPTED,flow.alarm(b.rule.id,clock(60000)));
        evaluate(flow,120000,110,110,110,110); evaluate(flow,180000,120,117,114,111);
        assertEquals(AlarmOutputFlow.Outcome.ACCEPTED,flow.alarm(a.rule.id,clock(180000))); assertEquals(3,plans.size());
    }
    @Test public void pendingTextCanUseTheNewestValueWithoutAnotherDeliveryClaim() throws Exception {
        AlarmLevel low=new SyntheticAlarmScenario(Kind.LOW).level; File file=folder.newFolder(); AlarmOutputFlow flow=flow(file,Collections.singletonList(low)); evaluate(flow,0,74);
        flow.alarm(low.rule.id,clock(0)); AlarmOutputFlow.Plan original=plans.get(0); evaluate(flow,1000,73); long revision=new AlarmStateStore(file).load().revision;
        AlarmOutputFlow.Plan updated=flow.refreshPending(original,clock(1000)); assertNotNull(updated); assertTrue(updated.speech.contains("73")); assertFalse(updated.speech.contains("74"));
        assertEquals(revision,new AlarmStateStore(file).load().revision); assertEquals(1,plans.size());
        evaluate(flow,2000,100); assertNull(flow.refreshPending(original,clock(2000)));
    }
    @Test public void olderAndConflictingSameTimestampNotificationsCannotBounceTheCurrentValue() {
        ReceiptWindow window=new ReceiptWindow(); long b=clock(0).wallMs;
        assertEquals(ReceiptWindow.Intake.ACCEPTED,window.receive("live",b,123,clock(0)));
        assertEquals(ReceiptWindow.Intake.ACCEPTED,window.receive("live",b+1000,124,clock(1000)));
        assertEquals(ReceiptWindow.Intake.OLD_NOTIFICATION,window.receive("another",b+500,122,clock(1500)));
        assertEquals(ReceiptWindow.Intake.INVALID,window.receive("live",b+1000,125,clock(1600)));
        assertEquals(2,window.points().size()); assertEquals(124,window.points().get(1).mgdl,0);
        assertEquals(ReceiptWindow.Intake.ACCEPTED,window.receive("live",b+2000,124,clock(2000)));
    }
    @Test public void refreshingPendingSpeechKeepsTheChosenTextVariant() throws Exception {
        AlarmLevel original=new SyntheticAlarmScenario(Kind.LOW).level;
        AlarmLevel low=new AlarmLevel(original.rule,original.name,original.toneUri,Arrays.asList("Erste Variante {wert}","Zweite Variante {wert}"));
        AlarmOutputFlow flow=flow(folder.newFolder(),Collections.singletonList(low)); evaluate(flow,0,74); flow.alarm(low.rule.id,clock(0)); evaluate(flow,1000,73);
        AlarmOutputFlow.Plan refreshed=flow.refreshPending(plans.get(0),clock(1000)); assertTrue(refreshed.speech.contains("Erste Variante 73")); assertFalse(refreshed.speech.contains("Zweite"));
    }
    @Test public void endedBundleMemberIsRemovedFromPendingSpeech() throws Exception {
        AlarmLevel low=new SyntheticAlarmScenario(Kind.LOW).level,fall=rate("fall",6); AlarmOutputFlow flow=flow(folder.newFolder(),Arrays.asList(low,fall)); evaluate(flow,0,140,118,96,74);
        flow.alarm(low.rule.id,clock(0)); evaluate(flow,60000,140,120,100,81); AlarmOutputFlow.Plan refreshed=flow.refreshPending(plans.get(0),clock(60000));
        assertNotNull(refreshed); assertEquals(Collections.singletonList(fall.rule.id),refreshed.ruleIds); assertFalse(refreshed.speech.contains("Niedriggrenze")); assertTrue(refreshed.speech.contains("81"));
    }
    @Test public void fourRapidFlatReceiptsMustNotTriggerBothRateDirections() {
        List<AlarmEngine.Rule> rules=Arrays.asList(new SyntheticAlarmScenario(Kind.FALLING).level.rule,new SyntheticAlarmScenario(Kind.RISING).level.rule);
        List<HistoryAnalysis.Point> burst=new ArrayList<>(); for(int i=0;i<4;i++) burst.add(HistoryAnalysis.Point.received(123,clock(0).wallMs+i*1000));
        AlarmEngine.Result r=new AlarmEngine(rules,ReceiptWindow.limits(),OutputMode.SPOKEN_ALARMS,HistoryAnalysis.TimeBasis.RECEIPT).evaluate(burst,clock(3000).wallMs,720,null,null);
        assertEquals(0,r.matchedRules());
    }
    private AlarmEngine.Result receiptDecision(long[] seconds,int[] values,double threshold) {
        List<AlarmEngine.Rule> rules=new ArrayList<>(); for(Kind kind:new Kind[]{Kind.RISING,Kind.FALLING}) rules.add(new AlarmEngine.Rule(kind.name(),kind,true,threshold,0,true,0,1440,new Outputs(true,true,0,0)));
        List<HistoryAnalysis.Point> p=new ArrayList<>(); for(int i=0;i<seconds.length;i++) p.add(HistoryAnalysis.Point.received(values[i],clock(0).wallMs+seconds[i]*1000));
        return new AlarmEngine(rules,ReceiptWindow.limits(),OutputMode.SPOKEN_ALARMS,HistoryAnalysis.TimeBasis.RECEIPT).evaluate(p,p.get(p.size()-1).timestampMs,720,null,null);
    }
    @Test public void fastChangingBurstNeedsDistinctMinutesBeforeRateAlarms() {
        assertEquals(0,receiptDecision(new long[]{0,1,2,3},new int[]{140,120,100,80},2).matchedRules());
        assertEquals(1,receiptDecision(new long[]{0,1,2,3,60,120,180},new int[]{145,144,143,140,125,110,95},2).matchedRules());
    }
    @Test public void roundedDownSourceMinutesCannotTurnASlowTrendIntoAFastOne() {
        AlarmEngine.Result r=receiptDecision(new long[]{0,59,118,177},new int[]{120,122,124,125},2);
        assertEquals(0,r.matchedRules());
        AlarmEngine.Result real=receiptDecision(new long[]{0,59,118,177,236},new int[]{120,124,128,132,136},2); assertEquals(1,real.matchedRules());
    }
}
