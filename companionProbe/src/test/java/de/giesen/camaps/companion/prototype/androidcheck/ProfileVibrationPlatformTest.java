// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import android.speech.tts.*;
import android.view.*;
import android.widget.*;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(qualifiers="de",sdk={28,34},shadows={AndroidUiTest.TestBellPlayer.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class ProfileVibrationPlatformTest {
    private Context app;
    private Vibrator vibrator;
    private final List<PreviewAudio> owners=new ArrayList<>();
    private final List<ActivityController<?>> activities=new ArrayList<>();
    @Before public void before() throws Exception {
        app=RuntimeEnvironment.getApplication();
        for(String prefs:new String[]{AppAppearance.PREFS,SoundDefaults.PREFS,"live_controls_v1",CamApsListener.PREFS}) app.getSharedPreferences(prefs,0).edit().clear().commit();
        for(String name:new String[]{LevelStore.FILE_NAME,OutputPreferenceStore.FILE_NAME,ProfileStore.FILE_NAME,SettingsTransaction.FILE_NAME}) Files.deleteIfExists(new File(app.getFilesDir(),name).toPath());
        vibrator=Build.VERSION.SDK_INT>=31?app.getSystemService(VibratorManager.class).getDefaultVibrator():(Vibrator)app.getSystemService(Context.VIBRATOR_SERVICE);
        shadowOf(vibrator).setHasVibrator(true); AndroidUiTest.TestBellPlayer.completionDelayMs=0;
        ShadowTextToSpeech.addVoice(new Voice("synthetic-de",Locale.GERMANY,Voice.QUALITY_NORMAL,Voice.LATENCY_NORMAL,false,Collections.emptySet()));
    }
    @After public void after() { for(ActivityController<?> activity:activities) activity.pause().stop().destroy(); for(PreviewAudio audio:owners) audio.close(); }
    private PreviewAudio audio() { PreviewAudio value=new PreviewAudio(app,message -> {}); owners.add(value); return value; }
    private AlarmLevel level(boolean audio) {
        AlarmLevel base=new SyntheticAlarmScenario(AlarmEngine.Kind.HIGH).level; AlarmEngine.Rule r=base.rule;
        return new AlarmLevel(new AlarmEngine.Rule(r.id,r.kind,true,r.threshold,0,true,0,1440,new AlarmEngine.Outputs(audio,audio,3,5)),"Teststufe",ToneCatalog.uri("samt",AlarmEngine.Kind.HIGH),Collections.emptyList(),VibrationCatalog.LONG_LONG.name());
    }
    private OutputPreferences prefs(boolean audio) { return new OutputPreferences(AlarmEngine.OutputMode.SPOKEN_ALARMS,0,SpeechTemplate.VALUE_DEFAULT,100,100,5,0,TrendSpeech.defaults(),audio,audio,false,true); }
    private ConfigurationSnapshot snapshot() { return new ConfigurationSnapshot(new LevelStore.Snapshot(1,Collections.singletonList(level(false))),new OutputPreferenceStore.Snapshot(1,prefs(false)),"DARK","rund",false,false); }
    private AlarmOutputFlow flow(PreviewAudio port,boolean sound) {
        File folder=new File(app.getCacheDir(),UUID.randomUUID().toString()); SyntheticAlarmScenario scenario=new SyntheticAlarmScenario(AlarmEngine.Kind.HIGH);
        AlarmOutputFlow flow=new AlarmOutputFlow(new AlarmStateStore(folder),Collections.singletonList(level(sound)),SyntheticAlarmScenario.limits(),prefs(sound),port);
        assertEquals(AlarmOutputFlow.Outcome.ACCEPTED,flow.evaluate(AlarmOutputFlow.Origin.SYNTHETIC,scenario.points(0,true),scenario.clock(0),720,null,NotificationParser.Trend.FLAT)); return flow;
    }
    private <T extends Activity> ActivityController<T> open(Class<T> type,Intent intent,Bundle state) { ActivityController<T> c=Robolectric.buildActivity(type,intent).create(state).start().resume().visible(); activities.add(c); return c; }
    private List<View> views(View view) { List<View> result=new ArrayList<>(); result.add(view); if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) result.addAll(views(((ViewGroup)view).getChildAt(i))); return result; }
    private View action(Activity activity,String title) { for(View view:views(activity.getWindow().getDecorView())) if(view instanceof TextView && (title.contentEquals(((TextView)view).getText()) || title.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))) return view; throw new AssertionError(title); }
    @Test public void vibrationOnlyPlanUsesAlarmUsageNoRepeatsAndStopsOnPause() throws Exception {
        new OutputPreferenceStore(app.getFilesDir()).save(0,prefs(false)); PreviewAudio audio=audio(); AlarmOutputFlow flow=flow(audio,false);
        assertEquals(AlarmOutputFlow.Outcome.ACCEPTED,flow.alarm(level(false).rule.id,new SyntheticAlarmScenario(AlarmEngine.Kind.HIGH).clock(0)));
        assertTrue(shadowOf(vibrator).isVibrating()); assertEquals(-1,shadowOf(vibrator).getRepeat()); assertArrayEquals(VibrationCatalog.LONG_LONG.timings(),shadowOf(vibrator).getPattern());
        if(Build.VERSION.SDK_INT>=33) assertEquals(VibrationAttributes.USAGE_ALARM,((VibrationAttributes)shadowOf(vibrator).getVibrationAttributesFromLastVibration()).getUsage());
        else assertEquals(AudioAttributes.USAGE_ALARM,shadowOf(vibrator).getAudioAttributesFromLastVibration().getUsage());
        assertTrue(audio.busy()); audio.pause(); assertTrue(shadowOf(vibrator).isCancelled()); assertFalse(audio.busy());
    }
    @Test public void absentVibratorCannotAcceptAVibrationOnlyAlarm() throws Exception {
        shadowOf(vibrator).setHasVibrator(false); new OutputPreferenceStore(app.getFilesDir()).save(0,prefs(false)); PreviewAudio audio=audio();
        assertEquals(AlarmOutputFlow.Outcome.OUTPUT_UNAVAILABLE,flow(audio,false).alarm(level(false).rule.id,new SyntheticAlarmScenario(AlarmEngine.Kind.HIGH).clock(0)));
        assertFalse(audio.busy());
    }
    @Test public void openingOrClosingAnotherPreviewDoesNotCancelTheActiveVibration() {
        PreviewAudio first=audio(),second=audio(); assertTrue(first.vibrate("LONG_LONG")); second.stop(); second.close();
        assertTrue(shadowOf(vibrator).isVibrating()); assertTrue(first.busy()); first.stop(); assertTrue(shadowOf(vibrator).isCancelled());
    }
    @Test public void profileUiSavesIntoASlotAndLoadsOnlyAfterConfirmation() throws Exception {
        ProfileController.apply(app,snapshot()); LivePreferences.set(app,true,true);
        ProfilesActivity activity=open(ProfilesActivity.class,new Intent(app,ProfilesActivity.class),null).get(); action(activity,"Speichern").performClick();
        AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog(); shadowOf(Looper.getMainLooper()).idle(); EditText name=null; for(View v:views(dialog.getWindow().getDecorView())) if(v instanceof EditText) name=(EditText)v;
        assertNotNull(name); name.setText("Testprofil"); dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        assertEquals("Testprofil",new ProfileStore(app.getFilesDir()).load().slots.get(0).name); assertTrue(LivePreferences.alarms(app));
        AppAppearance.set(app,AppAppearance.Mode.LIGHT); action(activity,"Laden").performClick(); assertTrue(LivePreferences.alarms(app));
        ShadowAlertDialog.getLatestAlertDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick(); shadowOf(Looper.getMainLooper()).idle();
        assertFalse(LivePreferences.alarms(app)); assertEquals(AppAppearance.Mode.DARK,AppAppearance.mode(app));
    }
    @Test public void speechAfterShortToneDoesNotCutOffTheLongVibrationPattern() throws Exception {
        new OutputPreferenceStore(app.getFilesDir()).save(0,prefs(true)); PreviewAudio audio=audio(); audio.prepareSpeech();
        TextToSpeech tts=ShadowTextToSpeech.getLastTextToSpeechInstance(); shadowOf(tts).getOnInitListener().onInit(TextToSpeech.SUCCESS); shadowOf(Looper.getMainLooper()).idle();
        assertEquals(AlarmOutputFlow.Outcome.ACCEPTED,flow(audio,true).alarm(level(true).rule.id,new SyntheticAlarmScenario(AlarmEngine.Kind.HIGH).clock(0)));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(550));
        assertNotNull(shadowOf(tts).getLastSpokenText()); assertTrue(shadowOf(vibrator).isVibrating());
        audio.stop(); assertTrue(shadowOf(vibrator).isCancelled());
    }
    @Test public void restoringAProfileDisarmsLiveKeepsRuntimeAndDoesNotRewriteSelectedTones() throws Exception {
        new LevelStore(app.getFilesDir()).save(0,Collections.emptyList()); new OutputPreferenceStore(app.getFilesDir()).save(0,OutputPreferences.defaults());
        LivePreferences.set(app,true,true); LivePreferences.pauseUntil(app,System.currentTimeMillis()+600000); long pause=LivePreferences.pauseUntil(app);
        File runtime=new File(app.getFilesDir(),"live-receipt-runtime-v1"); runtime.mkdirs(); Path state=new File(runtime,"synthetic-sentinel.bin").toPath(); byte[] sentinel={4,9,3}; Files.write(state,sentinel);
        PreviewAudio audio=audio(); assertTrue(audio.vibrate("LONG_LONG")); ProfileController.apply(app,snapshot());
        assertFalse(LivePreferences.alarms(app)); assertTrue(LivePreferences.monitor(app)); assertEquals(pause,LivePreferences.pauseUntil(app)); assertTrue(shadowOf(vibrator).isCancelled());
        assertArrayEquals(sentinel,Files.readAllBytes(state)); assertEquals(AppAppearance.Mode.DARK,AppAppearance.mode(app)); assertEquals("rund",SoundDefaults.preferredSet(app));
        assertEquals(ToneCatalog.uri("samt",AlarmEngine.Kind.HIGH),new LevelStore(app.getFilesDir()).load().levels.get(0).toneUri);
        assertFalse(LivePreferences.nativeDisplay(app)); assertFalse(LivePreferences.publicValue(app)); assertFalse(SettingsTransaction.pending(app.getFilesDir()));
    }
    @Test public void startupRecoversAnInterruptedProfileBeforeAnyLiveConsentCanBeUsed() throws Exception {
        SettingsTransaction.begin(app.getFilesDir(),snapshot()); LivePreferences.set(app,true,true);
        ActivityController<ProfilesActivity> controller=open(ProfilesActivity.class,new Intent(app,ProfilesActivity.class),null);
        assertFalse(LivePreferences.alarms(app)); assertTrue(new OutputPreferenceStore(app.getFilesDir()).load().preferences.vibrationEnabled);
        assertFalse(SettingsTransaction.pending(app.getFilesDir())); assertEquals(AppAppearance.Mode.DARK,AppAppearance.mode(app)); assertNotNull(action(controller.get(),"Profile"));
    }
    @Test public void vibrationStageDraftSurvivesRotationAndCanSaveWithoutToneOrSpeech() throws Exception {
        new OutputPreferenceStore(app.getFilesDir()).save(0,prefs(false)); new LevelStore(app.getFilesDir()).save(0,Collections.singletonList(level(false)));
        Intent intent=new Intent(app,AlarmLevelActivity.class).putExtra("id",level(false).rule.id);
        ActivityController<AlarmLevelActivity> first=open(AlarmLevelActivity.class,intent,null); Spinner pattern=null;
        for(View v:views(first.get().getWindow().getDecorView())) if(v instanceof Spinner) pattern=(Spinner)v;
        assertNotNull(pattern); pattern.setSelection(VibrationCatalog.SHORT_LONG.ordinal()); Bundle state=new Bundle(); first.saveInstanceState(state).pause().stop().destroy(); activities.remove(first);
        AlarmLevelActivity second=open(AlarmLevelActivity.class,intent,state).get(); assertFalse(((CheckBox)action(second,"Ton für diese Stufe")).isChecked());
        action(second,"Stufe speichern").performClick(); AlarmLevel saved=new LevelStore(app.getFilesDir()).load().levels.get(0);
        assertEquals("SHORT_LONG",saved.vibrationPattern); assertFalse(saved.rule.outputs.tone); assertFalse(saved.rule.outputs.speech);
    }
    @Test public void dashboardAllowsTurningOffLastAudioWhenAnEnabledStageHasVibration() throws Exception {
        new LevelStore(app.getFilesDir()).save(0,Collections.singletonList(level(false))); new OutputPreferenceStore(app.getFilesDir()).save(0,prefs(true));
        Activity activity=open(ProbeActivity.class,new Intent(app,ProbeActivity.class),null).get(); action(activity,"Ton").performClick(); action(activity,"Alarme sprechen").performClick();
        OutputPreferences result=new OutputPreferenceStore(app.getFilesDir()).load().preferences; assertFalse(result.tonesEnabled); assertFalse(result.alarmSpeechEnabled); assertTrue(result.vibrationEnabled);
        action(activity,"Vibration").performClick(); assertTrue(new OutputPreferenceStore(app.getFilesDir()).load().preferences.vibrationEnabled);
    }
}
