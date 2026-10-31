// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import de.giesen.camaps.companion.prototype.alarm.*;
import de.giesen.gva.watch.ArrowColor;
import java.io.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
public class ColoredTrendWordsTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    @Before public void german(){AppText.configure(Locale.GERMANY);}
    @After public void reset(){AppText.configure(Locale.GERMANY);}
    private TrendColorOverrides custom(boolean enabled){return new TrendColorOverrides(Arrays.asList("r0","r1","rot eigen","r3","r4","r5","r6"),Collections.nCopies(7,enabled),Arrays.asList("y0","y1","gelb eigen","y3","y4","y5","y6"),Collections.nCopies(7,enabled));}
    @Test public void allColorsInheritEachNormalDefaultAndUserWordWithoutAFixedColorSuffix(){
        for(NotificationParser.Trend t:TrendDisplayWords.TRENDS)for(ArrowColor c:ArrowColor.values()){
            TrendDisplayWords display=TrendDisplayWords.defaults();assertEquals(display.title(t),display.title(t,c));
            TrendSpeech speech=TrendSpeech.defaults();assertEquals(speech.word(t),speech.word(t,c));
        }
        TrendDisplayWords display=new TrendDisplayWords(Collections.nCopies(7,"Eigene Basis"));TrendSpeech speech=new TrendSpeech(Collections.nCopies(7,"eigene Sprache"));
        for(ArrowColor c:ArrowColor.values()){assertEquals("Eigene Basis",display.title(NotificationParser.Trend.FLAT,c));assertEquals("eigene Sprache",speech.word(NotificationParser.Trend.FLAT,c));}
        AppText.configure(Locale.ENGLISH);assertEquals("Steady",TrendDisplayWords.defaults().title(NotificationParser.Trend.FLAT,ArrowColor.YELLOW));
    }
    @Test public void enabledColorWordsAreIndependentPerDirectionAndDisabledDraftsInherit(){
        TrendDisplayWords display=new TrendDisplayWords(Collections.nCopies(7,"Basis"),custom(true));
        for(int i=0;i<7;i++){NotificationParser.Trend t=TrendDisplayWords.TRENDS.get(i);assertEquals(custom(true).red.get(i),display.title(t,ArrowColor.RED));assertEquals(custom(true).yellow.get(i),display.title(t,ArrowColor.YELLOW));assertEquals("Basis",display.title(t));}
        TrendDisplayWords disabled=new TrendDisplayWords(Collections.nCopies(7,"Neue Basis"),custom(false));assertEquals("Neue Basis",disabled.title(NotificationParser.Trend.FLAT,ArrowColor.RED));assertEquals("rot eigen",disabled.colors.red.get(2));
    }
    @Test public void blankColorWordsFollowTheNormalWordAndUnknownRemainsUnknown(){
        TrendColorOverrides blank=new TrendColorOverrides(Collections.nCopies(7,""),Collections.nCopies(7,true),Collections.nCopies(7,""),Collections.nCopies(7,true));
        assertEquals("Stabil",new TrendDisplayWords(Collections.nCopies(7,""),blank).title(NotificationParser.Trend.FLAT,ArrowColor.RED));
        assertEquals("Trend offen",new TrendDisplayWords(Collections.nCopies(7,""),custom(true)).title(NotificationParser.Trend.UNKNOWN,ArrowColor.YELLOW));
        assertEquals("",new TrendSpeech(TrendSpeech.defaults().words,Collections.nCopies(7,true),custom(true)).word(NotificationParser.Trend.UNKNOWN,ArrowColor.RED));
    }
    @Test public void masterSpeechSwitchStillMutesEveryColorAndColorPlaceholderStaysSeparate(){
        TrendSpeech speech=new TrendSpeech(TrendSpeech.defaults().words,Collections.nCopies(7,true),custom(true));
        SpeechTemplate.Values v=new SpeechTemplate.Values(123,NotificationParser.Trend.FLAT,0,0,speech,"de",GlucoseUnit.MGDL,ArrowColor.RED);
        assertEquals("123 rot eigen rot",SpeechTemplate.render("{wert} {trend} {farbe}",null,v,true,false));
        assertEquals("123 rot eigen",SpeechTemplate.render("{wert} {trend}",null,v,true,false));
        speech=new TrendSpeech(speech.words,Collections.nCopies(7,false),speech.colors);
        assertEquals("",speech.word(NotificationParser.Trend.FLAT,ArrowColor.RED));
        assertEquals("Vor 0 Sekunden empfangener Wert: 123 Milligramm pro Deziliter.",SpeechPolicy.observationText(123,NotificationParser.Trend.FLAT,0,speech,"de",GlucoseUnit.MGDL,ArrowColor.RED));
    }
    @Test public void manualSpeechAndSyntheticPreviewUseTheSelectedColoredWord(){
        TrendSpeech speech=new TrendSpeech(TrendSpeech.defaults().words,Collections.nCopies(7,true),custom(true));
        assertTrue(SpeechPolicy.observationText(123,NotificationParser.Trend.FLAT,0,speech,"de",GlucoseUnit.MGDL,ArrowColor.YELLOW).endsWith("Trend: gelb eigen."));
        assertTrue(SyntheticSpeech.value("{wert} {trend} {farbe}",speech).endsWith("123 rot eigen rot"));
    }
    @Test public void immutableCopiesPreserveDormantTextAndFlags(){
        List<String> red=new ArrayList<>(Collections.nCopies(7,"r"));List<Boolean> enabled=new ArrayList<>(Collections.nCopies(7,true));
        TrendColorOverrides c=new TrendColorOverrides(red,enabled,red,enabled);red.set(0,"changed");enabled.set(0,false);
        assertEquals("r",c.red.get(0));assertTrue(c.redEnabled.get(0));try{c.yellow.set(0,"x");fail();}catch(UnsupportedOperationException expected){}
    }
    @Test public void allStoredColorTextIsValidatedEvenWhenItsCheckboxIsOff(){
        for(String bad:Arrays.asList("bad\ntext","bad\ttext","{wert}",String.join("",Collections.nCopies(81,"x")))){
            try{new TrendColorOverrides(Collections.nCopies(7,bad),Collections.nCopies(7,false),Collections.nCopies(7,""),Collections.nCopies(7,false));fail(bad);}catch(IllegalArgumentException expected){}
        }
        TrendColorOverrides longWord=new TrendColorOverrides(Collections.nCopies(7,String.join("",Collections.nCopies(41,"x"))),Collections.nCopies(7,true),Collections.nCopies(7,""),Collections.nCopies(7,false));
        new TrendSpeech(TrendSpeech.defaults().words,Collections.nCopies(7,true),longWord);
        try{new TrendDisplayWords(Collections.nCopies(7,""),longWord);fail();}catch(IllegalArgumentException expected){}
    }
    private ConfigurationSnapshot snapshot(){return new ConfigurationSnapshot(new LevelStore.Snapshot(1,Collections.emptyList()),new OutputPreferenceStore.Snapshot(1,OutputPreferences.defaults().withTrendSpeech(new TrendSpeech(TrendSpeech.defaults().words,Collections.nCopies(7,true),custom(true)))),"DARK","samt",true,true,new TrendDisplayWords(Collections.nCopies(7,"Anzeige"),custom(false)));}
    @Test public void speechColorSettingsSurviveChannelLanguageAndUnitChangesAndDiskRoundTrip()throws Exception{
        OutputPreferences p=snapshot().output.preferences.withChannels(true,true,false).withSpeechLanguage("en").withUnitMode(GlucoseUnit.Mode.MMOL);
        OutputPreferenceStore store=new OutputPreferenceStore(temp.newFolder());store.save(0,p);OutputPreferences loaded=store.load().preferences;
        assertEquals("rot eigen",loaded.trendSpeech.word(NotificationParser.Trend.FLAT,ArrowColor.RED));assertEquals("gelb eigen",loaded.trendSpeech.word(NotificationParser.Trend.FLAT,ArrowColor.YELLOW));assertEquals("en",loaded.speechLanguage);assertEquals(GlucoseUnit.Mode.MMOL,loaded.unitMode);
    }
    @Test public void profilesAndInterruptedTransactionKeepDistinctDisplayAndSpeechColorSettings()throws Exception{
        File folder=temp.newFolder();ConfigurationSnapshot source=snapshot();ProfileStore store=new ProfileStore(folder);store.save(0,0,new ProfileStore.Entry("Farbtest",source));ConfigurationSnapshot saved=store.load().slots.get(0).settings;
        assertEquals("Anzeige",saved.displayWords.title(NotificationParser.Trend.FLAT,ArrowColor.RED));assertEquals("rot eigen",saved.displayWords.colors.red.get(2));assertFalse(saved.displayWords.colors.redEnabled.get(2));
        assertEquals("rot eigen",saved.output.preferences.trendSpeech.word(NotificationParser.Trend.FLAT,ArrowColor.RED));
        SettingsTransaction.begin(folder,saved);ConfigurationSnapshot recovered=SettingsTransaction.recoverCore(folder);assertEquals(saved.displayWords.colors.red,recovered.displayWords.colors.red);assertEquals(saved.output.preferences.trendSpeech.colors.yellow,recovered.output.preferences.trendSpeech.colors.yellow);
    }
    @Test public void legacySchemaTwoDisplayProfileInheritsNormalTextWithoutRewritingInput()throws Exception{
        ConfigurationSnapshot source=snapshot();ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(bytes)){out.writeUTF("XCAM_CONFIGURATION");out.writeInt(2);out.writeUTF("DARK");out.writeUTF("samt");out.writeBoolean(true);out.writeBoolean(true);for(byte[] b:Arrays.asList(LevelStore.encode(source.levels),OutputPreferenceStore.encode(source.output))){out.writeInt(b.length);out.write(b);}for(String word:source.displayWords.overrides)out.writeUTF(word);}
        byte[] input=ConfigurationFiles.seal(bytes.toByteArray()),before=input.clone();ConfigurationSnapshot loaded=ConfigurationSnapshot.decode(input);assertArrayEquals(before,input);assertEquals("Anzeige",loaded.displayWords.title(NotificationParser.Trend.FLAT,ArrowColor.YELLOW));assertEquals(Collections.nCopies(7,false),loaded.displayWords.colors.yellowEnabled);
    }
    @Test public void legacySchemaElevenSpeechWordsRemainLiteralAndAllColorsInherit()throws Exception{
        OutputPreferences p=OutputPreferences.defaults();ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream o=new DataOutputStream(bytes)){o.writeUTF("XCAM_OUTPUT");o.writeInt(11);o.writeLong(1);o.writeUTF(p.mode.name());o.writeInt(0);o.writeUTF(p.valueTemplate);o.writeInt(100);o.writeInt(100);o.writeInt(5);o.writeInt(0);for(String word:p.trendSpeech.words.subList(0,5))o.writeUTF(word);o.writeBoolean(true);o.writeBoolean(true);o.writeBoolean(false);o.writeBoolean(false);p.gap.write(o);o.writeUTF("de");o.writeUTF(p.trendSpeech.words.get(5));o.writeUTF(p.trendSpeech.words.get(6));o.writeUTF(p.gap.speechTemplate);o.writeUTF("CAMAPS");o.writeUTF("CAMAPS");for(int i=0;i<7;i++)o.writeBoolean(true);o.writeUTF("AUTO");}
        byte[] input=ConfigurationFiles.seal(bytes.toByteArray()),before=input.clone();OutputPreferences loaded=OutputPreferenceStore.decode(input).preferences;assertArrayEquals(before,input);assertEquals("stabil",loaded.trendSpeech.word(NotificationParser.Trend.FLAT,ArrowColor.RED));assertEquals(Collections.nCopies(7,false),loaded.trendSpeech.colors.redEnabled);
    }
}
