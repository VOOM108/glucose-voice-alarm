// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import android.app.*;
import android.content.*;
import android.os.*;
import android.service.notification.*;
import android.text.*;
import android.text.style.ForegroundColorSpan;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import de.giesen.camaps.companion.prototype.alarm.*;
import de.giesen.gva.watch.WatchSnapshot;
import java.io.*;
import java.time.Duration;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.*;
import org.robolectric.shadows.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={28,34},qualifiers="de",shadows={CamApsConnectionTest.ListenerPort.class,AndroidUiTest.TestRingtones.class,AndroidUiTest.TestRingtone.class,AndroidUiTest.TestBellPlayer.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class RetainedDisplayPlatformTest {
    Application c;ServiceController<CamApsListener> listener;ServiceController<LiveAlarmService> service;
    @Before public void reset()throws Exception {
        c=RuntimeEnvironment.getApplication();ConsentTestSupport.accept(c);AppText.configure(Locale.GERMAN);ProbeState.clear();
        c.getSharedPreferences(CamApsListener.PREFS,0).edit().putBoolean(CamApsListener.ENABLED,true).commit();
        c.getSharedPreferences(UnitPreferences.PREFS,0).edit().clear().commit();c.getSharedPreferences("gva_watch_link_v1",0).edit().clear().commit();
        c.getSharedPreferences("live_controls_v1",0).edit().clear().commit();c.deleteDatabase("probe-observations.db");
        for(String file:new String[]{LevelStore.FILE_NAME,OutputPreferenceStore.FILE_NAME})new File(c.getFilesDir(),file).delete();
        android.provider.Settings.Global.putInt(c.getContentResolver(),"boot_count",1);
        AndroidUiTest.TestBellPlayer.latest=null;AndroidUiTest.TestBellPlayer.completionDelayMs=0;
        ShadowSystemClock.advanceBy(Duration.ofHours(3));
        listener=Robolectric.buildService(CamApsListener.class).create();listener.get().onListenerConnected();
    }
    @After public void close(){if(service!=null)service.destroy();if(listener!=null)listener.destroy();ProbeState.clear();}
    private void send(String text){
        Notification n=new Notification.Builder(c,"synthetic-retention").setSmallIcon(R.drawable.ic_xcam_status).setOngoing(true).setContentText(text).build();
        listener.get().onNotificationPosted(new StatusBarNotification("com.camdiab.fx_alert.mgdl","com.camdiab.fx_alert.mgdl",1,"synthetic-retention",0,0,0,n,android.os.Process.myUserHandle(),System.currentTimeMillis()));
    }
    // Robolectric advances elapsed time independently of Java's wall clock. Age only
    // these synthetic receipts, keeping both recorded clocks consistent.
    private void advance(long minutes){
        ProbeState.Observation shown=ProbeState.display(c);assertNotNull(shown);long ms=minutes*60000L;
        org.robolectric.util.ReflectionHelpers.setField(shown,"receivedElapsed",shown.receivedElapsed-ms);
        org.robolectric.util.ReflectionHelpers.setField(shown,"receivedWall",shown.receivedWall-ms);
        org.robolectric.util.ReflectionHelpers.setField(shown,"postedWall",shown.postedWall-ms);
    }
    @Test public void missingValueKeepsOnlyTheDisplayAndOriginalAgeNotFreshSpeechInput()throws Exception {
        send("123 mg/dl ↗");ProbeState.Observation first=ProbeState.latest;assertNotNull(first);
        advance(1);send("Insulin abgegeben");assertNull(ProbeState.latest);
        assertSame(first,ProbeState.display(c));assertFalse(ProbeState.displayFresh(c,first));assertEquals(123,first.value,0);
        assertEquals(60000,SystemClock.elapsedRealtime()-first.receivedElapsed);
        try(ObservationStore store=new ObservationStore(c)){assertEquals(1,store.receipts(System.currentTimeMillis()).size());}
        c.getSharedPreferences("gva_watch_link_v1",0).edit().putBoolean("enabled",true).commit();
        WatchSnapshot wire=WatchSnapshot.decode(WatchPublisher.snapshot(c),System.currentTimeMillis());
        assertEquals(123,wire.value,0);assertEquals(first.receivedWall,wire.received);assertFalse(wire.sourceAvailable);assertFalse(wire.fresh(System.currentTimeMillis()));
        c.getSharedPreferences("gva_watch_link_v1",0).edit().putBoolean("enabled",false).commit();
        advance(1);send("124 mg/dl →");assertNotNull(ProbeState.latest);assertTrue(ProbeState.displayFresh(c,ProbeState.display(c)));assertEquals(124,ProbeState.display(c).value,0);
    }
    @Test public void silenceRetainsKnownValueForTwoHoursButNeverRenewsFreshness(){
        send("123 mg/dl →");ProbeState.Observation first=ProbeState.latest;
        advance(5);assertSame("receipt="+first.receivedWall+"/"+first.receivedElapsed+" now="+System.currentTimeMillis()+"/"+SystemClock.elapsedRealtime()+" allowed="+ProbeState.displayAllowed(c),first,ProbeState.display(c));assertFalse(ProbeState.displayFresh(c,first));
        advance(114);assertSame(first,ProbeState.display(c));advance(2);assertNull(ProbeState.display(c));
    }
    @Test public void disconnectRetainsDisplayButExplicitResetUnitConflictAndDisabledReceptionDoNot(){
        send("123 mg/dl →");ProbeState.Observation first=ProbeState.latest;listener.get().onListenerDisconnected();
        assertNull(ProbeState.latest);assertSame(first,ProbeState.display(c));assertFalse(ProbeState.displayFresh(c,first));
        c.getSharedPreferences(CamApsListener.PREFS,0).edit().putBoolean(CamApsListener.ENABLED,false).commit();assertNull(ProbeState.display(c));
        c.getSharedPreferences(CamApsListener.PREFS,0).edit().putBoolean(CamApsListener.ENABLED,true).commit();UnitPreferences.reset(c);assertNull(ProbeState.display(c));
        send("124 mg/dl →");assertNotNull(ProbeState.display(c));UnitPreferences.markConflict(c);assertNull(ProbeState.display(c));
    }
    @Test public void clockChangeAndMissingAcknowledgementsCannotRevealRetainedValues(){
        send("123 mg/dl →");ProbeState.Observation first=ProbeState.latest;ProbeState.unavailable();
        assertFalse(ProbeState.displayClockValid(first,first.receivedElapsed,first.receivedWall+120000));
        assertFalse(ProbeState.displayClockValid(first,first.receivedElapsed-1,first.receivedWall));
        c.getSharedPreferences(SafetyConsent.PREFS,0).edit().clear().commit();assertNull(ProbeState.display(c));
    }
    @Test public void oldNotificationKeepsTheReadingAndMarksClockAndAgeExplicitly(){
        send("123 mg/dl ↗");ProbeState.Observation first=ProbeState.latest;advance(5);ProbeState.unavailable();
        Notification n=new LiveNotifications(c).status(first.value,first.trend,first.receivedWall,false,"synthetic gap");
        assertEquals("123 ↗",n.extras.getCharSequence(Notification.EXTRA_TITLE).toString());
        assertTrue(n.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),n.extras.getCharSequence(Notification.EXTRA_TEXT).toString().endsWith("5 min alt"));
        assertTrue((n.flags&Notification.FLAG_ONGOING_EVENT)!=0);assertFalse(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER));
        Spanned reading=(Spanned)MetricNotification.reading(first.value,first.trend,GlucoseUnit.MGDL,false);
        assertEquals(0xff899298,reading.getSpans(0,reading.length(),ForegroundColorSpan.class)[0].getForegroundColor());
        Spanned clock=(Spanned)MetricNotification.clock(first.receivedWall,false);
        assertEquals(0xffff6b6b,clock.getSpans(0,clock.length(),ForegroundColorSpan.class)[0].getForegroundColor());
        AppText.configure(Locale.ENGLISH);assertEquals("5 min old",MetricNotification.ageTitle(first.receivedWall));
    }
    @Test public void retainedHighDisplayCannotRestartAlarmOrSpeechAfterSourceLoss()throws Exception {
        NotificationManager manager=c.getSystemService(NotificationManager.class);
        shadowOf(manager).setNotificationsEnabled(true);shadowOf(manager).setNotificationListenerAccessGranted(new ComponentName(c,CamApsListener.class),true);
        shadowOf(c.getSystemService(PowerManager.class)).setIgnoringBatteryOptimizations(c.getPackageName(),true);shadowOf(c.getSystemService(ActivityManager.class)).setBackgroundRestricted(false);
        new LevelStore(c.getFilesDir()).save(0,Collections.singletonList(new SyntheticAlarmScenario(AlarmEngine.Kind.HIGH).level));
        new OutputPreferenceStore(c.getFilesDir()).save(0,new OutputPreferences(AlarmEngine.OutputMode.TONES_ONLY,0,SpeechTemplate.VALUE_DEFAULT));
        LivePreferences.set(c,true,true);service=Robolectric.buildService(LiveAlarmService.class).create();service.startCommand(0,1);shadowOf(Looper.getMainLooper()).idle();
        send("200 mg/dl ↑");shadowOf(Looper.getMainLooper()).idle();assertNotNull(AndroidUiTest.TestBellPlayer.latest);
        AndroidUiTest.TestBellPlayer alarm=AndroidUiTest.TestBellPlayer.latest;assertTrue(alarm.playing);
        advance(1);send("Insulin abgegeben");shadowOf(Looper.getMainLooper()).idle();assertFalse(alarm.playing);assertNull(ProbeState.latest);
        advance(10);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertSame(alarm,AndroidUiTest.TestBellPlayer.latest);assertFalse(alarm.playing);assertNull(shadowOf(manager).getNotification(LiveNotifications.ALARM_ID));
        Notification status=shadowOf(manager).getNotification(LiveNotifications.STATUS_ID);
        assertTrue(status.extras.toString(),status.extras.getCharSequence(Notification.EXTRA_TITLE).toString().startsWith("200"));assertNotNull(ProbeState.display(c));
    }
    @Test @Config(sdk=28,qualifiers="de-w411dp-h891dp-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void dashboardKeepsOldReadingWithRedTimeAndManualSpeechStillRefusesIt()throws Exception {
        send("123 mg/dl ↗");advance(5);send("Insulin abgegeben");assertNull(ProbeState.latest);
        ActivityController<ProbeActivity> activity=Robolectric.buildActivity(ProbeActivity.class).create().start().resume().visible();
        try {
            TextView value=org.robolectric.util.ReflectionHelpers.getField(activity.get(),"value");
            TextView time=org.robolectric.util.ReflectionHelpers.getField(activity.get(),"readingTime");
            assertTrue(value.getText().toString(),value.getText().toString().startsWith("123"));assertEquals(AppAppearance.colors(activity.get()).error,time.getCurrentTextColor());
            View decor=activity.get().getWindow().getDecorView();decor.measure(View.MeasureSpec.makeMeasureSpec(411,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(891,View.MeasureSpec.EXACTLY));decor.layout(0,0,411,891);
            Bitmap image=Bitmap.createBitmap(411,891,Bitmap.Config.ARGB_8888);decor.draw(new Canvas(image));
            File folder=new File("build/ui-previews");assertTrue(folder.isDirectory()||folder.mkdirs());
            try(FileOutputStream out=new FileOutputStream(new File(folder,"retained-display.png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}
        } finally {activity.pause().stop().destroy();}
        ActivityController<ProbeDiagnosticsActivity> diagnostic=Robolectric.buildActivity(ProbeDiagnosticsActivity.class).create().start().resume().visible();
        try {
            assertTrue(click(diagnostic.get().getWindow().getDecorView(),"Letzten Empfang ansagen"));
            TextView spoken=org.robolectric.util.ReflectionHelpers.getField(diagnostic.get(),"speechStatus");assertTrue(spoken.getText().toString(),spoken.getText().toString().startsWith("Keine Ansage:"));
        } finally {diagnostic.pause().stop().destroy();}
    }
    private boolean click(View v,String label){
        if(v instanceof TextView&&label.contentEquals(((TextView)v).getText()))return v.performClick();
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(click(((ViewGroup)v).getChildAt(i),label))return true;
        return false;
    }
}
