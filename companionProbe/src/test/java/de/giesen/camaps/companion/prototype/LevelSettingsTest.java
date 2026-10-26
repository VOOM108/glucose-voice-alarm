// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import de.giesen.camaps.companion.prototype.alarm.*;
import de.giesen.camaps.companion.prototype.alarm.AlarmEngine.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Synthetic editor settings and isolated files only. */
public class LevelSettingsTest {
    private AlarmLevel level(String id,Kind kind,String threshold) {
        return LevelForm.parse(id,kind,"Teststufe "+id,threshold,"3","4","7",false,true,false,AlarmLevel.DEFAULT_TONE,true,0,1440);
    }
    private void invalid(Runnable action) {
        try { action.run(); fail("Expected validation rejection"); } catch (IllegalArgumentException expected) { /* Required. */ }
    }
    private interface FileCheck { void run(LevelStore store,Path folder) throws Exception; }
    private void fileTest(FileCheck check) throws Exception {
        Path folder=Files.createTempDirectory("level-settings-synthetic-");
        try { check.run(new LevelStore(folder.toFile()),folder); }
        finally { Files.deleteIfExists(folder.resolve(LevelStore.FILE_NAME+".new")); Files.deleteIfExists(folder.resolve(LevelStore.FILE_NAME)); Files.delete(folder); }
    }
    @Test public void allFiveKindsHaveEditableSettingsWithoutSeededRules() {
        for (Kind kind:Kind.values()) {
            AlarmLevel level=level(kind.name(),kind,LevelForm.isRate(kind)?"1,5":"123");
            assertEquals(kind,level.rule.kind); assertFalse(level.rule.enabled);
            assertEquals(LevelForm.hasRepeat(kind)?4:0,level.rule.outputs.repeatMinutes);
            assertEquals(LevelForm.hasSnooze(kind)?7:0,level.rule.outputs.snoozeMinutes);
            assertEquals(kind==Kind.PERSISTENT_HIGH?3:0,level.rule.durationMinutes);
        }
    }
    @Test public void commaAndPointRatesAreEquivalent() {
        assertEquals(level("a",Kind.RISING,"1,25").rule.threshold,level("b",Kind.RISING,"1.25").rule.threshold,0);
        assertEquals("1,25",LevelForm.number(1.25)); assertEquals("123",LevelForm.number(123));
    }
    @Test public void glucoseUsesWholeMgdlWithoutMedicalBounds() {
        assertEquals(33,level("a",Kind.LOW,"33").rule.threshold,0);
        assertEquals(450,level("b",Kind.HIGH,"450").rule.threshold,0);
        invalid(() -> level("a",Kind.HIGH,"123,5"));
    }
    @Test public void emptyNegativeUnitsAndNonfiniteNumbersAreRejected() {
        for (String text:new String[]{"","0","-2","NaN","Infinity","2e3","123 mg/dl","1,2.3"}) {
            invalid(() -> level("a",Kind.HIGH,text)); invalid(() -> level("a",Kind.RISING,text));
        }
    }
    @Test public void disabledStagesDoNotAllowInvalidHiddenThresholds() {
        invalid(() -> LevelForm.parse("a",Kind.HIGH,"Test","","","4","7",false,false,false,AlarmLevel.DEFAULT_TONE,true,0,1440));
    }
    @Test public void durationIsRequiredOnlyForPersistentHigh() {
        invalid(() -> LevelForm.parse("p",Kind.PERSISTENT_HIGH,"Test","123","","4","7",false,false,false,AlarmLevel.DEFAULT_TONE,true,0,1440));
        assertEquals(0,LevelForm.parse("h",Kind.HIGH,"Test","123","","4","7",false,false,false,AlarmLevel.DEFAULT_TONE,true,0,1440).rule.durationMinutes);
    }
    @Test public void repeatSnoozeMustBeExplicitPositiveWholeMinutes() {
        for (String bad:new String[]{"","0","-1","1,5","35792","99999999999"}) {
            invalid(() -> LevelForm.parse("h",Kind.HIGH,"Test","123","",bad,"7",false,false,false,AlarmLevel.DEFAULT_TONE,true,0,1440));
            invalid(() -> LevelForm.parse("h",Kind.HIGH,"Test","123","","4",bad,false,false,false,AlarmLevel.DEFAULT_TONE,true,0,1440));
        }
    }
    @Test public void stageNameMustBePresentAndCannotHideControlCharacters() {
        invalid(() -> LevelForm.parse("a",Kind.HIGH," ","123","","4","7",false,false,false,AlarmLevel.DEFAULT_TONE,true,0,1440));
        invalid(() -> LevelForm.parse("a",Kind.HIGH,"Test\nName","123","","4","7",false,false,false,AlarmLevel.DEFAULT_TONE,true,0,1440));
    }
    @Test public void onlyLocalAndroidToneUrisAreAccepted() {
        for (String uri:new String[]{"https://example.invalid/sound","file:///secret","", "content://sound\nraw"})
            invalid(() -> new AlarmLevel(level("a",Kind.HIGH,"123").rule,"Test",uri));
        assertNotNull(new AlarmLevel(level("a",Kind.HIGH,"123").rule,"Test","android.resource://test/raw/tone"));
    }
    @Test public void enabledTogglePreservesEveryOtherSetting() {
        AlarmLevel before=level("a",Kind.RISING,"2,5"),after=before.withEnabled(true);
        assertTrue(after.rule.enabled); assertFalse(before.rule.enabled);
        assertEquals(before.rule.id,after.rule.id); assertEquals(before.rule.threshold,after.rule.threshold,0);
        assertEquals(before.toneUri,after.toneUri); assertEquals(before.name,after.name);
        assertTrue(after.rule.outputs.tone); assertFalse(after.rule.outputs.speech);
    }
    @Test public void timeWindowRequiresTwoDistinctChosenTimes() {
        invalid(() -> LevelForm.parse("a",Kind.HIGH,"Test","123","","4","7",false,false,false,AlarmLevel.DEFAULT_TONE,false,-1,-1));
        invalid(() -> LevelForm.parse("a",Kind.HIGH,"Test","123","","4","7",false,false,false,AlarmLevel.DEFAULT_TONE,false,300,300));
        assertFalse(LevelForm.parse("a",Kind.HIGH,"Test","123","","4","7",false,false,false,AlarmLevel.DEFAULT_TONE,false,1380,360).rule.allDay);
    }
    @Test public void freshStoreHasNoDefaultMedicalStages() throws Exception {
        fileTest((store,folder) -> { assertEquals(0,store.load().revision); assertTrue(store.load().levels.isEmpty()); assertFalse(Files.exists(folder.resolve(LevelStore.FILE_NAME))); });
    }
    @Test public void completeSettingsRoundTripAcrossStoreInstances() throws Exception {
        fileTest((store,folder) -> {
            AlarmLevel source=LevelForm.parse("stage",Kind.PERSISTENT_HIGH,"Eigene Teststufe","123","3","4","7",true,true,true,
                    "content://media/internal/audio/media/9",false,1380,360);
            store.save(0,Arrays.asList(source)); AlarmLevel restored=new LevelStore(folder.toFile()).load().find("stage");
            assertEquals(source.name,restored.name); assertEquals(source.toneUri,restored.toneUri);
            assertEquals(source.rule.kind,restored.rule.kind); assertEquals(source.rule.threshold,restored.rule.threshold,0);
            assertEquals(3,restored.rule.durationMinutes); assertTrue(restored.rule.enabled); assertTrue(restored.rule.outputs.tone); assertTrue(restored.rule.outputs.speech);
            assertEquals(4,restored.rule.outputs.repeatMinutes); assertEquals(0,restored.rule.outputs.snoozeMinutes);
            assertFalse(restored.rule.allDay); assertEquals(1380,restored.rule.startMinute); assertEquals(360,restored.rule.endMinute);
        });
    }
    @Test public void editingAndDeletingPreserveUnrelatedStages() throws Exception {
        fileTest((store,folder) -> {
            AlarmLevel a=level("a",Kind.HIGH,"123"),b=level("b",Kind.HIGH,"145");
            LevelStore.Snapshot first=store.save(0,Arrays.asList(a,b));
            LevelStore.Snapshot updated=store.save(first.revision,Arrays.asList(a.withEnabled(true),b));
            assertTrue(updated.find("a").rule.enabled); assertFalse(updated.find("b").rule.enabled);
            assertEquals(145,updated.find("b").rule.threshold,0);
            store.save(updated.revision,Arrays.asList(b)); assertNull(store.load().find("a")); assertNotNull(store.load().find("b"));
        });
    }
    @Test public void staleSaveCannotOverwriteAnotherEditor() throws Exception {
        fileTest((store,folder) -> {
            LevelStore.Snapshot old=store.load(); store.save(old.revision,Arrays.asList(level("a",Kind.HIGH,"123")));
            byte[] before=Files.readAllBytes(folder.resolve(LevelStore.FILE_NAME));
            try { new LevelStore(folder.toFile()).save(old.revision,Collections.emptyList()); fail(); } catch (LevelStore.Conflict expected) {}
            assertArrayEquals(before,Files.readAllBytes(folder.resolve(LevelStore.FILE_NAME)));
        });
    }
    @Test public void duplicateIdsAndTooManyStagesCannotDamageExistingFile() throws Exception {
        fileTest((store,folder) -> {
            AlarmLevel a=level("a",Kind.HIGH,"123"); store.save(0,Arrays.asList(a));
            byte[] before=Files.readAllBytes(folder.resolve(LevelStore.FILE_NAME));
            invalid(() -> { try { store.save(1,Arrays.asList(a,a)); } catch (IOException e) { throw new AssertionError(e); } });
            List<AlarmLevel> excess=new ArrayList<>(); for (int i=0;i<=LevelStore.MAX_LEVELS;i++) excess.add(level("s"+i,Kind.HIGH,"123"));
            invalid(() -> { try { store.save(1,excess); } catch (IOException e) { throw new AssertionError(e); } });
            assertArrayEquals(before,Files.readAllBytes(folder.resolve(LevelStore.FILE_NAME)));
        });
    }
    @Test public void bitCorruptionIsDetectedWithoutReplacingTheFile() throws Exception {
        fileTest((store,folder) -> {
            store.save(0,Arrays.asList(level("a",Kind.HIGH,"123")));
            byte[] damaged=Files.readAllBytes(folder.resolve(LevelStore.FILE_NAME)); damaged[damaged.length/2]^=1;
            Files.write(folder.resolve(LevelStore.FILE_NAME),damaged);
            try { store.load(); fail(); } catch (IOException expected) {}
            try { store.save(1,Collections.emptyList()); fail(); } catch (IOException expected) {}
            assertArrayEquals(damaged,Files.readAllBytes(folder.resolve(LevelStore.FILE_NAME)));
        });
    }
    @Test public void truncatedAndOversizedArchivesAreRejected() throws Exception {
        fileTest((store,folder) -> {
            Files.write(folder.resolve(LevelStore.FILE_NAME),new byte[]{1,2,3});
            try { store.load(); fail(); } catch (IOException expected) {}
            Files.write(folder.resolve(LevelStore.FILE_NAME),new byte[LevelStore.MAX_ARCHIVE_BYTES+1]);
            try { store.load(); fail(); } catch (IOException expected) {}
        });
    }
    @Test public void leftoverTemporaryFileDoesNotReplaceCommittedSettings() throws Exception {
        fileTest((store,folder) -> {
            store.save(0,Arrays.asList(level("a",Kind.HIGH,"123")));
            Files.write(folder.resolve(LevelStore.FILE_NAME+".new"),new byte[]{9,8,7});
            assertNotNull(store.load().find("a"));
            store.save(1,Arrays.asList(level("b",Kind.LOW,"80")));
            assertNotNull(store.load().find("b")); assertFalse(Files.exists(folder.resolve(LevelStore.FILE_NAME+".new")));
        });
    }
    @Test public void restoredStagesCanFeedTheImportedKernelWithoutPlayingAnything() throws Exception {
        fileTest((store,folder) -> {
            store.save(0,Arrays.asList(level("h1",Kind.HIGH,"100").withEnabled(true),level("h2",Kind.HIGH,"130").withEnabled(true)));
            AlarmEngine engine=new AlarmEngine(store.load().rules(),new HistoryAnalysis.Limits(1,60000,60000),OutputMode.TONES_ONLY);
            Result result=engine.evaluate(Arrays.asList(new HistoryAnalysis.Point(140,10000000L)),10000000L,720,null,null);
            assertEquals("h2",result.selectedThresholdRule); assertEquals(2,result.matchedRules()); assertFalse(result.platformDeliveryEnabled);
        });
    }
    @Test public void deviceSelfTestNeverTouchesTheRealArchive() throws Exception {
        fileTest((store,folder) -> {
            store.save(0,Arrays.asList(level("a",Kind.HIGH,"123"))); byte[] original=Files.readAllBytes(folder.resolve(LevelStore.FILE_NAME));
            assertEquals(6,LevelStoreSelfTest.run(folder.toFile())); assertArrayEquals(original,Files.readAllBytes(folder.resolve(LevelStore.FILE_NAME)));
            try (java.util.stream.Stream<Path> children=Files.list(folder)) { assertEquals(1,children.count()); }
        });
    }
    @Test public void editorDiagnosticDoesNotExposeSavedSettings() {
        EditorDiagnostics.passed=6; String report=EditorDiagnostics.report("0.5.0-probe");
        assertTrue(report.contains("6/6")); assertTrue(report.contains("Live-Alarme: deaktiviert"));
        assertFalse(report.contains("123")); assertFalse(report.contains("Teststufe")); assertFalse(report.contains("content://"));
    }
    @Test public void rateRulesDoNotRequireRepeatOrSnoozeAndIgnoreFormerFields() {
        for (Kind kind:new Kind[]{Kind.RISING,Kind.FALLING}) {
            AlarmLevel empty=LevelForm.parse("rate",kind,"Test","2","","","",true,true,true,AlarmLevel.DEFAULT_TONE,true,0,1440);
            assertEquals(0,empty.rule.outputs.repeatMinutes); assertEquals(0,empty.rule.outputs.snoozeMinutes);
            AlarmLevel legacy=LevelForm.parse("rate",kind,"Test","2","","invalid","invalid",true,true,true,AlarmLevel.DEFAULT_TONE,true,0,1440);
            assertEquals(0,legacy.rule.outputs.repeatMinutes); assertEquals(0,legacy.rule.outputs.snoozeMinutes);
        }
    }
    @Test public void persistentHighRequiresRepeatButDoesNotRequireSnooze() {
        AlarmLevel persistent=LevelForm.parse("p",Kind.PERSISTENT_HIGH,"Test","123","3","4","",true,true,true,AlarmLevel.DEFAULT_TONE,true,0,1440);
        assertEquals(4,persistent.rule.outputs.repeatMinutes); assertEquals(0,persistent.rule.outputs.snoozeMinutes);
        invalid(() -> LevelForm.parse("p",Kind.PERSISTENT_HIGH,"Test","123","3","","",true,true,true,AlarmLevel.DEFAULT_TONE,true,0,1440));
    }
    @Test public void oldSavedRateTimesStayReadableButAreNotProjectedAsOutputSettings() throws Exception {
        fileTest((store,folder) -> {
            AlarmLevel legacy=new AlarmLevel(new Rule("old",Kind.RISING,true,2,0,true,0,1440,new Outputs(true,true,4,7)),"Alte Teststufe",AlarmLevel.DEFAULT_TONE);
            store.save(0,Arrays.asList(legacy)); LevelStore.Snapshot loaded=store.load();
            assertEquals(4,loaded.find("old").rule.outputs.repeatMinutes); assertEquals(7,loaded.find("old").rule.outputs.snoozeMinutes);
            assertEquals(0,loaded.rules().get(0).outputs.repeatMinutes); assertEquals(0,loaded.rules().get(0).outputs.snoozeMinutes);
            assertTrue(loaded.rules().get(0).enabled); assertEquals(2,loaded.rules().get(0).threshold,0);
        });
    }
    @Test public void correctedRateSettingsRoundTripWithNoTimingParameters() throws Exception {
        fileTest((store,folder) -> {
            AlarmLevel rate=LevelForm.parse("rate",Kind.FALLING,"Test","2,5","","","",false,true,false,AlarmLevel.DEFAULT_TONE,true,0,1440);
            store.save(0,Arrays.asList(rate)); AlarmLevel restored=new LevelStore(folder.toFile()).load().find("rate");
            assertEquals(0,restored.rule.outputs.repeatMinutes); assertEquals(0,restored.rule.outputs.snoozeMinutes);
            assertEquals(2.5,restored.rule.threshold,0); assertFalse(restored.rule.enabled);
            assertFalse(LevelForm.timingSummary(restored.rule).contains("Min."));
        });
    }
}
