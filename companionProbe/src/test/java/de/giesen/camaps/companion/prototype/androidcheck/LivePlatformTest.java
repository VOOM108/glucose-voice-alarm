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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Actual background service and private Android storage; all values and system audio are simulated. */
@RunWith(RobolectricTestRunner.class)
@Config(qualifiers="de",sdk={28,34},shadows={AndroidUiTest.TestRingtones.class,AndroidUiTest.TestRingtone.class,AndroidUiTest.TestBellPlayer.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class LivePlatformTest {
    @org.junit.Before public void acknowledgeForExistingFeatureTests(){ConsentTestSupport.accept(org.robolectric.RuntimeEnvironment.getApplication());}
    private Context app;
    private ServiceController<LiveAlarmService> service;
    private NotificationManager notifications;
    @Before public void before() throws Exception {
        app=RuntimeEnvironment.getApplication(); notifications=app.getSystemService(NotificationManager.class);
        app.getSharedPreferences("live_controls_v1",Context.MODE_PRIVATE).edit().clear().commit();
        app.getSharedPreferences(SoundDefaults.PREFS,Context.MODE_PRIVATE).edit().clear().commit();
        app.getSharedPreferences(CamApsListener.PREFS,Context.MODE_PRIVATE).edit().putBoolean(CamApsListener.ENABLED,true).commit();
        shadowOf(notifications).setNotificationsEnabled(true);
        shadowOf(notifications).setNotificationListenerAccessGranted(new ComponentName(app,CamApsListener.class),true);
        shadowOf(app.getSystemService(PowerManager.class)).setIgnoringBatteryOptimizations(app.getPackageName(),true);
        shadowOf(app.getSystemService(ActivityManager.class)).setBackgroundRestricted(false);
        for (String file:new String[]{LevelStore.FILE_NAME,OutputPreferenceStore.FILE_NAME,AlarmStateStore.FILE_NAME}) new File(app.getFilesDir(),file).delete();
        File live=new File(app.getFilesDir(),"live-receipt-runtime-v1"); new File(live,AlarmStateStore.FILE_NAME).delete();
        new LevelStore(app.getFilesDir()).save(0,Collections.singletonList(new SyntheticAlarmScenario(AlarmEngine.Kind.HIGH).level));
        new OutputPreferenceStore(app.getFilesDir()).save(0,new OutputPreferences(AlarmEngine.OutputMode.TONES_ONLY,0,SpeechTemplate.VALUE_DEFAULT));
        AndroidUiTest.TestBellPlayer.latest=null; AndroidUiTest.TestBellPlayer.completionDelayMs=0;
    }
    @After public void after() { if (service!=null) service.destroy(); }
    private LiveAlarmService start(boolean alarms) {
        LivePreferences.set(app,true,alarms); service=Robolectric.buildService(LiveAlarmService.class).create(); service.startCommand(0,1);
        shadowOf(Looper.getMainLooper()).idle(); return service.get();
    }
    private void receive() {
        receive(160);
    }
    private void receive(int value) {
        LiveAlarmService.receive("synthetic-notification",System.currentTimeMillis(),value,NotificationParser.Trend.UP);
        shadowOf(Looper.getMainLooper()).idle();
    }
    @Test public void systemGateExplainsEveryMissingPermissionInsteadOfClaimingBackgroundReliability() {
        assertTrue(SystemReadiness.read(app).ready());
        shadowOf(app.getSystemService(PowerManager.class)).setIgnoringBatteryOptimizations(app.getPackageName(),false);
        assertFalse(SystemReadiness.read(app).ready()); assertTrue(SystemReadiness.read(app).summary().contains("Ausnahme fehlt"));
        shadowOf(app.getSystemService(PowerManager.class)).setIgnoringBatteryOptimizations(app.getPackageName(),true);
        shadowOf(app.getSystemService(ActivityManager.class)).setBackgroundRestricted(true); assertFalse(SystemReadiness.read(app).ready());
    }
    @Test public void unarmedServiceCannotStartOutputEvenWithEnabledSavedLevels() {
        LiveAlarmService instance=start(false); receive();
        assertTrue(LiveAlarmService.running()); assertFalse(LiveAlarmService.live());
        assertNull(shadowOf(notifications).getNotification(LiveNotifications.ALARM_ID)); assertNull(AndroidUiTest.TestBellPlayer.latest);
        assertNotNull(shadowOf(instance).getLastForegroundNotification());
    }
    @Test public void armedBackgroundServiceUsesReceiptDataAndPostsAnActionableAlarmWithoutAnActivity() throws Exception {
        start(true); receive();
        assertTrue(LiveAlarmService.live()); Notification alarm=shadowOf(notifications).getNotification(LiveNotifications.ALARM_ID);
        assertNotNull(alarm); assertEquals(2,alarm.actions.length); assertNotNull(AndroidUiTest.TestBellPlayer.latest); assertTrue(AndroidUiTest.TestBellPlayer.latest.playing);
        AlarmStateStore.Snapshot state=new AlarmStateStore(new File(app.getFilesDir(),"live-receipt-runtime-v1")).load();
        assertTrue(state.entries.get("synthetic_HIGH").active);
    }
    @Test public void missingBatteryExemptionPreventsForegroundLiveStart() {
        shadowOf(app.getSystemService(PowerManager.class)).setIgnoringBatteryOptimizations(app.getPackageName(),false);
        LivePreferences.set(app,true,true); service=Robolectric.buildService(LiveAlarmService.class).create(); service.startCommand(0,1);
        assertFalse(LiveAlarmService.running()); assertTrue(shadowOf(service.get()).isStoppedBySelf()); assertNull(AndroidUiTest.TestBellPlayer.latest);
    }
    @Test public void savedGlobalPauseSuppressesActualOutputAcrossServiceRecreation() {
        LivePreferences.pauseUntil(app,System.currentTimeMillis()+600000); start(true); receive();
        assertNull(shadowOf(notifications).getNotification(LiveNotifications.ALARM_ID)); assertNull(AndroidUiTest.TestBellPlayer.latest);
        service.destroy(); service=null; start(true); receive(); assertNull(AndroidUiTest.TestBellPlayer.latest);
    }
    @Test public void stoppingServiceReleasesOutputAndDoesNotLeaveAlarmNotification() {
        start(true); receive(); assertTrue(AndroidUiTest.TestBellPlayer.latest.playing); service.destroy(); service=null;
        assertFalse(AndroidUiTest.TestBellPlayer.latest.playing); assertNull(shadowOf(notifications).getNotification(LiveNotifications.ALARM_ID)); assertFalse(LiveAlarmService.running());
    }
    @Test public void versionChangeDoesNotSilentlyReuseAnOlderLiveConsent() {
        LivePreferences.set(app,true,true); assertTrue(LivePreferences.alarms(app));
        app.getSharedPreferences("live_controls_v1",Context.MODE_PRIVATE).edit().putInt("consent_version",BuildConfig.VERSION_CODE-1).commit();
        assertFalse(LivePreferences.alarms(app));
    }
    @Test public void savingDisplayWordsDoesNotInterruptActiveToneOrRewriteAlarmState() throws Exception {
        LiveAlarmService instance=start(true);receive();
        AndroidUiTest.TestBellPlayer tone=AndroidUiTest.TestBellPlayer.latest;assertNotNull(tone);assertTrue(tone.playing);
        Object flow=org.robolectric.util.ReflectionHelpers.getField(instance,"flow");
        File alarmState=new File(new File(app.getFilesDir(),"live-receipt-runtime-v1"),AlarmStateStore.FILE_NAME);
        byte[] state=java.nio.file.Files.readAllBytes(alarmState.toPath());
        File outputFile=new File(app.getFilesDir(),OutputPreferenceStore.FILE_NAME);byte[] output=java.nio.file.Files.readAllBytes(outputFile.toPath());
        org.robolectric.android.controller.ActivityController<TrendDisplayActivity> controller=Robolectric.buildActivity(TrendDisplayActivity.class).create().start().resume().visible();
        try {
            TrendDisplayActivity editor=controller.get();
            ((EditText)editor.getWindow().getDecorView().findViewWithTag("trend_display_UP")).setText("Rasch aufwärts");
            editor.getWindow().getDecorView().findViewWithTag("trend_display_save").performClick();shadowOf(Looper.getMainLooper()).idle();
            assertTrue(editor.isFinishing());assertTrue(tone.playing);assertSame(tone,AndroidUiTest.TestBellPlayer.latest);
            assertSame(flow,org.robolectric.util.ReflectionHelpers.getField(instance,"flow"));assertTrue(LiveAlarmService.live());
            assertArrayEquals(state,java.nio.file.Files.readAllBytes(alarmState.toPath()));assertArrayEquals(output,java.nio.file.Files.readAllBytes(outputFile.toPath()));
            assertEquals("Rasch aufwärts",DisplayTrendPreferences.title(app,NotificationParser.Trend.UP));
        } finally {controller.pause().stop().destroy();}
    }
    @Test public void directionChangeInterruptsTheOldToneAndStartsTheSelectedLowAlarm() throws Exception {
        LevelStore store=new LevelStore(app.getFilesDir());
        store.save(store.load().revision,Arrays.asList(new SyntheticAlarmScenario(AlarmEngine.Kind.HIGH).level,
                new SyntheticAlarmScenario(AlarmEngine.Kind.LOW).level));
        start(true); receive(); AndroidUiTest.TestBellPlayer first=AndroidUiTest.TestBellPlayer.latest;
        assertTrue(first.playing); receive(65);
        assertFalse(first.playing); assertNotSame(first,AndroidUiTest.TestBellPlayer.latest);
        assertTrue(AndroidUiTest.TestBellPlayer.latest.playing);
        Notification alarm=shadowOf(notifications).getNotification(LiveNotifications.ALARM_ID);
        assertTrue(alarm.extras.getCharSequence(Notification.EXTRA_TITLE).toString().contains("Niedrig"));
    }
    @Test public void endedConditionStopsAudioAndRemovesItsNotification() {
        start(true); receive(); AndroidUiTest.TestBellPlayer first=AndroidUiTest.TestBellPlayer.latest;
        receive(100); assertFalse(first.playing);
        assertNull(shadowOf(notifications).getNotification(LiveNotifications.ALARM_ID));
    }
    @Test public void revokingNotificationsStopsAnAlreadyRunningLiveService() {
        start(true); receive(); shadowOf(notifications).setNotificationsEnabled(false);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2));
        assertTrue(shadowOf(service.get()).isStoppedBySelf()); assertFalse(AndroidUiTest.TestBellPlayer.latest.playing);
    }
    @Test public void observationHistoryIsPrivateBoundedAndDeduplicatesGraphicOnlyChanges() {
        long now=System.currentTimeMillis();
        try (ObservationStore store=new ObservationStore(app)) {
            store.insert("synthetic",now,now,new NotificationParser.Result(123,NotificationParser.Trend.UNKNOWN,"synthetic"));
            store.insert("synthetic",now,now+1,new NotificationParser.Result(123,NotificationParser.Trend.UP,"synthetic"));
            store.insert("old-synthetic",now-7*3600000L,now-7*3600000L,new NotificationParser.Result(100,NotificationParser.Trend.FLAT,"synthetic"));
            List<DiagnosticSeries.Sample> points=store.receipts(now+1); assertEquals(1,points.size()); assertEquals(123,points.get(0).value,0);
        }
    }
    @Test public void aggregateReferenceReportIsPersistedWithoutAnyHashOrValue() throws Exception {
        String exact=String.format(Locale.ROOT,"%064x",1),shape=String.format(Locale.ROOT,"%064x",2);
        ReferenceAuditStore.clear(app); ReferenceAuditStore.record(app,ReferenceAudit.Match.UNKNOWN,exact,shape); ReferenceAuditStore.record(app,ReferenceAudit.Match.UNKNOWN,exact,shape);
        String report=new String(Files.readAllBytes(new File(app.getCacheDir(),"reference-audit.txt").toPath()),StandardCharsets.UTF_8);
        assertTrue(report.contains("Wiederholt exakt unbekannt: 1")); assertFalse(report.contains(exact)); assertFalse(report.contains(shape));
    }
    @Test public void appIconAndLegacyNotificationFallbackAreRealAndroidResources() {
        assertNotNull(app.getDrawable(app.getApplicationInfo().icon));
        assertFalse(MetricNotification.apply(new Notification.Builder(app,LiveNotifications.STATUS_CHANNEL),123,NotificationParser.Trend.UP,System.currentTimeMillis()));
    }
    @Test public void compactValueNotificationHasOneValueNoUnitNoSecondsAndOnlyTheFixedClock() {
        Notification notification=new LiveNotifications(app).status(123,NotificationParser.Trend.UP,System.currentTimeMillis(),true,"Technical status must stay in the app");
        assertEquals("123 "+NotificationParser.Trend.UP.symbol,notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString());
        assertTrue(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().matches("[0-9]{2}:[0-9]{2}"));
        assertFalse(notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)); assertFalse(notification.extras.getBoolean(Notification.EXTRA_SHOW_WHEN));
    }
    @Test public void quietWindowAfterAnActualAlarmSurvivesServiceRecreationAndExpiresMonotonically() {
        android.provider.Settings.Global.putInt(app.getContentResolver(),android.provider.Settings.Global.BOOT_COUNT,1);
        start(true); receive(); assertTrue(LivePreferences.valueQuietRemaining(app,5)>299000);
        service.destroy(); service=null; start(false); assertTrue(LivePreferences.valueQuietRemaining(app,5)>299000);
        ShadowSystemClock.advanceBy(java.time.Duration.ofMinutes(6)); assertEquals(0,LivePreferences.valueQuietRemaining(app,5));
    }
    @Test public void allBundledToneResourcesAreRealUncompressedPcmWaves() throws Exception {
        for (String set:ToneCatalog.SETS) for (AlarmEngine.Kind kind:AlarmEngine.Kind.values()) {
            int id=app.getResources().getIdentifier("xcam_"+set+"_"+kind.name().toLowerCase(Locale.ROOT),"raw",app.getPackageName()); assertTrue(id!=0);
            try (android.content.res.AssetFileDescriptor asset=app.getResources().openRawResourceFd(id); InputStream input=asset.createInputStream()) {
                byte[] header=new byte[44]; assertEquals(44,input.read(header));
                assertEquals("RIFF",new String(header,0,4,StandardCharsets.US_ASCII)); assertEquals("WAVE",new String(header,8,4,StandardCharsets.US_ASCII));
                assertTrue(asset.getLength()>1000);
            }
        }
    }
    @Test public void nativeDisplayReportExposesOnlyFlagsAndNeverNotificationContent() {
        notifications.notify(LiveNotifications.STATUS_ID,new Notification.Builder(app,LiveNotifications.STATUS_CHANNEL)
                .setSmallIcon(R.drawable.ic_xcam_status).setContentTitle("synthetic-secret-4567").setContentText("synthetic-private-content").build());
        String report=DisplayDiagnostics.report(app); assertFalse(report.contains("4567")); assertFalse(report.contains("private-content"));
        assertTrue(report.contains("Eigene Betriebsbenachrichtigung: vorhanden")); assertEquals(11,report.trim().split("\n").length);
    }
}
