// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import de.giesen.camaps.companion.prototype.alarm.*;
import de.giesen.gva.watch.WatchAlert;
import java.io.*;
import java.time.Duration;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
@RunWith(RobolectricTestRunner.class)
@Config(sdk={28,34},qualifiers="de",instrumentedPackages={"de.giesen.camaps.companion.prototype.RuntimeClock","de.giesen.camaps.companion.prototype.WatchAlertTransport"},shadows={AndroidUiTest.TestBellPlayer.class,RecoveryGapPlatformTest.TestClock.class,WatchAlertPlatformTest.Transport.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class WatchAlertPlatformTest {
    Application c;ServiceController<LiveAlarmService> service;
    @Implements(value=WatchAlertTransport.class,isInAndroidSdk=false) public static class Transport {
        static final List<WatchAlert> events=new ArrayList<>();
        @Implementation protected static void enqueue(Context c,WatchAlert e){try{events.add(WatchAlert.decode(e.encode(),System.currentTimeMillis()));}catch(IOException ex){throw new AssertionError(ex);}}
    }
    @Before public void reset()throws Exception{
        c=RuntimeEnvironment.getApplication();ConsentTestSupport.accept(c);AppText.configure(Locale.GERMAN);
        for(String name:new String[]{WatchAlertPublisher.PREFS,"gva_watch_link_v1","live_controls_v1",SoundDefaults.PREFS})c.getSharedPreferences(name,0).edit().clear().commit();
        WatchAlertPublisher.clear(c);Transport.events.clear();ProbeState.clear();
        WatchPublisher.status=WatchTransferStatus.EMPTY;WatchAlertPublisher.status=WatchTransferStatus.EMPTY;
        c.getSharedPreferences(CamApsListener.PREFS,0).edit().putBoolean(CamApsListener.ENABLED,true).commit();
        NotificationManager manager=c.getSystemService(NotificationManager.class);shadowOf(manager).setNotificationsEnabled(true);shadowOf(manager).setNotificationListenerAccessGranted(new ComponentName(c,CamApsListener.class),true);
        shadowOf(c.getSystemService(PowerManager.class)).setIgnoringBatteryOptimizations(c.getPackageName(),true);shadowOf(c.getSystemService(ActivityManager.class)).setBackgroundRestricted(false);
        android.provider.Settings.Global.putInt(c.getContentResolver(),"boot_count",1);
        for(String name:new String[]{LevelStore.FILE_NAME,OutputPreferenceStore.FILE_NAME,SettingsTransaction.FILE_NAME})new File(c.getFilesDir(),name).delete();
        new File(new File(c.getFilesDir(),"live-receipt-runtime-v1"),AlarmStateStore.FILE_NAME).delete();
        new LevelStore(c.getFilesDir()).save(0,Collections.singletonList(new SyntheticAlarmScenario(AlarmEngine.Kind.HIGH).level));
        new OutputPreferenceStore(c.getFilesDir()).save(0,new OutputPreferences(AlarmEngine.OutputMode.TONES_ONLY,0,SpeechTemplate.VALUE_DEFAULT));
        AndroidUiTest.TestBellPlayer.latest=null;AndroidUiTest.TestBellPlayer.completionDelayMs=0;
    }
    @After public void close(){if(service!=null)service.destroy();WatchAlertPublisher.clear(c);Transport.events.clear();}
    private void start(boolean live){LivePreferences.set(c,true,live);service=Robolectric.buildService(LiveAlarmService.class).create();service.startCommand(0,1);shadowOf(Looper.getMainLooper()).idle();}
    private void receive(int value){LiveAlarmService.receive("synthetic-watch-alarm",RuntimeClock.read(c).wallMs,value,NotificationParser.Trend.UP);shadowOf(Looper.getMainLooper()).idle();}
    private long shows(){return Transport.events.stream().filter(e->e.action==WatchAlert.Action.SHOW&&!e.test).count();}
    @Test public void optInAndSyntheticTestDoNotDependOnDisplayLinkOrLiveAlarmEnable(){assertFalse(WatchAlertPublisher.enabled(c));WatchAlertPublisher.test(c);assertTrue(Transport.events.isEmpty());WatchAlertPublisher.set(c,true);WatchAlertPublisher.test(c);assertEquals(1,Transport.events.size());WatchAlert test=Transport.events.get(0);assertTrue(test.test);assertTrue(WatchAlertPublisher.sendAllowed(c,test));assertFalse(WatchPublisher.enabled(c));assertFalse(LivePreferences.alarms(c));new LiveNotifications(c).status(123,NotificationParser.Trend.FLAT,System.currentTimeMillis(),true,"");assertEquals(1,Transport.events.size());new LiveNotifications(c).clearAlarm();assertTrue(WatchAlertPublisher.sendAllowed(c,test));}
    @Test public void acceptedLiveAlarmRelaysOnceAndReceiptRefreshIsQuiet(){WatchAlertPublisher.set(c,true);start(true);receive(200);assertEquals(1,shows());WatchAlert first=Transport.events.stream().filter(e->e.action==WatchAlert.Action.SHOW).findFirst().get();assertTrue(first.text.contains("200"));shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));receive(210);assertEquals(1,shows());WatchAlert update=Transport.events.get(Transport.events.size()-1);assertEquals(WatchAlert.Action.UPDATE,update.action);assertEquals(first.token,update.token);assertTrue(update.text.contains("210"));Notification n=shadowOf(c.getSystemService(NotificationManager.class)).getNotification(LiveNotifications.ALARM_ID);assertTrue((n.flags&Notification.FLAG_LOCAL_ONLY)!=0);}
    @Test public void gapWarningUsesTheSameRelayAndNewReceiptClearsIt()throws Exception{OutputPreferenceStore out=new OutputPreferenceStore(c.getFilesDir());out.save(out.load().revision,out.load().preferences.withGap(new GapSettings(true,false,4,5,true,false,ToneCatalog.uri("samt",AlarmEngine.Kind.PERSISTENT_HIGH),null)));WatchAlertPublisher.set(c,true);start(true);receive(123);assertEquals(0,shows());shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(240));assertEquals(1,shows());WatchAlert gap=Transport.events.stream().filter(e->e.action==WatchAlert.Action.SHOW).findFirst().get();assertEquals("Datenlücke",gap.title);receive(124);assertTrue(Transport.events.stream().anyMatch(e->e.action==WatchAlert.Action.CLEAR&&e.token.equals(gap.token)));}
    @Test public void displayOnlyAndUnacceptedAlarmCallsCannotSendRealAlerts(){WatchAlertPublisher.set(c,true);WatchAlertPublisher.alarm(c,"Synthetic","No live output");start(false);receive(200);assertEquals(0,shows());assertTrue(Transport.events.isEmpty());}
    @Test public void disablingStopsPendingAlarmAndNeverReplaysOnReenable(){WatchAlertPublisher.set(c,true);start(true);receive(200);WatchAlert first=Transport.events.stream().filter(e->e.action==WatchAlert.Action.SHOW).findFirst().get();assertTrue(WatchAlertPublisher.sendAllowed(c,first));WatchAlertPublisher.set(c,false);assertFalse(WatchAlertPublisher.sendAllowed(c,first));WatchAlert clear=Transport.events.get(Transport.events.size()-1);assertEquals(WatchAlert.Action.CLEAR,clear.action);assertTrue(WatchAlertPublisher.sendAllowed(c,clear));int size=Transport.events.size();WatchAlertPublisher.set(c,true);assertEquals(size,Transport.events.size());assertFalse(WatchAlertPublisher.sendAllowed(c,first));}
    @Test public void pauseCancelsTheActualRelayWhileTestAlertsRemainExplicit(){WatchAlertPublisher.set(c,true);start(true);receive(200);WatchAlert first=Transport.events.stream().filter(e->e.action==WatchAlert.Action.SHOW).findFirst().get();LivePreferences.pauseUntil(c,RuntimeClock.read(c).wallMs+600000);LiveAlarmService.pauseChanged();shadowOf(Looper.getMainLooper()).idle();assertEquals(WatchAlert.Action.CLEAR,Transport.events.get(Transport.events.size()-1).action);assertFalse(WatchAlertPublisher.sendAllowed(c,first));WatchAlertPublisher.test(c);assertTrue(Transport.events.get(Transport.events.size()-1).test);}
    @Test public void missingConsentBlocksSavedRelayFlagAndExplicitTest(){c.getSharedPreferences(WatchAlertPublisher.PREFS,0).edit().putBoolean("enabled",true).commit();c.getSharedPreferences(SafetyConsent.PREFS,0).edit().clear().commit();WatchAlertPublisher.test(c);assertTrue(Transport.events.isEmpty());assertFalse(WatchAlertPublisher.enabled(c));}
    @Test public void watchSettingsOfferIndependentSwitchAndAnExplicitTestAction(){ActivityController<WatchSettingsActivity> a=Robolectric.buildActivity(WatchSettingsActivity.class).create().start().resume().visible();try{View root=a.get().getWindow().getDecorView();assertNotNull(find(root,"Anzeige: Noch kein Versand"));assertNotNull(find(root,"Alarme: Noch kein Versand"));TextView enable=find(root,"Alarme an Uhr senden");assertNotNull(enable);enable.performClick();assertTrue(WatchAlertPublisher.enabled(c));assertFalse(WatchPublisher.enabled(c));TextView test=find(root,"Uhrenalarm testen");assertNotNull(test);test.performClick();assertEquals(1,Transport.events.size());assertTrue(Transport.events.get(0).test);
        long completed=System.currentTimeMillis();WatchPublisher.status=new WatchTransferStatus(WatchTransferStatus.Event.DISPLAY,WatchTransferStatus.Result.SENT,completed);WatchAlertPublisher.status=new WatchTransferStatus(WatchTransferStatus.Event.TEST,WatchTransferStatus.Result.SENT,completed);
        org.robolectric.util.ReflectionHelpers.callInstanceMethod(a.get(),"renderStatus");
        assertTrue(((TextView)root.findViewWithTag("watch_display_status")).getText().toString().startsWith("Anzeige: Gesendet · "));
        assertTrue(((TextView)root.findViewWithTag("watch_alert_status")).getText().toString().startsWith("Alarme: Test gesendet · "));
        assertTrue(((TextView)root.findViewWithTag("watch_alert_status")).getText().toString().contains(new java.text.SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date(completed))));
    }finally{a.pause().stop().destroy();}}
    private TextView find(View v,String text){if(v instanceof TextView&&text.contentEquals(((TextView)v).getText()))return (TextView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView found=find(((ViewGroup)v).getChildAt(i),text);if(found!=null)return found;}return null;}
}
