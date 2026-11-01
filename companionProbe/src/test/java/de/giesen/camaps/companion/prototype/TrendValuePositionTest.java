// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import de.giesen.camaps.companion.prototype.alarm.*;
import de.giesen.gva.watch.ArrowColor;
import java.io.*;
import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;

public class TrendValuePositionTest {
    @Before public void language(){AppText.configure(Locale.GERMAN);}
    private TrendSpeech words(String text){return new TrendSpeech(Collections.nCopies(7,text));}
    private String render(String template,TrendSpeech words,NotificationParser.Trend trend,ArrowColor color,String language,GlucoseUnit unit){
        return SpeechTemplate.render(template,null,new SpeechTemplate.Values(123,trend,7,0,words,language,unit,color),true,false);
    }
    @Test public void literalTrendKeepsTheExistingLeadingValue(){
        assertEquals("Der Wert beträgt 123 Milligramm pro Deziliter. Trend: fallend.",render(SpeechTemplate.VALUE_DEFAULT,words("fallend"),NotificationParser.Trend.DOWN,ArrowColor.NORMAL,"de",GlucoseUnit.MGDL));
    }
    @Test public void nestedValueMovesOutOfTheLeadingClauseForEveryDirectionAndColor(){
        for(NotificationParser.Trend trend:TrendOrder.ALL)for(ArrowColor color:ArrowColor.values()){
            assertEquals("Trend: fallend bei 123.",render(SpeechTemplate.VALUE_DEFAULT,words("fallend bei {wert}"),trend,color,"de",GlucoseUnit.MGDL));
        }
    }
    @Test public void compactTemplateMovesTheValueAndKeepsTheOptionalColor(){
        assertEquals("fallend bei 123 rot",render("{wert} {trend} {farbe}",words("fallend bei {wert}"),NotificationParser.Trend.DOWN,ArrowColor.RED,"de",GlucoseUnit.MGDL));
        assertEquals("fallend bei 123 gelb",render("{value} {unit} {trend} {color}",words("fallend bei {value}"),NotificationParser.Trend.DOWN,ArrowColor.YELLOW,"de",GlucoseUnit.MGDL));
    }
    @Test public void independentTemplateSentencesAreRetained(){
        assertEquals("Hinweis! Trend: fällt bei 123. Alter 7 Sekunden. rot",render("Hinweis! Der Wert beträgt {wert} {einheit}. Trend: {trend}. Alter {alter_sekunden} Sekunden. {farbe}",words("fällt bei {wert}"),NotificationParser.Trend.DOWN,ArrowColor.RED,"de",GlucoseUnit.MGDL));
    }
    @Test public void customContextInTheValueClauseIsNotDiscarded(){
        assertEquals("Bitte prüfen. Trend: fallend bei 123.",render("Bitte prüfen: {wert} {einheit}. Trend: {trend}.",words("fallend bei {wert}"),NotificationParser.Trend.DOWN,ArrowColor.NORMAL,"de",GlucoseUnit.MGDL));
    }
    @Test public void englishAliasesAndConvertedUnitsAreUsedInsideThePhrase(){
        assertEquals("Trend: falling at 6.8 millimoles per liter. yellow",render(SpeechTemplate.valueDefault("en")+" {color}",words("falling at {value} {unit}"),NotificationParser.Trend.DOWN,ArrowColor.YELLOW,"en",GlucoseUnit.MMOL));
    }
    @Test public void unknownAndMutedDirectionsRetainTheLeadingValue(){
        for(TrendSpeech p:Arrays.asList(words("fallend bei {wert}"),new TrendSpeech(words("fallend bei {wert}").words,Collections.nCopies(7,false)))){
            assertEquals("Der Wert beträgt 123 Milligramm pro Deziliter.",render(SpeechTemplate.VALUE_DEFAULT,p,NotificationParser.Trend.UNKNOWN,ArrowColor.NORMAL,"de",GlucoseUnit.MGDL));
        }
        assertEquals("Der Wert beträgt 123 Milligramm pro Deziliter.",render(SpeechTemplate.VALUE_DEFAULT,new TrendSpeech(words("bei {wert}").words,Collections.nCopies(7,false)),NotificationParser.Trend.DOWN,ArrowColor.RED,"de",GlucoseUnit.MGDL));
    }
    @Test public void onlyTheActuallySelectedColorWordDeterminesValuePosition(){
        TrendColorOverrides colors=new TrendColorOverrides(Collections.nCopies(7,"rot bei {wert}"),Collections.nCopies(7,true),Collections.nCopies(7,"gelb bei {wert}"),Collections.nCopies(7,false));
        TrendSpeech p=new TrendSpeech(words("fallend").words,Collections.nCopies(7,true),colors);
        assertEquals("Trend: rot bei 123.",render(SpeechTemplate.VALUE_DEFAULT,p,NotificationParser.Trend.FLAT,ArrowColor.RED,"de",GlucoseUnit.MGDL));
        assertTrue(render(SpeechTemplate.VALUE_DEFAULT,p,NotificationParser.Trend.FLAT,ArrowColor.YELLOW,"de",GlucoseUnit.MGDL).startsWith("Der Wert beträgt 123"));
    }
    @Test public void manualValueSpeechUsesTheSamePositionAndKeepsReceiptAge(){
        assertEquals("Vor 7 Sekunden empfangen: bei 123.",SpeechPolicy.observationText(123,NotificationParser.Trend.FLAT,7,words("bei {wert}")));
    }
    @Test public void alarmFragmentsKeepTheAlarmWordingAndMoveTheValue(){
        AlarmLevel level=new SyntheticAlarmScenario(AlarmEngine.Kind.HIGH).level;
        String text=SpeechTemplate.render("Alarm bei {wert}. {trend}",level,new SpeechTemplate.Values(123,NotificationParser.Trend.FLAT,0,0,words("stabil bei {wert}")),false,false);
        assertEquals("Alarm. stabil bei 123",text);
    }
    @Test public void unsupportedNestedVariablesBracesAndControlCharactersAreRejected(){
        for(String bad:Arrays.asList("{trend}","{farbe}","{grenze}","{value", "{wert}}","bei\n{wert}",String.join("",Collections.nCopies(81,"x")))){
            try{words(bad);fail(bad);}catch(IllegalArgumentException expected){}
        }
        try{new TrendDisplayWords(Collections.nCopies(7,"{wert}"));fail();}catch(IllegalArgumentException expected){}
        TrendColorOverrides c=new TrendColorOverrides(Collections.nCopies(7,"{wert}"),Collections.nCopies(7,true),Collections.nCopies(7,""),Collections.nCopies(7,false));
        try{new TrendDisplayWords(Collections.nCopies(7,""),c);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void outputAndProfilesRoundTripValueWordsWithoutChangingLegacyIndices()throws Exception{
        List<String> labels=Arrays.asList("a {wert}","b {wert}","c {wert}","d {wert}","e {wert}","f {wert}","g {wert}");
        OutputPreferences p=OutputPreferences.defaults().withTrendSpeech(new TrendSpeech(labels));
        OutputPreferenceStore.Snapshot output=new OutputPreferenceStore.Snapshot(4,p);
        ConfigurationSnapshot cfg=new ConfigurationSnapshot(new LevelStore.Snapshot(1,Collections.emptyList()),output,"SYSTEM","samt",true,true);
        TrendSpeech loaded=ConfigurationSnapshot.decode(cfg.encode()).output.preferences.trendSpeech;
        assertEquals("f {wert}",loaded.word(NotificationParser.Trend.DOUBLE_UP));assertEquals("a {wert}",loaded.word(NotificationParser.Trend.UP_RIGHT));assertEquals("g {wert}",loaded.word(NotificationParser.Trend.DOUBLE_DOWN));
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(ConfigurationFiles.open(OutputPreferenceStore.encode(output),8192)))){in.readUTF();assertEquals(13,in.readInt());}
    }
    @Test public void historicalFormatDoesNotAcceptNewTokenPayloads()throws Exception{
        byte[] body=ConfigurationFiles.open(OutputPreferenceStore.encode(new OutputPreferenceStore.Snapshot(1,OutputPreferences.defaults().withTrendSpeech(words("bei {wert}")))),8192);
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(body))){in.readUTF();int at=body.length-in.available();body[at+3]=12;}
        try{OutputPreferenceStore.decode(ConfigurationFiles.seal(body));fail();}catch(IOException expected){}
    }
    @Test public void oneSharedPresentationOrderDoesNotChangeStoredIndices(){
        assertEquals(Arrays.asList(NotificationParser.Trend.DOUBLE_UP,NotificationParser.Trend.UP,NotificationParser.Trend.UP_RIGHT,NotificationParser.Trend.FLAT,NotificationParser.Trend.DOWN_RIGHT,NotificationParser.Trend.DOWN,NotificationParser.Trend.DOUBLE_DOWN),TrendOrder.ALL);
        assertEquals(5,TrendOrder.storedIndex(NotificationParser.Trend.DOUBLE_UP));assertEquals(0,TrendOrder.storedIndex(NotificationParser.Trend.UP_RIGHT));
    }
}
