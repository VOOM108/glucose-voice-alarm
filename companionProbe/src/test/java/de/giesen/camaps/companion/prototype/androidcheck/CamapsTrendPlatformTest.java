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
public class CamapsTrendPlatformTest {
    @org.junit.Before public void acknowledgeForExistingFeatureTests(){ConsentTestSupport.accept(org.robolectric.RuntimeEnvironment.getApplication());}
    private Context app;private final List<ActivityController<?>> activities=new ArrayList<>();private ServiceController<LiveAlarmService> service;
    @Before public void before() throws Exception {
        app=RuntimeEnvironment.getApplication();AppText.configure(Locale.GERMAN);ProbeState.latest=null;ProbeState.vectorCandidate=null;
        for(String name:new String[]{LevelStore.FILE_NAME,OutputPreferenceStore.FILE_NAME,ProfileStore.FILE_NAME,SettingsTransaction.FILE_NAME})new File(app.getFilesDir(),name).delete();
        new File(new File(app.getFilesDir(),"live-receipt-runtime-v1"),AlarmStateStore.FILE_NAME).delete();
        for(String name:new String[]{"live_controls_v1",SoundDefaults.PREFS,AppAppearance.PREFS,CamApsListener.PREFS})app.getSharedPreferences(name,0).edit().clear().commit();
        app.getSharedPreferences(CamApsListener.PREFS,0).edit().putBoolean(CamApsListener.ENABLED,true).commit();
        NotificationManager manager=app.getSystemService(NotificationManager.class);shadowOf(manager).setNotificationsEnabled(true);shadowOf(manager).setNotificationListenerAccessGranted(new ComponentName(app,CamApsListener.class),true);
        shadowOf(app.getSystemService(PowerManager.class)).setIgnoringBatteryOptimizations(app.getPackageName(),true);shadowOf(app.getSystemService(ActivityManager.class)).setBackgroundRestricted(false);
        android.provider.Settings.Global.putInt(app.getContentResolver(),android.provider.Settings.Global.BOOT_COUNT,1);
    }
    @After public void after(){for(ActivityController<?> c:activities)c.pause().stop().destroy();if(service!=null)service.destroy();ProbeState.latest=null;}
    private <T extends Activity> ActivityController<T> open(Class<T> type,Bundle state){ActivityController<T> c=Robolectric.buildActivity(type).create(state).start().resume().visible();activities.add(c);shadowOf(Looper.getMainLooper()).idle();return c;}
    private View tag(Activity a,String tag){return a.getWindow().getDecorView().findViewWithTag(tag);}
    private List<View> views(View v){List<View> result=new ArrayList<>();result.add(v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)result.addAll(views(((ViewGroup)v).getChildAt(i)));return result;}
    private void save(Activity a){for(View v:views(a.getWindow().getDecorView()))if("Ausgabe-Einstellungen speichern".equals(v.getContentDescription())){v.performClick();return;}throw new AssertionError("save");}
    private void observation(NotificationParser.Trend original){long wall=System.currentTimeMillis();List<HistoryAnalysis.Point> data=Arrays.asList(HistoryAnalysis.Point.received(120,wall-180000),HistoryAnalysis.Point.received(117,wall-120000),HistoryAnalysis.Point.received(114,wall-60000),HistoryAnalysis.Point.received(111,wall));ProbeState.latest=new ProbeState.Observation(111,original,SystemClock.elapsedRealtime(),wall,"synthetic",ReceiptRate.calculate(data,wall));}
    @Test public void dashboardUsesOneCamapsArrowAndKeepsSeparateNumericRate(){
        observation(NotificationParser.Trend.UP);ProbeActivity a=open(ProbeActivity.class,null).get();
        assertEquals("111 ↑",((TextView)tag(a,"dashboard_value")).getText().toString());
        assertTrue(((TextView)tag(a,"camaps_trend")).getText().toString().contains("Schnell steigend"));assertNull(tag(a,"calculated_trend"));
        TextView rate=(TextView)tag(a,"calculated_rate");assertEquals(View.VISIBLE,rate.getVisibility());assertEquals("-3,0 mg/dl/min",rate.getText().toString());assertEquals(1,rate.getMaxLines());
    }
    @Test public void editorHasNoSourceChoicesAndPreservesCustomDraftOnRotation() throws Exception {
        ActivityController<OutputSettingsActivity> first=open(OutputSettingsActivity.class,null);assertNull(tag(first.get(),"aod_trend_source"));assertNull(tag(first.get(),"value_trend_source"));
        List<EditText> fields=new ArrayList<>();for(View v:views(first.get().getWindow().getDecorView()))if(v instanceof EditText)fields.add((EditText)v);
        fields.get(1).setText("Eigener Wert {wert} {trend}");
        Bundle state=new Bundle();first.saveInstanceState(state).pause().stop().destroy();activities.remove(first);OutputSettingsActivity second=open(OutputSettingsActivity.class,state).get();save(second);
        OutputPreferences p=new OutputPreferenceStore(app.getFilesDir()).load().preferences;assertEquals("Eigener Wert {wert} {trend}",p.valueTemplate);assertFalse(LivePreferences.alarms(app));
    }
    private void receive(int value){LiveAlarmService.receive("synthetic",RuntimeClock.read(app).wallMs,value,NotificationParser.Trend.UP);shadowOf(Looper.getMainLooper()).idle();}
    private String title(){return shadowOf(app.getSystemService(NotificationManager.class)).getNotification(LiveNotifications.STATUS_ID).extras.getCharSequence(Notification.EXTRA_TITLE).toString();}
    @Test public void backgroundDisplayKeepsCamapsArrowDespiteOppositeNumericRate() throws Exception {
        LivePreferences.set(app,true,false);service=Robolectric.buildService(LiveAlarmService.class).create();service.startCommand(0,1);shadowOf(Looper.getMainLooper()).idle();
        receive(120);assertEquals("120 ↑",title());for(int value:new int[]{117,114,111}){shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(1));receive(value);}assertEquals("111 ↑",title());
    }
    @Test public void listenerPublishesCamapsArrowAndIndependentNumericDashboardRate() throws Exception {
        LivePreferences.set(app,true,false);service=Robolectric.buildService(LiveAlarmService.class).create();service.startCommand(0,1);shadowOf(Looper.getMainLooper()).idle();
        ServiceController<CamApsListener> listener=Robolectric.buildService(CamApsListener.class).create();
        try {int index=0;for(int value:new int[]{120,117,114,111}){if(index++>0)shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(1));Notification n=new Notification.Builder(app,"synthetic").setSmallIcon(R.drawable.ic_xcam_status).setOngoing(true).setContentText(value+" mg/dl ↑").build();long time=RuntimeClock.read(app).wallMs;listener.get().onNotificationPosted(new android.service.notification.StatusBarNotification("com.camdiab.fx_alert.mgdl","com.camdiab.fx_alert.mgdl",1,null,1000,0,0,n,android.os.UserHandle.getUserHandleForUid(1000),time));shadowOf(Looper.getMainLooper()).idle();}
            assertEquals(NotificationParser.Trend.UP,ProbeState.latest.trend);assertEquals(-3,ProbeState.latest.receiptRate.rate,0);assertEquals("111 ↑",title());
        } finally {listener.destroy();}
    }
    @Test public void missingCamapsArrowLeavesNumericRateButDoesNotInventDirection(){
        observation(NotificationParser.Trend.UNKNOWN);ProbeActivity a=open(ProbeActivity.class,null).get();
        assertTrue(((TextView)tag(a,"camaps_trend")).getText().toString().contains("Kein Trendpfeil"));assertEquals("111",((TextView)tag(a,"dashboard_value")).getText().toString());assertNull(tag(a,"calculated_trend"));
        assertEquals(View.VISIBLE,tag(a,"calculated_rate").getVisibility());assertTrue(((TextView)tag(a,"calculated_rate")).getText().toString().contains("mg/dl/min"));
    }
    @Test public void staleReadingHidesNumericRate(){
        observation(NotificationParser.Trend.UP);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(5));ProbeActivity a=open(ProbeActivity.class,null).get();
        assertEquals(View.GONE,tag(a,"calculated_rate").getVisibility());assertEquals("",((TextView)tag(a,"calculated_rate")).getText().toString());
    }
    @Test public void individualSpeechSwitchesSurviveRotationAndSaveWithoutEnablingLive() throws Exception {
        ActivityController<OutputSettingsActivity> first=open(OutputSettingsActivity.class,null);
        for(int index:new int[]{0,2,3})((CheckBox)tag(first.get(),"trend_speech_"+index)).setChecked(false);
        Bundle state=new Bundle();first.saveInstanceState(state).pause().stop().destroy();activities.remove(first);OutputSettingsActivity second=open(OutputSettingsActivity.class,state).get();
        for(int index=0;index<7;index++)assertEquals(index!=0 && index!=2 && index!=3,((CheckBox)tag(second,"trend_speech_"+index)).isChecked());save(second);
        TrendSpeech saved=new OutputPreferenceStore(app.getFilesDir()).load().preferences.trendSpeech;assertEquals("",saved.word(NotificationParser.Trend.FLAT));assertEquals("stabil",saved.words.get(2));assertFalse(LivePreferences.alarms(app));
        observation(NotificationParser.Trend.FLAT);ProbeActivity dashboard=open(ProbeActivity.class,null).get();assertEquals("111 →",((TextView)tag(dashboard,"dashboard_value")).getText().toString());
    }
    @Test public void changingLanguageAndResettingTextDoesNotReenableMutedWords() throws Exception {
        OutputPreferenceStore store=new OutputPreferenceStore(app.getFilesDir());List<Boolean> enabled=new ArrayList<>(Collections.nCopies(7,true));enabled.set(2,false);
        store.save(0,OutputPreferences.defaults().withTrendSpeech(new TrendSpeech(TrendSpeech.defaults().words,enabled)));
        OutputSettingsActivity editor=open(OutputSettingsActivity.class,null).get();((Spinner)tag(editor,"speech_language")).setSelection(1);shadowOf(Looper.getMainLooper()).idle();
        for(View v:views(editor.getWindow().getDecorView()))if(v instanceof TextView && "Standardtexte einsetzen".contentEquals(((TextView)v).getText()))v.performClick();
        org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle();save(editor);
        OutputPreferences saved=store.load().preferences;assertEquals("en",saved.speechLanguage);assertEquals("steady",saved.trendSpeech.words.get(2));assertFalse(saved.trendSpeech.enabled.get(2));
    }
}
