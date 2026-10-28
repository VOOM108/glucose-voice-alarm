// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import android.app.Activity;
import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.speech.tts.*;
import android.view.*;
import android.widget.*;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.*;
import java.time.Duration;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowTextToSpeech;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Android 9 simulation: real Activities/widgets/private files, fake speech and ringtone services. */
@RunWith(RobolectricTestRunner.class)
@Config(qualifiers="de",sdk=28,shadows={AndroidUiTest.TestRingtones.class,AndroidUiTest.TestRingtone.class,AndroidUiTest.TestBellPlayer.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class AndroidUiTest {
    @org.junit.Before public void acknowledgeForExistingFeatureTests(){ConsentTestSupport.accept(org.robolectric.RuntimeEnvironment.getApplication());}
    private Context app;
    private final List<PreviewAudio> audioOwners=new ArrayList<>();
    private final List<ActivityController<?>> activities=new ArrayList<>();
    @Before public void before() {
        app=RuntimeEnvironment.getApplication(); TestRingtone.latest=null;
        app.getSharedPreferences(AppAppearance.PREFS,Context.MODE_PRIVATE).edit().clear().commit();
        TestBellPlayer.latest=null; TestBellPlayer.completionDelayMs=0;
        app.getSharedPreferences("live_controls_v1",Context.MODE_PRIVATE).edit().clear().commit();
        app.getSharedPreferences(SoundDefaults.PREFS,Context.MODE_PRIVATE).edit().clear().commit();
        ProbeState.latest=null; ProbeState.vectorCandidate=null;
        app.getSharedPreferences(CamApsListener.PREFS,Context.MODE_PRIVATE).edit().clear().commit();
        for (String name:new String[]{LevelStore.FILE_NAME,AlarmStateStore.FILE_NAME,OutputPreferenceStore.FILE_NAME}) {
            new File(app.getFilesDir(),name).delete(); new File(app.getFilesDir(),name+".new").delete();
        }
        ShadowTextToSpeech.addVoice(new Voice("synthetic-de",Locale.GERMANY,Voice.QUALITY_NORMAL,Voice.LATENCY_NORMAL,false,Collections.emptySet()));
    }
    @After public void after() {
        for (ActivityController<?> controller:activities) controller.pause().stop().destroy();
        for (PreviewAudio audio:audioOwners) audio.close();
    }
    private <T extends Activity> ActivityController<T> open(Class<T> kind,Intent intent,Bundle state) {
        ActivityController<T> controller=Robolectric.buildActivity(kind,intent).create(state).start().resume().visible();
        activities.add(controller); return controller;
    }
    private <T extends Activity> ActivityController<T> open(Class<T> kind) { return open(kind,new Intent(app,kind),null); }
    private List<View> views(View root) {
        List<View> result=new ArrayList<>(); result.add(root);
        if (root instanceof ViewGroup) for (int i=0;i<((ViewGroup)root).getChildCount();i++) result.addAll(views(((ViewGroup)root).getChildAt(i)));
        return result;
    }
    private View text(Activity activity,String label) {
        for (View view:views(activity.getWindow().getDecorView())) if (view instanceof TextView && (label.equals(((TextView)view).getText().toString()) || label.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))) return view;
        throw new AssertionError("Missing view: "+label);
    }
    private List<EditText> fields(Activity activity) {
        List<EditText> result=new ArrayList<>(); for (View view:views(activity.getWindow().getDecorView())) if (view instanceof EditText) result.add((EditText)view); return result;
    }
    private PreviewAudio audio() { PreviewAudio result=new PreviewAudio(app,message -> {}); audioOwners.add(result); return result; }
    private ShadowTextToSpeech initialize(PreviewAudio audio) {
        audio.prepareSpeech(); TextToSpeech engine=ShadowTextToSpeech.getLastTextToSpeechInstance(); assertNotNull(engine);
        ShadowTextToSpeech shadow=shadowOf(engine); shadow.getOnInitListener().onInit(TextToSpeech.SUCCESS); shadowOf(Looper.getMainLooper()).idle(); return shadow;
    }
    @Test public void tonesOnlyDiagnosticsCreateNoSpeechEngineAndBlockManualPreview() throws Exception {
        new OutputPreferenceStore(app.getFilesDir()).save(0,new OutputPreferences(AlarmEngine.OutputMode.TONES_ONLY,0,SpeechTemplate.VALUE_DEFAULT));
        ProbeActivity activity=open(ProbeDiagnosticsActivity.class).get();
        assertNull(ShadowTextToSpeech.getLastTextToSpeechInstance());
        assertTrue(text(activity,"Sprachvorschau mit Testtext").performClick());
        assertNull(ShadowTextToSpeech.getLastTextToSpeechInstance());
    }
    @Test public void outputFormSurvivesRotationAndSavesTheActualEditedModeIntervalAndTemplate() throws Exception {
        ActivityController<OutputSettingsActivity> first=open(OutputSettingsActivity.class); Activity activity=first.get();
        text(activity,"Werte sprechen").performClick();
        fields(activity).get(0).setText("7"); fields(activity).get(1).setText("Beispiel {wert} {einheit}");
        Bundle state=new Bundle(); first.saveInstanceState(state).pause().stop().destroy(); activities.remove(first);
        OutputSettingsActivity restored=open(OutputSettingsActivity.class,new Intent(app,OutputSettingsActivity.class),state).get();
        assertEquals("7",fields(restored).get(0).getText().toString());
        assertEquals("Beispiel {wert} {einheit}",fields(restored).get(1).getText().toString());
        assertTrue(((Switch)text(restored,"Werte sprechen")).isChecked());
        text(restored,"Ausgabe-Einstellungen speichern").performClick();
        OutputPreferences saved=new OutputPreferenceStore(app.getFilesDir()).load().preferences;
        assertEquals(AlarmEngine.OutputMode.SPOKEN_ALARMS_AND_VALUES,saved.mode); assertEquals(7,saved.valueIntervalMinutes);
        assertEquals("Beispiel {wert} {einheit}",saved.valueTemplate);
    }
    @Test public void levelSpeechDraftSurvivesRotationAndIsNotLostBySavingOtherFields() throws Exception {
        SyntheticAlarmScenario scenario=new SyntheticAlarmScenario(AlarmEngine.Kind.HIGH);
        AlarmLevel original=new AlarmLevel(scenario.level.rule,"Teststufe",scenario.level.toneUri,Arrays.asList("Alt {wert}","Alt zwei {wert}"));
        new LevelStore(app.getFilesDir()).save(0,Collections.singletonList(original));
        Intent intent=new Intent(app,AlarmLevelActivity.class).putExtra("id",original.rule.id);
        ActivityController<AlarmLevelActivity> first=open(AlarmLevelActivity.class,intent,null); Activity activity=first.get();
        EditText speech=null; for (EditText field:fields(activity)) if (field.getText().toString().startsWith("Alt {wert}")) speech=field;
        assertNotNull(speech); speech.setText("Neu {wert}\nZweite {trend}");
        Bundle state=new Bundle(); first.saveInstanceState(state).pause().stop().destroy(); activities.remove(first);
        AlarmLevelActivity restored=open(AlarmLevelActivity.class,intent,state).get();
        assertNotNull(text(restored,"Neu {wert}\nZweite {trend}"));
        fields(restored).get(0).setText("Neuer Name"); text(restored,"Stufe speichern").performClick();
        AlarmLevel saved=new LevelStore(app.getFilesDir()).load().levels.get(0);
        assertEquals("Neuer Name",saved.name); assertEquals(Arrays.asList("Neu {wert}","Zweite {trend}"),saved.speechVariants);
    }
    @Test public void speechPreparationNeverReplaysTheRequestThatArrivedBeforeReadiness() {
        PreviewAudio audio=audio(); assertFalse(audio.speak("Synthetischer Test"));
        ShadowTextToSpeech shadow=initialize(audio); assertNull(shadow.getLastSpokenText());
        assertTrue(audio.speak("Erneut ausdrücklich gestartet")); assertEquals("Erneut ausdrücklich gestartet",shadow.getLastSpokenText());
    }
    @Test public void savingTonesOnlyStopsSpeechAndReleasesItsEngine() throws Exception {
        PreviewAudio audio=audio(); ShadowTextToSpeech shadow=initialize(audio); assertTrue(audio.speak("Test"));
        OutputPreferenceStore store=new OutputPreferenceStore(app.getFilesDir());
        store.save(store.load().revision,new OutputPreferences(AlarmEngine.OutputMode.TONES_ONLY,0,SpeechTemplate.VALUE_DEFAULT));
        PreviewAudio.configurationChanged();
        assertTrue(shadow.isStopped()); assertTrue(shadow.isShutdown());
        assertFalse(audio.speak("Darf nicht sprechen")); assertEquals("Test",shadow.getLastSpokenText());
    }
    @Test public void lifecyclePauseStopsAndReleasesTheSpeechEngine() {
        PreviewAudio audio=audio(); ShadowTextToSpeech shadow=initialize(audio); audio.speak("Test"); audio.pause();
        assertTrue(shadow.isStopped()); assertTrue(shadow.isShutdown());
    }
    private AlarmOutputFlow startCombined(PreviewAudio audio) {
        SyntheticAlarmScenario scenario=new SyntheticAlarmScenario(AlarmEngine.Kind.HIGH);
        File folder=new File(app.getCacheDir(),"flow-"+UUID.randomUUID());
        AlarmOutputFlow flow=new AlarmOutputFlow(new AlarmStateStore(folder),Collections.singletonList(scenario.level),SyntheticAlarmScenario.limits(),OutputPreferences.defaults(),audio);
        assertEquals(AlarmOutputFlow.Outcome.ACCEPTED,flow.evaluate(AlarmOutputFlow.Origin.SYNTHETIC,scenario.points(0,true),scenario.clock(0),720,null,scenario.trend(true)));
        assertEquals(AlarmOutputFlow.Outcome.ACCEPTED,flow.alarm(scenario.level.rule.id,scenario.clock(0)));
        return flow;
    }
    @Test public void combinedOutputStopsToneBeforeSpeechAndCancelsItsDelayedSpeechOnModeChange() throws Exception {
        PreviewAudio audio=audio(); ShadowTextToSpeech shadow=initialize(audio); startCombined(audio);
        assertNotNull(TestRingtone.latest); assertTrue(TestRingtone.latest.playing); assertNull(shadow.getLastSpokenText());
        OutputPreferenceStore store=new OutputPreferenceStore(app.getFilesDir());
        store.save(store.load().revision,new OutputPreferences(AlarmEngine.OutputMode.TONES_ONLY,0,SpeechTemplate.VALUE_DEFAULT)); PreviewAudio.configurationChanged();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(9));
        assertFalse(TestRingtone.latest.playing); assertNull(shadow.getLastSpokenText()); assertTrue(shadow.isShutdown());
    }
    @Test public void combinedOutputIsSequentialAndExplicitStopCancelsBothParts() {
        PreviewAudio audio=audio(); ShadowTextToSpeech shadow=initialize(audio); startCombined(audio);
        assertTrue(TestRingtone.latest.playing); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(8));
        assertFalse(TestRingtone.latest.playing); assertTrue(shadow.getLastSpokenText().startsWith("Dies ist eine Testansage mit synthetischen Daten."));
        audio.stop(); assertTrue(shadow.isStopped());
    }
    @Test public void absentArrowHasReadableStatusAndAssignmentButtonImmediatelyBelowIt() {
        app.getSharedPreferences(CamApsListener.PREFS,Context.MODE_PRIVATE).edit().putBoolean(CamApsListener.ENABLED,true).commit();
        ProbeState.latest=new ProbeState.Observation(123,NotificationParser.Trend.UNKNOWN,SystemClock.elapsedRealtime(),System.currentTimeMillis(),"synthetic");
        ProbeActivity activity=open(ProbeActivity.class).get(); shadowOf(Looper.getMainLooper()).idle();
        View trend=activity.getWindow().getDecorView().findViewWithTag("camaps_trend");
        assertEquals("CamAPS  Kein Trendpfeil",((TextView)trend).getText().toString());
        View button=text(activity,"Pfeilgrafik zuordnen …");
        ViewGroup parent=(ViewGroup)trend.getParent();
        assertSame(button,parent.getChildAt(parent.indexOfChild(trend)+1)); assertFalse(button.isEnabled());
        assertNotNull(text(activity,"123 mg/dl"));
    }
    @Test public void knownGraphicIsExplicitlyLabeledAsNotNeedingAnotherConfirmation() {
        app.getSharedPreferences(CamApsListener.PREFS,Context.MODE_PRIVATE).edit().putBoolean(CamApsListener.ENABLED,true).commit();
        String exact=String.format(Locale.ROOT,"%064x",1),shape=String.format(Locale.ROOT,"%064x",2);
        app.getSharedPreferences(VectorReference.PREFS,Context.MODE_PRIVATE).edit().putString(exact,NotificationParser.Trend.UP_RIGHT.name()).commit();
        ProbeState.vectorCandidate=new VectorReference.Candidate(exact,shape,"synthetic",android.graphics.Bitmap.createBitmap(64,64,android.graphics.Bitmap.Config.ARGB_8888));
        ProbeState.latest=new ProbeState.Observation(123,NotificationParser.Trend.UP_RIGHT,SystemClock.elapsedRealtime(),System.currentTimeMillis(),"synthetic");
        ProbeActivity activity=open(ProbeActivity.class).get(); shadowOf(Looper.getMainLooper()).idle();
        assertTrue(text(activity,"Bekannte Pfeilgrafik prüfen/ändern …").isEnabled());
        boolean explained=false;
        for (View view:views(activity.getWindow().getDecorView())) if (view instanceof TextView && ((TextView)view).getText().toString().contains("keine erneute Bestätigung nötig")) explained=true;
        assertTrue(explained);
    }
    @Test public void outdatedInitializationCallbackCannotEnableANewerUninitializedSpeechEngine() {
        PreviewAudio audio=audio(); audio.prepareSpeech(); ShadowTextToSpeech old=shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance());
        audio.pause(); audio.prepareSpeech(); ShadowTextToSpeech current=shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance());
        assertNotSame(old,current); old.getOnInitListener().onInit(TextToSpeech.SUCCESS); shadowOf(Looper.getMainLooper()).idle();
        assertFalse(audio.speak("Nicht freigegeben")); assertNull(current.getLastSpokenText());
        current.getOnInitListener().onInit(TextToSpeech.SUCCESS); shadowOf(Looper.getMainLooper()).idle(); assertTrue(audio.speak("Neu gestartet"));
    }
    @Test public void networkOnlyGermanVoiceDoesNotPassOfflineReadiness() {
        ShadowTextToSpeech.reset();
        ShadowTextToSpeech.addVoice(new Voice("network-de",Locale.GERMANY,Voice.QUALITY_NORMAL,Voice.LATENCY_NORMAL,true,Collections.emptySet()));
        PreviewAudio audio=audio(); ShadowTextToSpeech shadow=initialize(audio);
        assertFalse(audio.speak("Keine Netzwerkstimme")); assertNull(shadow.getLastSpokenText()); assertNull(shadow.getCurrentVoice());
    }
    private void readyExceptBattery() {
        app.getSharedPreferences(CamApsListener.PREFS,Context.MODE_PRIVATE).edit().putBoolean(CamApsListener.ENABLED,true).commit();
        android.app.NotificationManager manager=app.getSystemService(android.app.NotificationManager.class);
        shadowOf(manager).setNotificationsEnabled(true);
        shadowOf(manager).setNotificationListenerAccessGranted(new ComponentName(app,CamApsListener.class),true);
        shadowOf(app.getSystemService(android.app.ActivityManager.class)).setBackgroundRestricted(false);
        shadowOf(app.getSystemService(PowerManager.class)).setIgnoringBatteryOptimizations(app.getPackageName(),false);
    }
    @Test public void missingBatteryExceptionOffersAnEnabledDirectActionAndPermissionReturnNeverArmsAlarms() {
        readyExceptBattery(); ActivityController<ReadinessActivity> controller=open(ReadinessActivity.class);
        ReadinessActivity activity=controller.get(); shadowOf(Looper.getMainLooper()).idle();
        View action=text(activity,"Akku-Ausnahme erteilen"); assertTrue(action.isEnabled()); action.performClick();
        Intent intent=shadowOf(activity).getNextStartedActivity();
        assertEquals(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,intent.getAction());
        assertEquals("package:"+app.getPackageName(),intent.getDataString());
        assertFalse(LivePreferences.alarms(app));
        shadowOf(app.getSystemService(PowerManager.class)).setIgnoringBatteryOptimizations(app.getPackageName(),true);
        controller.pause().resume(); shadowOf(Looper.getMainLooper()).idle();
        assertTrue(text(activity,"Live-Alarme einschalten").isEnabled()); assertFalse(LivePreferences.alarms(app));
        assertFalse(LiveAlarmService.running());
    }
    @Test public void technicalTestsAreAbsentFromHomeAndAvailableOnTheirOwnPage() {
        ProbeActivity activity=open(ProbeActivity.class).get(); shadowOf(Looper.getMainLooper()).idle();
        for (View view:views(activity.getWindow().getDecorView())) if (view instanceof TextView)
            assertFalse(((TextView)view).getText().toString().contains("Alarmkern mit Testdaten prüfen"));
        ProbeDiagnosticsActivity diagnostic=open(ProbeDiagnosticsActivity.class).get();
        assertTrue(text(diagnostic,"Alarmkern mit Testdaten prüfen").isShown());
    }
    @Test public void savedLivePreferenceDoesNotClaimThatAServiceIsRunning() {
        readyExceptBattery(); shadowOf(app.getSystemService(PowerManager.class)).setIgnoringBatteryOptimizations(app.getPackageName(),true);
        LivePreferences.set(app,true,true);
        ProbeActivity activity=open(ProbeActivity.class).get(); shadowOf(Looper.getMainLooper()).idle();
        assertNotNull(text(activity,"Live-Alarmdienst läuft nicht"));
    }
    @Test public void eachMissingGateNamesItsOwnNextActionInOrder() {
        assertEquals(SystemReadiness.Step.RECEIVER,new SystemReadiness(false,false,false,false,true).nextStep());
        assertEquals("Benachrichtigungszugriff erlauben",new SystemReadiness(true,false,false,false,true).nextAction());
        assertEquals("Eigene Benachrichtigungen erlauben",new SystemReadiness(true,true,false,false,true).nextAction());
        assertEquals("Akku-Ausnahme erteilen",new SystemReadiness(true,true,true,false,true).nextAction());
        assertEquals("Hintergrundnutzung erlauben",new SystemReadiness(true,true,true,true,true).nextAction());
        assertEquals(SystemReadiness.Step.READY,new SystemReadiness(true,true,true,true,false).nextStep());
    }
    private AlarmLevel speechOnlyLevel() {
        AlarmLevel original=new SyntheticAlarmScenario(AlarmEngine.Kind.HIGH).level; AlarmEngine.Rule r=original.rule;
        return new AlarmLevel(new AlarmEngine.Rule(r.id,r.kind,true,r.threshold,r.durationMinutes,r.allDay,r.startMinute,r.endMinute,
                new AlarmEngine.Outputs(false,true,r.outputs.repeatMinutes,r.outputs.snoozeMinutes)),"Sprachstufe",original.toneUri);
    }
    @Test public void disablingSpeechMakesTheStoredToneMandatoryWhileSpeechAllowsItToBeOptional() throws Exception {
        AlarmLevel level=speechOnlyLevel(); new LevelStore(app.getFilesDir()).save(0,Collections.singletonList(level));
        AlarmLevelActivity activity=open(AlarmLevelActivity.class,new Intent(app,AlarmLevelActivity.class).putExtra("id",level.rule.id),null).get();
        CheckBox tone=(CheckBox)text(activity,"Ton für diese Stufe"); assertFalse(tone.isChecked()); assertTrue(tone.isEnabled());
        text(activity,"Gesprochener Alarm für diese Stufe").performClick();
        assertTrue(tone.isChecked()); assertFalse(tone.isEnabled()); text(activity,"Stufe speichern").performClick();
        AlarmLevel saved=new LevelStore(app.getFilesDir()).load().levels.get(0); assertTrue(saved.rule.outputs.tone); assertFalse(saved.rule.outputs.speech);
    }
    @Test public void directToneModeCannotSilentlyMuteAnEnabledSpeechOnlyLevel() throws Exception {
        new LevelStore(app.getFilesDir()).save(0,Collections.singletonList(speechOnlyLevel()));
        ProbeActivity activity=open(ProbeActivity.class).get(); text(activity,"Alarme sprechen").performClick();
        assertEquals(AlarmEngine.OutputMode.SPOKEN_ALARMS,new OutputPreferenceStore(app.getFilesDir()).load().preferences.mode);
        android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog(); assertNotNull(dialog);
        assertTrue(((TextView)dialog.findViewById(android.R.id.message)).getText().toString().contains("fehlt ein aktiver Alarmweg"));
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(speechOnlyLevel().rule.id,shadowOf(activity).getNextStartedActivity().getStringExtra("id"));
    }
    @Test public void dashboardRouteSwitchesSaveIndependentlyAndRejectDisablingTheLastAlarmRoute() throws Exception {
        ProbeActivity activity=open(ProbeActivity.class).get(); text(activity,"Alarme sprechen").performClick();
        assertEquals(AlarmEngine.OutputMode.TONES_ONLY,new OutputPreferenceStore(app.getFilesDir()).load().preferences.mode);
        assertTrue(((Switch)text(activity,"Ton")).isChecked()); assertFalse(((Switch)text(activity,"Alarme sprechen")).isChecked());
        text(activity,"Ton").performClick(); assertTrue(((Switch)text(activity,"Ton")).isChecked());
        assertTrue(new OutputPreferenceStore(app.getFilesDir()).load().preferences.tonesEnabled); assertFalse(LivePreferences.alarms(app));
    }
    @Test public void dashboardMarksAnOldValueAsStaleWithoutASecondsCounter() {
        app.getSharedPreferences(CamApsListener.PREFS,Context.MODE_PRIVATE).edit().putBoolean(CamApsListener.ENABLED,true).commit();
        ProbeState.latest=new ProbeState.Observation(123,NotificationParser.Trend.FLAT,SystemClock.elapsedRealtime()-300000,System.currentTimeMillis()-300000,"synthetic");
        org.robolectric.util.ReflectionHelpers.setField(ProbeState.latest,"receivedWall",ProbeState.latest.postedWall);
        ProbeActivity activity=open(ProbeActivity.class).get(); shadowOf(Looper.getMainLooper()).idle(); boolean stale=false;
        for (View view:views(activity.getWindow().getDecorView())) if ("dashboard_time".equals(view.getTag())) {
            TextView time=(TextView)view; stale=time.getCurrentTextColor()==AppAppearance.colors(activity).error;
            assertTrue(time.getText().toString().matches("[0-9]{2}:[0-9]{2}")); assertTrue(time.getContentDescription().toString().contains("nicht mehr aktuell"));
        }
        assertTrue(stale); assertNotNull(text(activity,"123 mg/dl"));
    }
    @Test public void independentVolumeDraftsSurviveRotationAndToneVolumeDoesNotChangeTheSystemAlarmVolume() throws Exception {
        ActivityController<OutputSettingsActivity> first=open(OutputSettingsActivity.class); List<SeekBar> bars=new ArrayList<>();
        for (View view:views(first.get().getWindow().getDecorView())) if (view instanceof SeekBar) bars.add((SeekBar)view);
        assertEquals(3,bars.size()); bars.get(0).setProgress(25); bars.get(1).setProgress(60); bars.get(2).setProgress(6);
        Bundle state=new Bundle(); first.saveInstanceState(state).pause().stop().destroy(); activities.remove(first);
        OutputSettingsActivity restored=open(OutputSettingsActivity.class,new Intent(app,OutputSettingsActivity.class),state).get();
        assertNotNull(text(restored,"Töne: 25 %")); assertNotNull(text(restored,"Sprache: 60 %"));
        assertNotNull(text(restored,"Glockenton-Boost: +6 dB"));
        int systemVolume=app.getSystemService(AudioManager.class).getStreamVolume(AudioManager.STREAM_ALARM);
        text(restored,"Tonlautstärke anhören").performClick(); assertEquals(0.2494078f,TestBellPlayer.latest.volume,0.0001f);
        assertEquals(systemVolume,app.getSystemService(AudioManager.class).getStreamVolume(AudioManager.STREAM_ALARM));
        text(restored,"Ausgabe-Einstellungen speichern").performClick();
        OutputPreferences saved=new OutputPreferenceStore(app.getFilesDir()).load().preferences;
        assertEquals(25,saved.toneVolumePercent); assertEquals(60,saved.speechVolumePercent);
        assertEquals(6,saved.bellBoostDb);
        OutputModeUi.channel(restored,1,false,() -> {});
        assertEquals(6,new OutputPreferenceStore(app.getFilesDir()).load().preferences.bellBoostDb);
        saved=new OutputPreferenceStore(app.getFilesDir()).load().preferences; assertEquals(25,saved.toneVolumePercent); assertEquals(60,saved.speechVolumePercent);
    }
    @Test public void savedBoostOnlyChangesBundledBellGainAndNeverTheSystemOrExternalToneVolume() throws Exception {
        new OutputPreferenceStore(app.getFilesDir()).save(0,new OutputPreferences(AlarmEngine.OutputMode.SPOKEN_ALARMS,0,SpeechTemplate.VALUE_DEFAULT,100,50,5,6));
        PreviewAudio audio=audio(); int system=app.getSystemService(AudioManager.class).getStreamVolume(AudioManager.STREAM_ALARM);
        assertTrue(audio.tone(ToneCatalog.uri("samt",AlarmEngine.Kind.HIGH))); assertEquals(0.997631f,TestBellPlayer.latest.volume,0.00001f);
        assertTrue(audio.tone(AlarmLevel.DEFAULT_TONE)); assertEquals(1f,TestRingtone.latest.volume,0);
        assertEquals(system,app.getSystemService(AudioManager.class).getStreamVolume(AudioManager.STREAM_ALARM));
        assertFalse(audio.tone(ToneCatalog.uri("samt",AlarmEngine.Kind.HIGH),0,6)); assertFalse(audio.busy());
    }
    @Test public void bundledDefaultsReplaceTheOldDefaultOnceAndAlwaysPreserveCustomTonesAndRuleFlags() throws Exception {
        AlarmLevel high=new SyntheticAlarmScenario(AlarmEngine.Kind.HIGH).level;
        AlarmLevel low=new SyntheticAlarmScenario(AlarmEngine.Kind.LOW).level;
        AlarmLevel custom=new AlarmLevel(low.rule,"Eigener Ton","content://media/internal/audio/media/42");
        LevelStore store=new LevelStore(app.getFilesDir()); store.save(0,Arrays.asList(high,custom));
        SoundDefaults.install(app); AlarmLevel changed=store.load().find(high.rule.id);
        assertEquals(ToneCatalog.uri("samt",AlarmEngine.Kind.HIGH),changed.toneUri);
        assertEquals(high.rule.threshold,changed.rule.threshold,0); assertEquals(high.rule.enabled,changed.rule.enabled);
        assertEquals(high.rule.outputs.tone,changed.rule.outputs.tone); assertEquals(custom.toneUri,store.load().find(custom.rule.id).toneUri);
        SoundDefaults.useSet(app,"rund"); assertEquals(ToneCatalog.uri("rund",AlarmEngine.Kind.HIGH),store.load().find(high.rule.id).toneUri);
        assertEquals(custom.toneUri,store.load().find(custom.rule.id).toneUri);
        LevelStore.Snapshot current=store.load(); store.save(current.revision,Arrays.asList(high,custom));
        SoundDefaults.install(app); assertEquals(AlarmLevel.DEFAULT_TONE,store.load().find(high.rule.id).toneUri);
    }
    @Test public void bundledBellEndsBeforeSpeechWithoutAnEightSecondWait() {
        TestBellPlayer.completionDelayMs=600;
        PreviewAudio audio=audio(); ShadowTextToSpeech shadow=initialize(audio);
        SyntheticAlarmScenario scenario=new SyntheticAlarmScenario(AlarmEngine.Kind.HIGH);
        AlarmLevel own=new AlarmLevel(scenario.level.rule,"Kurzer Testton",ToneCatalog.uri("samt",AlarmEngine.Kind.HIGH));
        AlarmOutputFlow flow=new AlarmOutputFlow(new AlarmStateStore(new File(app.getCacheDir(),"short-bell-flow")),Collections.singletonList(own),SyntheticAlarmScenario.limits(),OutputPreferences.defaults(),audio);
        flow.evaluate(AlarmOutputFlow.Origin.SYNTHETIC,scenario.points(0,true),scenario.clock(0),720,null,scenario.trend(true));
        assertEquals(AlarmOutputFlow.Outcome.ACCEPTED,flow.alarm(own.rule.id,scenario.clock(0)));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500)); assertTrue(TestBellPlayer.latest.playing); assertNull(shadow.getLastSpokenText());
        assertTrue(audio.busy());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(460)); assertFalse(TestBellPlayer.latest.playing); assertFalse(TestBellPlayer.latest.released); assertNull(shadow.getLastSpokenText());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(120)); assertTrue(TestBellPlayer.latest.released); assertNotNull(shadow.getLastSpokenText());
    }
    @Test public void stoppingDuringBellDrainCancelsFollowupAndReleasesPlayer() {
        PreviewAudio audio=audio(); boolean[] followed={false};
        assertTrue(audio.toneThen(ToneCatalog.uri("samt",AlarmEngine.Kind.HIGH),() -> followed[0]=true));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(360)); assertTrue(audio.busy());
        audio.pause(); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10));
        assertTrue(TestBellPlayer.latest.released); assertFalse(followed[0]); assertFalse(audio.busy());
    }
    @Test public void playerErrorAndWatchdogNeverStartAFollowup() {
        PreviewAudio audio=audio(); boolean[] followed={false};
        String uri=ToneCatalog.uri("samt",AlarmEngine.Kind.HIGH);
        assertTrue(audio.toneThen(uri,() -> followed[0]=true));
        TestBellPlayer.latest.fail(); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10));
        assertTrue(TestBellPlayer.latest.released); assertFalse(followed[0]); assertFalse(audio.busy());
        TestBellPlayer.completionDelayMs=20000;
        assertTrue(audio.toneThen(uri,() -> followed[0]=true));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(9));
        assertTrue(TestBellPlayer.latest.released); assertFalse(followed[0]); assertFalse(audio.busy());
    }
    @Test public void setAuditionWaitsForCompletionAndLeavingCancelsRemainingNotes() {
        TestBellPlayer.completionDelayMs=1000;
        ActivityController<ToneSetsActivity> controller=open(ToneSetsActivity.class);
        text(controller.get(),"Set anhören").performClick(); TestBellPlayer first=TestBellPlayer.latest;
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000)); assertSame(first,TestBellPlayer.latest); assertTrue(first.playing);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(680)); assertNotSame(first,TestBellPlayer.latest); assertTrue(first.released);
        TestBellPlayer second=TestBellPlayer.latest; controller.pause();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10)); assertSame(second,TestBellPlayer.latest); assertTrue(second.released);
    }
    @Test public void displayControlsAreDirectlyVisibleAndTheRequiredMetricLabelIsNeverBlank() {
        readyExceptBattery(); ReadinessActivity activity=open(ReadinessActivity.class).get(); shadowOf(Looper.getMainLooper()).idle();
        CheckBox toggle=(CheckBox)text(activity,"Große Live-Anzeige verwenden"); assertTrue(toggle.isShown()); assertTrue(toggle.isChecked());
        toggle.performClick(); assertFalse(LivePreferences.nativeDisplay(app)); assertFalse(LivePreferences.alarms(app));
        assertTrue(text(activity,"Live-Anzeige in Android öffnen").isShown());
        assertTrue(MetricNotification.label(System.currentTimeMillis()).matches("[0-9]{2}:[0-9]{2}"));
    }
    @Test public void compactNativeTitleDescribesEachArrowWithoutRepeatingTheValueOrAppName() {
        String[] expected={"Doppelt aufwärts","Schnell steigend","Steigend","Stabil","Fallend","Schnell fallend","Doppelt abwärts","Trend offen"};
        NotificationParser.Trend[] trends=NotificationParser.Trend.values(); assertEquals(expected.length,trends.length);
        for (int i=0;i<trends.length;i++) assertEquals(expected[i],MetricNotification.trendTitle(trends[i]));
    }
    @Test public void liveOutputPreemptsPreviewAndManualOutputCannotOverlapIt() {
        PreviewAudio preview=audio(); initialize(preview); assertTrue(preview.speak("Manuelle Probe")); assertTrue(preview.busy());
        PreviewAudio live=new PreviewAudio(app,message -> {},() -> true); audioOwners.add(live); initialize(live);
        assertTrue(live.tone(ToneCatalog.uri("samt",AlarmEngine.Kind.LOW))); assertFalse(preview.busy());
        assertFalse(preview.speak("Darf sich nicht überlagern")); assertFalse(preview.tone(ToneCatalog.uri("samt",AlarmEngine.Kind.HIGH))); assertTrue(live.busy());
        live.stop(); assertTrue(preview.speak("Danach wieder möglich"));
    }
    @Test public void aNewManualPreviewCancelsThePreviousOwnersPendingSpeech() {
        TestBellPlayer.completionDelayMs=1000;
        PreviewAudio first=audio(); ShadowTextToSpeech old=initialize(first); startCombined(first);
        PreviewAudio second=audio(); ShadowTextToSpeech next=initialize(second); assertTrue(second.speak("Neue Probe"));
        assertFalse(first.busy()); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(12)); assertNull(old.getLastSpokenText()); assertEquals("Neue Probe",next.getLastSpokenText());
    }
    @Implements(MediaPlayer.class) public static class TestBellPlayer {
        static TestBellPlayer latest;
        static int created;
        static long completionDelayMs;
        @RealObject MediaPlayer real;
        boolean playing,released;
        float volume=1;
        long duration;
        MediaPlayer.OnCompletionListener completion;
        MediaPlayer.OnErrorListener error;
        final Handler handler=new Handler(Looper.getMainLooper());
        @Implementation protected void __constructor__() { latest=this; created++; }
        @Implementation protected void setAudioAttributes(AudioAttributes attributes) { assertEquals(AudioAttributes.USAGE_ALARM,attributes.getUsage()); }
        @Implementation protected void setDataSource(FileDescriptor fd,long offset,long length) { duration=(length-44)*1000/(44100*2); }
        @Implementation protected void prepare() { }
        @Implementation protected void setLooping(boolean looping) { assertFalse(looping); }
        @Implementation protected void setVolume(float left,float right) { assertEquals(left,right,0); volume=left; }
        @Implementation protected void setOnCompletionListener(MediaPlayer.OnCompletionListener listener) { completion=listener; }
        @Implementation protected void setOnErrorListener(MediaPlayer.OnErrorListener listener) { error=listener; }
        @Implementation protected void start() { playing=true; handler.postDelayed(() -> { if (!released) { playing=false; completion.onCompletion(real); } },duration+completionDelayMs); }
        @Implementation protected void release() { released=true; playing=false; handler.removeCallbacksAndMessages(null); }
        void fail() { error.onError(real,1,0); }
    }
    @Implements(RingtoneManager.class) public static class TestRingtones {
        @Implementation protected static Ringtone getRingtone(Context context,Uri uri) {
            return ReflectionHelpers.callConstructor(Ringtone.class,ReflectionHelpers.ClassParameter.from(Context.class,context),ReflectionHelpers.ClassParameter.from(boolean.class,true));
        }
    }
    @Implements(Ringtone.class) public static class TestRingtone {
        static TestRingtone latest;
        boolean playing;
        float volume=1;
        @Implementation protected void __constructor__(Context context,boolean remote) { latest=this; }
        @Implementation protected void setAudioAttributes(AudioAttributes attributes) { }
        @Implementation protected void setLooping(boolean value) { }
        @Implementation protected void setVolume(float value) { volume=value; }
        @Implementation protected void play() { playing=true; }
        @Implementation protected void stop() { playing=false; }
        @Implementation protected boolean isPlaying() { return playing; }
    }
}
