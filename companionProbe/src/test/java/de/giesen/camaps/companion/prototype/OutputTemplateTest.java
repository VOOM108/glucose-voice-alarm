// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import de.giesen.camaps.companion.prototype.alarm.*;
import de.giesen.camaps.companion.prototype.alarm.AlarmEngine.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class OutputTemplateTest {
    private AlarmLevel level(Kind kind,boolean enabled,boolean tone,boolean speech,List<String> texts) {
        return new AlarmLevel(new Rule("test",kind,enabled,100,kind==Kind.PERSISTENT_HIGH?3:0,true,0,1440,new Outputs(tone,speech,3,5)),
                "Teststufe $\\",AlarmLevel.DEFAULT_TONE,texts);
    }
    private interface Check { void run(Path folder) throws Exception; }
    private void fileTest(Check check) throws Exception {
        Path folder=Files.createTempDirectory("output-settings-synthetic-");
        try { check.run(folder); }
        finally {
            for (String name:new String[]{OutputPreferenceStore.FILE_NAME,OutputPreferenceStore.FILE_NAME+".new",LevelStore.FILE_NAME,LevelStore.FILE_NAME+".new"}) Files.deleteIfExists(folder.resolve(name));
            Files.delete(folder);
        }
    }
    private void invalid(Runnable action) { try { action.run(); fail(); } catch (IllegalArgumentException expected) {} }
    @Test public void unknownTrendIsOmittedWhileTheNumericValueIsRetained() {
        SpeechTemplate.Values unknown=new SpeechTemplate.Values(123,NotificationParser.Trend.UNKNOWN,0,0);
        assertEquals("Der Wert beträgt 123 Milligramm pro Deziliter.",SpeechTemplate.render(SpeechTemplate.VALUE_DEFAULT,null,unknown,true,false));
        assertEquals("Wert 123.",SpeechTemplate.render("Trend: {trend}, Wert {wert}.",null,unknown,true,false));
        assertEquals("123",SpeechTemplate.render("{wert} {trend}",null,unknown,true,false));
        String manual=SpeechPolicy.observationText(123,NotificationParser.Trend.UNKNOWN,1);
        assertTrue(manual.contains("123")); assertFalse(manual.contains("unbekannt")); assertFalse(manual.contains("Trend"));
        SpeechTemplate.Values known=new SpeechTemplate.Values(123,NotificationParser.Trend.UP,0,0);
        assertTrue(SpeechTemplate.render(SpeechTemplate.VALUE_DEFAULT,null,known,true,false).contains("schnell steigend"));
        invalid(() -> new SpeechTemplate.Values(0,NotificationParser.Trend.UNKNOWN,0,0));
    }
    @Test public void everyKindHasAValidLiteralDefaultAndDurationUsesWords() {
        for (Kind kind:Kind.values()) {
            String text=SpeechTemplate.defaultAlarm(kind); SpeechTemplate.validate(text,kind,false);
            assertFalse(text.contains(">")); assertFalse(text.contains("<"));
        }
        assertTrue(SpeechTemplate.defaultAlarm(Kind.PERSISTENT_HIGH).contains("seit {dauer_minuten} Minuten"));
    }
    @Test public void renderedPreviewIsClearlySyntheticAndUsesTypedValues() {
        AlarmLevel level=level(Kind.PERSISTENT_HIGH,true,true,true,Collections.emptyList());
        String rendered=SpeechTemplate.render(level.speechText(0),level,new SpeechTemplate.Values(123,NotificationParser.Trend.UP_RIGHT,5,4),false,true);
        assertTrue(rendered.startsWith("Dies ist eine Testansage")); assertTrue(rendered.contains("seit 4 Minuten"));
        assertTrue(rendered.contains("123 Milligramm pro Deziliter")); assertFalse(rendered.contains("{"));
    }
    @Test public void userTextIsNotRegexCodeOrAnExpression() {
        AlarmLevel level=level(Kind.HIGH,true,true,true,Collections.emptyList());
        String rendered=SpeechTemplate.render("Stufe {stufe}, Grenze {grenze}.",level,new SpeechTemplate.Values(123,NotificationParser.Trend.UNKNOWN,0,0),false,false);
        assertTrue(rendered.contains("Teststufe $\\")); assertTrue(rendered.contains("100"));
        invalid(() -> SpeechTemplate.validate("{wert*2}",Kind.HIGH,false));
        invalid(() -> SpeechTemplate.validate("{unbekannt}",Kind.HIGH,false));
    }
    @Test public void durationPlaceholderIsRejectedForNonDurationKinds() {
        invalid(() -> SpeechTemplate.validate("Seit {dauer_minuten} Minuten",Kind.HIGH,false));
        invalid(() -> SpeechTemplate.validate("Seit {dauer_minuten} Minuten",Kind.RISING,false));
    }
    @Test public void periodicTemplatesCannotAccidentallyUseAlarmContext() {
        SpeechTemplate.validate("Wert {wert}, Trend {trend}, Alter {alter_sekunden} Sekunden.",null,true);
        invalid(() -> SpeechTemplate.validate("Grenze {grenze}",null,true));
        invalid(() -> SpeechTemplate.validate("Stufe {stufe}",null,true));
    }
    @Test public void variantsAreExplicitBoundedAndDeterministic() {
        List<String> variants=SpeechTemplate.variants("Test eins {wert}\n\nTest zwei {wert}",Kind.HIGH);
        AlarmLevel level=level(Kind.HIGH,true,true,true,variants);
        assertEquals(2,variants.size()); assertEquals(variants.get(0),level.speechText(0));
        assertEquals(variants.get(1),level.speechText(1)); assertEquals(variants.get(0),level.speechText(2));
        invalid(() -> SpeechTemplate.variants("a\nb\nc\nd\ne",Kind.HIGH));
    }
    @Test public void enablingStageDoesNotDropSpeechVariants() {
        AlarmLevel disabled=level(Kind.HIGH,false,true,true,Arrays.asList("Eigener Text {wert}"));
        assertEquals(disabled.speechVariants,disabled.withEnabled(true).speechVariants);
    }
    @Test public void tonesOnlyBlocksAllSpeechWithoutEnablingAnyTone() {
        OutputPreferences prefs=new OutputPreferences(OutputMode.TONES_ONLY,3,SpeechTemplate.VALUE_DEFAULT);
        assertFalse(prefs.speechAllowed()); assertFalse(prefs.periodicAllowed());
        assertFalse(prefs.alarmToneAllowed(level(Kind.HIGH,true,false,true,Collections.emptyList())));
        assertFalse(prefs.alarmSpeechAllowed(level(Kind.HIGH,true,true,true,Collections.emptyList())));
    }
    @Test public void outputModesNeverEnableADisabledRule() {
        for (OutputMode mode:OutputMode.values()) {
            OutputPreferences prefs=new OutputPreferences(mode,3,SpeechTemplate.VALUE_DEFAULT);
            AlarmLevel disabled=level(Kind.HIGH,false,true,true,Collections.emptyList());
            assertFalse(prefs.alarmToneAllowed(disabled)); assertFalse(prefs.alarmSpeechAllowed(disabled));
        }
    }
    @Test public void onlyThirdModePermitsPeriodicValuesWithAnExplicitInterval() {
        assertFalse(OutputPreferences.defaults().periodicAllowed());
        assertTrue(new OutputPreferences(OutputMode.SPOKEN_ALARMS_AND_VALUES,3,SpeechTemplate.VALUE_DEFAULT).periodicAllowed());
        invalid(() -> new OutputPreferences(OutputMode.SPOKEN_ALARMS_AND_VALUES,0,SpeechTemplate.VALUE_DEFAULT));
    }
    @Test public void outputPreferencesRoundTripAndDetectStaleWrites() throws Exception {
        fileTest(folder -> {
            OutputPreferenceStore store=new OutputPreferenceStore(folder.toFile()); assertEquals(0,store.load().revision);
            OutputPreferences prefs=new OutputPreferences(OutputMode.TONES_ONLY,3,"Testwert {wert} {einheit}.");
            store.save(0,prefs); OutputPreferenceStore.Snapshot restored=new OutputPreferenceStore(folder.toFile()).load();
            assertEquals(OutputMode.TONES_ONLY,restored.preferences.mode); assertEquals(3,restored.preferences.valueIntervalMinutes);
            assertEquals(prefs.valueTemplate,restored.preferences.valueTemplate);
            try { store.save(0,OutputPreferences.defaults()); fail(); } catch (IOException expected) {}
            assertEquals(OutputMode.TONES_ONLY,store.load().preferences.mode);
        });
    }
    @Test public void corruptedOutputPreferencesRemainUntouched() throws Exception {
        fileTest(folder -> {
            OutputPreferenceStore store=new OutputPreferenceStore(folder.toFile()); store.save(0,OutputPreferences.defaults());
            byte[] damaged=Files.readAllBytes(folder.resolve(OutputPreferenceStore.FILE_NAME)); damaged[0]^=1;
            Files.write(folder.resolve(OutputPreferenceStore.FILE_NAME),damaged);
            try { store.save(1,OutputPreferences.defaults()); fail(); } catch (IOException expected) {}
            assertArrayEquals(damaged,Files.readAllBytes(folder.resolve(OutputPreferenceStore.FILE_NAME)));
        });
    }
    @Test public void schemaTwoRetainsVoiceVariantsAcrossFreshInstances() throws Exception {
        fileTest(folder -> {
            AlarmLevel source=level(Kind.HIGH,true,true,true,Arrays.asList("Text eins {wert}","Text zwei {wert}"));
            LevelStore store=new LevelStore(folder.toFile()); store.save(0,Arrays.asList(source));
            AlarmLevel restored=new LevelStore(folder.toFile()).load().find("test");
            assertEquals(source.speechVariants,restored.speechVariants); assertEquals(source.name,restored.name);
            assertEquals(source.rule.threshold,restored.rule.threshold,0);
        });
    }
    @Test public void legacySchemaOneLoadsWithoutRewritingAndUpgradesOnExplicitSave() throws Exception {
        fileTest(folder -> {
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();
            try (DataOutputStream out=new DataOutputStream(buffer)) {
                out.writeUTF("XCAM_ALARM_LEVELS"); out.writeInt(1); out.writeLong(4); out.writeInt(1);
                out.writeUTF("old"); out.writeUTF("Alte Teststufe"); out.writeUTF("HIGH"); out.writeBoolean(false);
                out.writeDouble(123); out.writeInt(0); out.writeBoolean(true); out.writeInt(0); out.writeInt(1440);
                out.writeBoolean(true); out.writeBoolean(false); out.writeInt(3); out.writeInt(5); out.writeUTF(AlarmLevel.DEFAULT_TONE);
            }
            byte[] body=buffer.toByteArray(); ByteArrayOutputStream encoded=new ByteArrayOutputStream(); encoded.write(body);
            encoded.write(MessageDigest.getInstance("SHA-256").digest(body));
            Path target=folder.resolve(LevelStore.FILE_NAME); Files.write(target,encoded.toByteArray());
            LevelStore store=new LevelStore(folder.toFile()); LevelStore.Snapshot legacy=store.load();
            assertEquals(4,legacy.revision); assertEquals(123,legacy.find("old").rule.threshold,0);
            assertTrue(legacy.find("old").speechVariants.isEmpty()); assertArrayEquals(encoded.toByteArray(),Files.readAllBytes(target));
            store.save(legacy.revision,legacy.levels); assertEquals(123,store.load().find("old").rule.threshold,0);
            assertEquals(5,store.load().revision);
        });
    }
    @Test public void outputVolumesRoundTripAndLegacyOutputFileLoadsAtFullRelativeVolumeWithoutRewriting() throws Exception {
        fileTest(folder -> {
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();
            try (DataOutputStream out=new DataOutputStream(buffer)) {
                out.writeUTF("XCAM_OUTPUT"); out.writeInt(1); out.writeLong(4); out.writeUTF("SPOKEN_ALARMS"); out.writeInt(0); out.writeUTF(SpeechTemplate.VALUE_DEFAULT);
            }
            byte[] body=buffer.toByteArray(); ByteArrayOutputStream encoded=new ByteArrayOutputStream(); encoded.write(body); encoded.write(MessageDigest.getInstance("SHA-256").digest(body));
            Path target=folder.resolve(OutputPreferenceStore.FILE_NAME); Files.write(target,encoded.toByteArray());
            OutputPreferenceStore store=new OutputPreferenceStore(folder.toFile()); OutputPreferenceStore.Snapshot legacy=store.load();
            assertEquals(100,legacy.preferences.toneVolumePercent); assertEquals(100,legacy.preferences.speechVolumePercent);
            assertArrayEquals(encoded.toByteArray(),Files.readAllBytes(target));
            store.save(legacy.revision,new OutputPreferences(OutputMode.SPOKEN_ALARMS,0,SpeechTemplate.VALUE_DEFAULT,37,63));
            OutputPreferences restored=new OutputPreferenceStore(folder.toFile()).load().preferences;
            assertEquals(37,restored.toneVolumePercent); assertEquals(63,restored.speechVolumePercent);
        });
    }
    @Test public void mutedSpeechRequiresAnAudibleToneAndZeroVolumesCannotSatisfyTheRequirement() {
        AlarmLevel level=level(Kind.HIGH,true,true,true,Collections.emptyList());
        assertNull(OutputRequirements.missingTone(Arrays.asList(level),new OutputPreferences(OutputMode.SPOKEN_ALARMS,0,SpeechTemplate.VALUE_DEFAULT,20,0)));
        assertNotNull(OutputRequirements.missingTone(Arrays.asList(level),new OutputPreferences(OutputMode.SPOKEN_ALARMS,0,SpeechTemplate.VALUE_DEFAULT,0,0)));
        assertNull(OutputRequirements.missingTone(Arrays.asList(level),new OutputPreferences(OutputMode.SPOKEN_ALARMS,0,SpeechTemplate.VALUE_DEFAULT,0,20)));
        invalid(() -> new OutputPreferences(OutputMode.SPOKEN_ALARMS,0,SpeechTemplate.VALUE_DEFAULT,-1,50));
        invalid(() -> new OutputPreferences(OutputMode.SPOKEN_ALARMS,0,SpeechTemplate.VALUE_DEFAULT,50,101));
    }
    @Test public void persistentHighIsExplicitlyExemptFromMandatoryToneAndDisabledLevelsAreAlwaysExempt() {
        OutputPreferences p=new OutputPreferences(OutputMode.TONES_ONLY,0,SpeechTemplate.VALUE_DEFAULT);
        assertNull(OutputRequirements.missingTone(Arrays.asList(level(Kind.PERSISTENT_HIGH,true,false,true,Collections.emptyList())),p));
        assertNull(OutputRequirements.missingTone(Arrays.asList(level(Kind.PERSISTENT_HIGH,true,false,false,Collections.emptyList())),p));
        assertNull(OutputRequirements.missingTone(Arrays.asList(level(Kind.HIGH,false,false,false,Collections.emptyList())),p));
        assertNotNull(OutputRequirements.missingTone(Arrays.asList(level(Kind.HIGH,true,false,true,Collections.emptyList())),p));
    }
    @Test public void schemaTwoOutputLoadsWithoutRewritingAndBoostOnlyChangesOnExplicitSave() throws Exception {
        fileTest(folder -> {
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();
            try (DataOutputStream out=new DataOutputStream(buffer)) {
                out.writeUTF("XCAM_OUTPUT"); out.writeInt(2); out.writeLong(4); out.writeUTF("SPOKEN_ALARMS"); out.writeInt(7);
                out.writeUTF(SpeechTemplate.VALUE_DEFAULT); out.writeInt(37); out.writeInt(63); out.writeInt(9);
            }
            byte[] body=buffer.toByteArray(); ByteArrayOutputStream encoded=new ByteArrayOutputStream(); encoded.write(body); encoded.write(MessageDigest.getInstance("SHA-256").digest(body));
            Path target=folder.resolve(OutputPreferenceStore.FILE_NAME); Files.write(target,encoded.toByteArray());
            OutputPreferenceStore store=new OutputPreferenceStore(folder.toFile()); OutputPreferenceStore.Snapshot legacy=store.load();
            assertEquals(0,legacy.preferences.bellBoostDb); assertEquals(37,legacy.preferences.toneVolumePercent); assertEquals(63,legacy.preferences.speechVolumePercent); assertEquals(9,legacy.preferences.valueQuietMinutes);
            assertArrayEquals(encoded.toByteArray(),Files.readAllBytes(target));
            store.save(legacy.revision,new OutputPreferences(OutputMode.SPOKEN_ALARMS,7,SpeechTemplate.VALUE_DEFAULT,37,63,9,6));
            OutputPreferences restored=new OutputPreferenceStore(folder.toFile()).load().preferences;
            assertEquals(6,restored.bellBoostDb); assertEquals(37,restored.toneVolumePercent); assertEquals(63,restored.speechVolumePercent); assertEquals(9,restored.valueQuietMinutes);
            invalid(() -> new OutputPreferences(OutputMode.SPOKEN_ALARMS,0,SpeechTemplate.VALUE_DEFAULT,100,100,5,-1));
            invalid(() -> new OutputPreferences(OutputMode.SPOKEN_ALARMS,0,SpeechTemplate.VALUE_DEFAULT,100,100,5,7));
        });
    }
}
