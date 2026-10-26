// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static de.giesen.camaps.companion.prototype.NotificationParser.Trend.*;

public class ReceiptRateTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private static final long NOW=1800000000000L,M=60000;
    @Before public void before() { AppText.configure(Locale.GERMAN); }
    private List<HistoryAnalysis.Point> points(int... values) {
        List<HistoryAnalysis.Point> result=new ArrayList<>(); for(int i=0;i<values.length;i++)result.add(HistoryAnalysis.Point.received(values[i],NOW-(values.length-1-i)*M));return result;
    }
    @Test public void fourMinuteBucketsUseTheirLastReadingAndExactElapsedTime() {
        assertNull(ReceiptRate.calculate(points(120,117,114),NOW).rate);
        List<HistoryAnalysis.Point> burst=new ArrayList<>(points(120,117,114,111));burst.add(HistoryAnalysis.Point.received(110,NOW+1000));
        ReceiptRate.Result result=ReceiptRate.calculate(burst,NOW+1000);assertEquals(-10*60000.0/181000,result.rate,1e-12);
        assertEquals(0,ReceiptRate.calculate(points(120,120,120,120),NOW).rate,0);
        List<HistoryAnalysis.Point> rapid=new ArrayList<>();for(int i=0;i<4;i++)rapid.add(HistoryAnalysis.Point.received(120-i,NOW-3000+i*1000));
        assertNull(ReceiptRate.calculate(rapid,NOW).rate);
    }
    @Test public void invalidStaleGappedAndMixedHistoriesNeverBecomeStable() {
        List<HistoryAnalysis.Point> valid=points(120,117,114,111);
        assertNull(ReceiptRate.calculate(valid,NOW+240001).rate); assertNull(ReceiptRate.calculate(valid,NOW-1).rate);
        assertNull(ReceiptRate.calculate(Arrays.asList(HistoryAnalysis.Point.received(120,NOW-9*M),HistoryAnalysis.Point.received(117,NOW-2*M),HistoryAnalysis.Point.received(114,NOW-M),HistoryAnalysis.Point.received(111,NOW)),NOW).rate);
        Collections.reverse(valid);assertNull(ReceiptRate.calculate(valid,NOW).rate);
        valid=points(120,117,114,111);valid.set(0,new HistoryAnalysis.Point(120,NOW-3*M));assertNull(ReceiptRate.calculate(valid,NOW).rate);
        assertNull(ReceiptRate.calculate(null,NOW).rate);assertNull(ReceiptRate.calculate(points(120,117,114,111),0).rate);
    }
    @Test public void receiptRestartAndClockJumpRequireFourNewMinutes() {
        ReceiptWindow window=new ReceiptWindow();for(int i=0;i<4;i++){long t=NOW-(3-i)*M;window.receive("test",t,120-3*i,new AlarmStateStore.Clock(t,10*M+i*M,1));}
        assertEquals(-3,ReceiptRate.calculate(window.points(),NOW).rate,0);
        window.receive("test",NOW+M,108,new AlarmStateStore.Clock(NOW+M,1,2));assertNull(ReceiptRate.calculate(window.points(),NOW+M).rate);
    }
    @Test public void diagnosticResultExpiresWithoutInventingAnArrow() {
        ReceiptRate.Result c=ReceiptRate.calculate(points(120,117,114,111),NOW);
        assertEquals(-3,c.at(NOW),0);assertEquals(-3,c.at(NOW+240000),0);
        assertNull(c.at(NOW+240001));assertNull(c.at(NOW-1));assertNull(ReceiptRate.unavailable().at(NOW));
    }
    private byte[] legacySources(OutputPreferences p,String first,String second) throws Exception {
        byte[] body=ConfigurationFiles.open(OutputPreferenceStore.encode(new OutputPreferenceStore.Snapshot(3,p)),8192);
        body=Arrays.copyOf(body,body.length-7-6);body[2+"XCAM_OUTPUT".length()+3]=9;
        ByteArrayOutputStream tail=new ByteArrayOutputStream();try(DataOutputStream out=new DataOutputStream(tail)){out.writeUTF("CAMAPS");out.writeUTF("CAMAPS");}
        ByteArrayOutputStream replaced=new ByteArrayOutputStream();replaced.write(body,0,body.length-tail.size());
        try(DataOutputStream out=new DataOutputStream(replaced)){out.writeUTF(first);out.writeUTF(second);}return ConfigurationFiles.seal(replaced.toByteArray());
    }
    @Test public void retiredSourceChoicesReadWithoutRewritingCustomProfileData() throws Exception {
        OutputPreferences p=new OutputPreferences(AlarmEngine.OutputMode.SPOKEN_ALARMS_AND_VALUES,3,"Custom {wert} {trend}",65,80,7,3,new TrendSpeech(Arrays.asList("up","fast up","level","down","fast down","double up","double down")),true,true,true,true)
                .withGap(GapSettings.defaults().withTemplate("Custom gap {minutes}")).withSpeechLanguage("en");
        OutputPreferences loaded=OutputPreferenceStore.decode(legacySources(p,"CALCULATED","CALCULATED")).preferences;
        ConfigurationSnapshot config=new ConfigurationSnapshot(new LevelStore.Snapshot(1,Collections.emptyList()),new OutputPreferenceStore.Snapshot(2,loaded),"DARK","samt",true,true);
        OutputPreferences saved=ConfigurationSnapshot.decode(config.encode()).output.preferences;
        assertEquals(p.valueTemplate,saved.valueTemplate);assertEquals(p.trendSpeech.words,saved.trendSpeech.words);assertEquals("en",saved.speechLanguage);assertEquals(p.gap.speechTemplate,saved.gap.speechTemplate);assertEquals(65,saved.toneVolumePercent);assertTrue(saved.vibrationEnabled);
        assertArrayEquals(OutputPreferenceStore.encode(new OutputPreferenceStore.Snapshot(3,p)),OutputPreferenceStore.encode(new OutputPreferenceStore.Snapshot(3,saved)));
    }
    @Test public void invalidRetiredFieldsStillRejectCorruption() throws Exception {
        for(String[] fields:new String[][]{{"INVALID","CAMAPS"},{"CAMAPS","INVALID"}})try {OutputPreferenceStore.decode(legacySources(OutputPreferences.defaults(),fields[0],fields[1]));fail("corrupt source accepted");}catch(IOException expected){}
    }
    @Test public void oldSchemaEightPreservesCustomData() throws Exception {
        OutputPreferences p=OutputPreferences.defaults().withSpeechLanguage("en").withGap(GapSettings.defaults().withTemplate("Custom gap {minutes}"));
        byte[] body=ConfigurationFiles.open(legacySources(p,"CAMAPS","CAMAPS"),8192);
        ByteArrayOutputStream tail=new ByteArrayOutputStream();try(DataOutputStream out=new DataOutputStream(tail)){out.writeUTF("CAMAPS");out.writeUTF("CAMAPS");}
        body=Arrays.copyOf(body,body.length-tail.size());body[2+"XCAM_OUTPUT".length()+3]=8;
        OutputPreferences saved=OutputPreferenceStore.decode(ConfigurationFiles.seal(body)).preferences;
        assertEquals("en",saved.speechLanguage);assertEquals(p.gap.speechTemplate,saved.gap.speechTemplate);
    }
    private AlarmOutputFlow.Port collect(List<AlarmOutputFlow.Plan> plans) { return new AlarmOutputFlow.Port(){public boolean available(AlarmOutputFlow.Plan p){return true;}public boolean start(AlarmOutputFlow.Plan p){plans.add(p);return true;}public void stop(){}}; }
    @Test public void criticalRateTriggersWithoutCamapsArrowAndIndependentlyOfReadingInterval() throws Exception {
        List<AlarmOutputFlow.Plan> plans=new ArrayList<>();
        AlarmEngine.Rule rule=new AlarmEngine.Rule("fall",AlarmEngine.Kind.FALLING,true,2.5,0,true,0,1440,new AlarmEngine.Outputs(false,true,0,0));
        AlarmLevel level=new AlarmLevel(rule,"Eigene Rate",AlarmLevel.DEFAULT_TONE,Collections.emptyList());
        OutputPreferences preferences=new OutputPreferences(AlarmEngine.OutputMode.SPOKEN_ALARMS_AND_VALUES,30,"{wert} {trend}");
        AlarmOutputFlow flow=AlarmOutputFlow.liveReceipts(new AlarmStateStore(temporary.newFolder()),Collections.singletonList(level),ReceiptWindow.limits(),preferences,collect(plans),OutputGuard.disabled());
        AlarmStateStore.Clock clock=new AlarmStateStore.Clock(NOW,10*M,1);
        assertEquals(AlarmOutputFlow.Outcome.ACCEPTED,flow.evaluate(AlarmOutputFlow.Origin.LIVE,points(120,117,114,111),clock,720,NOW-3*M,UNKNOWN));
        assertTrue(flow.lastDecision().decisions.get(0).conditionMet);assertEquals(AlarmOutputFlow.Outcome.ACCEPTED,flow.alarm("fall",clock));assertEquals("Kritische Fallrate bei 111.",plans.get(0).speech);
    }
    @Test public void normalReadingSpeechAlwaysUsesCamapsAndOmitsMissingArrow() throws Exception {
        for(NotificationParser.Trend original:new NotificationParser.Trend[]{FLAT,UNKNOWN}) {
            List<AlarmOutputFlow.Plan> plans=new ArrayList<>();
            OutputPreferences p=new OutputPreferences(AlarmEngine.OutputMode.SPOKEN_ALARMS_AND_VALUES,1,"{wert} Trend {trend}");
            AlarmLevel level=new SyntheticAlarmScenario(AlarmEngine.Kind.HIGH).level;
            AlarmOutputFlow flow=AlarmOutputFlow.liveReceipts(new AlarmStateStore(temporary.newFolder()),Collections.singletonList(level),ReceiptWindow.limits(),p,collect(plans),OutputGuard.disabled());
            assertEquals(AlarmOutputFlow.Outcome.ACCEPTED,flow.evaluate(AlarmOutputFlow.Origin.LIVE,points(120,117,114,111),new AlarmStateStore.Clock(NOW,10*M,1),720,NOW-3*M,original));
            assertEquals(AlarmOutputFlow.Outcome.ACCEPTED,flow.value(new AlarmStateStore.Clock(NOW+M,11*M,1)));
            String speech=plans.get(0).speech;assertTrue(speech.contains("111"));assertFalse(speech.contains("fallend"));
            if(original==FLAT)assertTrue(speech.contains("stabil"));else {assertFalse(speech.contains("stabil"));assertFalse(speech.contains("Trend"));}
        }
    }
    @Test public void criticalDefaultTemplatesDoNotReplaceSavedLiteralSpeechOrNames() {
        for(AlarmEngine.Kind kind:new AlarmEngine.Kind[]{AlarmEngine.Kind.RISING,AlarmEngine.Kind.FALLING}) {
            AlarmEngine.Rule rule=new AlarmEngine.Rule("rate",kind,true,5,0,true,0,1440,new AlarmEngine.Outputs(false,true,0,0));
            AlarmLevel custom=new AlarmLevel(rule,"Mein Alarm",AlarmLevel.DEFAULT_TONE,Collections.singletonList("Mein Text {wert}"));
            assertEquals("Mein Text {wert}",custom.speechText(0,"en"));assertEquals("Mein Alarm",custom.name);
            assertEquals(kind==AlarmEngine.Kind.RISING?"Kritische Anstiegsrate bei {wert}.":"Kritische Fallrate bei {wert}.",SpeechTemplate.defaultAlarm(kind,"de"));
            assertEquals(kind==AlarmEngine.Kind.RISING?"Critical rise rate at {value}.":"Critical fall rate at {value}.",SpeechTemplate.defaultAlarm(kind,"en"));
        }
    }
}
