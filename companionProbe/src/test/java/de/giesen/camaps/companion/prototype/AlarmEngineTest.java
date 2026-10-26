// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import de.giesen.camaps.companion.prototype.alarm.AlarmEngine;
import de.giesen.camaps.companion.prototype.alarm.AlarmEngine.*;
import de.giesen.camaps.companion.prototype.alarm.xdrip.ActiveBgAlert;
import de.giesen.camaps.companion.prototype.alarm.xdrip.XDripRuntime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Synthetic fixtures for actual imported methods, not a second implementation of those rules. */
public class AlarmEngineTest {
    private static final long M=60000,B=10000000;
    private final Outputs both=new Outputs(true,true,3,7);
    private Rule rule(String id,Kind kind,double threshold) { return new Rule(id,kind,true,threshold,1,true,0,1440,both); }
    private AlarmEngine engine(Rule... rules) {
        return new AlarmEngine(Arrays.asList(rules),new HistoryAnalysis.Limits(1,60*M,9*M),OutputMode.SPOKEN_ALARMS);
    }
    private HistoryAnalysis.Point point(int value,long minute) { return new HistoryAnalysis.Point(value,B+minute*M); }
    private List<HistoryAnalysis.Point> series(int... values) {
        List<HistoryAnalysis.Point> out=new ArrayList<>(); for (int i=0;i<values.length;i++) out.add(point(values[i],i)); return out;
    }
    private Result at(AlarmEngine engine,List<HistoryAnalysis.Point> data) {
        return engine.evaluate(data,data.get(data.size()-1).confirmedMeasurementMs,720,B-10*M,null);
    }
    private Decision decision(Result result,String id) {
        for (Decision decision:result.decisions) if (id.equals(decision.rule.id)) return decision;
        throw new AssertionError("Missing synthetic rule");
    }
    @Test public void noRulesMeansNoAlarmAndNoHiddenStaticLow() {
        Result result=at(AlarmEngine.inactive(),series(55));
        assertEquals(Status.DISABLED,result.status); assertEquals(0,result.matchedRules());
        assertFalse(result.platformDeliveryEnabled);
    }
    @Test public void multipleHighLevelsUseSourcePriorityAndSeparateOutputs() {
        Rule first=rule("h1",Kind.HIGH,100);
        Rule second=new Rule("h2",Kind.HIGH,true,130,0,true,0,1440,new Outputs(false,true,8,12));
        Result result=at(engine(first,second),series(140));
        assertEquals(2,result.matchedRules()); assertEquals("h2",result.selectedThresholdRule);
        assertTrue(decision(result,"h1").toneRequested); assertFalse(decision(result,"h2").toneRequested);
        assertEquals(8,decision(result,"h2").rule.outputs.repeatMinutes);
        assertEquals(12,decision(result,"h2").rule.outputs.snoozeMinutes);
    }
    @Test public void multipleLowLevelsAndLowOverHighUseSourcePriority() {
        Result result=at(engine(rule("l1",Kind.LOW,80),rule("l2",Kind.LOW,60),rule("h",Kind.HIGH,40)),series(50));
        assertEquals(3,result.matchedRules()); assertEquals("l2",result.selectedThresholdRule);
    }
    @Test public void sourceThresholdsAreInclusive() {
        assertTrue(decision(at(engine(rule("h",Kind.HIGH,100)),series(100)),"h").conditionMet);
        assertTrue(decision(at(engine(rule("l",Kind.LOW,100)),series(100)),"l").conditionMet);
    }
    @Test public void disabledLevelIsNeverActivatedByOutputMode() {
        Rule disabled=new Rule("off",Kind.HIGH,false,100,0,true,0,1440,both);
        Result result=at(engine(disabled,rule("h",Kind.HIGH,150)),series(120));
        assertFalse(decision(result,"off").conditionMet); assertFalse(decision(result,"off").speechRequested);
        assertEquals(0,result.matchedRules());
    }
    @Test public void tonesOnlySuppressesEveryRequestedSpeechWithoutEnablingATone() {
        Rule quiet=new Rule("quiet",Kind.HIGH,true,100,0,true,0,1440,new Outputs(false,true,1,1));
        AlarmEngine engine=new AlarmEngine(Arrays.asList(quiet),new HistoryAnalysis.Limits(1,M,M),OutputMode.TONES_ONLY);
        Decision result=decision(at(engine,series(120)),"quiet");
        assertTrue(result.conditionMet); assertFalse(result.speechRequested); assertFalse(result.toneRequested);
    }
    @Test public void specialSourceCodesDoNotBecomeThresholdAlarms() {
        assertEquals(0,at(engine(rule("l",Kind.LOW,100)),series(14)).matchedRules());
    }
    @Test public void sourceTimeWindowIncludesMidnightAndEndpoints() {
        Rule night=new Rule("night",Kind.HIGH,true,100,0,false,23*60,6*60,both);
        AlarmEngine engine=engine(night);
        assertEquals(1,engine.evaluate(series(120),B,23*60,B-1,null).matchedRules());
        assertEquals(1,engine.evaluate(series(120),B,6*60,B-1,null).matchedRules());
        assertEquals(0,engine.evaluate(series(120),B,12*60,B-1,null).matchedRules());
    }
    @Test public void rapidRiseAndFallUseOriginalFourPointRule() {
        assertEquals(1,at(engine(rule("rise",Kind.RISING,2)),series(100,103,106,109)).matchedRules());
        assertEquals(1,at(engine(rule("fall",Kind.FALLING,2)),series(109,106,103,100)).matchedRules());
        assertEquals(0,at(engine(rule("rise",Kind.RISING,4)),series(100,103,106,109)).matchedRules());
    }
    @Test public void eachRateLevelIsIndependent() {
        Result result=at(engine(rule("r1",Kind.RISING,1),rule("r2",Kind.RISING,2),rule("r4",Kind.RISING,4)),series(100,103,106,109));
        assertTrue(decision(result,"r1").conditionMet); assertTrue(decision(result,"r2").conditionMet);
        assertFalse(decision(result,"r4").conditionMet);
    }
    @Test public void rapidRuleRequiresFourPointsAndRecentProgress() {
        assertEquals(0,at(engine(rule("rise",Kind.RISING,2)),series(100,103,106)).matchedRules());
        assertEquals(0,at(engine(rule("rise",Kind.RISING,3)),series(100,105,110,110)).matchedRules());
    }
    @Test public void originalLongLastIntervalBranchIsPreserved() {
        // Original >7 minute branch returns true even for this latest reversal; deliberately not corrected here.
        List<HistoryAnalysis.Point> points=Arrays.asList(point(100,0),point(110,1),point(150,2),point(140,10));
        assertEquals(1,at(engine(rule("rise",Kind.RISING,3)),points).matchedRules());
    }
    @Test public void originalRecentPointWindowIsPreserved() {
        List<HistoryAnalysis.Point> points=Arrays.asList(point(100,0),point(110,9),point(120,18),point(150,27));
        assertEquals(0,at(engine(rule("rise",Kind.RISING,1)),points).matchedRules());
    }
    @Test public void recoveryIsASeparateSourceDecisionNotInventedFromAnArrow() {
        Decision high=decision(at(engine(rule("h",Kind.HIGH,150)),series(160,158,156)),"h");
        assertTrue(high.conditionMet); assertTrue(high.recovering);
        Decision low=decision(at(engine(rule("l",Kind.LOW,70)),series(50,55,60)),"l");
        assertTrue(low.conditionMet); assertTrue(low.recovering);
    }
    @Test public void persistentStartsThenRequiresStrictWholeMinuteThreshold() {
        AlarmEngine engine=engine(rule("p",Kind.PERSISTENT_HIGH,150));
        assertEquals(0,at(engine,Arrays.asList(point(159,-1),point(160,0))).matchedRules());
        assertEquals(0,at(engine,Arrays.asList(point(160,0),point(161,1))).matchedRules());
        assertEquals(1,at(engine,Arrays.asList(point(160,0),point(161,1),point(162,2))).matchedRules());
    }
    @Test public void persistentCountsPlateauTimeExactlyAsOriginal() {
        AlarmEngine engine=engine(rule("p",Kind.PERSISTENT_HIGH,150));
        at(engine,Arrays.asList(point(159,-1),point(160,0)));
        assertEquals(0,at(engine,Arrays.asList(point(160,0),point(160,1))).matchedRules());
        assertEquals(1,at(engine,Arrays.asList(point(160,0),point(160,1),point(161,2))).matchedRules());
    }
    @Test public void persistentCountsFallingHighTimeExactlyAsOriginal() {
        AlarmEngine engine=engine(rule("p",Kind.PERSISTENT_HIGH,150));
        at(engine,Arrays.asList(point(159,-1),point(160,0)));
        assertEquals(0,at(engine,Arrays.asList(point(160,0),point(158,1))).matchedRules());
        assertEquals(1,at(engine,Arrays.asList(point(160,0),point(158,1),point(159,2))).matchedRules());
    }
    @Test public void persistentResetsAtThresholdAndRequiresANewStart() {
        AlarmEngine engine=engine(rule("p",Kind.PERSISTENT_HIGH,150));
        at(engine,Arrays.asList(point(159,-1),point(160,0)));
        at(engine,Arrays.asList(point(160,0),point(150,1)));
        assertEquals(0,at(engine,Arrays.asList(point(150,1),point(151,2))).matchedRules());
        assertEquals(0,at(engine,Arrays.asList(point(151,2),point(152,3))).matchedRules());
        assertEquals(1,at(engine,Arrays.asList(point(151,2),point(152,3),point(153,4))).matchedRules());
    }
    @Test public void persistentNeedsSourceSessionContextAtDecisionTime() {
        AlarmEngine engine=engine(rule("p",Kind.PERSISTENT_HIGH,150));
        engine.evaluate(Arrays.asList(point(159,-1),point(160,0)),B,720,null,null);
        Result result=engine.evaluate(Arrays.asList(point(160,0),point(161,2)),B+2*M,720,null,null);
        assertEquals(0,result.matchedRules());
    }
    @Test public void persistentLevelsKeepSeparateStartStateAndThresholds() {
        Rule shortRule=new Rule("short",Kind.PERSISTENT_HIGH,true,150,1,true,0,1440,both);
        Rule longRule=new Rule("long",Kind.PERSISTENT_HIGH,true,150,3,true,0,1440,both);
        AlarmEngine engine=engine(shortRule,longRule);
        at(engine,Arrays.asList(point(159,-1),point(160,0)));
        Result result=at(engine,Arrays.asList(point(160,0),point(161,1),point(162,2)));
        assertTrue(decision(result,"short").conditionMet); assertFalse(decision(result,"long").conditionMet);
    }
    @Test public void persistentDoesNotAcceptSparseSourceHistory() {
        AlarmEngine engine=engine(rule("p",Kind.PERSISTENT_HIGH,150));
        at(engine,Arrays.asList(point(159,-1),point(160,0)));
        assertEquals(0,at(engine,Arrays.asList(point(160,0),point(161,31))).matchedRules());
    }
    @Test public void unconfirmedNotificationTimesBlockTheWholeCore() {
        AlarmEngine engine=engine(rule("h",Kind.HIGH,100),rule("p",Kind.PERSISTENT_HIGH,100),rule("r",Kind.RISING,1));
        Result result=engine.evaluate(Arrays.asList(new HistoryAnalysis.Point(200,null)),B,720,null,null);
        assertEquals(Status.DATA_BLOCKED,result.status);
        assertEquals(HistoryAnalysis.Quality.UNCONFIRMED_MEASUREMENT_TIME,result.quality);
        assertEquals(0,result.matchedRules()); assertFalse(result.platformDeliveryEnabled);
    }
    @Test public void duplicateConfirmedMeasurementsDoNotInventARate() {
        Result result=at(engine(rule("r",Kind.RISING,1)),Arrays.asList(point(100,0),point(100,0),point(100,0),point(100,0)));
        assertEquals(0,result.matchedRules());
    }
    @Test public void clockMovingBackClearsThePrivateTimer() {
        AlarmEngine engine=engine(rule("h",Kind.HIGH,100));
        at(engine,series(120,121));
        assertEquals(Status.CLOCK_BLOCKED,at(engine,series(120)).status);
    }
    @Test public void decisionPortReceivesResultsWithoutAPlatformOutputRoute() {
        Result[] received={null};
        Result result=engine(rule("h",Kind.HIGH,100)).evaluate(series(120),B,720,null,value -> received[0]=value);
        assertSame(result,received[0]); assertFalse(result.platformDeliveryEnabled);
    }
    @Test public void importedSnoozeAndReadyBoundaryUseInjectedClock() {
        ActiveBgAlert active=new ActiveBgAlert();
        synchronized(XDripRuntime.class) {
            XDripRuntime.enter(new XDripRuntime.Environment(B,720,null,Collections.emptyList(),new HashMap<>()));
            try { active.snooze(3); assertTrue(active.is_snoozed); assertEquals(Long.valueOf(B+3*M),active.next_alert_at); }
            finally { XDripRuntime.leave(); }
            XDripRuntime.enter(new XDripRuntime.Environment(B+3*M,720,null,Collections.emptyList(),new HashMap<>()));
            try { assertFalse(active.ready_to_alarm()); } finally { XDripRuntime.leave(); }
            XDripRuntime.enter(new XDripRuntime.Environment(B+3*M+1,720,null,Collections.emptyList(),new HashMap<>()));
            try {
                assertTrue(active.ready_to_alarm()); assertEquals(0,active.getAndUpdateAlertingMinutes());
                assertFalse(active.is_snoozed); assertEquals(Long.valueOf(B+3*M+1),active.alert_started_at);
            } finally { XDripRuntime.leave(); }
        }
    }
    @Test(expected=IllegalArgumentException.class) public void duplicateRuleIdsAreRejected() {
        engine(rule("same",Kind.HIGH,100),rule("same",Kind.LOW,80));
    }
    @Test(expected=IllegalStateException.class) public void originalDependenciesRequireBoundContext() {
        XDripRuntime.JoH.tsl();
    }
}
