// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import de.giesen.camaps.companion.prototype.alarm.*;
import de.giesen.camaps.companion.prototype.alarm.AlarmEngine.*;
import de.giesen.camaps.companion.prototype.alarm.AlarmStateStore.*;
import java.io.*;import java.util.*;
import org.junit.Test;import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Synthetic receipt episodes; missing samples are never invented. */
public class PersistentDurationTest {
 @org.junit.Rule public TemporaryFolder temporary=new TemporaryFolder();
 private static final long B=10000000L,M=60000L;
 private final ReceiptWindow window=new ReceiptWindow();
 private AlarmStateStore store;private PersistentAlarmSession session;
 private Rule rule(String id,double threshold){return new Rule(id,Kind.PERSISTENT_HIGH,true,threshold,5,true,0,1440,new Outputs(false,true,30,0));}
 private Clock clock(long minute){return new Clock(B+minute*M,1000000L+minute*M,1);}
 private void start(Rule...rules)throws Exception{store=new AlarmStateStore(temporary.newFolder());session=new PersistentAlarmSession(store,Arrays.asList(rules),ReceiptWindow.limits(),OutputMode.SPOKEN_ALARMS,HistoryAnalysis.TimeBasis.RECEIPT);}
 private Result receive(long minute,double value)throws Exception{Clock now=clock(minute);window.receive("synthetic",now.wallMs,value,now);return session.evaluate(window.points(),window.episodePoints(),now,720,window.sessionStart(),null);}
 private long since(String id)throws Exception{Entry e=store.load().entries.get(id);return e==null?0:e.highSince;}
 @Test public void firstHighReceiptStartsDurationWithoutANumericalTrend()throws Exception{start(rule("p",180));Result r=receive(0,200);assertEquals(B,since("p"));assertEquals(0,r.matchedRules());}
 @Test public void initiallyFallingValuesDoNotDelayTheRecordedBeginning()throws Exception{start(rule("p",180));receive(0,300);for(int i=1;i<=60;i++)receive(i,300-i);assertEquals(B,since("p"));assertEquals(1,receive(61,241).matchedRules());}
 @Test public void fiveHoursAboveWithEarlyGapsReportsElapsedFiveHours()throws Exception{
  start(rule("p",180));Result r=null;for(int i=0;i<=300;i++){if(i>50 && i<65 || i>100 && i<110)continue;r=receive(i,200+i/10.0);}
  assertEquals(B,since("p"));assertEquals(300,(clock(300).wallMs-since("p"))/M);assertEquals(1,r.matchedRules());assertEquals(278,window.episodePoints().size());
 }
 @Test public void aGapRetainsOldActualReceiptsButRestartsTheRateWindow()throws Exception{start(rule("p",180));receive(0,200);receive(1,201);receive(9,220);assertEquals(1,window.points().size());assertEquals(3,window.episodePoints().size());assertFalse(window.clockChanged());assertEquals(B,since("p"));assertEquals(1,receive(10,221).matchedRules());}
 @Test public void noNewSampleMeansNoCurrentAlarmButTheBeginningIsRetained()throws Exception{start(rule("p",180));receive(0,200);receive(1,201);Result r=session.evaluate(window.points(),window.episodePoints(),clock(9),720,window.sessionStart(),null);assertEquals(Status.DATA_BLOCKED,r.status);assertEquals(B,since("p"));}
 @Test public void invalidDataDoesNotAssertNumericalRecovery()throws Exception{start(rule("p",180));receive(0,200);List<HistoryAnalysis.Point> invalid=Collections.singletonList(HistoryAnalysis.Point.received(Double.NaN,B+M));assertEquals(Status.DATA_BLOCKED,session.evaluate(invalid,invalid,clock(1),720,B,null).status);assertEquals(B,since("p"));}
 @Test public void aFreshValueAtTheBoundaryEndsTheEpisode()throws Exception{start(rule("p",180));receive(0,200);receive(9,180);assertEquals(0,since("p"));receive(10,200);assertEquals(B+10*M,since("p"));}
 @Test public void aFreshValueBelowTheBoundaryEndsTheEpisode()throws Exception{start(rule("p",180));receive(0,200);receive(9,179);assertEquals(0,since("p"));}
 @Test public void risingAgainAfterAPlateauUsesTheWholeElapsedEpisode()throws Exception{start(rule("p",180));receive(0,200);for(int i=1;i<=20;i++)assertEquals(0,receive(i,200).matchedRules());assertEquals(B,since("p"));assertEquals(1,receive(21,201).matchedRules());}
 @Test public void eachPersistentLevelKeepsItsOwnBeginning()throws Exception{start(rule("p",180),rule("q",240));receive(0,200);assertEquals(0,since("q"));receive(1,230);receive(2,250);assertEquals(B,since("p"));assertEquals(B+2*M,since("q"));}
 @Test public void processRecreationWithFreshSessionBoundaryRetainsVerifiedBeginning()throws Exception{Rule r=rule("p",180);start(r);receive(0,200);receive(1,201);session=new PersistentAlarmSession(store,Collections.singletonList(r),ReceiptWindow.limits(),OutputMode.SPOKEN_ALARMS,HistoryAnalysis.TimeBasis.RECEIPT);window.clear();receive(9,220);assertEquals(1,receive(10,221).matchedRules());assertEquals(B,since("p"));}
 @Test public void anEpisodeHistoryWithADifferentLatestValueCannotRaise()throws Exception{start(rule("p",180));receive(0,200);receive(1,201);List<HistoryAnalysis.Point> current=Arrays.asList(HistoryAnalysis.Point.received(220,B+9*M),HistoryAnalysis.Point.received(221,B+10*M));List<HistoryAnalysis.Point> wrong=Arrays.asList(HistoryAnalysis.Point.received(250,B+9*M),HistoryAnalysis.Point.received(251,B+10*M));assertEquals(0,session.evaluate(current,wrong,clock(10),720,B+9*M,null).matchedRules());assertEquals(B,since("p"));}
 @Test public void futureEpisodePointsAreNotAcceptedAsEvidence()throws Exception{start(rule("p",180));receive(0,200);receive(1,201);List<HistoryAnalysis.Point> wrong=new ArrayList<>(window.episodePoints());wrong.add(HistoryAnalysis.Point.received(250,B+20*M));assertEquals(0,session.evaluate(window.points(),wrong,clock(1),720,B,null).matchedRules());}
 @Test public void noCrossGapRateIsCalculatedFromTheEpisodeHistory()throws Exception{Rule rate=new Rule("rate",Kind.RISING,true,5,0,true,0,1440,new Outputs(false,true,0,0));start(rule("p",180),rate);receive(0,200);receive(1,201);receive(9,300);receive(10,301);Result r=receive(11,302);for(Decision d:r.decisions)if(d.rule.kind==Kind.RISING)assertFalse(d.conditionMet);}
 @Test public void measurementModeRetainsOriginalSlopeDependentTimerStart()throws Exception{AlarmEngine engine=new AlarmEngine(Collections.singletonList(rule("p",180)),new HistoryAnalysis.Limits(1,4*M,4*M),OutputMode.SPOKEN_ALARMS);engine.evaluate(Collections.singletonList(new HistoryAnalysis.Point(200,B)),B,720,B,null);assertTrue(engine.persistentTimers().isEmpty());}
 @Test public void rateAndEpisodeHistoriesAreBothBounded()throws Exception{ReceiptWindow w=new ReceiptWindow();for(int i=0;i<600;i++)w.receive("s",B+i*M,200,clock(i));assertEquals(500,w.points().size());assertEquals(500,w.episodePoints().size());}
 @Test public void realClockChangeClearsEpisodeSamplesInsteadOfManufacturingContinuity(){ReceiptWindow w=new ReceiptWindow();w.receive("s",B,200,clock(0));w.receive("s",B+10*M,201,new Clock(B+10*M,1000000+M,1));assertTrue(w.clockChanged());assertEquals(1,w.episodePoints().size());}
 @Test public void deliveryReservationDoesNotRestartTheEpisode()throws Exception{start(rule("p",180));for(int i=0;i<=6;i++)receive(i,200+i);session.claimDelivery("p",clock(6));session.accepted("p",clock(6));assertEquals(B,since("p"));receive(7,207);assertEquals(B,since("p"));}
}
