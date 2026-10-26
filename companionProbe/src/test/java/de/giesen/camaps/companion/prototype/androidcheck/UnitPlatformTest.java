// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.*;
import java.time.Duration;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(qualifiers="de",sdk={28,34},instrumentedPackages={"de.giesen.camaps.companion.prototype.RuntimeClock"},shadows={RecoveryGapPlatformTest.TestClock.class,AndroidUiTest.TestBellPlayer.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class UnitPlatformTest {
    @org.junit.Before public void acknowledgeForExistingFeatureTests(){ConsentTestSupport.accept(org.robolectric.RuntimeEnvironment.getApplication());}
    private Context app;private final List<ActivityController<?>> activities=new ArrayList<>();private ServiceController<LiveAlarmService> service;private ServiceController<CamApsListener> listener;
    @Before public void before() throws Exception {
        app=RuntimeEnvironment.getApplication();app.deleteDatabase("probe-observations.db");AppText.configure(Locale.GERMAN);ProbeState.latest=null;ProbeState.vectorCandidate=null;
        for(String name:new String[]{LevelStore.FILE_NAME,OutputPreferenceStore.FILE_NAME,ProfileStore.FILE_NAME,SettingsTransaction.FILE_NAME})new File(app.getFilesDir(),name).delete();
        new File(new File(app.getFilesDir(),"live-receipt-runtime-v1"),AlarmStateStore.FILE_NAME).delete();
        for(String name:new String[]{"live_controls_v1",SoundDefaults.PREFS,AppAppearance.PREFS,CamApsListener.PREFS,UnitPreferences.PREFS})app.getSharedPreferences(name,0).edit().clear().commit();
        app.getSharedPreferences(CamApsListener.PREFS,0).edit().putBoolean(CamApsListener.ENABLED,true).commit();
        NotificationManager manager=app.getSystemService(NotificationManager.class);shadowOf(manager).setNotificationsEnabled(true);shadowOf(manager).setNotificationListenerAccessGranted(new ComponentName(app,CamApsListener.class),true);
        shadowOf(app.getSystemService(PowerManager.class)).setIgnoringBatteryOptimizations(app.getPackageName(),true);shadowOf(app.getSystemService(ActivityManager.class)).setBackgroundRestricted(false);
        android.provider.Settings.Global.putInt(app.getContentResolver(),android.provider.Settings.Global.BOOT_COUNT,1);
    }
    @After public void after(){for(ActivityController<?> c:activities)c.pause().stop().destroy();if(service!=null)service.destroy();if(listener!=null)listener.destroy();ProbeState.latest=null;}
    private <T extends Activity> ActivityController<T> open(Class<T> type,Bundle state){ActivityController<T> c=Robolectric.buildActivity(type).create(state).start().resume().visible();activities.add(c);shadowOf(Looper.getMainLooper()).idle();return c;}
    private View tag(Activity a,String tag){return a.getWindow().getDecorView().findViewWithTag(tag);}
    private List<View> views(View v){List<View> result=new ArrayList<>();result.add(v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)result.addAll(views(((ViewGroup)v).getChildAt(i)));return result;}
    private void save(Activity a){for(View v:views(a.getWindow().getDecorView()))if("Ausgabe-Einstellungen speichern".equals(v.getContentDescription())){v.performClick();return;}throw new AssertionError("save");}
    private void action(Activity a,String title){for(View v:views(a.getWindow().getDecorView()))if(v instanceof TextView && (title.contentEquals(((TextView)v).getText()) || title.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))){v.performClick();return;}throw new AssertionError(title);}
    private void confirm(boolean positive){AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);dialog.getButton(positive?android.content.DialogInterface.BUTTON_POSITIVE:android.content.DialogInterface.BUTTON_NEGATIVE).performClick();shadowOf(Looper.getMainLooper()).idle();}
    private void send(String pkg,String text){if(listener==null)listener=Robolectric.buildService(CamApsListener.class).create();shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20));long wall=RuntimeClock.read(app).wallMs;Notification n=new Notification.Builder(app,"synthetic").setSmallIcon(R.drawable.ic_xcam_status).setOngoing(true).setContentText(text).build();listener.get().onNotificationPosted(new android.service.notification.StatusBarNotification(pkg,pkg,1,null,1000,0,0,n,UserHandle.getUserHandleForUid(1000),wall));shadowOf(Looper.getMainLooper()).idle();}
    private OutputPreferences output() throws Exception{return new OutputPreferenceStore(app.getFilesDir()).load().preferences;}
    private void mode(GlucoseUnit.Mode value) throws Exception{OutputPreferenceStore store=new OutputPreferenceStore(app.getFilesDir());OutputPreferenceStore.Snapshot s=store.load();store.save(s.revision,s.preferences.withUnitMode(value));}
    @Test public void firstMmolReceiptFixesSourceAcrossListenerRestart(){
        send("com.camdiab.fx_alert.mmoll","5,1 mmol/L →");assertEquals(GlucoseUnit.MMOL,UnitPreferences.source(app));assertEquals(91.89282,ProbeState.latest.value,1e-10);
        listener.destroy();listener=null;send("com.camdiab.fx_alert.mmoll","5.2 mmol/L →");assertEquals(GlucoseUnit.MMOL,UnitPreferences.source(app));assertEquals(5.2*18.0182,ProbeState.latest.value,1e-10);
    }
    @Test public void changedSourceUnitIsRejectedAndVisibleWithoutAutomaticRelearning(){
        send("com.camdiab.fx_alert.mmoll","5.1 mmol/L →");send("com.camdiab.fx_alert.mgdl","100 mg/dl →");assertNull(ProbeState.latest);assertEquals(GlucoseUnit.MMOL,UnitPreferences.source(app));assertTrue(UnitPreferences.conflict(app));
        ProbeActivity a=open(ProbeActivity.class,null).get();assertEquals(View.VISIBLE,tag(a,"unit_warning").getVisibility());
    }
    @Test public void conflictingPayloadNeverLearnsOrProducesAReading(){
        send("com.camdiab.fx_alert.mmoll","5.1 mg/dl →");assertNull(ProbeState.latest);assertNull(UnitPreferences.source(app));assertTrue(UnitPreferences.conflict(app));
    }
    @Test public void resettingDetectionNeedsConfirmationStopsLiveAndAllowsANewUnit(){
        UnitPreferences.accept(app,GlucoseUnit.MMOL);LivePreferences.set(app,true,true);DeveloperOptionsActivity editor=open(DeveloperOptionsActivity.class,null).get();action(editor,"CamAPS-Einheit neu erkennen");confirm(false);assertEquals(GlucoseUnit.MMOL,UnitPreferences.source(app));assertTrue(LivePreferences.alarms(app));
        action(editor,"CamAPS-Einheit neu erkennen");confirm(true);assertNull(UnitPreferences.source(app));assertFalse(LivePreferences.alarms(app));send("com.camdiab.fx_alert.mgdl","100 mg/dl →");assertEquals(GlucoseUnit.MGDL,UnitPreferences.source(app));assertEquals(100,ProbeState.latest.value,0);
    }
    @Test public void overrideNeedsConfirmationAndDoesNotChangeStoredThresholds() throws Exception{
        LevelStore levels=new LevelStore(app.getFilesDir());levels.save(0,Collections.singletonList(new SyntheticAlarmScenario(AlarmEngine.Kind.LOW).level));byte[] old=java.nio.file.Files.readAllBytes(new File(app.getFilesDir(),LevelStore.FILE_NAME).toPath());
        DeveloperOptionsActivity editor=open(DeveloperOptionsActivity.class,null).get();((Spinner)tag(editor,"unit_mode")).setSelection(2);action(editor,"Einheit übernehmen");assertEquals(GlucoseUnit.Mode.AUTO,output().unitMode);confirm(false);assertEquals(GlucoseUnit.Mode.AUTO,output().unitMode);
        action(editor,"Einheit übernehmen");confirm(true);assertEquals(GlucoseUnit.Mode.MMOL,output().unitMode);assertArrayEquals(old,java.nio.file.Files.readAllBytes(new File(app.getFilesDir(),LevelStore.FILE_NAME).toPath()));assertFalse(LivePreferences.alarms(app));
    }
    @Test public void dashboardAndNotificationUseOneChosenUnit() throws Exception{
        send("com.camdiab.fx_alert.mmoll","5.1 mmol/L →");ProbeActivity a=open(ProbeActivity.class,null).get();assertEquals("5,1 →",((TextView)tag(a,"dashboard_value")).getText().toString());assertEquals("mmol/l",((TextView)tag(a,"dashboard_unit")).getText().toString());
        LiveNotifications notifications=new LiveNotifications(app);Notification n=notifications.status(91.89282,NotificationParser.Trend.FLAT,System.currentTimeMillis(),true,"");assertEquals("5,1 →",n.extras.getCharSequence(Notification.EXTRA_TITLE).toString());
        mode(GlucoseUnit.Mode.MGDL);Notification converted=notifications.status(91.89282,NotificationParser.Trend.FLAT,System.currentTimeMillis(),true,"");assertEquals("92 →",converted.extras.getCharSequence(Notification.EXTRA_TITLE).toString());
    }
    private AlarmLevel ownLevel() throws Exception{AlarmLevel level=new SyntheticAlarmScenario(AlarmEngine.Kind.LOW).level;new LevelStore(app.getFilesDir()).save(0,Collections.singletonList(level));return level;}
    private ActivityController<AlarmLevelActivity> levelEditor(AlarmLevel level,Bundle saved){ActivityController<AlarmLevelActivity> c=Robolectric.buildActivity(AlarmLevelActivity.class,new Intent(app,AlarmLevelActivity.class).putExtra("id",level.rule.id)).create(saved).start().resume().visible();activities.add(c);return c;}
    private List<EditText> fields(Activity a){List<EditText> result=new ArrayList<>();for(View v:views(a.getWindow().getDecorView()))if(v instanceof EditText)result.add((EditText)v);return result;}
    @Test public void openingAndSavingAnMmolThresholdDoesNotRoundTheStoredBoundary() throws Exception{
        mode(GlucoseUnit.Mode.MMOL);AlarmLevel level=ownLevel();AlarmLevelActivity editor=levelEditor(level,null).get();action(editor,"Stufe speichern");assertEquals(level.rule.threshold,new LevelStore(app.getFilesDir()).load().find(level.rule.id).rule.threshold,0);
    }
    @Test public void editedThresholdUsesItsCapturedUnitAfterRotationAndGlobalUnitChange() throws Exception{
        mode(GlucoseUnit.Mode.MMOL);AlarmLevel level=ownLevel();ActivityController<AlarmLevelActivity> first=levelEditor(level,null);fields(first.get()).get(1).setText("5,1");Bundle state=new Bundle();first.saveInstanceState(state).pause().stop().destroy();activities.remove(first);mode(GlucoseUnit.Mode.MGDL);
        AlarmLevelActivity second=levelEditor(level,state).get();action(second,"Stufe speichern");assertEquals(91.89282,new LevelStore(app.getFilesDir()).load().find(level.rule.id).rule.threshold,1e-10);
    }
    @Test public void oldIntegerDiagnosticDatabaseMigratesAndRetainsFractionalNewValues() throws Exception{
        File file=app.getDatabasePath("probe-observations.db");file.getParentFile().mkdirs();android.database.sqlite.SQLiteDatabase db=android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file,null);db.execSQL("CREATE TABLE observation (id INTEGER PRIMARY KEY,notification_key TEXT NOT NULL,posted_ms INTEGER NOT NULL,received_ms INTEGER NOT NULL,mgdl INTEGER NOT NULL,trend TEXT NOT NULL,UNIQUE(notification_key,posted_ms,mgdl,trend))");long wall=RuntimeClock.read(app).wallMs;db.execSQL("INSERT INTO observation VALUES(1,'synthetic',?,?,123,'FLAT')",new Object[]{wall-1000,wall-1000});db.setVersion(1);db.close();
        ObservationStore store=new ObservationStore(app);try{store.insert("decimal",wall,wall,new NotificationParser.Result(91.89282,NotificationParser.Trend.FLAT,"synthetic",GlucoseUnit.MMOL));List<DiagnosticSeries.Sample> points=store.receipts(wall);assertEquals(2,points.size());assertEquals(123,points.get(0).value,0);assertEquals(91.89282,points.get(1).value,0);assertEquals(2,store.getReadableDatabase().getVersion());}finally{store.close();}
    }
    @Test public void normalOutputEditorPreservesOverrideAndProfilesNeverChangeLearnedSource() throws Exception{
        UnitPreferences.accept(app,GlucoseUnit.MMOL);mode(GlucoseUnit.Mode.MGDL);OutputSettingsActivity editor=open(OutputSettingsActivity.class,null).get();save(editor);assertEquals(GlucoseUnit.Mode.MGDL,output().unitMode);
        ConfigurationSnapshot settings=ProfileController.capture(app);ProfileController.apply(app,settings);assertEquals(GlucoseUnit.MMOL,UnitPreferences.source(app));assertEquals(GlucoseUnit.Mode.MGDL,output().unitMode);assertFalse(LivePreferences.alarms(app));
    }
    @Test public void syntheticAlarmPreviewAlsoFollowsTheAutomaticMmolUnit() throws Exception{
        org.robolectric.shadows.ShadowTextToSpeech.addVoice(new android.speech.tts.Voice("synthetic-unit-de",Locale.GERMANY,android.speech.tts.Voice.QUALITY_NORMAL,android.speech.tts.Voice.LATENCY_NORMAL,false,Collections.emptySet()));
        UnitPreferences.accept(app,GlucoseUnit.MMOL);new OutputPreferenceStore(app.getFilesDir()).save(0,OutputPreferences.defaults().withChannels(false,true,false));SyntheticAlarmActivity editor=open(SyntheticAlarmActivity.class,null).get();
        android.speech.tts.TextToSpeech tts=org.robolectric.shadows.ShadowTextToSpeech.getLastTextToSpeechInstance();assertNotNull(tts);shadowOf(tts).getOnInitListener().onInit(android.speech.tts.TextToSpeech.SUCCESS);shadowOf(Looper.getMainLooper()).idle();
        View trigger=tag(editor,"synthetic_alarm_try");assertNotNull(trigger);trigger.performClick();shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
        String text=shadowOf(tts).getLastSpokenText();assertNotNull("Prepared synthetic offline voice must receive speech",text);
        assertTrue(text,text.contains("Millimol pro Liter"));assertTrue(text,text.contains("synthetischen Daten"));
    }
}
