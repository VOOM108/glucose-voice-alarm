// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class TrendDisplayWordsTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    @After public void resetLanguage() {AppText.configure(Locale.GERMANY);}
    @Test public void displayOverridesAreLiteralAndLanguageFallbackDoesNotUseSpeechWords() {
        List<String> entries=new ArrayList<>(Arrays.asList("a","b","Ruhig","d","e","f","g"));
        TrendDisplayWords display=new TrendDisplayWords(entries);entries.set(2,"different");
        AppText.configure(Locale.GERMANY);
        for(int i=0;i<7;i++)assertEquals(display.overrides.get(i),display.title(TrendDisplayWords.TRENDS.get(i)));
        assertEquals("stabil",TrendSpeech.defaults().word(NotificationParser.Trend.FLAT));
        assertEquals("Sehr schnell steigend",TrendDisplayWords.defaults().title(NotificationParser.Trend.DOUBLE_UP));
        assertEquals("Sehr schnell fallend",TrendDisplayWords.defaults().title(NotificationParser.Trend.DOUBLE_DOWN));
        AppText.configure(Locale.ENGLISH);
        assertEquals("Ruhig",display.title(NotificationParser.Trend.FLAT));
        assertEquals("Rising very fast",TrendDisplayWords.defaults().title(NotificationParser.Trend.DOUBLE_UP));
        assertEquals("Falling very fast",TrendDisplayWords.defaults().title(NotificationParser.Trend.DOUBLE_DOWN));
        assertEquals("rising very fast",TrendSpeech.defaults("en").word(NotificationParser.Trend.DOUBLE_UP));
        assertEquals("sehr schnell fallend",TrendSpeech.defaults("de").word(NotificationParser.Trend.DOUBLE_DOWN));
        assertEquals("Trend unmapped",display.title(NotificationParser.Trend.UNKNOWN));
        try {display.overrides.set(0,"x");fail();}catch(UnsupportedOperationException expected){}
    }
    @Test public void displayValidationAllowsDefaultButRejectsLongMultilineAndTemplateContent() {
        assertEquals(Collections.nCopies(7,""),new TrendDisplayWords(Collections.nCopies(7,"   ")).overrides);
        assertEquals(40,new TrendDisplayWords(Collections.nCopies(7,String.join("",Collections.nCopies(40,"x")))).overrides.get(0).length());
        for(String bad:Arrays.asList(null,"line\nbreak","x\ty","{trend}",String.join("",Collections.nCopies(41,"x")))) {
            List<String> words=new ArrayList<>(Collections.nCopies(7,""));words.set(3,bad);
            try {new TrendDisplayWords(words);fail();}catch(IllegalArgumentException expected){}
        }
        try {new TrendDisplayWords(Collections.emptyList());fail();}catch(IllegalArgumentException expected){}
    }
    private ConfigurationSnapshot snapshot() {
        OutputPreferences speech=OutputPreferences.defaults().withTrendSpeech(new TrendSpeech(Arrays.asList("s1","s2","s3","s4","s5","s6","s7")));
        return new ConfigurationSnapshot(new LevelStore.Snapshot(1,Collections.emptyList()),new OutputPreferenceStore.Snapshot(1,speech),"DARK","samt",true,true,
                new TrendDisplayWords(Arrays.asList("v1","v2","v3","v4","v5","v6","v7")));
    }
    @Test public void profileSlotsAndInterruptedApplyKeepDisplayWordsSeparateFromSpeech() throws Exception {
        File directory=temporary.newFolder();ProfileStore store=new ProfileStore(directory);ConfigurationSnapshot source=snapshot();
        store.save(0,0,new ProfileStore.Entry("Test",source));ConfigurationSnapshot saved=store.load().slots.get(0).settings;
        assertEquals(source.displayWords.overrides,saved.displayWords.overrides);
        assertEquals(source.output.preferences.trendSpeech.words,saved.output.preferences.trendSpeech.words);
        SettingsTransaction.begin(directory,saved);ConfigurationSnapshot recovered=SettingsTransaction.recoverCore(directory);
        assertEquals(source.displayWords.overrides,recovered.displayWords.overrides);
        assertEquals(source.output.preferences.trendSpeech.words,new OutputPreferenceStore(directory).load().preferences.trendSpeech.words);
        assertTrue(SettingsTransaction.pending(directory));SettingsTransaction.complete(directory);assertFalse(SettingsTransaction.pending(directory));
    }
    @Test public void schemaOneProfilesRemainReadableWithoutMutatingTheirBytes() throws Exception {
        ConfigurationSnapshot source=snapshot();ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(bytes)) {
            out.writeUTF("XCAM_CONFIGURATION");out.writeInt(1);out.writeUTF("DARK");out.writeUTF("samt");out.writeBoolean(true);out.writeBoolean(true);
            byte[] levels=LevelStore.encode(source.levels),output=OutputPreferenceStore.encode(source.output);
            out.writeInt(levels.length);out.write(levels);out.writeInt(output.length);out.write(output);
        }
        byte[] old=ConfigurationFiles.seal(bytes.toByteArray()),copy=old.clone();ConfigurationSnapshot saved=ConfigurationSnapshot.decode(old);
        assertArrayEquals(copy,old);assertEquals(Collections.nCopies(7,""),saved.displayWords.overrides);
        assertEquals(source.output.preferences.trendSpeech.words,saved.output.preferences.trendSpeech.words);
    }
}
