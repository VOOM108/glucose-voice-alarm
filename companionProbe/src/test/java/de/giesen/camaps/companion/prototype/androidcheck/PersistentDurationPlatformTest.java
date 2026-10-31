// SPDX-License-Identifier: GPL-3.0-or-later
// Synthetic service regression: no real device data or audio.
package de.giesen.camaps.companion.prototype;
import android.app.*;import android.content.*;import android.os.*;import android.speech.tts.*;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.*;import java.time.Duration;import java.util.*;
import org.junit.*;import org.junit.runner.RunWith;
import org.robolectric.*;import org.robolectric.android.controller.ServiceController;import org.robolectric.annotation.*;import org.robolectric.shadows.ShadowTextToSpeech;import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;import static org.robolectric.Shadows.shadowOf;
@RunWith(RobolectricTestRunner.class)
@Config(sdk={28,34},qualifiers="de",instrumentedPackages={"de.giesen.camaps.companion.prototype.RuntimeClock"},shadows={PersistentDurationPlatformTest.TestClock.class,AlarmSpeechCompletionTest.TestSpeech.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class PersistentDurationPlatformTest {
 private Context app;private ServiceController<LiveAlarmService> service;private TextToSpeech speech;
 @Before public void setup()throws Exception{
  TestClock.wallShift=0;TestClock.boot=1;app=RuntimeEnvironment.getApplication();ConsentTestSupport.accept(app);AppText.configure(Locale.GERMANY);
  for(String name:new String[]{"live_controls_v1",SoundDefaults.PREFS,AppAppearance.PREFS,CamApsListener.PREFS})app.getSharedPreferences(name,0).edit().clear().commit();
  for(String name:new String[]{LevelStore.FILE_NAME,OutputPreferenceStore.FILE_NAME,ProfileStore.FILE_NAME,SettingsTransaction.FILE_NAME})new File(app.getFilesDir(),name).delete();
  new File(new File(app.getFilesDir(),"live-receipt-runtime-v1"),AlarmStateStore.FILE_NAME).delete();ProbeState.clear();
  app.getSharedPreferences(CamApsListener.PREFS,0).edit().putBoolean(CamApsListener.ENABLED,true).commit();
  NotificationManager n=app.getSystemService(NotificationManager.class);shadowOf(n).setNotificationsEnabled(true);shadowOf(n).setNotificationListenerAccessGranted(new ComponentName(app,CamApsListener.class),true);
  shadowOf(app.getSystemService(PowerManager.class)).setIgnoringBatteryOptimizations(app.getPackageName(),true);shadowOf(app.getSystemService(ActivityManager.class)).setBackgroundRestricted(false);
  AlarmEngine.Rule r=new AlarmEngine.Rule("synthetic_persistent",AlarmEngine.Kind.PERSISTENT_HIGH,true,180,5,true,0,1440,new AlarmEngine.Outputs(false,true,30,30));
  new LevelStore(app.getFilesDir()).save(0,Collections.singletonList(new AlarmLevel(r,"Synthetic high",AlarmLevel.DEFAULT_TONE,Collections.singletonList("Seit {dauer_minuten}"),null,false)));
  new OutputPreferenceStore(app.getFilesDir()).save(0,new OutputPreferences(AlarmEngine.OutputMode.SPOKEN_ALARMS,1,"Wert {wert}",100,100,10).withChannels(false,true,false,false));
  ShadowTextToSpeech.addVoice(new Voice("synthetic-de",Locale.GERMANY,Voice.QUALITY_NORMAL,Voice.LATENCY_NORMAL,false,Collections.emptySet()));AlarmSpeechCompletionTest.TestSpeech.texts.clear();AlarmSpeechCompletionTest.TestSpeech.result=TextToSpeech.SUCCESS;
  LivePreferences.set(app,true,true);service=Robolectric.buildService(LiveAlarmService.class).create();service.startCommand(0,1);idle();speech=ShadowTextToSpeech.getLastTextToSpeechInstance();shadowOf(speech).getOnInitListener().onInit(TextToSpeech.SUCCESS);idle();
 }
 @After public void cleanup(){if(service!=null)service.destroy();ProbeState.clear();}
 private void idle(){shadowOf(Looper.getMainLooper()).idle();}
 private void advance(long ms){org.robolectric.shadows.ShadowSystemClock.advanceBy(Duration.ofMillis(ms));idle();}
 private void receive(int value){LiveAlarmService.receive("synthetic",RuntimeClock.read(app).wallMs,value,NotificationParser.Trend.UP);idle();}
 private List<String> texts(){return AlarmSpeechCompletionTest.TestSpeech.texts;}
 private void finish(){PreviewAudio audio=ReflectionHelpers.getField(service.get(),"audio");String id=ReflectionHelpers.getField(audio,"activeSpeechId");if(id!=null){shadowOf(speech).getUtteranceProgressListener().onDone(id);idle();}}
 private Notification alarm(){return shadowOf(app.getSystemService(NotificationManager.class)).getNotification(LiveNotifications.ALARM_ID);}

 @Implements(value=RuntimeClock.class,isInAndroidSdk=false)public static class TestClock {
  static long wallShift,boot=1;
  @Implementation protected static AlarmStateStore.Clock read(Context c){long e=SystemClock.elapsedRealtime();return new AlarmStateStore.Clock(10000000L+e+wallShift,e,boot);}
 }
 private AlarmStateStore store(){return new AlarmStateStore(new File(app.getFilesDir(),"live-receipt-runtime-v1"));}
 private long since()throws Exception{AlarmStateStore.Entry e=store().load().entries.get("synthetic_persistent");return e==null?0:e.highSince;}
 private void restart(){service.destroy();service=Robolectric.buildService(LiveAlarmService.class).create();service.startCommand(0,1);idle();speech=ShadowTextToSpeech.getLastTextToSpeechInstance();shadowOf(speech).getOnInitListener().onInit(TextToSpeech.SUCCESS);idle();}
 @Test public void gapTimeIsCountedAndTheSpokenDurationUsesTheOldBeginning()throws Exception{
  receive(200);long first=since();advance(60000);receive(201);advance(8*60000);assertTrue(texts().isEmpty());assertEquals(first,since());
  receive(220);assertTrue(texts().isEmpty());assertEquals(first,since());advance(60000);receive(221);assertEquals(Collections.singletonList("Seit 10"),texts());assertEquals(first,since());
 }
 @Test public void anInitiallyFallingHighValueAlreadyStartsTheEpisode()throws Exception{
  receive(250);long first=since();assertTrue(first>0);advance(60000);receive(249);assertEquals(first,since());assertTrue(texts().isEmpty());
 }
 @Test public void plateauMinutesAreIncludedWhenRisingAgain()throws Exception{
  receive(200);long first=since();for(int i=0;i<20;i++){advance(60000);receive(200);}assertTrue(texts().isEmpty());advance(60000);receive(201);assertEquals(Collections.singletonList("Seit 21"),texts());assertEquals(first,since());
 }
 @Test public void aStaleValueNeverStartsAnAlarmEvenThoughTheTimerIsRetained()throws Exception{
  receive(200);long first=since();advance(10*60000);assertTrue(texts().isEmpty());assertNull(alarm());assertEquals(first,since());
 }
 @Test public void sameBootSettingsReloadAfterAGapDoesNotEraseTheBeginning()throws Exception{
  receive(200);long first=since();advance(60000);receive(201);advance(8*60000);LiveAlarmService.settingsChanged();idle();assertEquals(first,since());receive(220);advance(60000);receive(221);assertEquals(Collections.singletonList("Seit 10"),texts());
 }
 @Test public void sameBootServiceRestartRequiresFreshValuesAndKeepsElapsedTime()throws Exception{
  receive(200);long first=since();advance(60000);receive(201);advance(8*60000);restart();assertEquals(first,since());assertTrue(texts().isEmpty());receive(220);advance(60000);receive(221);assertEquals(Collections.singletonList("Seit 10"),texts());
 }
 @Test public void invalidSourceDoesNotAssertThatHighHasEnded()throws Exception{
  receive(200);long first=since();advance(60000);receive(201);LiveAlarmService.invalidCurrentSource();idle();advance(8*60000);assertEquals(first,since());assertTrue(texts().isEmpty());receive(220);advance(60000);receive(221);assertEquals(Collections.singletonList("Seit 10"),texts());
 }
 @Test public void confirmedBoundaryValueAfterAGapEndsTheOldEpisode()throws Exception{
  receive(200);advance(60000);receive(201);advance(8*60000);receive(180);assertEquals(0,since());advance(60000);receive(200);assertTrue(since()>0);assertTrue(texts().isEmpty());
 }
 @Test public void anActualClockJumpDoesNotReuseTheOldDuration()throws Exception{
  receive(200);long first=since();advance(60000);receive(201);TestClock.wallShift=10*60000;advance(60000);receive(220);assertTrue(since()>first+10*60000);assertTrue(texts().isEmpty());
 }
 @Test public void rebootDoesNotReuseAnUnverifiedElapsedClock()throws Exception{
  receive(200);long first=since();advance(60000);receive(201);TestClock.boot=2;advance(60000);restart();receive(220);assertTrue(since()>first);assertTrue(texts().isEmpty());
 }
 @Test public void aGapDoesNotResetAnExistingRepeatDeadline()throws Exception{
  receive(200);for(int i=1;i<=6;i++){advance(60000);receive(200+i);}assertEquals(1,texts().size());finish();long first=since(),deadline=store().load().entries.get("synthetic_persistent").nextAt;
  advance(8*60000);receive(220);advance(60000);receive(221);assertEquals(1,texts().size());assertEquals(first,since());assertEquals(deadline,store().load().entries.get("synthetic_persistent").nextAt);
 }
 @Test public void dataGapsDoNotManufactureRateSamples()throws Exception{
  receive(200);advance(60000);receive(201);advance(8*60000);receive(300);ReceiptWindow w=ReflectionHelpers.getField(service.get(),"window");assertEquals(1,w.points().size());assertEquals(3,w.episodePoints().size());assertTrue(texts().isEmpty());
 }
}
