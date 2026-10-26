// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;

public class GlucoseUnitTest {
    private static final long NOW=1800000000000L;
    private NotificationParser.Result parse(GlucoseUnit unit,String... text) {return NotificationParser.parse(Arrays.asList(text),Arrays.asList(text),unit);}
    @Before public void before(){AppText.configure(Locale.GERMAN);}
    @Test public void packageAllowlistDeclaresAllFiveKnownCamapsUnitsOnly(){
        for(String name:new String[]{"com.camdiab.fx_alert.mgdl","com.camdiab.fx_alert.hx.mgdl"})assertEquals(GlucoseUnit.MGDL,NotificationParser.packageUnit(name));
        for(String name:new String[]{"com.camdiab.fx_alert.mmoll","com.camdiab.fx_alert.hx.mmoll","com.camdiab.fx_alert.mmoll.ca"})assertEquals(GlucoseUnit.MMOL,NotificationParser.packageUnit(name));
        for(String name:new String[]{"com.camdiab.fx_alert","another.app","com.camdiab.fx_alert.mmoll.fake"})assertFalse(NotificationParser.acceptsPackage(name));
    }
    @Test public void decimalsCommaAndPointWithSeparateUnitFieldsKeepExactCanonicalPrecision(){
        for(String text:new String[]{"5.1 mmol/L →","5,1 mmol/l →","5,1"}){
            NotificationParser.Result r=parse(GlucoseUnit.MMOL,text,"mmol/L");assertEquals(5.1*18.0182,r.mgdl,1e-12);assertEquals(GlucoseUnit.MMOL,r.sourceUnit);
            assertEquals("5,1",GlucoseUnit.MMOL.number(r.mgdl,Locale.GERMAN));
        }
    }
    @Test public void magnitudeNeverSelectsTheSourceUnit(){
        assertEquals(6,parse(GlucoseUnit.MGDL,"6").mgdl,0);assertEquals(6*18.0182,parse(GlucoseUnit.MMOL,"6").mgdl,1e-12);
        assertNull(parse(GlucoseUnit.MGDL,"6.0").mgdl);assertNull(NotificationParser.parse(Arrays.asList("6"),Arrays.asList("6"),null).mgdl);
    }
    @Test public void conflictingUnitLabelsAndDifferentValuesAreRejected(){
        assertNull(parse(GlucoseUnit.MGDL,"123 mg/dl","mmol/L").mgdl);assertNull(parse(GlucoseUnit.MMOL,"5.1 mg/dl").mgdl);
        assertNull(parse(GlucoseUnit.MMOL,"5.1 mmol/l","5.2 mmol/l").mgdl);assertEquals(5.1*18.0182,parse(GlucoseUnit.MMOL,"5.1","5,1 mmol/l").mgdl,1e-12);
    }
    @Test public void dosesComparisonsStatusAndClockAreNotGlucose(){
        for(String text:new String[]{"5.1 U","12:34","≥5.1 mmol/L","≤5.1","LOW","HIGH","0","-5.1","5.123","100 mmol/l"})assertNull(text,parse(GlucoseUnit.MMOL,text).mgdl);
    }
    @Test public void arrowReferenceCombinationDoesNotLoseSourceUnit(){
        NotificationParser.Result r=TrendReference.combine(parse(GlucoseUnit.MMOL,"5.1"),NotificationParser.Trend.UP);
        assertEquals(GlucoseUnit.MMOL,r.sourceUnit);assertEquals(NotificationParser.Trend.UP,r.trend);assertEquals(5.1*18.0182,r.mgdl,1e-12);
    }
    @Test public void displayAndRateRoundOnlyAtTheBoundaryAndFollowLocale(){
        double value=GlucoseUnit.MMOL.canonical(5.1);assertEquals("92",GlucoseUnit.MGDL.number(value,Locale.GERMAN));assertEquals("5.1",GlucoseUnit.MMOL.number(value,Locale.ENGLISH));
        assertEquals("+0,28 mmol/l/min",GlucoseUnit.MMOL.rate(5,Locale.GERMAN));assertEquals("-0.28 mmol/l/min",GlucoseUnit.MMOL.rate(-5,Locale.ENGLISH));assertEquals("+5.0 mg/dl/min",GlucoseUnit.MGDL.rate(5,Locale.ENGLISH));
        assertEquals(91.89282,value,1e-12);
    }
    @Test public void decimalReceiptRateIsNotComputedFromRoundedDisplayNumbers(){
        List<HistoryAnalysis.Point> data=new ArrayList<>();for(int i=0;i<4;i++)data.add(HistoryAnalysis.Point.received(GlucoseUnit.MMOL.canonical(5+i/10.0),NOW-(3-i)*60000));
        assertEquals(1.80182,ReceiptRate.calculate(data,NOW).rate,1e-10);
        HistoryAnalysis.Result result=HistoryAnalysis.analyze(data,NOW,ReceiptWindow.limits(),HistoryAnalysis.TimeBasis.RECEIPT);assertEquals(5.40546,result.deltaMgdl,1e-10);
    }
    @Test public void sourceDecimalsReachTheBoundaryAlarmWithoutRounding(){
        AlarmEngine.Rule rule=new AlarmEngine.Rule("low",AlarmEngine.Kind.LOW,true,91.9,0,true,0,1440,new AlarmEngine.Outputs(false,true,3,5));
        AlarmEngine engine=new AlarmEngine(Collections.singletonList(rule),ReceiptWindow.limits(),AlarmEngine.OutputMode.SPOKEN_ALARMS,HistoryAnalysis.TimeBasis.RECEIPT);
        List<HistoryAnalysis.Point> data=Arrays.asList(HistoryAnalysis.Point.received(100,NOW-60000),HistoryAnalysis.Point.received(GlucoseUnit.MMOL.canonical(5.1),NOW));
        AlarmEngine.Result result=engine.evaluate(data,NOW,720,NOW-60000,null);
        assertEquals(1,result.matchedRules());
    }
    @Test public void outputModeAndAllCopiesRoundtripWithProfilesAndCustomSpeech() throws Exception {
        OutputPreferences p=OutputPreferences.defaults().withUnitMode(GlucoseUnit.Mode.MMOL).withSpeechLanguage("en").withChannels(false,true,false,true).withGap(GapSettings.defaults().withTemplate("Own gap {minutes}")).withTrendSpeech(new TrendSpeech(Arrays.asList("up","fast up","steady","down","fast down","double up","double down")));
        ConfigurationSnapshot c=new ConfigurationSnapshot(new LevelStore.Snapshot(1,Collections.emptyList()),new OutputPreferenceStore.Snapshot(2,p),"DARK","samt",true,true);
        OutputPreferences saved=ConfigurationSnapshot.decode(c.encode()).output.preferences;assertEquals(GlucoseUnit.Mode.MMOL,saved.unitMode);assertEquals(p.trendSpeech.words,saved.trendSpeech.words);assertEquals(p.gap.speechTemplate,saved.gap.speechTemplate);
    }
    @Test public void oldSchemaTenPreservesTextAndFlagsAndDefaultsToAuto() throws Exception {
        OutputPreferences p=OutputPreferences.defaults().withTrendSpeech(new TrendSpeech(TrendSpeech.defaults().words,Arrays.asList(false,true,false,true,true,true,false)));
        byte[] body=ConfigurationFiles.open(OutputPreferenceStore.encode(new OutputPreferenceStore.Snapshot(1,p)),8192);body=Arrays.copyOf(body,body.length-6);body[2+"XCAM_OUTPUT".length()+3]=10;
        OutputPreferences saved=OutputPreferenceStore.decode(ConfigurationFiles.seal(body)).preferences;assertEquals(GlucoseUnit.Mode.AUTO,saved.unitMode);assertEquals(p.trendSpeech.enabled,saved.trendSpeech.enabled);
    }
    @Test public void templatesUseTheChosenValueAndUnitButPreserveLiteralText(){
        for(String language:new String[]{"de","en"}){
            SpeechTemplate.Values v=new SpeechTemplate.Values(GlucoseUnit.MMOL.canonical(5.1),NotificationParser.Trend.FLAT,0,0,TrendSpeech.defaults(language),language,GlucoseUnit.MMOL);
            assertEquals("en".equals(language)?"5.1 millimoles per liter":"5,1 Millimol pro Liter",SpeechTemplate.render("{value} {unit}",null,v,true,false));
            assertTrue(SpeechTemplate.render("Literal mg/dl: {value}",null,v,true,false).startsWith("Literal mg/dl:"));
            assertTrue(SpeechPolicy.observationText(GlucoseUnit.MMOL.canonical(5.1),NotificationParser.Trend.FLAT,0,TrendSpeech.defaults(language),language,GlucoseUnit.MMOL).contains("en".equals(language)?"5.1":"5,1"));
        }
    }
    @Test public void unitEditorCanPreserveUntouchedThresholdExactlyAndConvertAnExplicitEdit(){
        AlarmLevel unchanged=LevelForm.parseWithUnit("low",AlarmEngine.Kind.LOW,"Own","5,0","","3","5",true,false,true,AlarmLevel.DEFAULT_TONE,true,0,1440,GlucoseUnit.MMOL,90.0);assertEquals(90,unchanged.rule.threshold,0);
        AlarmLevel edited=LevelForm.parseWithUnit("low",AlarmEngine.Kind.LOW,"Own","5,1","","3","5",true,false,true,AlarmLevel.DEFAULT_TONE,true,0,1440,GlucoseUnit.MMOL,null);assertEquals(91.89282,edited.rule.threshold,1e-12);
    }
    @Test public void nonFiniteCanonicalPointsNeverReachCalculation(){
        for(double value:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}){
            assertEquals(ReceiptWindow.Intake.INVALID,new ReceiptWindow().receive("synthetic",NOW,value,new AlarmStateStore.Clock(NOW,60000,1)));
            assertFalse(HistoryAnalysis.analyze(Arrays.asList(HistoryAnalysis.Point.received(value,NOW)),NOW,ReceiptWindow.limits(),HistoryAnalysis.TimeBasis.RECEIPT).usable());
        }
    }
}
