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

public class RecoveryGapTest {
    @org.junit.Rule public TemporaryFolder folder=new TemporaryFolder();
    private final List<AlarmOutputFlow.Plan> plans=new ArrayList<>();
    private boolean accept=true;
    private AlarmLevel low() { AlarmLevel l=new SyntheticAlarmScenario(Kind.LOW).level; return new AlarmLevel(l.rule,l.name,l.toneUri,Collections.emptyList(),"SHORT",true); }
    private List<HistoryAnalysis.Point> points(long offset,int... values) { List<HistoryAnalysis.Point> result=new ArrayList<>(); for(int i=0;i<values.length;i++) result.add(new HistoryAnalysis.Point(values[i],SyntheticAlarmScenario.BASE+offset-(values.length-1-i)*60000L)); return result; }
    private Clock now(long elapsed) { return new SyntheticAlarmScenario(Kind.LOW).clock(elapsed); }
    private AlarmOutputFlow flow(File file,List<AlarmLevel> levels) { return new AlarmOutputFlow(new AlarmStateStore(file),levels,new HistoryAnalysis.Limits(1,240000,240000),OutputPreferences.defaults(),new AlarmOutputFlow.Port() {
        public boolean available(AlarmOutputFlow.Plan p) { return true; } public boolean start(AlarmOutputFlow.Plan p) { plans.add(p); return accept; } public void stop() { }
    }); }
    private void evaluate(AlarmOutputFlow flow,long offset,NotificationParser.Trend trend,int... values) { assertEquals(AlarmOutputFlow.Outcome.ACCEPTED,flow.evaluate(AlarmOutputFlow.Origin.SYNTHETIC,points(offset,values),now(offset),720,null,trend)); }
    @Test public void firstLowIsDeliveredEvenWhenAlreadyRecoveringAndOnlyDueRepeatIsSuppressed() throws Exception {
        AlarmOutputFlow flow=flow(folder.newFolder(),Collections.singletonList(low())); evaluate(flow,0,NotificationParser.Trend.UP,60,65,72);
        assertEquals(AlarmOutputFlow.Outcome.ACCEPTED,flow.alarm(low().rule.id,now(0))); assertEquals(1,plans.size());
        evaluate(flow,180001,NotificationParser.Trend.UP,62,68,75); assertEquals(AlarmOutputFlow.Outcome.SUPPRESSED,flow.alarm(low().rule.id,now(180001))); assertEquals(1,plans.size());
        evaluate(flow,240000,NotificationParser.Trend.FLAT,75,75,75); assertEquals(AlarmOutputFlow.Outcome.ACCEPTED,flow.alarm(low().rule.id,now(240000))); assertEquals(2,plans.size());
    }
    @Test public void highRecoveryIsSymmetricAndDoesNotSuppressWhileValuesAreStillRising() throws Exception {
        AlarmLevel base=new SyntheticAlarmScenario(Kind.HIGH).level; AlarmLevel high=new AlarmLevel(base.rule,base.name,base.toneUri,Collections.emptyList(),null,true);
        AlarmOutputFlow flow=flow(folder.newFolder(),Collections.singletonList(high)); evaluate(flow,0,NotificationParser.Trend.DOWN,205,200,195);
        assertEquals(AlarmOutputFlow.Outcome.ACCEPTED,flow.alarm(high.rule.id,now(0)));
        evaluate(flow,180001,NotificationParser.Trend.DOWN,200,195,190); assertEquals(AlarmOutputFlow.Outcome.SUPPRESSED,flow.alarm(high.rule.id,now(180001)));
        evaluate(flow,240000,NotificationParser.Trend.DOWN,180,185,190); assertEquals(AlarmOutputFlow.Outcome.ACCEPTED,flow.alarm(high.rule.id,now(240000)));
    }
    @Test public void acceptedEpisodeSurvivesRecreationButAnUnacceptedAttemptCannotEnableSuppression() throws Exception {
        File file=folder.newFolder(); AlarmOutputFlow first=flow(file,Collections.singletonList(low())); accept=false; evaluate(first,0,NotificationParser.Trend.UP,60,65,72);
        assertEquals(AlarmOutputFlow.Outcome.ATTEMPT_FAILED,first.alarm(low().rule.id,now(0)));
        AlarmOutputFlow next=flow(file,Collections.singletonList(low())); accept=true; evaluate(next,180001,NotificationParser.Trend.UP,62,68,75);
        assertEquals(AlarmOutputFlow.Outcome.ACCEPTED,next.alarm(low().rule.id,now(180001)));
        AlarmOutputFlow restored=flow(file,Collections.singletonList(low())); evaluate(restored,360002,NotificationParser.Trend.UP,62,68,75);
        assertEquals(AlarmOutputFlow.Outcome.SUPPRESSED,restored.alarm(low().rule.id,now(360002)));
    }
    @Test public void missingOrContradictorySourceArrowCannotSuppressARecoveringRepeat() throws Exception {
        AlarmOutputFlow flow=flow(folder.newFolder(),Collections.singletonList(low())); evaluate(flow,0,NotificationParser.Trend.UP,60,65,72); flow.alarm(low().rule.id,now(0));
        evaluate(flow,180001,NotificationParser.Trend.UNKNOWN,62,68,75); assertEquals(AlarmOutputFlow.Outcome.ACCEPTED,flow.alarm(low().rule.id,now(180001)));
        evaluate(flow,360002,NotificationParser.Trend.DOWN,62,68,75); assertEquals(AlarmOutputFlow.Outcome.ACCEPTED,flow.alarm(low().rule.id,now(360002)));
    }
    @Test public void aNewLowEpisodeAfterAnObservedEndHasItsOwnFirstAlarm() throws Exception {
        AlarmOutputFlow flow=flow(folder.newFolder(),Collections.singletonList(low())); evaluate(flow,0,NotificationParser.Trend.UP,60,65,72); flow.alarm(low().rule.id,now(0));
        evaluate(flow,180001,NotificationParser.Trend.FLAT,90,90,90);
        evaluate(flow,240000,NotificationParser.Trend.UP,60,65,72); assertEquals(AlarmOutputFlow.Outcome.ACCEPTED,flow.alarm(low().rule.id,now(240000)));
    }
    @Test public void lowAndFallingAreBundledWithBothTextsAndBothDurableReservations() throws Exception {
        AlarmLevel fall=new SyntheticAlarmScenario(Kind.FALLING).level; File file=folder.newFolder(); AlarmOutputFlow flow=flow(file,Arrays.asList(low(),fall));
        evaluate(flow,0,NotificationParser.Trend.DOWN,110,98,86,74);
        assertEquals(AlarmOutputFlow.Outcome.ACCEPTED,flow.alarm(low().rule.id,now(0))); AlarmOutputFlow.Plan plan=plans.get(0);
        assertEquals(Arrays.asList(low().rule.id,fall.rule.id),plan.ruleIds); assertTrue(plan.speech.contains("Niedrig")); assertTrue(plan.speech.contains("Kritische Fallrate bei 74.")); assertEquals(2,plan.toneUris.size());
        AlarmStateStore.Snapshot state=new AlarmStateStore(file).load(); assertTrue(state.entries.get(fall.rule.id).acceptedInEpisode); assertTrue(state.entries.get(low().rule.id).acceptedInEpisode);
        assertEquals(AlarmOutputFlow.Outcome.NOT_DUE,flow.alarm(fall.rule.id,now(1000)));
    }
    @Test public void aPreviouslyDeliveredFallingEpisodeIsNotAnnouncedAgainInsideTheLowBundle() throws Exception {
        AlarmLevel fall=new SyntheticAlarmScenario(Kind.FALLING).level; AlarmOutputFlow flow=flow(folder.newFolder(),Arrays.asList(low(),fall));
        evaluate(flow,0,NotificationParser.Trend.DOWN,140,128,116,104); assertEquals(AlarmOutputFlow.Outcome.ACCEPTED,flow.alarm(fall.rule.id,now(0)));
        evaluate(flow,60000,NotificationParser.Trend.DOWN,128,110,92,74); assertEquals(AlarmOutputFlow.Outcome.ACCEPTED,flow.alarm(low().rule.id,now(60000))); assertEquals(1,plans.get(1).ruleIds.size());
    }
    @Test public void gapWaitsFourMinutesThenRepeatsWithoutEverProvidingAValue() {
        GapMonitor monitor=new GapMonitor(); GapSettings p=new GapSettings(true,false,4,5,true,true,AlarmLevel.DEFAULT_TONE,null);
        monitor.receive(points(0,110,98,86,74),now(0),NotificationParser.Trend.DOWN,null);
        assertFalse(monitor.due(p,now(239999))); assertTrue(monitor.due(p,now(240000))); String message=monitor.message(now(240000));
        assertTrue(message.contains("Zuletzt schnell fallend")); assertTrue(message.contains("Aktueller Wert unbekannt")); assertFalse(message.contains("74"));
        monitor.reserve(p,now(240000)); assertFalse(monitor.due(p,now(539999))); assertTrue(monitor.due(p,now(540000)));
    }
    @Test public void newValueEndsGapAndNoWarningIsInferredAtStartupOrAcrossBootClockChanges() {
        GapMonitor monitor=new GapMonitor(); GapSettings p=new GapSettings(true,false,4,0,true,true,AlarmLevel.DEFAULT_TONE,null);
        assertFalse(monitor.due(p,now(999999))); monitor.receive(points(0,100),now(0),NotificationParser.Trend.UP,null); assertTrue(monitor.due(p,now(240000))); monitor.reserve(p,now(240000)); assertFalse(monitor.due(p,now(999999)));
        monitor.receive(points(300000,100),now(300000),NotificationParser.Trend.FLAT,null); assertFalse(monitor.waiting()); assertFalse(monitor.due(p,now(300001)));
        assertFalse(monitor.due(p,new Clock(now(600000).wallMs,now(600000).elapsedMs,2)));
        assertFalse(monitor.due(p,new Clock(now(600000).wallMs+10000,now(600000).elapsedMs,1))); assertFalse(monitor.due(p,now(1000000)));
    }
    @Test public void fastOnlyGapUsesComputedRateWhenArrowMissingAndBlocksContradictoryDirection() {
        AlarmLevel fall=new SyntheticAlarmScenario(Kind.FALLING).level; List<HistoryAnalysis.Point> points=points(0,110,98,86,74);
        Result checked=new AlarmEngine(Collections.singletonList(fall.rule),new HistoryAnalysis.Limits(1,240000,240000),OutputMode.TONES_ONLY).evaluate(points,now(0).wallMs,720,null,null);
        GapSettings p=new GapSettings(true,true,4,0,true,false,AlarmLevel.DEFAULT_TONE,null); GapMonitor monitor=new GapMonitor();
        monitor.receive(points,now(0),NotificationParser.Trend.UNKNOWN,checked); assertTrue(monitor.due(p,now(240000)));
        monitor.receive(points,now(0),NotificationParser.Trend.UP,checked); assertFalse(monitor.due(p,now(240000)));
        monitor.receive(points,now(0),NotificationParser.Trend.FLAT,null); assertFalse(monitor.due(p,now(240000)));
    }
    @Test public void settingsAndProfilesRoundtripRecoveryAndGapWithoutRestoringLiveState() throws Exception {
        GapSettings gap=new GapSettings(true,true,4,0,false,false,AlarmLevel.DEFAULT_TONE,"LONG_SHORT"); OutputPreferences p=OutputPreferences.defaults().withChannels(false,false,false,true).withGap(gap);
        ConfigurationSnapshot before=new ConfigurationSnapshot(new LevelStore.Snapshot(1,Collections.singletonList(low())),new OutputPreferenceStore.Snapshot(1,p),"DARK","samt",true,true);
        ConfigurationSnapshot after=ConfigurationSnapshot.decode(before.encode()); assertTrue(after.levels.levels.get(0).suppressRecoveryRepeats); assertTrue(after.output.preferences.gap.enabled); assertTrue(after.output.preferences.gap.onlyAfterFast); assertEquals(0,after.output.preferences.gap.repeatMinutes); assertEquals("LONG_SHORT",after.output.preferences.gap.vibrationPattern);
    }
}
