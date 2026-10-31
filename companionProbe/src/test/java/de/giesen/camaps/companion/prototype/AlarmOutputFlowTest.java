// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import de.giesen.camaps.companion.prototype.alarm.*;
import de.giesen.camaps.companion.prototype.alarm.AlarmEngine.*;
import de.giesen.camaps.companion.prototype.alarm.AlarmOutputFlow.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** End-to-end from real settings files through pinned rules/durable state into fake audio ports. */
public class AlarmOutputFlowTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private static final long M=SyntheticAlarmScenario.MINUTE;
    private static final OutputPreferences SPOKEN=OutputPreferences.defaults();
    private static final class Audio implements Port {
        final List<Plan> plans=new ArrayList<>();
        boolean ready=true,accept=true,fail;
        Runnable beforeStart;
        Runnable availability;
        int stopped;
        @Override public boolean available(Plan plan) { if (availability!=null) availability.run(); return ready; }
        @Override public boolean start(Plan plan) {
            if (beforeStart!=null) beforeStart.run();
            if (fail) throw new IllegalStateException("Synthetic audio failure");
            plans.add(plan); return accept;
        }
        @Override public void stop() { stopped++; }
    }
    private static final class Run {
        final File folder;
        final AlarmStateStore store;
        final SyntheticAlarmScenario scenario;
        final List<AlarmLevel> levels;
        final Audio audio=new Audio();
        final OutputPreferences preferences;
        AlarmOutputFlow flow;
        Run(File folder,Kind kind,OutputPreferences preferences,List<AlarmLevel> custom) throws Exception {
            this.folder=folder; this.preferences=preferences; scenario=new SyntheticAlarmScenario(kind);
            LevelStore settings=new LevelStore(folder);
            settings.save(0,custom==null?Collections.singletonList(scenario.level):custom);
            levels=settings.load().levels; store=new AlarmStateStore(folder); reload();
        }
        void reload() { flow=new AlarmOutputFlow(store,levels,SyntheticAlarmScenario.limits(),preferences,audio); }
        Outcome evaluate(long offset,boolean met) {
            return flow.evaluate(Origin.SYNTHETIC,scenario.points(offset,met),scenario.clock(offset),720,
                    SyntheticAlarmScenario.BASE-10*M,scenario.trend(met));
        }
        Outcome alarm(long offset) { return flow.alarm(scenario.level.rule.id,scenario.clock(offset)); }
        long activate() {
            assertEquals(Outcome.ACCEPTED,evaluate(0,true));
            if (scenario.kind==Kind.PERSISTENT_HIGH) { assertEquals(Outcome.CONDITION_FALSE,alarm(0)); assertEquals(Outcome.ACCEPTED,evaluate(2*M,true)); return 2*M; }
            return 0;
        }
    }
    private Run run(Kind kind,OutputPreferences preferences) throws Exception { return new Run(temporary.newFolder(),kind,preferences,null); }
    private Run run(Kind kind) throws Exception { return run(kind,SPOKEN); }
    @Test public void allFiveKindsUseSavedSettingsOriginalRulesAndDurableReservationBeforeAudio() throws Exception {
        for (Kind kind:new Kind[]{Kind.HIGH,Kind.LOW,Kind.PERSISTENT_HIGH,Kind.RISING,Kind.FALLING}) {
            Run r=run(kind); long at=r.activate();
            r.audio.beforeStart=() -> { try { assertTrue(r.store.load().entries.get(r.scenario.level.rule.id).active); } catch (IOException e) { throw new AssertionError(e); } };
            assertEquals(kind.name(),Outcome.ACCEPTED,r.alarm(at));
            Plan plan=r.audio.plans.get(0); assertTrue(plan.synthetic); assertNotNull(plan.toneUri);
            assertTrue(plan.speech.startsWith("Dies ist eine Testansage mit synthetischen Daten."));
            assertEquals(Outcome.NOT_DUE,r.alarm(at)); assertEquals(1,r.audio.plans.size());
            r.reload(); assertEquals(Outcome.DATA_BLOCKED,r.alarm(at)); assertEquals(1,r.audio.plans.size());
        }
    }
    @Test public void liveInputHasNoPathToStateMutationOrAudio() throws Exception {
        Run r=run(Kind.HIGH);
        assertEquals(Outcome.LIVE_BLOCKED,r.flow.evaluate(Origin.LIVE,r.scenario.points(0,true),r.scenario.clock(0),720,null,null));
        assertFalse(new File(r.folder,AlarmStateStore.FILE_NAME).exists()); assertTrue(r.audio.plans.isEmpty());
        assertEquals(Outcome.DATA_BLOCKED,r.alarm(0));
    }
    @Test public void tonesOnlyRemovesSpeechForEveryAlarmKind() throws Exception {
        OutputPreferences tones=new OutputPreferences(OutputMode.TONES_ONLY,1,SpeechTemplate.VALUE_DEFAULT);
        for (Kind kind:new Kind[]{Kind.HIGH,Kind.LOW,Kind.PERSISTENT_HIGH,Kind.RISING,Kind.FALLING}) {
            Run r=run(kind,tones); long at=r.activate(); assertEquals(Outcome.ACCEPTED,r.alarm(at));
            assertNull(r.audio.plans.get(0).speech); assertNotNull(r.audio.plans.get(0).toneUri);
            assertEquals(Outcome.DISABLED,r.flow.value(r.scenario.clock(at)));
        }
    }
    @Test public void speechOnlyAndDisabledIndividualOutputsStayIndependent() throws Exception {
        SyntheticAlarmScenario s=new SyntheticAlarmScenario(Kind.HIGH); AlarmLevel original=s.level;
        for (boolean speak:new boolean[]{true,false}) {
            AlarmLevel level=new AlarmLevel(new AlarmEngine.Rule(original.rule.id,Kind.HIGH,true,150,0,true,0,1440,new Outputs(false,speak,3,5)),"Test",AlarmLevel.DEFAULT_TONE);
            Run r=new Run(temporary.newFolder(),Kind.HIGH,SPOKEN,Collections.singletonList(level)); r.evaluate(0,true);
            assertEquals(speak?Outcome.ACCEPTED:Outcome.DISABLED,r.alarm(0));
            if (speak) { assertNull(r.audio.plans.get(0).toneUri); assertNotNull(r.audio.plans.get(0).speech); }
            else { assertTrue(r.audio.plans.isEmpty()); assertTrue(r.store.load().entries.isEmpty()); }
        }
    }
    @Test public void aModeNeverEnablesAnInactiveSavedLevel() throws Exception {
        SyntheticAlarmScenario s=new SyntheticAlarmScenario(Kind.HIGH);
        Run r=new Run(temporary.newFolder(),Kind.HIGH,SPOKEN,Collections.singletonList(s.level.withEnabled(false)));
        assertEquals(Outcome.DISABLED,r.evaluate(0,true)); assertTrue(r.audio.plans.isEmpty());
    }
    @Test public void unavailableAudioDoesNotConsumeTheAlarmAndDoesNotQueueLaterPlayback() throws Exception {
        Run r=run(Kind.HIGH); r.evaluate(0,true); r.audio.ready=false;
        assertEquals(Outcome.OUTPUT_UNAVAILABLE,r.alarm(0)); assertTrue(r.store.load().entries.isEmpty());
        r.audio.ready=true; assertTrue(r.audio.plans.isEmpty());
        assertEquals(Outcome.ACCEPTED,r.alarm(0));
    }
    @Test public void failedAudioAttemptRemainsReservedAcrossRestart() throws Exception {
        Run r=run(Kind.HIGH); r.evaluate(0,true); r.audio.accept=false;
        assertEquals(Outcome.ATTEMPT_FAILED,r.alarm(0)); r.reload(); r.evaluate(M,true); r.audio.accept=true;
        assertEquals(Outcome.NOT_DUE,r.alarm(M)); assertEquals(1,r.audio.plans.size());
        assertEquals(0,r.store.load().entries.get(r.scenario.level.rule.id).lastDeliveredAt-SyntheticAlarmScenario.BASE);
    }
    @Test public void aThrowingAudioAdapterCannotEraseTheCommittedAttempt() throws Exception {
        Run r=run(Kind.HIGH); r.evaluate(0,true); r.audio.fail=true;
        assertEquals(Outcome.ATTEMPT_FAILED,r.alarm(0)); assertTrue(r.store.load().entries.get(r.scenario.level.rule.id).active);
    }
    @Test public void configurationOrStateRaceAfterPreflightCannotReachAudio() throws Exception {
        Run r=run(Kind.HIGH); r.evaluate(0,true);
        r.audio.availability=() -> {
            try { AlarmStateStore.Snapshot old=r.store.load(); r.store.save(old.revision,r.scenario.clock(0),old.entries.values()); }
            catch (IOException e) { throw new AssertionError(e); }
        };
        assertEquals(Outcome.STORAGE_BLOCKED,r.alarm(0)); assertTrue(r.audio.plans.isEmpty());
    }
    @Test public void corruptedStateIsPreservedAndNoPlanIsPublished() throws Exception {
        Run r=run(Kind.HIGH); r.evaluate(0,true);
        Path file=new File(r.folder,AlarmStateStore.FILE_NAME).toPath(); byte[] bytes=Files.readAllBytes(file); bytes[0]^=1; Files.write(file,bytes);
        assertEquals(Outcome.STORAGE_BLOCKED,r.evaluate(M,true)); assertTrue(r.audio.plans.isEmpty()); assertArrayEquals(bytes,Files.readAllBytes(file));
    }
    @Test public void snoozeSurvivesReloadAndUsesStrictOriginalThresholdDeadline() throws Exception {
        Run r=run(Kind.HIGH); r.evaluate(0,true); assertEquals(Outcome.ACCEPTED,r.alarm(0));
        assertEquals(Outcome.ACCEPTED,r.flow.snooze(r.scenario.level.rule.id,5,r.scenario.clock(0))); r.reload();
        assertEquals(Outcome.DATA_BLOCKED,r.alarm(6*M));
        r.evaluate(5*M,true); assertEquals(Outcome.NOT_DUE,r.alarm(5*M));
        r.evaluate(5*M+1,true); assertEquals(Outcome.ACCEPTED,r.alarm(5*M+1));
    }
    @Test public void rateEpisodesRearmOnlyAfterConfirmedConditionEnd() throws Exception {
        for (Kind kind:new Kind[]{Kind.RISING,Kind.FALLING}) {
            Run r=run(kind); r.evaluate(0,true); assertEquals(Outcome.ACCEPTED,r.alarm(0));
            r.reload(); r.evaluate(M,true); assertEquals(Outcome.NOT_DUE,r.alarm(M));
            r.evaluate(2*M,false); assertEquals(Outcome.CONDITION_FALSE,r.alarm(2*M));
            r.evaluate(3*M,true); assertEquals(Outcome.ACCEPTED,r.alarm(3*M)); assertEquals(2,r.audio.plans.size());
        }
    }
    @Test public void periodicValuesRequireThirdModeIntervalAndFreshConfirmedInputWithoutCatchupSpam() throws Exception {
        OutputPreferences periodic=new OutputPreferences(OutputMode.SPOKEN_ALARMS_AND_VALUES,1,SpeechTemplate.VALUE_DEFAULT);
        Run r=run(Kind.HIGH,periodic); r.evaluate(0,true);
        assertEquals(Outcome.NOT_DUE,r.flow.value(r.scenario.clock(0)));
        r.evaluate(M,true); assertEquals(Outcome.ACCEPTED,r.flow.value(r.scenario.clock(M)));
        assertNull(r.audio.plans.get(0).ruleId); assertNull(r.audio.plans.get(0).toneUri);
        assertTrue(r.audio.plans.get(0).speech.startsWith("Dies ist eine Testansage"));
        assertEquals(Outcome.NOT_DUE,r.flow.value(r.scenario.clock(M)));
        r.evaluate(10*M,true); assertEquals(Outcome.ACCEPTED,r.flow.value(r.scenario.clock(10*M)));
        assertEquals(Outcome.NOT_DUE,r.flow.value(r.scenario.clock(10*M))); assertEquals(2,r.audio.plans.size());
        r.reload(); assertEquals(Outcome.DATA_BLOCKED,r.flow.value(r.scenario.clock(11*M)));
        r.evaluate(11*M,true); assertEquals(Outcome.NOT_DUE,r.flow.value(r.scenario.clock(11*M)));
    }
    @Test public void spokenAlarmsModeDoesNotSpeakPeriodicValues() throws Exception {
        Run r=run(Kind.HIGH); r.evaluate(0,true); r.evaluate(M,true);
        assertEquals(Outcome.DISABLED,r.flow.value(r.scenario.clock(M))); assertTrue(r.audio.plans.isEmpty());
    }
    @Test public void staleUnknownAndPostEvaluationClockJumpCannotReachAudio() throws Exception {
        Run r=run(Kind.HIGH); r.evaluate(0,true); assertEquals(Outcome.DATA_BLOCKED,r.alarm(M+1));
        r.evaluate(0,true);
        assertEquals(Outcome.DATA_BLOCKED,r.flow.alarm(r.scenario.level.rule.id,new AlarmStateStore.Clock(SyntheticAlarmScenario.BASE+2*M,1000000,1)));
        assertEquals(Outcome.DATA_BLOCKED,r.flow.evaluate(Origin.SYNTHETIC,Collections.singletonList(new HistoryAnalysis.Point(160,null)),r.scenario.clock(0),720,null,null));
        assertTrue(r.audio.plans.isEmpty());
    }
    @Test public void pauseAndStopCancelPortAndResumeRequiresNewData() throws Exception {
        Run r=run(Kind.HIGH); r.evaluate(0,true); r.flow.pause(true);
        assertEquals(Outcome.PAUSED,r.alarm(0)); r.flow.pause(false); assertEquals(Outcome.DATA_BLOCKED,r.alarm(0));
        r.evaluate(0,true); assertEquals(Outcome.ACCEPTED,r.alarm(0)); r.flow.stop();
        assertEquals(Outcome.DATA_BLOCKED,r.alarm(0)); assertTrue(r.audio.stopped>=3);
    }
    @Test public void lowerThresholdCannotBypassOriginalSelectedLevel() throws Exception {
        SyntheticAlarmScenario s=new SyntheticAlarmScenario(Kind.HIGH);
        AlarmEngine.Rule weaker=new AlarmEngine.Rule("weaker",Kind.HIGH,true,120,0,true,0,1440,new Outputs(true,true,3,5));
        Run r=new Run(temporary.newFolder(),Kind.HIGH,SPOKEN,Arrays.asList(new AlarmLevel(weaker,"Schwächer",AlarmLevel.DEFAULT_TONE),s.level));
        r.evaluate(0,true); assertEquals(Outcome.CONDITION_FALSE,r.flow.alarm("weaker",s.clock(0))); assertEquals(Outcome.ACCEPTED,r.alarm(0));
    }
    @Test public void savedCustomVariantsAreRenderedFromCurrentSyntheticDataOnActualAttempts() throws Exception {
        SyntheticAlarmScenario s=new SyntheticAlarmScenario(Kind.HIGH);
        AlarmLevel custom=new AlarmLevel(s.level.rule,"Testname",s.level.toneUri,Arrays.asList("Erste {stufe}: {wert}","Zweite {stufe}: {wert}"));
        Run r=new Run(temporary.newFolder(),Kind.HIGH,SPOKEN,Collections.singletonList(custom));
        r.evaluate(0,true); assertEquals(Outcome.ACCEPTED,r.alarm(0));
        r.evaluate(3*M+1,true); assertEquals(Outcome.ACCEPTED,r.alarm(3*M+1));
        assertTrue(r.audio.plans.get(0).speech.endsWith("Erste Testname: 160")); assertTrue(r.audio.plans.get(1).speech.endsWith("Zweite Testname: 160"));
    }
    @Test public void externalLowerCutoffDecisionCanSuppressAllFiveKindsWithoutConsumingOutputDeadlines() throws Exception {
        for (Kind kind:new Kind[]{Kind.HIGH,Kind.LOW,Kind.PERSISTENT_HIGH,Kind.RISING,Kind.FALLING}) {
            Run r=run(kind);
            r.flow=new AlarmOutputFlow(r.store,r.levels,SyntheticAlarmScenario.limits(),SPOKEN,r.audio,input -> OutputGuard.Reason.OWN_LOWER_CUTOFF);
            long at=r.activate(); assertEquals(Outcome.SUPPRESSED,r.alarm(at));
            assertEquals(OutputGuard.Reason.OWN_LOWER_CUTOFF,r.flow.suppressionReason()); assertTrue(r.audio.plans.isEmpty());
            for (AlarmStateStore.Entry row:r.store.load().entries.values()) assertFalse(row.active);
        }
    }
    @Test public void ambiguousPolicyBlocksAndCancelsPendingAudioUntilFreshEvaluation() throws Exception {
        Run r=run(Kind.HIGH); boolean[] allowed={false};
        r.flow=new AlarmOutputFlow(r.store,r.levels,SyntheticAlarmScenario.limits(),SPOKEN,r.audio,input -> allowed[0]?OutputGuard.Reason.NONE:null);
        r.evaluate(0,true); assertEquals(Outcome.SUPPRESSED,r.alarm(0));
        assertEquals(OutputGuard.Reason.POLICY_NOT_CONFIGURED,r.flow.suppressionReason()); allowed[0]=true;
        assertEquals(Outcome.DATA_BLOCKED,r.alarm(0)); r.evaluate(0,true); assertEquals(Outcome.ACCEPTED,r.alarm(0));
    }
    @Test public void policyReceivesExplicitKindSourceTrendAndCurrentValueWithoutAnInferredClinicalDefault() throws Exception {
        Run r=run(Kind.HIGH); List<OutputGuard.Input> checked=new ArrayList<>();
        r.flow=new AlarmOutputFlow(r.store,r.levels,SyntheticAlarmScenario.limits(),SPOKEN,r.audio,input -> { checked.add(input); return OutputGuard.Reason.RECOVERING_TREND; });
        r.evaluate(0,true); assertEquals(Outcome.SUPPRESSED,r.alarm(0));
        assertEquals(Kind.HIGH,checked.get(0).rule.kind); assertEquals(160,checked.get(0).mgdl,0);
        assertEquals(NotificationParser.Trend.UP,checked.get(0).sourceTrend); assertFalse(checked.get(0).periodicValue);
    }
    @Test public void periodicValueSuppressionIsAnExplicitSeparatePolicyInput() throws Exception {
        OutputPreferences p=new OutputPreferences(OutputMode.SPOKEN_ALARMS_AND_VALUES,1,SpeechTemplate.VALUE_DEFAULT,100,100,0);
        Run r=run(Kind.HIGH,p);
        r.flow=new AlarmOutputFlow(r.store,r.levels,SyntheticAlarmScenario.limits(),p,r.audio,input -> input.periodicValue?OutputGuard.Reason.POLICY_NOT_CONFIGURED:OutputGuard.Reason.NONE);
        r.evaluate(0,true); assertEquals(Outcome.ACCEPTED,r.alarm(0)); r.evaluate(M,true);
        assertEquals(Outcome.SUPPRESSED,r.flow.value(r.scenario.clock(M))); assertEquals(1,r.audio.plans.size());
    }
    @Test public void acceptedAlarmDelaysOnlyNormalValuesForTheConfiguredWindow() throws Exception {
        Run r=run(Kind.HIGH,new OutputPreferences(OutputMode.SPOKEN_ALARMS_AND_VALUES,1,SpeechTemplate.VALUE_DEFAULT,100,100,5));
        r.evaluate(0,true); assertEquals(Outcome.ACCEPTED,r.alarm(0));
        r.evaluate(M,false); int stopped=r.audio.stopped;
        assertEquals(Outcome.NOT_DUE,r.flow.value(r.scenario.clock(M))); assertEquals(stopped,r.audio.stopped);
        r.evaluate(5*M,false); assertEquals(Outcome.ACCEPTED,r.flow.value(r.scenario.clock(5*M)));
        assertEquals(2,r.audio.plans.size());
    }
    @Test public void alarmRepeatRemainsAllowedAndExtendsTheValuePause() throws Exception {
        Run r=run(Kind.HIGH,new OutputPreferences(OutputMode.SPOKEN_ALARMS_AND_VALUES,1,SpeechTemplate.VALUE_DEFAULT,100,100,5));
        r.evaluate(0,true); assertEquals(Outcome.ACCEPTED,r.alarm(0));
        r.evaluate(3*M+1,true); assertEquals(Outcome.ACCEPTED,r.alarm(3*M+1));
        r.evaluate(5*M,false); assertEquals(Outcome.NOT_DUE,r.flow.value(r.scenario.clock(5*M)));
        r.evaluate(8*M+1,false); assertEquals(Outcome.ACCEPTED,r.flow.value(r.scenario.clock(8*M+1)));
    }
    @Test public void failedAlarmAttemptDoesNotSuppressNormalValuesAndZeroDisablesTheWindow() throws Exception {
        for (boolean fail:new boolean[]{true,false}) {
            Run r=run(Kind.HIGH,new OutputPreferences(OutputMode.SPOKEN_ALARMS_AND_VALUES,1,SpeechTemplate.VALUE_DEFAULT,100,100,fail?5:0));
            r.evaluate(0,true); r.audio.accept=!fail; assertEquals(fail?Outcome.ATTEMPT_FAILED:Outcome.ACCEPTED,r.alarm(0));
            r.audio.accept=true; r.evaluate(M,false); assertEquals(Outcome.ACCEPTED,r.flow.value(r.scenario.clock(M)));
        }
    }

    @Test public void colorChangesOnlyExplicitAlarmTextAndDoesNotRearmAnExistingAlarm() throws Exception {
        SyntheticAlarmScenario s=new SyntheticAlarmScenario(Kind.HIGH);
        AlarmLevel level=new AlarmLevel(s.level.rule,s.level.name,s.level.toneUri,Collections.singletonList("{wert} {trend} {farbe}"));
        Run r=new Run(temporary.newFolder(),Kind.HIGH,SPOKEN,Collections.singletonList(level));
        r.flow.setArrowColor(de.giesen.gva.watch.ArrowColor.RED); r.evaluate(0,true);
        assertEquals(Outcome.ACCEPTED,r.alarm(0)); assertTrue(r.audio.plans.get(0).speech.endsWith("160 schnell steigend rot"));
        r.flow.setArrowColor(de.giesen.gva.watch.ArrowColor.YELLOW); r.evaluate(M,true);
        assertEquals(Outcome.NOT_DUE,r.alarm(M));
        Plan refreshed=r.flow.refreshPending(r.audio.plans.get(0),r.scenario.clock(M));
        assertNotNull(refreshed); assertTrue(refreshed.speech.endsWith("160 schnell steigend gelb"));
        assertEquals(1,r.audio.plans.size());
    }
    @Test public void periodicColorUsesLatestConfirmedColorAndNormalRemainsEmpty() throws Exception {
        Run r=run(Kind.HIGH,new OutputPreferences(OutputMode.SPOKEN_ALARMS_AND_VALUES,1,"{wert} {trend} {farbe}"));
        r.evaluate(0,true); r.flow.setArrowColor(de.giesen.gva.watch.ArrowColor.RED); r.evaluate(M,true);
        assertEquals(Outcome.ACCEPTED,r.flow.value(r.scenario.clock(M)));
        assertTrue(r.audio.plans.get(0).speech.endsWith("160 schnell steigend rot"));
        r.flow.setArrowColor(de.giesen.gva.watch.ArrowColor.NORMAL); r.evaluate(2*M,true);
        assertEquals(Outcome.ACCEPTED,r.flow.value(r.scenario.clock(2*M)));
        assertTrue(r.audio.plans.get(1).speech.endsWith("160 schnell steigend"));
    }

    private TrendSpeech coloredSpeech(){return new TrendSpeech(TrendSpeech.defaults().words,Collections.nCopies(7,true),new TrendColorOverrides(Collections.nCopies(7,"rotwort"),Collections.nCopies(7,true),Collections.nCopies(7,"gelbwort"),Collections.nCopies(7,true)));}
    @Test public void coloredAlarmWordsAndPendingRefreshUseCurrentColorWithoutChangingReservation()throws Exception{
        SyntheticAlarmScenario scenario=new SyntheticAlarmScenario(Kind.HIGH);AlarmLevel level=new AlarmLevel(scenario.level.rule,"Farbtest",AlarmLevel.DEFAULT_TONE,Collections.singletonList("{wert} {trend} {farbe}"));
        Run r=new Run(temporary.newFolder(),Kind.HIGH,SPOKEN.withTrendSpeech(coloredSpeech()),Collections.singletonList(level));
        r.flow.setArrowColor(de.giesen.gva.watch.ArrowColor.RED);r.evaluate(0,true);assertEquals(Outcome.ACCEPTED,r.alarm(0));assertTrue(r.audio.plans.get(0).speech.endsWith("160 rotwort rot"));
        r.flow.setArrowColor(de.giesen.gva.watch.ArrowColor.YELLOW);r.evaluate(M,true);assertEquals(Outcome.NOT_DUE,r.alarm(M));
        assertTrue(r.flow.refreshPending(r.audio.plans.get(0),r.scenario.clock(M)).speech.endsWith("160 gelbwort gelb"));assertEquals(1,r.audio.plans.size());
    }
    @Test public void periodicValueOutputUsesIndependentColoredWordsAndTheOptionalColorVariable()throws Exception{
        Run r=run(Kind.HIGH,new OutputPreferences(OutputMode.SPOKEN_ALARMS_AND_VALUES,1,"{wert} {trend} {farbe}").withTrendSpeech(coloredSpeech()));
        r.evaluate(0,true);r.flow.setArrowColor(de.giesen.gva.watch.ArrowColor.RED);r.evaluate(M,true);assertEquals(Outcome.ACCEPTED,r.flow.value(r.scenario.clock(M)));assertTrue(r.audio.plans.get(0).speech.endsWith("160 rotwort rot"));
        r.flow.setArrowColor(de.giesen.gva.watch.ArrowColor.YELLOW);r.evaluate(2*M,true);assertEquals(Outcome.ACCEPTED,r.flow.value(r.scenario.clock(2*M)));assertTrue(r.audio.plans.get(1).speech.endsWith("160 gelbwort gelb"));
    }
}
