// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.*;
import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;

public class BetaLocalizationTest {
    @Before public void before() { AppText.configure(Locale.GERMAN); }
    @After public void after() { AppText.configure(Locale.GERMAN); }
    @Test public void englishCatalogIsPackagedAndDoesNotTranslateTheAppNameOrUserText() {
        AppText.configure(Locale.ENGLISH); assertEquals("Output enabled",AppText.tr("Ausgabe aktiv")); assertEquals("Glucose Voice Alarm",AppText.tr("Glucose Voice Alarm")); assertEquals("Mein ganz eigener Text",AppText.tr("Mein ganz eigener Text"));
        AppText.configure(Locale.GERMAN); assertEquals("Ausgabe aktiv",AppText.tr("Ausgabe aktiv"));
    }
    @Test public void englishDefaultsUseEnglishTokensUnitsAndSyntheticPrefix() {
        SpeechTemplate.Values v=new SpeechTemplate.Values(123,NotificationParser.Trend.FLAT,5,10,TrendSpeech.defaults("en"),"en");
        String text=SpeechTemplate.render(SpeechTemplate.valueDefault("en"),null,v,true,true);
        assertTrue(text.startsWith("This is a test")); assertTrue(text.contains("123 milligrams per deciliter")); assertTrue(text.contains("steady")); assertFalse(text.contains("{value}"));
    }
    @Test public void bothTokenLanguagesRemainValidAndUnknownTrendIsNotSpoken() {
        SpeechTemplate.Values v=new SpeechTemplate.Values(123,NotificationParser.Trend.UNKNOWN,0,0,TrendSpeech.defaults("en"),"en");
        assertEquals("123 123",SpeechTemplate.render("{wert} {value}",null,v,true,false));
        String text=SpeechTemplate.render(SpeechTemplate.valueDefault("en"),null,v,true,false); assertFalse(text.contains("Trend")); assertFalse(text.contains("unknown"));
    }
    @Test public void allEnglishAlarmDefaultsRenderWithoutChangingStoredCustomVariants() {
        for(AlarmEngine.Kind kind:AlarmEngine.Kind.values()) {
            AlarmLevel base=new SyntheticAlarmScenario(kind).level;
            assertTrue(base.speechText(0,"en").startsWith(kind==AlarmEngine.Kind.RISING || kind==AlarmEngine.Kind.FALLING?"Critical":"The value"));
            AlarmLevel custom=new AlarmLevel(base.rule,base.name,base.toneUri,Collections.singletonList("Mein Alarm {wert}")); assertEquals("Mein Alarm {wert}",custom.speechText(0,"en"));
            SpeechTemplate.render(base.speechText(0,"en"),base,new SpeechTemplate.Values(123,NotificationParser.Trend.UP,0,10,TrendSpeech.defaults("en"),"en"),false,false);
        }
    }
    @Test public void newOutputSettingsKeepLanguageSevenWordsAndGapTextAcrossChannelChangesAndProfiles() throws Exception {
        GapSettings gap=GapSettings.defaults().withTemplate("Missing for {minutes}. {trend}");
        OutputPreferences p=new OutputPreferences(AlarmEngine.OutputMode.SPOKEN_ALARMS,0,"My own {value}",80,60,5,0,TrendSpeech.defaults("en")).withSpeechLanguage("en").withGap(gap).withChannels(true,true,false);
        OutputPreferences saved=OutputPreferenceStore.decode(OutputPreferenceStore.encode(new OutputPreferenceStore.Snapshot(4,p))).preferences;
        assertEquals("en",saved.speechLanguage); assertEquals(7,saved.trendSpeech.words.size()); assertEquals("My own {value}",saved.valueTemplate); assertEquals(gap.speechTemplate,saved.gap.speechTemplate);
        ConfigurationSnapshot config=new ConfigurationSnapshot(new LevelStore.Snapshot(1,Collections.emptyList()),new OutputPreferenceStore.Snapshot(4,p),"DARK","samt",true,true);
        assertEquals("en",ConfigurationSnapshot.decode(config.encode()).output.preferences.speechLanguage);
    }
    private byte[] legacy(int schema) throws Exception {
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        try(DataOutputStream o=new DataOutputStream(b)) {
            o.writeUTF("XCAM_OUTPUT"); o.writeInt(schema); o.writeLong(1); o.writeUTF(AlarmEngine.OutputMode.SPOKEN_ALARMS.name()); o.writeInt(0); o.writeUTF("Eigener alter Text {wert}");
            if(schema>=2) { o.writeInt(70);o.writeInt(60);o.writeInt(5); } if(schema>=3)o.writeInt(2);
            if(schema>=4) for(String word:Arrays.asList("auf","schnell auf","ruhig","ab","schnell ab"))o.writeUTF(word);
            if(schema>=5) { o.writeBoolean(true);o.writeBoolean(true);o.writeBoolean(false); } if(schema>=6)o.writeBoolean(false); if(schema>=7)GapSettings.defaults().write(o);
        }
        return ConfigurationFiles.seal(b.toByteArray());
    }
    @Test public void everyPreviousOutputSchemaRemainsGermanEvenInAnEnglishInterface() throws Exception {
        AppText.configure(Locale.ENGLISH);
        for(int schema=1;schema<=7;schema++) {
            OutputPreferences p=OutputPreferenceStore.decode(legacy(schema)).preferences;
            assertEquals("de",p.speechLanguage); assertEquals("Eigener alter Text {wert}",p.valueTemplate); assertEquals(7,p.trendSpeech.words.size()); assertEquals("doppelt aufwärts",p.trendSpeech.words.get(5));
        }
    }
    @Test public void doubleArrowsHaveIndependentSpeechAndLegacyFiveTextsArePreserved() {
        TrendSpeech old=new TrendSpeech(Arrays.asList("eins","zwei","drei","vier","fünf"));
        assertEquals("zwei",old.word(NotificationParser.Trend.UP)); assertEquals("doppelt aufwärts",old.word(NotificationParser.Trend.DOUBLE_UP)); assertEquals("doppelt abwärts",old.word(NotificationParser.Trend.DOUBLE_DOWN));
    }
    @Test public void gapTemplateAllowsOnlyGapContextAndNoCurrentReading() {
        for(String token:new String[]{"{wert}","{value}","{unit}","{threshold}"}) try { GapSpeechTemplate.validate(token); fail(token); } catch(IllegalArgumentException expected) {}
        assertEquals("Gap 4 minutes.",GapSpeechTemplate.render("Gap {minutes} minutes. {trend} {status}","en",4,"",""));
        assertTrue(GapSpeechTemplate.render("","en",4,"Last trend: falling fast.","").contains("Current value unknown"));
    }
}
