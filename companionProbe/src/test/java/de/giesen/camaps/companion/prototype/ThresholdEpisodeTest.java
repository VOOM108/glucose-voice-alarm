// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import de.giesen.camaps.companion.prototype.alarm.*;
import de.giesen.camaps.companion.prototype.alarm.AlarmEngine.*;
import de.giesen.camaps.companion.prototype.alarm.AlarmStateStore.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

/** Fresh numerical recovery ends only an automatic threshold repeat episode. */
public class ThresholdEpisodeTest {
    private static final long B=10000000,M=60000,E=1000000;
    private Path folder;private AlarmStateStore store;
    @Before public void before()throws Exception{folder=Files.createTempDirectory("threshold-episode-synthetic-");store=new AlarmStateStore(folder.toFile());}
    @After public void after()throws Exception{Files.deleteIfExists(folder.resolve(AlarmStateStore.FILE_NAME+".new"));Files.deleteIfExists(folder.resolve(AlarmStateStore.FILE_NAME));Files.delete(folder);}
    private Clock clock(long n){return new Clock(B+n,E+n,1);}
    private Rule rule(String id,Kind kind,double threshold){return new Rule(id,kind,true,threshold,0,true,0,1440,new Outputs(false,true,30,30));}
    private PersistentAlarmSession session(Rule...rules){return new PersistentAlarmSession(store,Arrays.asList(rules),new HistoryAnalysis.Limits(1,4*M,2*M),OutputMode.SPOKEN_ALARMS);}
    private Result eval(PersistentAlarmSession s,long n,double value)throws Exception{return s.evaluate(Collections.singletonList(new HistoryAnalysis.Point(value,B+n)),clock(n),720,null,null);}
    private void start(PersistentAlarmSession s,String id,double value)throws Exception{eval(s,0,value);s.recordDelivered(id,clock(0));s.accepted(id,clock(0));}
    @Test public void highRecoveryAllowsImmediateNewAlarmBeforeThirtyMinuteRepeat()throws Exception{
        PersistentAlarmSession s=session(rule("h",Kind.HIGH,210));start(s,"h",220);eval(s,M,209);assertTrue(store.load().entries.isEmpty());
        eval(s,2*M,220);assertTrue(s.deliveryReady("h",clock(2*M)));s.recordDelivered("h",clock(2*M));assertEquals(B+32*M,store.load().entries.get("h").nextAt);
    }
    @Test public void lowRecoveryAllowsImmediateNewAlarmBeforeThirtyMinuteRepeat()throws Exception{
        PersistentAlarmSession s=session(rule("l",Kind.LOW,80));start(s,"l",70);eval(s,M,81);assertTrue(store.load().entries.isEmpty());eval(s,2*M,70);assertTrue(s.deliveryReady("l",clock(2*M)));
    }
    @Test public void continuouslyHighReadingsKeepTheOriginalRepeatDeadline()throws Exception{
        PersistentAlarmSession s=session(rule("h",Kind.HIGH,210));start(s,"h",220);eval(s,M,260);assertFalse(s.deliveryReady("h",clock(M)));assertEquals(B+30*M,store.load().entries.get("h").nextAt);
        eval(s,30*M,260);assertFalse(s.deliveryReady("h",clock(30*M)));eval(s,30*M+1,260);assertTrue(s.deliveryReady("h",clock(30*M+1)));
    }
    @Test public void continuouslyLowReadingsKeepTheRepeatDeadline()throws Exception{
        PersistentAlarmSession s=session(rule("l",Kind.LOW,80));start(s,"l",70);eval(s,M,60);assertFalse(s.deliveryReady("l",clock(M)));assertEquals(B+30*M,store.load().entries.get("l").nextAt);
    }
    @Test public void equalityDoesNotEndEitherThresholdEpisode()throws Exception{
        for(Kind kind:new Kind[]{Kind.HIGH,Kind.LOW}){
            Rule r=rule("x",kind,100);PersistentAlarmSession s=session(r);start(s,"x",kind==Kind.HIGH?110:90);eval(s,M,100);
            assertTrue(store.load().entries.get("x").active);assertFalse(s.deliveryReady("x",clock(M)));Files.delete(folder.resolve(AlarmStateStore.FILE_NAME));
        }
    }
    @Test public void manualHighSnoozeSurvivesNumericalRecoveryAndRecrossing()throws Exception{
        PersistentAlarmSession s=session(rule("h",Kind.HIGH,210));start(s,"h",220);s.snooze("h",30,clock(0));eval(s,M,209);eval(s,2*M,220);
        assertTrue(store.load().entries.get("h").snoozed);assertFalse(s.deliveryReady("h",clock(2*M)));assertEquals(B+30*M,store.load().entries.get("h").nextAt);
    }
    @Test public void manualLowSnoozeSurvivesRecoveryAndRecrossing()throws Exception{
        PersistentAlarmSession s=session(rule("l",Kind.LOW,80));start(s,"l",70);s.snooze("l",30,clock(0));eval(s,M,81);eval(s,2*M,70);assertFalse(s.deliveryReady("l",clock(2*M)));assertTrue(store.load().entries.get("l").snoozed);
    }
    @Test public void recoveredEpisodeStaysResetAfterProcessRecreation()throws Exception{
        Rule r=rule("h",Kind.HIGH,210);PersistentAlarmSession s=session(r);start(s,"h",220);eval(s,M,209);PersistentAlarmSession next=session(r);eval(next,2*M,220);assertTrue(next.deliveryReady("h",clock(2*M)));
    }
    @Test public void restartWithoutAConfirmedNormalReadingKeepsTheRepeat()throws Exception{
        Rule r=rule("h",Kind.HIGH,210);start(session(r),"h",220);PersistentAlarmSession next=session(r);eval(next,M,220);assertFalse(next.deliveryReady("h",clock(M)));
    }
    @Test public void staleNormalReadingCannotResetTheEpisode()throws Exception{
        PersistentAlarmSession s=session(rule("h",Kind.HIGH,210));start(s,"h",220);
        Result result=s.evaluate(Collections.singletonList(new HistoryAnalysis.Point(200,B-5*M)),clock(M),720,null,null);assertEquals(Status.DATA_BLOCKED,result.status);assertTrue(store.load().entries.get("h").active);
    }
    @Test public void unknownTimestampAndSourceErrorCodesCannotResetTheEpisode()throws Exception{
        PersistentAlarmSession s=session(rule("h",Kind.HIGH,210));start(s,"h",220);
        s.evaluate(Collections.singletonList(new HistoryAnalysis.Point(200,null)),clock(M),720,null,null);assertTrue(store.load().entries.get("h").active);
        eval(s,2*M,5);assertTrue(store.load().entries.get("h").active);
    }
    @Test public void aDataGapIsNotNumericalRecovery()throws Exception{
        PersistentAlarmSession s=session(rule("h",Kind.HIGH,210));start(s,"h",220);
        Result result=s.evaluate(Arrays.asList(new HistoryAnalysis.Point(220,B-4*M),new HistoryAnalysis.Point(200,B+M)),clock(M),720,null,null);
        assertEquals(Status.DATA_BLOCKED,result.status);assertTrue(store.load().entries.get("h").active);
    }
    @Test public void closingScheduleWhileStillHighDoesNotResetTheEpisode()throws Exception{
        Rule r=new Rule("h",Kind.HIGH,true,210,0,false,719,721,new Outputs(false,true,30,30));PersistentAlarmSession s=session(r);start(s,"h",220);
        s.evaluate(Collections.singletonList(new HistoryAnalysis.Point(220,B+M)),clock(M),722,null,null);assertTrue(store.load().entries.get("h").active);assertEquals(B+30*M,store.load().entries.get("h").nextAt);
    }
    @Test public void highLevelsKeepIndependentRepeatsWhileTheRecoveredHigherLevelRearms()throws Exception{
        Rule a=rule("a",Kind.HIGH,180),b=rule("b",Kind.HIGH,240);PersistentAlarmSession s=session(a,b);start(s,"a",200);
        eval(s,M,250);s.recordDelivered("b",clock(M));s.accepted("b",clock(M));assertEquals(2,store.load().entries.size());
        eval(s,2*M,220);assertFalse(store.load().entries.containsKey("b"));assertFalse(s.deliveryReady("a",clock(2*M)));
        eval(s,3*M,250);assertTrue(s.deliveryReady("b",clock(3*M)));assertEquals(B+30*M,store.load().entries.get("a").nextAt);
    }
    @Test public void lowLevelsKeepIndependentRepeats()throws Exception{
        Rule a=rule("a",Kind.LOW,90),b=rule("b",Kind.LOW,70);PersistentAlarmSession s=session(a,b);start(s,"a",80);
        eval(s,M,60);s.recordDelivered("b",clock(M));eval(s,2*M,80);assertFalse(store.load().entries.containsKey("b"));assertFalse(s.deliveryReady("a",clock(2*M)));eval(s,3*M,60);assertTrue(s.deliveryReady("b",clock(3*M)));
    }
    @Test public void schemaThreeRoundTripsMultipleSameDirectionRows()throws Exception{
        Rule a=rule("a",Kind.HIGH,180),b=rule("b",Kind.HIGH,240);PersistentAlarmSession s=session(a,b);start(s,"a",200);eval(s,M,250);s.recordDelivered("b",clock(M));
        Snapshot read=new AlarmStateStore(folder.toFile()).load();assertEquals(2,read.entries.size());assertTrue(read.entries.get("a").active);assertTrue(read.entries.get("b").active);
    }
    private void rewriteSchema(int schema)throws Exception{
        byte[] encoded=Files.readAllBytes(folder.resolve(AlarmStateStore.FILE_NAME));byte[] body=Arrays.copyOf(encoded,encoded.length-32);
        ByteBuffer.wrap(body).putInt(2+"XCAM_ALARM_RUNTIME".length(),schema);
        if(schema==1)body=Arrays.copyOf(body,body.length-1);
        Files.write(folder.resolve(AlarmStateStore.FILE_NAME),ConfigurationFiles.seal(body));
    }
    @Test public void legacySingleThresholdSchemasRemainReadableAndRecoverNumerically()throws Exception{
        for(int schema:new int[]{1,2}){
            Rule r=rule("h",Kind.HIGH,210);PersistentAlarmSession s=session(r);start(s,"h",220);rewriteSchema(schema);
            assertTrue(store.load().entries.get("h").active);eval(session(r),M,209);assertTrue(store.load().entries.isEmpty());Files.delete(folder.resolve(AlarmStateStore.FILE_NAME));
        }
    }
    @Test public void invalidLegacyMultipleThresholdRowsRemainRejectedWithoutOverwrite()throws Exception{
        Rule a=rule("a",Kind.HIGH,180),b=rule("b",Kind.HIGH,240);PersistentAlarmSession s=session(a,b);start(s,"a",200);eval(s,M,250);s.recordDelivered("b",clock(M));rewriteSchema(2);
        byte[] before=Files.readAllBytes(folder.resolve(AlarmStateStore.FILE_NAME));try{store.load();fail();}catch(IOException expected){}assertArrayEquals(before,Files.readAllBytes(folder.resolve(AlarmStateStore.FILE_NAME)));
    }
    @Test public void oppositeActiveThresholdDirectionsRemainInvalid()throws Exception{
        Rule h=rule("h",Kind.HIGH,210),l=rule("l",Kind.LOW,80);
        try{new Snapshot(1,clock(0),Arrays.asList(new Entry("h",AlarmStateStore.fingerprint(h),Kind.HIGH,0,true,false,B,B+M,B),new Entry("l",AlarmStateStore.fingerprint(l),Kind.LOW,0,true,false,B,B+M,B)));fail();}catch(IllegalArgumentException expected){}
    }
}
