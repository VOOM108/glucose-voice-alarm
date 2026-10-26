// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class UiModelTest {
    @Test public void smoothDrawingNeverOvershootsBetweenSamplesIncludingPlateausAndSharpTurns() {
        int[] values={120,200,95,95,180,85,130}; List<DiagnosticSeries.Sample> points=new ArrayList<>();
        for(int i=0;i<values.length;i++) points.add(new DiagnosticSeries.Sample(1000+i*60000L,values[i]));
        double[][] controls=ChartCurve.controls(points); assertEquals(values.length-1,controls.length);
        for(int i=0;i<controls.length;i++) for(int sample=0;sample<=100;sample++) {
            double t=sample/100.0,s=1-t,c[]=controls[i];
            double y=s*s*s*values[i]+3*s*s*t*c[1]+3*s*t*t*c[3]+t*t*t*c[5];
            assertTrue(y>=Math.min(values[i],values[i+1])-.000001); assertTrue(y<=Math.max(values[i],values[i+1])+.000001);
        }
    }
    @Test public void smoothingAcceptsIsolatedPointsButCannotBridgeReversedTime() {
        assertEquals(0,ChartCurve.controls(Collections.singletonList(new DiagnosticSeries.Sample(100,123))).length);
        try { ChartCurve.controls(Arrays.asList(new DiagnosticSeries.Sample(100,123),new DiagnosticSeries.Sample(100,145))); fail(); } catch(IllegalArgumentException expected) {}
    }
    @Test public void editableTrendWordsAreLiteralAndSharedByTemplateManualAndSyntheticSpeech() {
        TrendSpeech words=new TrendSpeech(Arrays.asList("nach oben","rasch aufwärts","ruhig","nach unten","rasch abwärts"));
        String text=SpeechTemplate.render("{wert}: {trend}",null,new SpeechTemplate.Values(123,NotificationParser.Trend.FLAT,0,0,words),true,false);
        assertEquals("123: ruhig",text);
        assertTrue(SpeechPolicy.observationText(123,NotificationParser.Trend.FLAT,1,words).endsWith("Trend: ruhig."));
        assertTrue(SyntheticSpeech.value("{trend}",words).endsWith("ruhig")); assertEquals("",words.word(NotificationParser.Trend.UNKNOWN));
        try { new TrendSpeech(Arrays.asList("{wert}","b","c","d","e")); fail(); } catch(IllegalArgumentException expected) {}
    }
    @Test public void valuesCanSpeakWhileAlarmSpeechIsOffAndTheLastAlarmRouteCannotBeDisabled() {
        OutputPreferences p=new OutputPreferences(AlarmEngine.OutputMode.SPOKEN_ALARMS_AND_VALUES,1,SpeechTemplate.VALUE_DEFAULT).withChannels(true,false,true);
        AlarmLevel level=new SyntheticAlarmScenario(AlarmEngine.Kind.HIGH).level;
        assertTrue(p.periodicAllowed()); assertTrue(p.alarmToneAllowed(level)); assertFalse(p.alarmSpeechAllowed(level)); assertTrue(p.speechAllowed());
        try { p.withChannels(false,false,true); fail(); } catch(IllegalArgumentException expected) {}
        OutputPreferences onlyVoice=p.withChannels(false,true,false); assertFalse(onlyVoice.alarmToneAllowed(level)); assertTrue(onlyVoice.alarmSpeechAllowed(level)); assertFalse(onlyVoice.periodicAllowed());
    }
    @Test public void schemaThreeMigratesWithoutRewritingAndKeepsIndependentRoutesAndWordsAfterExplicitSave() throws Exception {
        Path folder=Files.createTempDirectory("synthetic-ui-output-"); Path file=folder.resolve(OutputPreferenceStore.FILE_NAME);
        try {
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();
            try(DataOutputStream out=new DataOutputStream(buffer)) {
                out.writeUTF("XCAM_OUTPUT"); out.writeInt(3); out.writeLong(8); out.writeUTF("SPOKEN_ALARMS_AND_VALUES"); out.writeInt(3); out.writeUTF(SpeechTemplate.VALUE_DEFAULT); out.writeInt(48); out.writeInt(63); out.writeInt(7); out.writeInt(4);
            }
            byte[] body=buffer.toByteArray(); buffer.write(MessageDigest.getInstance("SHA-256").digest(body)); byte[] old=buffer.toByteArray(); Files.write(file,old);
            OutputPreferenceStore store=new OutputPreferenceStore(folder.toFile()); OutputPreferenceStore.Snapshot loaded=store.load();
            assertArrayEquals(old,Files.readAllBytes(file)); assertEquals(4,loaded.preferences.bellBoostDb); assertTrue(loaded.preferences.valueSpeechEnabled); assertTrue(loaded.preferences.alarmSpeechEnabled); assertTrue(loaded.preferences.tonesEnabled);
            TrendSpeech words=new TrendSpeech(Arrays.asList("a","b","stabilissimo","d","e"));
            OutputPreferences desired=new OutputPreferences(AlarmEngine.OutputMode.SPOKEN_ALARMS_AND_VALUES,3,SpeechTemplate.VALUE_DEFAULT,48,63,7,4,words,true,false,true);
            store.save(loaded.revision,desired); OutputPreferences restored=new OutputPreferenceStore(folder.toFile()).load().preferences;
            assertTrue(restored.tonesEnabled); assertFalse(restored.alarmSpeechEnabled); assertTrue(restored.valueSpeechEnabled);
            assertEquals("stabilissimo",restored.trendSpeech.word(NotificationParser.Trend.FLAT)); assertEquals(4,restored.bellBoostDb); assertEquals(48,restored.toneVolumePercent); assertEquals(7,restored.valueQuietMinutes);
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(folder.resolve(OutputPreferenceStore.FILE_NAME+".new")); Files.delete(folder); }
    }
    @Test public void independentRoutesReachActualOutputPlansWithoutChangingRules() throws Exception {
        Path folder=Files.createTempDirectory("synthetic-channel-plans-"); List<AlarmOutputFlow.Plan> plans=new ArrayList<>();
        try {
            SyntheticAlarmScenario scenario=new SyntheticAlarmScenario(AlarmEngine.Kind.HIGH);
            OutputPreferences p=new OutputPreferences(AlarmEngine.OutputMode.SPOKEN_ALARMS_AND_VALUES,1,"{wert} {trend}",100,100,0,0,TrendSpeech.defaults(),true,false,true);
            AlarmOutputFlow flow=new AlarmOutputFlow(new AlarmStateStore(folder.toFile()),Collections.singletonList(scenario.level),SyntheticAlarmScenario.limits(),p,new AlarmOutputFlow.Port() {
                public boolean available(AlarmOutputFlow.Plan plan) { return true; } public boolean start(AlarmOutputFlow.Plan plan) { plans.add(plan); return true; } public void stop() {}
            });
            assertEquals(AlarmOutputFlow.Outcome.ACCEPTED,flow.evaluate(AlarmOutputFlow.Origin.SYNTHETIC,scenario.points(0,true),scenario.clock(0),720,null,NotificationParser.Trend.FLAT));
            assertEquals(AlarmOutputFlow.Outcome.ACCEPTED,flow.alarm(scenario.level.rule.id,scenario.clock(0))); assertNotNull(plans.get(0).toneUri); assertNull(plans.get(0).speech);
            assertEquals(AlarmOutputFlow.Outcome.ACCEPTED,flow.value(scenario.clock(60000))); assertNull(plans.get(1).toneUri); assertTrue(plans.get(1).speech.contains("stabil"));
        } finally { Files.deleteIfExists(folder.resolve(AlarmStateStore.FILE_NAME)); Files.deleteIfExists(folder.resolve(AlarmStateStore.FILE_NAME+".new")); Files.delete(folder); }
    }
}
