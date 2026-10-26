// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import de.giesen.camaps.companion.prototype.alarm.*;
import de.giesen.camaps.companion.prototype.alarm.AlarmEngine.*;
import de.giesen.camaps.companion.prototype.alarm.AlarmStateStore.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real files, actual imported rules, synthetic settings and clocks only. */
public class AlarmStateTest {
    private static final long B=10000000,M=60000,E=1000000;
    private Clock clock(long offset) { return new Clock(B+offset,E+offset,1); }
    private Rule rule(String id,Kind kind,double threshold) { return new Rule(id,kind,true,threshold,kind==Kind.PERSISTENT_HIGH?1:0,true,0,1440,new Outputs(true,true,3,5)); }
    private PersistentAlarmSession session(AlarmStateStore store,Rule... rules) {
        return new PersistentAlarmSession(store,Arrays.asList(rules),new HistoryAnalysis.Limits(1,60*M,M),OutputMode.SPOKEN_ALARMS);
    }
    private HistoryAnalysis.Point point(int value,long offset) { return new HistoryAnalysis.Point(value,B+offset); }
    private Result evaluate(PersistentAlarmSession session,long offset,int value) throws IOException {
        return session.evaluate(Arrays.asList(point(value,offset)),clock(offset),720,B-10*M,null);
    }
    private void activate(PersistentAlarmSession session,String id,int value) throws IOException {
        evaluate(session,0,value); session.recordDelivered(id,clock(0));
    }
    private interface Check { void run(AlarmStateStore store,Path folder) throws Exception; }
    private void fileTest(Check check) throws Exception {
        Path folder=Files.createTempDirectory("alarm-state-synthetic-");
        try { check.run(new AlarmStateStore(folder.toFile()),folder); }
        finally { Files.deleteIfExists(folder.resolve(AlarmStateStore.FILE_NAME+".new")); Files.deleteIfExists(folder.resolve(AlarmStateStore.FILE_NAME)); Files.delete(folder); }
    }
    private interface Action { void run() throws IOException; }
    private void blocked(Action action) throws IOException {
        try { action.run(); fail("Expected blocked action"); } catch (IOException expected) { /* No output. */ }
    }
    @Test public void noStateCreatesNoFileAndNoOutput() throws Exception {
        fileTest((store,folder) -> { assertEquals(0,store.load().revision); assertTrue(store.load().entries.isEmpty()); assertFalse(Files.exists(folder.resolve(AlarmStateStore.FILE_NAME))); });
    }
    @Test public void activationAndSnoozeSurviveNewStoreAndNewSession() throws Exception {
        fileTest((store,folder) -> {
            Rule high=rule("h",Kind.HIGH,100); PersistentAlarmSession first=session(store,high);
            activate(first,"h",120); first.snooze("h",5,clock(0));
            PersistentAlarmSession restored=session(new AlarmStateStore(folder.toFile()),high);
            Entry state=restored.view(clock(M)).entries.get("h");
            assertTrue(state.active); assertTrue(state.snoozed); assertEquals(B+5*M,state.nextAt);
            assertTrue(restored.needsFreshData());
            blocked(() -> restored.recordDelivered("h",clock(6*M)));
        });
    }
    @Test public void importedSnoozeBoundaryAndFreshDataAreBothRequired() throws Exception {
        fileTest((store,folder) -> {
            PersistentAlarmSession first=session(store,rule("h",Kind.HIGH,100)); activate(first,"h",120); first.snooze("h",5,clock(0));
            PersistentAlarmSession restored=session(store,rule("h",Kind.HIGH,100));
            evaluate(restored,5*M,120); blocked(() -> restored.recordDelivered("h",clock(5*M)));
            evaluate(restored,5*M+1,120); restored.recordDelivered("h",clock(5*M+1));
            Entry state=store.load().entries.get("h"); assertFalse(state.snoozed); assertEquals(B+5*M+1,state.startedAt);
        });
    }
    @Test public void confirmedPersistentHighTimerSurvivesProcessRecreation() throws Exception {
        fileTest((store,folder) -> {
            Rule persistent=rule("p",Kind.PERSISTENT_HIGH,150);
            PersistentAlarmSession first=session(store,persistent);
            first.evaluate(Arrays.asList(point(159,-M),point(160,0)),clock(0),720,B-2*M,null);
            assertEquals(B,store.load().entries.get("p").highSince);
            PersistentAlarmSession restored=session(new AlarmStateStore(folder.toFile()),persistent);
            Result result=restored.evaluate(Arrays.asList(point(160,0),point(160,M),point(161,2*M)),clock(2*M),720,B-2*M,null);
            assertEquals(1,result.matchedRules()); assertEquals(B,store.load().entries.get("p").highSince);
        });
    }
    @Test public void sourcePersistentRepeatSurvivesPlateauAndClearsBelowThreshold() throws Exception {
        fileTest((store,folder) -> {
            PersistentAlarmSession first=session(store,rule("p",Kind.PERSISTENT_HIGH,150));
            first.evaluate(Arrays.asList(point(159,-M),point(160,0)),clock(0),720,B-2*M,null);
            first.evaluate(Arrays.asList(point(160,0),point(161,M),point(162,2*M)),clock(2*M),720,B-2*M,null);
            first.recordDelivered("p",clock(2*M));
            PersistentAlarmSession restored=session(store,rule("p",Kind.PERSISTENT_HIGH,150));
            restored.evaluate(Arrays.asList(point(162,2*M),point(162,3*M)),clock(3*M),720,B-2*M,null);
            assertTrue(store.load().entries.get("p").active); assertEquals(B+5*M,store.load().entries.get("p").nextAt);
            evaluate(restored,4*M,150); assertTrue(store.load().entries.isEmpty());
        });
    }
    @Test public void wallClockForwardDoesNotShortenSnoozeWithinSameBoot() throws Exception {
        fileTest((store,folder) -> {
            PersistentAlarmSession first=session(store,rule("h",Kind.HIGH,100)); activate(first,"h",120); first.snooze("h",5,clock(0));
            Clock shifted=new Clock(B+61*M,E+M,1);
            Entry state=session(store,rule("h",Kind.HIGH,100)).view(shifted).entries.get("h");
            assertEquals(4*M,state.nextAt-shifted.wallMs); assertTrue(state.snoozed);
        });
    }
    @Test public void wallClockBackDoesNotExtendSnoozeWithinSameBoot() throws Exception {
        fileTest((store,folder) -> {
            PersistentAlarmSession first=session(store,rule("h",Kind.HIGH,100)); activate(first,"h",120); first.snooze("h",5,clock(0));
            Clock shifted=new Clock(B-59*M,E+M,1);
            assertEquals(4*M,session(store,rule("h",Kind.HIGH,100)).view(shifted).entries.get("h").nextAt-shifted.wallMs);
        });
    }
    @Test public void rebootKeepsAbsolutePauseButNeverAutoReplays() throws Exception {
        fileTest((store,folder) -> {
            PersistentAlarmSession first=session(store,rule("h",Kind.HIGH,100)); activate(first,"h",120); first.snooze("h",5,clock(0));
            PersistentAlarmSession after=session(new AlarmStateStore(folder.toFile()),rule("h",Kind.HIGH,100));
            Clock rebooted=new Clock(B+M,1000,2);
            assertEquals(B+5*M,after.view(rebooted).entries.get("h").nextAt); assertTrue(after.needsFreshData());
            blocked(() -> after.recordDelivered("h",new Clock(B+6*M,301000,2)));
        });
    }
    @Test public void backwardTimeAcrossRebootBlocksWithoutErasingSavedPause() throws Exception {
        fileTest((store,folder) -> {
            PersistentAlarmSession first=session(store,rule("h",Kind.HIGH,100)); activate(first,"h",120); first.snooze("h",5,clock(0));
            byte[] before=Files.readAllBytes(folder.resolve(AlarmStateStore.FILE_NAME));
            blocked(() -> session(store,rule("h",Kind.HIGH,100)).view(new Clock(B-M,1000,2)));
            assertArrayEquals(before,Files.readAllBytes(folder.resolve(AlarmStateStore.FILE_NAME)));
        });
    }
    @Test public void decreasingElapsedTimeWithinSameBootIsNotTrusted() throws Exception {
        fileTest((store,folder) -> {
            PersistentAlarmSession first=session(store,rule("h",Kind.HIGH,100)); activate(first,"h",120);
            blocked(() -> first.view(new Clock(B+M,E-1,1)));
        });
    }
    @Test public void disabledDeletedOrChangedConditionsCannotInheritState() throws Exception {
        fileTest((store,folder) -> {
            Rule original=rule("h",Kind.HIGH,100); PersistentAlarmSession first=session(store,original); activate(first,"h",120);
            assertTrue(session(store,rule("h",Kind.HIGH,110)).view(clock(M)).entries.isEmpty());
            Rule disabled=new Rule("h",Kind.HIGH,false,100,0,true,0,1440,original.outputs);
            assertTrue(session(store,disabled).view(clock(M)).entries.isEmpty());
            assertTrue(session(store).view(clock(M)).entries.isEmpty());
            session(store,disabled).reconcile(clock(M)); assertTrue(store.load().entries.isEmpty());
        });
    }
    @Test public void nameToneAndDefaultSnoozeChangesDoNotCancelAnAlreadySetPause() throws Exception {
        fileTest((store,folder) -> {
            Rule original=rule("h",Kind.HIGH,100); PersistentAlarmSession first=session(store,original); activate(first,"h",120); first.snooze("h",5,clock(0));
            Rule outputChanged=new Rule("h",Kind.HIGH,true,100,0,true,0,1440,new Outputs(false,false,8,12));
            Entry restored=session(store,outputChanged).view(clock(M)).entries.get("h");
            assertTrue(restored.snoozed); assertEquals(B+5*M,restored.nextAt);
            assertEquals(AlarmStateStore.fingerprint(original),AlarmStateStore.fingerprint(outputChanged));
        });
    }
    @Test public void oldHigherLevelPauseSuppressesLowerLevelButNotEscalation() throws Exception {
        fileTest((store,folder) -> {
            Rule low=rule("a",Kind.HIGH,100),high=rule("b",Kind.HIGH,130),higher=rule("c",Kind.HIGH,180);
            PersistentAlarmSession first=session(store,low,high,higher); activate(first,"b",140); first.snooze("b",5,clock(0));
            PersistentAlarmSession after=session(store,low,high,higher);
            evaluate(after,M,120); blocked(() -> after.recordDelivered("a",clock(M)));
            evaluate(after,2*M,190); after.recordDelivered("c",clock(2*M));
            assertTrue(store.load().entries.get("c").active); assertFalse(store.load().entries.containsKey("b"));
        });
    }
    @Test public void directionChangeCanReplaceAPausedThresholdAlarm() throws Exception {
        fileTest((store,folder) -> {
            Rule high=rule("h",Kind.HIGH,100),low=rule("l",Kind.LOW,80);
            PersistentAlarmSession first=session(store,high,low); activate(first,"h",120); first.snooze("h",5,clock(0));
            evaluate(first,M,70); first.recordDelivered("l",clock(M));
            assertTrue(store.load().entries.get("l").active); assertFalse(store.load().entries.containsKey("h"));
        });
    }
    @Test public void noCurrentThresholdKeepsAnUnexpiredPause() throws Exception {
        fileTest((store,folder) -> {
            PersistentAlarmSession first=session(store,rule("h",Kind.HIGH,100)); activate(first,"h",120); first.snooze("h",5,clock(0));
            evaluate(first,M,90); assertTrue(store.load().entries.get("h").snoozed);
            evaluate(first,6*M,90); assertTrue(store.load().entries.isEmpty());
        });
    }
    private List<HistoryAnalysis.Point> rising(long offset) { return Arrays.asList(point(100,offset-3*M),point(103,offset-2*M),point(106,offset-M),point(109,offset)); }
    @Test public void rateEpisodeSurvivesRestartAndRearmsOnKnownFalse() throws Exception {
        fileTest((store,folder) -> {
            Rule rise=rule("r",Kind.RISING,2); PersistentAlarmSession first=session(store,rise);
            first.evaluate(rising(0),clock(0),720,B-10*M,null); first.recordDelivered("r",clock(0));
            PersistentAlarmSession after=session(new AlarmStateStore(folder.toFile()),rise);
            after.evaluate(rising(M),clock(M),720,B-10*M,null); blocked(() -> after.recordDelivered("r",clock(M)));
            after.evaluate(Arrays.asList(point(109,0),point(109,M),point(109,2*M),point(109,3*M)),clock(3*M),720,B-10*M,null);
            assertTrue(store.load().entries.isEmpty());
            after.evaluate(rising(4*M),clock(4*M),720,B-10*M,null); after.recordDelivered("r",clock(4*M));
            assertEquals(B+4*M,store.load().entries.get("r").lastDeliveredAt);
        });
    }
    @Test public void continuousRateEpisodeUsesOriginalTechnicalDeadline() throws Exception {
        fileTest((store,folder) -> {
            PersistentAlarmSession first=session(store,rule("r",Kind.RISING,2));
            first.evaluate(rising(0),clock(0),720,B-10*M,null); first.recordDelivered("r",clock(0));
            long delay=PersistentAlarmSession.RATE_REARM_MS; assertEquals(21474000,delay);
            first.evaluate(rising(delay-1),clock(delay-1),720,B-10*M,null); blocked(() -> first.recordDelivered("r",clock(delay-1)));
            first.evaluate(rising(delay),clock(delay),720,B-10*M,null); first.recordDelivered("r",clock(delay));
        });
    }
    @Test public void corruptedStateCannotPublishAnyDecisionOrOverwriteTheFile() throws Exception {
        fileTest((store,folder) -> {
            PersistentAlarmSession first=session(store,rule("h",Kind.HIGH,100)); activate(first,"h",120);
            byte[] broken=Files.readAllBytes(folder.resolve(AlarmStateStore.FILE_NAME)); broken[0]^=1; Files.write(folder.resolve(AlarmStateStore.FILE_NAME),broken);
            boolean[] delivered={false};
            blocked(() -> first.evaluate(Arrays.asList(point(120,M)),clock(M),720,null,result -> delivered[0]=true));
            assertFalse(delivered[0]); assertArrayEquals(broken,Files.readAllBytes(folder.resolve(AlarmStateStore.FILE_NAME)));
        });
    }
    @Test public void concurrentOldDecisionCannotOverwriteANewerSnapshot() throws Exception {
        fileTest((store,folder) -> {
            Rule high=rule("h",Kind.HIGH,100); PersistentAlarmSession a=session(store,high),b=session(store,high);
            evaluate(a,0,120); evaluate(b,0,120); byte[] before=Files.readAllBytes(folder.resolve(AlarmStateStore.FILE_NAME));
            blocked(() -> a.recordDelivered("h",clock(0))); assertArrayEquals(before,Files.readAllBytes(folder.resolve(AlarmStateStore.FILE_NAME)));
        });
    }
    @Test public void unknownOrStaleMeasurementsNeverBecomeDeliveryAfterRestore() throws Exception {
        fileTest((store,folder) -> {
            PersistentAlarmSession first=session(store,rule("h",Kind.HIGH,100)); activate(first,"h",120);
            Result blocked=first.evaluate(Arrays.asList(new HistoryAnalysis.Point(120,null)),clock(M),720,null,null);
            assertEquals(Status.DATA_BLOCKED,blocked.status); assertTrue(first.needsFreshData());
            blocked(() -> first.recordDelivered("h",clock(M)));
            evaluate(first,2*M,120); blocked(() -> first.recordDelivered("h",clock(4*M)));
        });
    }
    @Test public void noSnoozeForRateOrPersistentHigh() throws Exception {
        fileTest((store,folder) -> {
            for (Kind kind:new Kind[]{Kind.RISING,Kind.FALLING,Kind.PERSISTENT_HIGH}) {
                try { session(store,rule("x",kind,2)).snooze("x",5,clock(0)); fail(); } catch (IllegalArgumentException expected) {}
            }
        });
    }
    @Test public void staleWriterAndInvalidRuntimeEntriesAreRejected() throws Exception {
        fileTest((store,folder) -> {
            Snapshot first=store.save(0,clock(0),Collections.emptyList());
            blocked(() -> store.save(0,clock(0),Collections.emptyList())); assertEquals(first.revision,store.load().revision);
            try { new Entry("h",AlarmStateStore.fingerprint(rule("h",Kind.HIGH,100)),Kind.HIGH,0,false,true,0,0,0); fail(); }
            catch (IllegalArgumentException expected) {}
        });
    }
    @Test public void configurationBarrierInvalidatesAnOldDecisionEvenWithNoActiveRows() throws Exception {
        fileTest((store,folder) -> {
            PersistentAlarmSession old=session(store,rule("h",Kind.HIGH,100)); evaluate(old,0,120);
            session(store,rule("h",Kind.HIGH,110)).reconcile(clock(0));
            blocked(() -> old.recordDelivered("h",clock(0))); assertTrue(store.load().entries.isEmpty());
        });
    }
    @Test public void standaloneRestartSelfTestUsesNoRealStateFile() throws Exception {
        fileTest((store,folder) -> {
            PersistentAlarmSession active=session(store,rule("h",Kind.HIGH,100)); activate(active,"h",120); active.snooze("h",5,clock(0));
            byte[] original=Files.readAllBytes(folder.resolve(AlarmStateStore.FILE_NAME));
            assertEquals(8,AlarmStateSelfTest.run(folder.toFile())); assertArrayEquals(original,Files.readAllBytes(folder.resolve(AlarmStateStore.FILE_NAME)));
            try (java.util.stream.Stream<Path> children=Files.list(folder)) { assertEquals(1,children.count()); }
        });
    }
    @Test public void runtimeDiagnosticContainsNoRealIdsOrDeadlines() {
        RuntimeDiagnostics.passed=8; String report=RuntimeDiagnostics.report("0.6.0-probe");
        assertTrue(report.contains("8/8")); assertFalse(report.contains(Long.toString(B))); assertFalse(report.contains("nextAt"));
        assertFalse(report.contains("fingerprint")); assertTrue(report.contains("Live-Alarme: deaktiviert"));
    }
}
