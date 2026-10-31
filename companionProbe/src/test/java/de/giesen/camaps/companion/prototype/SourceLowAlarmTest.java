// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import de.giesen.camaps.companion.prototype.alarm.*;
import de.giesen.camaps.companion.prototype.alarm.AlarmEngine.*;
import de.giesen.camaps.companion.prototype.alarm.AlarmOutputFlow.*;
import de.giesen.gva.watch.ArrowColor;
import java.io.*;
import java.util.*;
import org.junit.Test;
import org.junit.Before;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Real durable state and live receipt path; fixed synthetic observations and fake output only. */
public class SourceLowAlarmTest {
    @org.junit.Rule public TemporaryFolder temp=new TemporaryFolder();
    private static final long B=10000000,M=60000;
    private static AlarmLevel low(String id,double boundary,boolean enabled,boolean allDay,int start,int end) {
        return new AlarmLevel(new Rule(id,Kind.LOW,enabled,boundary,0,allDay,start,end,new Outputs(true,true,3,5)),id,AlarmLevel.DEFAULT_TONE);
    }
    private static AlarmLevel warning(boolean tone,boolean speech,String vibration,List<String> words) {
        return new AlarmLevel(new Rule("soon",Kind.SOURCE_LOW,true,1,0,true,0,1440,new Outputs(tone,speech,0,0)),"Bald niedrig",AlarmLevel.DEFAULT_TONE,words,vibration);
    }
    private final class Run {
        final File folder; final List<AlarmLevel> levels; final List<Plan> plans=new ArrayList<>();
        final AlarmStateStore store; AlarmOutputFlow flow; boolean ready=true,accept=true,paused;
        long boot=1; OutputPreferences preferences=OutputPreferences.defaults();
        Run(List<AlarmLevel> levels)throws Exception {this(temp.newFolder(),levels);}
        Run(File folder,List<AlarmLevel> levels)throws Exception {this.folder=folder;this.levels=levels;store=new AlarmStateStore(folder);reload();}
        void reload(){flow=AlarmOutputFlow.liveReceipts(store,levels,ReceiptWindow.limits(),preferences,new Port(){
            public boolean available(Plan p){return ready;}
            public boolean start(Plan p){plans.add(p);return accept;}
            public void stop(){}
        },input->paused?OutputGuard.Reason.GLOBAL_PAUSE:OutputGuard.Reason.NONE);}
        AlarmStateStore.Clock clock(long at){return new AlarmStateStore.Clock(B+at,1000000+at,boot);}
        Outcome observe(double value,NotificationParser.Trend trend,ArrowColor color,long at){
            flow.setArrowColor(color);return flow.evaluate(Origin.LIVE,Collections.singletonList(HistoryAnalysis.Point.received(value,B+at)),clock(at),720,B-10*M,trend);
        }
        Outcome fire(long at){return flow.alarm("soon",clock(at));}
        void red(double value,long at){assertEquals(Outcome.ACCEPTED,observe(value,NotificationParser.Trend.DOWN_RIGHT,ArrowColor.RED,at));}
    }
    private Run run()throws Exception{return new Run(Arrays.asList(warning(true,true,null,Collections.emptyList()),low("low",80,true,true,0,1440)));}
    @Before public void german(){AppText.configure(Locale.GERMANY);}
    @Test public void allSevenRecognizedDirectionsCanTriggerFromAFirstRedReceiptWithoutRateHistory()throws Exception{
        for(NotificationParser.Trend trend:NotificationParser.Trend.values())if(trend!=NotificationParser.Trend.UNKNOWN){
            Run r=run();assertEquals(Outcome.ACCEPTED,r.observe(123,trend,ArrowColor.RED,0));assertEquals(trend.name(),Outcome.ACCEPTED,r.fire(0));
            assertEquals(Kind.SOURCE_LOW,r.plans.get(0).kind);assertEquals("Laut Datenquelle bald niedrig",r.plans.get(0).speech);
            assertFalse(r.plans.get(0).synthetic);
        }
    }
    @Test public void normalYellowAndUnknownArrowsCannotTriggerEvenAtAHighValue()throws Exception{
        for(ArrowColor color:ArrowColor.values())for(NotificationParser.Trend trend:new NotificationParser.Trend[]{NotificationParser.Trend.FLAT,NotificationParser.Trend.UNKNOWN}){
            if(color==ArrowColor.RED && trend!=NotificationParser.Trend.UNKNOWN)continue;
            Run r=run();r.observe(250,trend,color,0);assertEquals(Outcome.CONDITION_FALSE,r.fire(0));assertTrue(r.plans.isEmpty());
        }
    }
    @Test public void equalAndLowerValuesLeaveTheExistingLowAlarmResponsible()throws Exception{
        for(double value:new double[]{80,79,65}){Run r=run();r.red(value,0);assertEquals(Outcome.CONDITION_FALSE,r.fire(0));
            assertEquals("low",r.flow.lastDecision().selectedThresholdRule);assertEquals(Outcome.ACCEPTED,r.flow.alarm("low",r.clock(0)));}
    }
    @Test public void highestCurrentlyEnabledLowBoundaryPreventsOverlapWithAnyLowLevel()throws Exception{
        Run r=new Run(Arrays.asList(warning(true,true,null,Collections.emptyList()),low("a",80,true,true,0,1440),low("b",90,true,true,0,1440),low("off",120,false,true,0,1440)));
        r.red(85,0);assertEquals(Outcome.CONDITION_FALSE,r.fire(0));assertEquals("b",r.flow.lastDecision().selectedThresholdRule);
        r.red(91,M);assertEquals(Outcome.ACCEPTED,r.fire(M));
    }
    @Test public void noActiveLowOrAClosedLowScheduleCannotInventABoundary()throws Exception{
        for(List<AlarmLevel> levels:Arrays.asList(Collections.singletonList(warning(true,true,null,Collections.emptyList())),Arrays.asList(warning(true,true,null,Collections.emptyList()),low("off",80,false,true,0,1440)),Arrays.asList(warning(true,true,null,Collections.emptyList()),low("night",80,true,false,0,100)))){
            Run r=new Run(levels);r.red(123,0);assertEquals(Outcome.CONDITION_FALSE,r.fire(0));assertTrue(r.plans.isEmpty());}
    }
    @Test public void oneRedPhaseNeverRepeatsEvenAfterDaysAndAServiceReload()throws Exception{
        Run r=run();r.red(123,0);assertEquals(Outcome.ACCEPTED,r.fire(0));
        for(long at:new long[]{M,6*60*M,10*24*60*M}){r.reload();r.red(123,at);assertEquals(Outcome.NOT_DUE,r.fire(at));}
        assertEquals(1,r.plans.size());
    }
    @Test public void sourceAlarmCannotBeSnoozed()throws Exception{
        Run r=run();r.red(123,0);assertEquals(Outcome.ACCEPTED,r.fire(0));assertEquals(Outcome.STORAGE_BLOCKED,r.flow.snooze("soon",5,r.clock(0)));
    }
    @Test public void lowValuesWithinTheSameRedPhaseDoNotRearmTheWarning()throws Exception{
        Run r=run();r.red(123,0);assertEquals(Outcome.ACCEPTED,r.fire(0));r.red(65,M);assertEquals(Outcome.CONDITION_FALSE,r.fire(M));
        r.red(123,2*M);assertEquals(Outcome.NOT_DUE,r.fire(2*M));assertEquals(1,r.plans.size());
    }
    @Test public void aRedPhaseThatFirstArrivesLowCanWarnOnceWhenItBecomesEligible()throws Exception{
        Run r=run();r.red(65,0);assertEquals(Outcome.CONDITION_FALSE,r.fire(0));r.red(123,M);assertEquals(Outcome.ACCEPTED,r.fire(M));
    }
    @Test public void normalAndYellowFreshArrowsRearmForANewRedPhase()throws Exception{
        for(ArrowColor clear:new ArrowColor[]{ArrowColor.NORMAL,ArrowColor.YELLOW}){
            Run r=run();r.red(123,0);assertEquals(Outcome.ACCEPTED,r.fire(0));
            r.observe(123,NotificationParser.Trend.FLAT,clear,M);assertEquals(Outcome.CONDITION_FALSE,r.fire(M));
            r.red(123,2*M);assertEquals(Outcome.ACCEPTED,r.fire(2*M));assertEquals(2,r.plans.size());}
    }
    @Test public void unknownArrowAndStaleInputNeverEndTheRedPhase()throws Exception{
        Run r=run();r.red(123,0);assertEquals(Outcome.ACCEPTED,r.fire(0));
        r.observe(123,NotificationParser.Trend.UNKNOWN,ArrowColor.NORMAL,M);assertEquals(Outcome.CONDITION_FALSE,r.fire(M));
        assertEquals(Outcome.DATA_BLOCKED,r.fire(10*M));r.reload();r.red(123,11*M);assertEquals(Outcome.NOT_DUE,r.fire(11*M));
    }
    @Test public void unavailableOutputDoesNotConsumeThePhaseButAFailedAttemptDoes()throws Exception{
        Run r=run();r.red(123,0);r.ready=false;assertEquals(Outcome.OUTPUT_UNAVAILABLE,r.fire(0));
        r.ready=true;r.accept=false;assertEquals(Outcome.ATTEMPT_FAILED,r.fire(0));r.reload();r.accept=true;r.red(123,M);assertEquals(Outcome.NOT_DUE,r.fire(M));
    }
    @Test public void globalPauseBlocksOutputWithoutConsumingThePhase()throws Exception{
        Run r=run();r.paused=true;r.red(123,0);assertEquals(Outcome.SUPPRESSED,r.fire(0));r.paused=false;r.red(123,M);assertEquals(Outcome.ACCEPTED,r.fire(M));
    }
    @Test public void phoneBootAndCosmeticRuleChangesKeepTheRedPhaseClaim()throws Exception{
        Run r=run();r.red(123,0);assertEquals(Outcome.ACCEPTED,r.fire(0));
        Run next=new Run(r.folder,Arrays.asList(new AlarmLevel(r.levels.get(0).rule,"Eigener Name",AlarmLevel.DEFAULT_TONE,Collections.singletonList("Eigener Text")),low("low",90,true,true,0,1440)));
        next.boot=2;next.reload();next.red(123,M);assertEquals(Outcome.NOT_DUE,next.fire(M));assertTrue(next.plans.isEmpty());
    }
    @Test public void explicitTextAndOnlySelectedChannelsAreUsed()throws Exception{
        Run r=new Run(Arrays.asList(warning(false,true,null,Collections.singletonList("CamAPS {wert} {trend} {farbe}")),low("low",80,true,true,0,1440)));
        r.red(123,0);assertEquals(Outcome.ACCEPTED,r.fire(0));Plan plan=r.plans.get(0);assertNull(plan.toneUri);assertNull(plan.vibrationPattern);assertEquals("CamAPS 123 fallend rot",plan.speech);
    }
    @Test public void vibrationOnlyAndTonesOnlyRemainIndependent()throws Exception{
        for(boolean vibrate:new boolean[]{false,true}){
            String pattern=vibrate?VibrationCatalog.values()[0].name():null;
            Run r=new Run(Arrays.asList(warning(!vibrate,false,pattern,Collections.emptyList()),low("low",80,true,true,0,1440)));
            r.preferences=r.preferences.withChannels(true,false,false,true);r.reload();r.red(123,0);assertEquals(Outcome.ACCEPTED,r.fire(0));
            assertNull(r.plans.get(0).speech);assertEquals(pattern,r.plans.get(0).vibrationPattern);assertEquals(!vibrate,r.plans.get(0).toneUri!=null);}
    }
    @Test public void pendingSpeechDropsWhenLowIsReachedOrRedDisappears()throws Exception{
        Run r=run();r.red(123,0);assertEquals(Outcome.ACCEPTED,r.fire(0));Plan pending=r.plans.get(0);
        r.red(65,M);assertNull(r.flow.refreshPending(pending,r.clock(M)));
        r.observe(123,NotificationParser.Trend.FLAT,ArrowColor.NORMAL,2*M);assertNull(r.flow.refreshPending(pending,r.clock(2*M)));
    }
    @Test public void sourceWarningLevelAndProfilesRoundTripWithoutInventingAThreshold()throws Exception{
        Run r=run();LevelStore ls=new LevelStore(r.folder);LevelStore.Snapshot saved=ls.save(0,r.levels);
        ConfigurationSnapshot config=new ConfigurationSnapshot(saved,new OutputPreferenceStore.Snapshot(1,r.preferences),"LIGHT","samt",true,true);
        AlarmLevel restored=ConfigurationSnapshot.decode(config.encode()).levels.find("soon");assertEquals(Kind.SOURCE_LOW,restored.rule.kind);
        assertEquals(0,restored.effectiveRule().outputs.repeatMinutes);assertEquals(0,restored.effectiveRule().outputs.snoozeMinutes);
        assertEquals("Laut Datenquelle bald niedrig",restored.speechText(0));
        try{SpeechTemplate.validate("{grenze}",Kind.SOURCE_LOW,false);fail();}catch(IllegalArgumentException expected){}
    }

    @Test public void ownScheduleIsRecheckedBeforeActualDelivery()throws Exception{
        AlarmLevel original=warning(true,true,null,Collections.emptyList());
        AlarmLevel scheduled=new AlarmLevel(new AlarmEngine.Rule("soon",Kind.SOURCE_LOW,true,1,0,false,720,721,original.rule.outputs),original.name,original.toneUri);
        Run r=new Run(Arrays.asList(scheduled,low("low",80,true,true,0,1440)));r.red(123,0);
        assertEquals(Outcome.CONDITION_FALSE,r.fire(2*M));assertTrue(r.plans.isEmpty());
    }
}
