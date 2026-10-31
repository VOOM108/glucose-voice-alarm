// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.util.*;
import java.io.*;
import org.junit.*;
import static org.junit.Assert.*;

public class TrendSpeechToggleTest {
    private static final NotificationParser.Trend[] TRENDS={NotificationParser.Trend.UP_RIGHT,NotificationParser.Trend.UP,NotificationParser.Trend.FLAT,NotificationParser.Trend.DOWN_RIGHT,NotificationParser.Trend.DOWN,NotificationParser.Trend.DOUBLE_UP,NotificationParser.Trend.DOUBLE_DOWN};
    @Test public void eachWordCanBeMutedWithoutLosingTextOrLeavingAnEmptyTrendSentence() {
        for(String language:new String[]{"de","en"}) for(int index=0;index<TrendSpeech.COUNT;index++) {
            TrendSpeech defaults=TrendSpeech.defaults(language);List<Boolean> enabled=new ArrayList<>(defaults.enabled);enabled.set(index,false);
            TrendSpeech muted=new TrendSpeech(defaults.words,enabled);assertEquals(defaults.words,muted.words);assertEquals("",muted.word(TRENDS[index]));
            SpeechTemplate.Values values=new SpeechTemplate.Values(123,TRENDS[index],10,0,muted,language);
            String spoken=SpeechTemplate.render(SpeechTemplate.valueDefault(language),null,values,true,false);assertTrue(spoken.contains("123"));assertFalse(spoken.contains("Trend"));assertFalse(spoken.contains(defaults.word(TRENDS[index])));
            assertEquals("123.",SpeechTemplate.render("{wert}. {trend}.",null,values,true,false));
            String manual=SpeechPolicy.observationText(123,TRENDS[index],10,muted,language);assertTrue(manual.contains("123"));assertFalse(manual.contains("Trend"));
            assertEquals(defaults.word(TRENDS[index]),new TrendSpeech(muted.words).word(TRENDS[index]));
            for(int other=0;other<TrendSpeech.COUNT;other++) if(other!=index)assertEquals(defaults.word(TRENDS[other]),muted.word(TRENDS[other]));
        }
    }
    @Test public void allMutedFlagsPersistThroughProfilesLanguageAndChannelChanges() throws Exception {
        TrendSpeech muted=new TrendSpeech(TrendSpeech.defaults().words,Collections.nCopies(7,false));
        OutputPreferences p=OutputPreferences.defaults().withTrendSpeech(muted).withSpeechLanguage("en").withChannels(true,true,false,true).withGap(GapSettings.defaults().withTemplate("Own gap {minutes}"));
        ConfigurationSnapshot config=new ConfigurationSnapshot(new LevelStore.Snapshot(1,Collections.emptyList()),new OutputPreferenceStore.Snapshot(2,p),"DARK","samt",true,true);
        OutputPreferences saved=ConfigurationSnapshot.decode(config.encode()).output.preferences;
        assertEquals(muted.enabled,saved.trendSpeech.enabled);assertEquals(muted.words,saved.trendSpeech.words);assertEquals("en",saved.speechLanguage);assertTrue(saved.alarmSpeechEnabled);
        AlarmLevel level=new SyntheticAlarmScenario(AlarmEngine.Kind.FALLING).level;
        assertTrue(SpeechTemplate.render(level.speechText(0,"en"),level,new SpeechTemplate.Values(123,NotificationParser.Trend.DOWN,0,0,muted,"en"),false,false).contains("Critical fall rate"));
    }
    @Test public void previousSchemaNineDefaultsToAllEnabledWithoutRewritingBytes() throws Exception {
        byte[] old=LegacyOutputFixture.encode(9,3,OutputPreferences.defaults(),"CAMAPS","CAMAPS"),copy=old.clone();
        OutputPreferences saved=OutputPreferenceStore.decode(old).preferences;assertEquals(Collections.nCopies(7,true),saved.trendSpeech.enabled);assertArrayEquals(copy,old);
    }
    @Test public void switchListsAreValidatedAndDefensivelyCopied() {
        List<Boolean> flags=new ArrayList<>(Collections.nCopies(7,true));TrendSpeech words=new TrendSpeech(TrendSpeech.defaults().words,flags);flags.set(0,false);assertTrue(words.enabled.get(0));
        try {words.enabled.set(0,false);fail();}catch(UnsupportedOperationException expected){}
        for(List<Boolean> invalid:Arrays.asList(Collections.<Boolean>emptyList(),Arrays.asList(true,true,true,true,true,true,null)))try {new TrendSpeech(words.words,invalid);fail();}catch(IllegalArgumentException expected){}
    }
}
