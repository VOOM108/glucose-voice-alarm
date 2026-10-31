// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ProfileVibrationTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private AlarmLevel level() {
        AlarmLevel base=new SyntheticAlarmScenario(AlarmEngine.Kind.HIGH).level;
        AlarmEngine.Rule r=base.rule;
        return new AlarmLevel(new AlarmEngine.Rule(r.id,r.kind,true,r.threshold,0,true,0,1440,new AlarmEngine.Outputs(false,false,3,5)),"Synthetische Stufe",base.toneUri,Collections.singletonList("Test {wert}"),VibrationCatalog.LONG_SHORT.name());
    }
    private OutputPreferences vibration() { return new OutputPreferences(AlarmEngine.OutputMode.TONES_ONLY,3,"{wert} {trend}",0,0,7,4,new TrendSpeech(Arrays.asList("a","b","ruhig","d","e")),false,false,false,true); }
    private ConfigurationSnapshot config() { return new ConfigurationSnapshot(new LevelStore.Snapshot(10,Collections.singletonList(level())),new OutputPreferenceStore.Snapshot(12,vibration()),"DARK","klar",false,false); }
    @Test public void vibrationAloneIsAValidRouteAndAllChannelsOffIsRejected() {
        OutputPreferences p=vibration(); OutputRequirements.require(Collections.singletonList(level()),p); assertTrue(p.alarmVibrationAllowed(level()));
        assertFalse(p.alarmToneAllowed(level())); assertFalse(p.alarmSpeechAllowed(level()));
        try { p.withChannels(false,false,false,false); fail(); } catch(IllegalArgumentException expected) { }
        AlarmLevel missing=new AlarmLevel(level().rule,level().name,level().toneUri);
        assertSame(missing,OutputRequirements.missingTone(Collections.singletonList(missing),p));
    }
    @Test public void patternsAreFiniteAndDisablingALevelKeepsItsSelectedPattern() {
        assertEquals(6,VibrationCatalog.values().length); for(VibrationCatalog pattern:VibrationCatalog.values()) {
            long[] times=pattern.timings(); assertEquals(0,times[0]); assertTrue(times.length==2 || times.length==4); assertTrue(pattern.duration()<=1080);
            times[1]=999; assertTrue(pattern.timings()[1]<=450);
        }
        assertEquals(level().vibrationPattern,level().withEnabled(false).vibrationPattern);
    }
    @Test public void newStorageRoundtripKeepsVibrationWithoutChangingRuleConditions() throws Exception {
        File dir=temporary.newFolder(); LevelStore levels=new LevelStore(dir); OutputPreferenceStore output=new OutputPreferenceStore(dir);
        levels.save(0,Collections.singletonList(level())); output.save(0,vibration());
        assertEquals(level().vibrationPattern,levels.load().levels.get(0).vibrationPattern); assertTrue(output.load().preferences.vibrationEnabled);
        assertEquals(level().rule.threshold,levels.load().levels.get(0).rule.threshold,0); assertEquals("ruhig",output.load().preferences.trendSpeech.words.get(2));
    }
    @Test public void previousSchemasRemainReadableWithoutEnablingVibrationOrRewritingFiles() throws Exception {
        File dir=temporary.newFolder(); AlarmLevel plain=new AlarmLevel(level().rule,level().name,level().toneUri);
        byte[] levels=ConfigurationFiles.open(LevelStore.encode(new LevelStore.Snapshot(8,Collections.singletonList(plain))),LevelStore.MAX_ARCHIVE_BYTES);
        levels=Arrays.copyOf(levels,levels.length-3); // Schema 2 ends before the new empty pattern UTF.
        int header=2+"XCAM_ALARM_LEVELS".length(); levels[header+3]=2; byte[] legacyLevels=ConfigurationFiles.seal(levels);
        Path levelPath=new File(dir,LevelStore.FILE_NAME).toPath(); Files.write(levelPath,legacyLevels);
        byte[] legacyOutput=LegacyOutputFixture.encode(5,9,OutputPreferences.defaults(),"CAMAPS","CAMAPS");
        Path outputPath=new File(dir,OutputPreferenceStore.FILE_NAME).toPath(); Files.write(outputPath,legacyOutput);
        assertNull(new LevelStore(dir).load().levels.get(0).vibrationPattern); assertFalse(new OutputPreferenceStore(dir).load().preferences.vibrationEnabled);
        assertArrayEquals(legacyLevels,Files.readAllBytes(levelPath)); assertArrayEquals(legacyOutput,Files.readAllBytes(outputPath));
    }
    @Test public void profileHasFiveIndependentNamedSlotsAndRejectsStaleWriters() throws Exception {
        ProfileStore store=new ProfileStore(temporary.newFolder()); ProfileStore.Snapshot snapshot=store.load(); assertEquals(5,snapshot.slots.size());
        for(int slot=0;slot<5;slot++) snapshot=store.save(snapshot.revision,slot,new ProfileStore.Entry("Profil "+slot,config()));
        assertEquals("Profil 4",store.load().slots.get(4).name);
        try { store.save(0,0,null); fail(); } catch(IOException expected) { }
        try { store.save(snapshot.revision,5,null); fail(); } catch(IllegalArgumentException expected) { }
        store.save(snapshot.revision,2,null); assertNull(store.load().slots.get(2)); assertNotNull(store.load().slots.get(1));
    }
    @Test public void profileRoundtripIncludesEveryConfigurationGroup() throws Exception {
        ConfigurationSnapshot restored=ConfigurationSnapshot.decode(config().encode());
        assertEquals("DARK",restored.appearance); assertEquals("klar",restored.soundSet); assertFalse(restored.nativeDisplay); assertFalse(restored.publicValue);
        assertEquals(level().speechVariants,restored.levels.levels.get(0).speechVariants); assertEquals(level().vibrationPattern,restored.levels.levels.get(0).vibrationPattern);
        assertEquals(4,restored.output.preferences.bellBoostDb); assertEquals(7,restored.output.preferences.valueQuietMinutes); assertTrue(restored.output.preferences.vibrationEnabled);
        assertEquals(vibration().trendSpeech.words,restored.output.preferences.trendSpeech.words);
    }
    @Test public void corruptProfilesArePreservedAndCannotBeOverwrittenAsEmpty() throws Exception {
        File dir=temporary.newFolder(); ProfileStore store=new ProfileStore(dir); store.save(0,0,new ProfileStore.Entry("Test",config()));
        Path file=new File(dir,ProfileStore.FILE_NAME).toPath(); byte[] bytes=Files.readAllBytes(file); bytes[35]^=1; Files.write(file,bytes);
        try { store.load(); fail(); } catch(IOException expected) { }
        try { store.save(1,0,null); fail(); } catch(IOException expected) { }
        assertArrayEquals(bytes,Files.readAllBytes(file));
    }
    @Test public void interruptedProfileApplyRecoversBothFilesAndRejectsEditorsUntilCompleted() throws Exception {
        File dir=temporary.newFolder(); LevelStore levels=new LevelStore(dir); OutputPreferenceStore output=new OutputPreferenceStore(dir);
        levels.save(0,Collections.emptyList()); output.save(0,OutputPreferences.defaults());
        SettingsTransaction.begin(dir,config());
        // Simulate a process dying after replacing the first file, before replacing the second.
        ConfigurationSnapshot pending=ConfigurationSnapshot.decode(Files.readAllBytes(new File(dir,SettingsTransaction.FILE_NAME).toPath()));
        ConfigurationFiles.write(new File(dir,LevelStore.FILE_NAME),LevelStore.encode(pending.levels));
        assertTrue(output.load().preferences.vibrationEnabled); assertEquals(level().name,levels.load().levels.get(0).name);
        assertEquals(2,levels.load().revision); assertEquals(2,output.load().revision);
        try { levels.save(2,Collections.emptyList()); fail(); } catch(IOException expected) { }
        try { output.save(2,OutputPreferences.defaults()); fail(); } catch(IOException expected) { }
        SettingsTransaction.complete(dir); levels.save(2,Collections.emptyList());
        try { output.save(1,OutputPreferences.defaults()); fail(); } catch(IOException expected) { }
        assertTrue(output.load().preferences.vibrationEnabled);
    }
    @Test public void corruptPendingJournalBlocksReadsWithoutReplacingEitherLiveFile() throws Exception {
        File dir=temporary.newFolder(); LevelStore levels=new LevelStore(dir); OutputPreferenceStore output=new OutputPreferenceStore(dir);
        levels.save(0,Collections.emptyList()); output.save(0,OutputPreferences.defaults());
        byte[] beforeLevels=Files.readAllBytes(new File(dir,LevelStore.FILE_NAME).toPath()),beforeOutput=Files.readAllBytes(new File(dir,OutputPreferenceStore.FILE_NAME).toPath());
        SettingsTransaction.begin(dir,config()); Path pending=new File(dir,SettingsTransaction.FILE_NAME).toPath(); byte[] bytes=Files.readAllBytes(pending); bytes[40]^=1; Files.write(pending,bytes);
        try { levels.load(); fail(); } catch(IOException expected) { }
        try { output.load(); fail(); } catch(IOException expected) { }
        assertArrayEquals(beforeLevels,Files.readAllBytes(new File(dir,LevelStore.FILE_NAME).toPath())); assertArrayEquals(beforeOutput,Files.readAllBytes(new File(dir,OutputPreferenceStore.FILE_NAME).toPath()));
    }
    @Test public void vibrationOnlyUsesTheSameDurableAlarmClaimAndSnooze() throws Exception {
        SyntheticAlarmScenario scenario=new SyntheticAlarmScenario(AlarmEngine.Kind.HIGH); List<AlarmOutputFlow.Plan> plans=new ArrayList<>();
        AlarmOutputFlow flow=new AlarmOutputFlow(new AlarmStateStore(temporary.newFolder()),Collections.singletonList(level()),SyntheticAlarmScenario.limits(),vibration(),new AlarmOutputFlow.Port() {
            public boolean available(AlarmOutputFlow.Plan plan) { return true; } public boolean start(AlarmOutputFlow.Plan plan) { plans.add(plan); return true; } public void stop() { }
        });
        assertEquals(AlarmOutputFlow.Outcome.ACCEPTED,flow.evaluate(AlarmOutputFlow.Origin.SYNTHETIC,scenario.points(0,true),scenario.clock(0),720,null,NotificationParser.Trend.FLAT));
        assertEquals(AlarmOutputFlow.Outcome.ACCEPTED,flow.alarm(level().rule.id,scenario.clock(0))); assertNull(plans.get(0).toneUri); assertNull(plans.get(0).speech); assertEquals(level().vibrationPattern,plans.get(0).vibrationPattern);
        assertEquals(AlarmOutputFlow.Outcome.NOT_DUE,flow.alarm(level().rule.id,scenario.clock(1000)));
        assertEquals(AlarmOutputFlow.Outcome.ACCEPTED,flow.snooze(level().rule.id,5,scenario.clock(1000))); assertEquals(1,plans.size());
    }
}
