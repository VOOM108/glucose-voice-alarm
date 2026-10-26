// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.app.*;
import android.content.*;
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
import org.robolectric.android.controller.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(qualifiers="de",sdk={28,34},instrumentedPackages={"de.giesen.camaps.companion.prototype.RuntimeClock"},shadows={AndroidUiTest.TestBellPlayer.class,RecoveryGapPlatformTest.TestClock.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class RecoveryGapPlatformTest {
    private Context app;
    private NotificationManager notifications;
    private ServiceController<LiveAlarmService> service;
    private final List<ActivityController<?>> activities=new ArrayList<>();
    @Before public void before() throws Exception {
        app=RuntimeEnvironment.getApplication(); notifications=app.getSystemService(NotificationManager.class);
        for(String prefs:new String[]{"live_controls_v1",SoundDefaults.PREFS,AppAppearance.PREFS}) app.getSharedPreferences(prefs,0).edit().clear().commit();
        app.getSharedPreferences(CamApsListener.PREFS,0).edit().putBoolean(CamApsListener.ENABLED,true).commit();
        shadowOf(notifications).setNotificationsEnabled(true); shadowOf(notifications).setNotificationListenerAccessGranted(new ComponentName(app,CamApsListener.class),true);
        shadowOf(app.getSystemService(PowerManager.class)).setIgnoringBatteryOptimizations(app.getPackageName(),true); shadowOf(app.getSystemService(ActivityManager.class)).setBackgroundRestricted(false);
        android.provider.Settings.Global.putInt(app.getContentResolver(),android.provider.Settings.Global.BOOT_COUNT,1);
        for(String file:new String[]{LevelStore.FILE_NAME,OutputPreferenceStore.FILE_NAME,SettingsTransaction.FILE_NAME,ProfileStore.FILE_NAME}) new File(app.getFilesDir(),file).delete();
        new File(new File(app.getFilesDir(),"live-receipt-runtime-v1"),AlarmStateStore.FILE_NAME).delete();
        new LevelStore(app.getFilesDir()).save(0,Collections.singletonList(new SyntheticAlarmScenario(AlarmEngine.Kind.FALLING).level));
        GapSettings gap=new GapSettings(true,false,4,5,true,true,ToneCatalog.uri("samt",AlarmEngine.Kind.PERSISTENT_HIGH),null);
        new OutputPreferenceStore(app.getFilesDir()).save(0,new OutputPreferences(AlarmEngine.OutputMode.TONES_ONLY,0,SpeechTemplate.VALUE_DEFAULT).withGap(gap));
        AndroidUiTest.TestBellPlayer.latest=null; AndroidUiTest.TestBellPlayer.created=0; AndroidUiTest.TestBellPlayer.completionDelayMs=0;
        ShadowTextToSpeech.addVoice(new Voice("synthetic-de",Locale.GERMANY,Voice.QUALITY_NORMAL,Voice.LATENCY_NORMAL,false,Collections.emptySet()));
    }
    @After public void after() { for(ActivityController<?> c:activities) c.pause().stop().destroy(); if(service!=null) service.destroy(); }
    private void start(boolean alarms) { LivePreferences.set(app,true,alarms); service=Robolectric.buildService(LiveAlarmService.class).create(); service.startCommand(0,1); shadowOf(Looper.getMainLooper()).idle(); }
    private void receive(int value) { LiveAlarmService.receive("synthetic",RuntimeClock.read(app).wallMs,value,NotificationParser.Trend.DOWN); shadowOf(Looper.getMainLooper()).idle(); }
    private void advance(long seconds) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds)); }
    private List<View> views(View v) { List<View> list=new ArrayList<>(); list.add(v); if(v instanceof ViewGroup) for(int i=0;i<((ViewGroup)v).getChildCount();i++) list.addAll(views(((ViewGroup)v).getChildAt(i))); return list; }
    private View action(Activity activity,String title) { for(View v:views(activity.getWindow().getDecorView())) if(v instanceof TextView && (title.contentEquals(((TextView)v).getText()) || title.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))) return v; throw new AssertionError(title); }
    @Test public void invalidCamapsUpdateCannotEraseGapContextAndWarningPersistsUntilFreshReceipt() {
        start(true); receive(123); LiveAlarmService.invalidCurrentSource(); shadowOf(Looper.getMainLooper()).idle(); advance(238); assertNull(AndroidUiTest.TestBellPlayer.latest);
        advance(2); assertNotNull(AndroidUiTest.TestBellPlayer.latest); Notification warning=shadowOf(notifications).getNotification(LiveNotifications.ALARM_ID);
        assertEquals("Datenlücke",warning.extras.getCharSequence(Notification.EXTRA_TITLE).toString()); assertTrue(warning.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("schnell fallend"));
        assertFalse(warning.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("123"));
        AndroidUiTest.TestBellPlayer playing=AndroidUiTest.TestBellPlayer.latest; LiveAlarmService.invalidCurrentSource(); shadowOf(Looper.getMainLooper()).idle(); assertTrue(playing.playing);
        LiveAlarmService.sourceDisconnected(); shadowOf(Looper.getMainLooper()).idle(); assertTrue(playing.playing);
        advance(10); assertNotNull(shadowOf(notifications).getNotification(LiveNotifications.ALARM_ID));
        receive(125); assertNull(shadowOf(notifications).getNotification(LiveNotifications.ALARM_ID));
    }
    @Test public void noGapWarningAtStartupWithoutARealReceiptOrInDisplayOnlyMode() {
        start(true); advance(300); assertNull(AndroidUiTest.TestBellPlayer.latest); service.destroy(); service=null;
        start(false); receive(123); advance(300); assertNull(AndroidUiTest.TestBellPlayer.latest);
    }
    @Test public void globalPauseAlsoBlocksGapWarningAndUnpauseRechecksIt() {
        start(true); receive(123); LivePreferences.pauseUntil(app,RuntimeClock.read(app).wallMs+600000); LiveAlarmService.pauseChanged(); shadowOf(Looper.getMainLooper()).idle(); advance(245); assertNull(AndroidUiTest.TestBellPlayer.latest);
        LivePreferences.pauseUntil(app,0); LiveAlarmService.pauseChanged(); shadowOf(Looper.getMainLooper()).idle(); advance(2); assertNotNull(AndroidUiTest.TestBellPlayer.latest);
    }
    @Test public void bothLowAndFallingAppearInTheOneActuallySpokenPlan() throws Exception {
        LevelStore levels=new LevelStore(app.getFilesDir()); levels.save(levels.load().revision,Arrays.asList(new SyntheticAlarmScenario(AlarmEngine.Kind.LOW).level,new SyntheticAlarmScenario(AlarmEngine.Kind.FALLING).level));
        OutputPreferenceStore output=new OutputPreferenceStore(app.getFilesDir()); output.save(output.load().revision,OutputPreferences.defaults()); start(true);
        TextToSpeech tts=ShadowTextToSpeech.getLastTextToSpeechInstance(); assertNotNull(tts); shadowOf(tts).getOnInitListener().onInit(TextToSpeech.SUCCESS); shadowOf(Looper.getMainLooper()).idle();
        receive(110); advance(60); receive(98); advance(60); receive(86); advance(60); receive(74); advance(3);
        String spoken=shadowOf(tts).getLastSpokenText(); assertNotNull(spoken); assertTrue(spoken.contains("Niedriggrenze")); assertTrue(spoken.contains("sinkt schnell"));
        Notification alarm=shadowOf(notifications).getNotification(LiveNotifications.ALARM_ID); assertTrue(alarm.extras.getCharSequence(Notification.EXTRA_TITLE).toString().contains("Schnell sinkend"));
    }
    @Test public void gapEditorDraftSurvivesRotationAndDoesNotArmLive() throws Exception {
        ActivityController<GapSettingsActivity> first=Robolectric.buildActivity(GapSettingsActivity.class).create().start().resume().visible(); activities.add(first);
        List<EditText> fields=new ArrayList<>(); for(View v:views(first.get().getWindow().getDecorView())) if(v instanceof EditText) fields.add((EditText)v);
        fields.get(0).setText("6"); fields.get(1).setText("0"); Bundle state=new Bundle(); first.saveInstanceState(state).pause().stop().destroy(); activities.remove(first);
        ActivityController<GapSettingsActivity> second=Robolectric.buildActivity(GapSettingsActivity.class).create(state).start().resume().visible(); activities.add(second);
        action(second.get(),"Speichern").performClick(); GapSettings saved=new OutputPreferenceStore(app.getFilesDir()).load().preferences.gap;
        assertEquals(6,saved.delayMinutes); assertEquals(0,saved.repeatMinutes); assertFalse(LivePreferences.alarms(app));
    }
    private TextToSpeech combinedLevels() throws Exception {
        List<AlarmLevel> list=new ArrayList<>(); list.add(new SyntheticAlarmScenario(AlarmEngine.Kind.LOW).level);
        for(int threshold:new int[]{2,4,6}) list.add(new AlarmLevel(new AlarmEngine.Rule("fall_"+threshold,AlarmEngine.Kind.FALLING,true,threshold,0,true,0,1440,new AlarmEngine.Outputs(true,true,0,0)),"Fallen "+threshold,AlarmLevel.DEFAULT_TONE));
        LevelStore store=new LevelStore(app.getFilesDir()); store.save(store.load().revision,list);
        OutputPreferenceStore output=new OutputPreferenceStore(app.getFilesDir()); output.save(output.load().revision,OutputPreferences.defaults()); start(true);
        TextToSpeech tts=ShadowTextToSpeech.getLastTextToSpeechInstance(); shadowOf(tts).getOnInitListener().onInit(TextToSpeech.SUCCESS); shadowOf(Looper.getMainLooper()).idle(); return tts;
    }
    private void fallingLow() { receive(110); advance(60); receive(98); advance(60); receive(86); advance(60); receive(74); }
    @Test public void newReceiptRefreshesPendingSpeechWithoutRestartingTheTwoBundledBells() throws Exception {
        TextToSpeech tts=combinedLevels(); AndroidUiTest.TestBellPlayer.completionDelayMs=3000; fallingLow();
        AndroidUiTest.TestBellPlayer first=AndroidUiTest.TestBellPlayer.latest; assertNotNull(first); assertEquals(1,AndroidUiTest.TestBellPlayer.created);
        advance(1); receive(73); assertSame(first,AndroidUiTest.TestBellPlayer.latest); assertTrue(first.playing); assertNull(shadowOf(tts).getLastSpokenText());
        Notification n=shadowOf(notifications).getNotification(LiveNotifications.ALARM_ID); assertTrue(n.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("73")); assertTrue((n.flags&Notification.FLAG_ONLY_ALERT_ONCE)!=0);
        advance(10); assertEquals(2,AndroidUiTest.TestBellPlayer.created); String spoken=shadowOf(tts).getLastSpokenText(); assertNotNull(spoken); assertTrue(spoken.contains("73")); assertFalse(spoken.contains("74"));
        assertTrue(spoken.contains("Niedriggrenze")); assertTrue(spoken.contains("sinkt schnell")); advance(20); assertEquals(2,AndroidUiTest.TestBellPlayer.created);
    }
    @Test public void oldAndConflictingReceiptsCannotChangeAlarmOrRestartSound() throws Exception {
        combinedLevels(); AndroidUiTest.TestBellPlayer.completionDelayMs=3000; fallingLow(); long posted=RuntimeClock.read(app).wallMs;
        LiveAlarmService.receive("old",posted-500,86,NotificationParser.Trend.UP); LiveAlarmService.receive("synthetic",posted,72,NotificationParser.Trend.DOWN); shadowOf(Looper.getMainLooper()).idle();
        Notification n=shadowOf(notifications).getNotification(LiveNotifications.ALARM_ID); assertTrue(n.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("74")); assertEquals(1,AndroidUiTest.TestBellPlayer.created);
        advance(10); assertEquals(2,AndroidUiTest.TestBellPlayer.created);
    }
    @Test public void alarmNotificationFollowsCurrentReceiptAfterPlaybackAndClearsWhenConditionsEnd() throws Exception {
        TextToSpeech tts=combinedLevels(); fallingLow(); advance(3); assertNotNull(shadowOf(tts).getLastSpokenText());
        advance(30); receive(72); Notification n=shadowOf(notifications).getNotification(LiveNotifications.ALARM_ID);
        assertTrue(n.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("72")); assertTrue((n.flags&Notification.FLAG_ONLY_ALERT_ONCE)!=0); assertEquals(2,AndroidUiTest.TestBellPlayer.created);
        advance(60); receive(110); advance(60); receive(110); advance(60); receive(110); advance(60); receive(110); assertNull(shadowOf(notifications).getNotification(LiveNotifications.ALARM_ID));
    }
    @Test public void pauseDiscardsUpdatedPendingSpeechAndDoesNotRestartCoveredStages() throws Exception {
        TextToSpeech tts=combinedLevels(); AndroidUiTest.TestBellPlayer.completionDelayMs=3000; fallingLow(); advance(1); receive(73);
        LivePreferences.pauseUntil(app,RuntimeClock.read(app).wallMs+600000); LiveAlarmService.pauseChanged(); shadowOf(Looper.getMainLooper()).idle(); advance(20);
        assertNull(shadowOf(tts).getLastSpokenText()); assertTrue(AndroidUiTest.TestBellPlayer.latest.released); assertEquals(1,AndroidUiTest.TestBellPlayer.created); assertNull(shadowOf(notifications).getNotification(LiveNotifications.ALARM_ID));
    }
    private android.service.notification.StatusBarNotification posted(long time,String text) {
        Notification n=new Notification.Builder(app,"synthetic").setSmallIcon(R.drawable.ic_xcam_status).setOngoing(true).setContentText(text).build();
        return new android.service.notification.StatusBarNotification("com.camdiab.fx_alert.mgdl","com.camdiab.fx_alert.mgdl",1,null,1000,0,0,n,android.os.UserHandle.getUserHandleForUid(1000),time);
    }
    @Test public void listenerRejectsOlderUpdatesAndRemovalBeforeDashboardOrAlarmPublication() {
        ServiceController<CamApsListener> listener=Robolectric.buildService(CamApsListener.class).create();
        try {
            listener.get().onNotificationPosted(posted(RuntimeClock.read(app).wallMs,"123 mg/dl →")); advance(1); long current=RuntimeClock.read(app).wallMs;
            listener.get().onNotificationPosted(posted(current,"124 mg/dl →")); assertEquals(124,ProbeState.latest.value);
            listener.get().onNotificationPosted(posted(current-500,"122 mg/dl ↓")); assertEquals(124,ProbeState.latest.value);
            listener.get().onNotificationRemoved(posted(current-500,"122 mg/dl ↓")); assertEquals(124,ProbeState.latest.value);
            listener.get().onNotificationPosted(posted(current,"125 mg/dl ↑")); assertEquals(124,ProbeState.latest.value);
            advance(1); listener.get().onNotificationPosted(posted(RuntimeClock.read(app).wallMs,"124 mg/dl →")); assertEquals(124,ProbeState.latest.value);
        } finally { listener.destroy(); }
    }
    @Test public void lastGlobalRouteCannotBeDisabledWhenTheGapWouldBecomeSilent() throws Exception {
        OutputPreferenceStore store=new OutputPreferenceStore(app.getFilesDir()); OutputPreferences p=store.load().preferences;
        GapSettings gap=new GapSettings(true,false,4,5,true,false,AlarmLevel.DEFAULT_TONE,null); store.save(store.load().revision,p.withGap(gap));
        ActivityController<ProbeActivity> c=Robolectric.buildActivity(ProbeActivity.class).create().start().resume().visible(); activities.add(c);
        OutputModeUi.channel(c.get(),0,false,() -> {}); assertTrue(store.load().preferences.tonesEnabled);
    }
    private TextToSpeech gapWithBothRates(boolean low) throws Exception {
        List<AlarmLevel> list=new ArrayList<>(); if(low) list.add(new SyntheticAlarmScenario(AlarmEngine.Kind.LOW).level);
        list.add(new SyntheticAlarmScenario(AlarmEngine.Kind.FALLING).level); list.add(new SyntheticAlarmScenario(AlarmEngine.Kind.RISING).level);
        LevelStore store=new LevelStore(app.getFilesDir()); store.save(store.load().revision,list);
        OutputPreferenceStore out=new OutputPreferenceStore(app.getFilesDir()); out.save(out.load().revision,OutputPreferences.defaults().withGap(new GapSettings(true,false,4,5,true,true,ToneCatalog.uri("samt",AlarmEngine.Kind.PERSISTENT_HIGH),null)));
        start(true); TextToSpeech tts=ShadowTextToSpeech.getLastTextToSpeechInstance(); shadowOf(tts).getOnInitListener().onInit(TextToSpeech.SUCCESS); shadowOf(Looper.getMainLooper()).idle(); return tts;
    }
    @Test public void aFlatBurstAfterGapCannotCreateAlternatingRateAlarmsButRealMinutesStillCan() throws Exception {
        gapWithBothRates(false); receive(123); advance(245); assertEquals(1,AndroidUiTest.TestBellPlayer.created);
        for(int i=0;i<6;i++) { advance(1); receive(123); } advance(20);
        assertEquals(1,AndroidUiTest.TestBellPlayer.created); assertNull(shadowOf(notifications).getNotification(LiveNotifications.ALARM_ID));
        advance(60); receive(130); advance(60); receive(137); advance(60); receive(144); advance(3); assertEquals(2,AndroidUiTest.TestBellPlayer.created);
        assertTrue(shadowOf(notifications).getNotification(LiveNotifications.ALARM_ID).extras.getCharSequence(Notification.EXTRA_TITLE).toString().contains("steigend"));
    }
    @Test public void returningLowDuringGapToneCancelsGapSpeechWithoutFalseRateToneTrain() throws Exception {
        TextToSpeech tts=gapWithBothRates(true); AndroidUiTest.TestBellPlayer.completionDelayMs=3000; receive(123); advance(240);
        AndroidUiTest.TestBellPlayer gapPlayer=AndroidUiTest.TestBellPlayer.latest; assertTrue(gapPlayer.playing); receive(74); assertTrue(gapPlayer.released);
        for(int i=0;i<4;i++) { advance(1); receive(74); } advance(10);
        assertEquals(2,AndroidUiTest.TestBellPlayer.created); String text=shadowOf(tts).getLastSpokenText(); assertNotNull(text); assertTrue(text.contains("Niedriggrenze")); assertFalse(text.contains("keine neuen Werte")); assertFalse(text.contains("schnell"));
    }
    @Test public void missingRateHistoryAfterGapDoesNotInventAnEndAndRearmTheSameFallingEpisode() throws Exception {
        gapWithBothRates(false); receive(160); advance(60); receive(150); advance(60); receive(140); advance(60); receive(130); advance(3);
        assertEquals(1,AndroidUiTest.TestBellPlayer.created); advance(242); assertEquals(2,AndroidUiTest.TestBellPlayer.created);
        receive(120); advance(60); receive(110); advance(60); receive(100); advance(60); receive(90); advance(5);
        assertEquals(2,AndroidUiTest.TestBellPlayer.created);
    }
    @Implements(value=RuntimeClock.class,isInAndroidSdk=false) public static class TestClock {
        @Implementation protected static AlarmStateStore.Clock read(Context context) {
            long elapsed=SystemClock.elapsedRealtime(); return new AlarmStateStore.Clock(10000000L+elapsed,elapsed,1);
        }
    }
    @Test public void dashboardRequestsReadyLiveOutputWithoutNavigation() {
        ActivityController<ProbeActivity> c=Robolectric.buildActivity(ProbeActivity.class).create().start().resume().visible(); activities.add(c);
        assertNull(shadowOf(c.get()).getNextStartedActivity()); action(c.get(),"Live-Alarme").performClick();
        assertTrue(LivePreferences.alarms(app)); assertNull(shadowOf(c.get()).getNextStartedActivity());
        Intent start=shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService(); assertNotNull(start); assertEquals(new ComponentName(app,LiveAlarmService.class),start.getComponent());
        assertEquals(View.GONE,findTag(c.get(),"activation_hint").getVisibility());
    }
    private View findTag(Activity activity,String tag) { for(View v:views(activity.getWindow().getDecorView())) if(tag.equals(v.getTag())) return v; throw new AssertionError(tag); }
    @Test public void missingPermissionShowsInlineLinkAndNeverStartsOrNavigatesAutomatically() {
        shadowOf(notifications).setNotificationListenerAccessGranted(new ComponentName(app,CamApsListener.class),false);
        ActivityController<ProbeActivity> c=Robolectric.buildActivity(ProbeActivity.class).create().start().resume().visible(); activities.add(c);
        action(c.get(),"Live-Alarme").performClick(); assertFalse(LivePreferences.alarms(app)); assertNull(shadowOf(c.get()).getNextStartedActivity());
        TextView hint=(TextView)findTag(c.get(),"activation_hint"); assertEquals(View.VISIBLE,hint.getVisibility()); assertEquals(AppAppearance.colors(c.get()).error,hint.getCurrentTextColor());
        hint.performClick(); assertEquals(new ComponentName(app,ReadinessActivity.class),shadowOf(c.get()).getNextStartedActivity().getComponent());
    }
    @Test public void missingLevelShowsAnInlineSettingsLinkWithoutChangingSavedConfiguration() throws Exception {
        LevelStore store=new LevelStore(app.getFilesDir()); store.save(store.load().revision,Collections.emptyList());
        OutputPreferenceStore out=new OutputPreferenceStore(app.getFilesDir()); out.save(out.load().revision,OutputPreferences.defaults()); long revision=out.load().revision;
        ActivityController<ProbeActivity> c=Robolectric.buildActivity(ProbeActivity.class).create().start().resume().visible(); activities.add(c); action(c.get(),"Live-Alarme").performClick();
        assertFalse(LivePreferences.alarms(app)); assertEquals(revision,out.load().revision); assertNull(shadowOf(c.get()).getNextStartedActivity());
        View hint=findTag(c.get(),"activation_hint"); assertEquals(View.VISIBLE,hint.getVisibility()); hint.performClick();
        assertEquals(new ComponentName(app,AlarmLevelsActivity.class),shadowOf(c.get()).getNextStartedActivity().getComponent());
    }
    @Test public void anUnstartedServiceDoesNotLeaveTheDashboardSwitchPermanentlyChecked() {
        ActivityController<ProbeActivity> c=Robolectric.buildActivity(ProbeActivity.class).create().start().resume().visible(); activities.add(c); action(c.get(),"Live-Alarme").performClick();
        assertTrue(((Switch)action(c.get(),"Live-Alarme")).isChecked()); advance(6);
        assertFalse(((Switch)action(c.get(),"Live-Alarme")).isChecked()); assertEquals(View.VISIBLE,findTag(c.get(),"activation_hint").getVisibility()); assertNull(shadowOf(c.get()).getNextStartedActivity());
    }
}
