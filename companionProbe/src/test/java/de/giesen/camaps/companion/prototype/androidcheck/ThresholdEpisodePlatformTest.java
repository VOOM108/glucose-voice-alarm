// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.app.*;
import android.content.*;
import android.os.*;
import android.speech.tts.*;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.*;
import java.time.Duration;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowTextToSpeech;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={28,34},qualifiers="de",instrumentedPackages={"de.giesen.camaps.companion.prototype.RuntimeClock"},
        shadows={RecoveryGapPlatformTest.TestClock.class,AlarmSpeechCompletionTest.TestSpeech.class,AndroidUiTest.TestBellPlayer.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class ThresholdEpisodePlatformTest {
    private Context app;private ServiceController<LiveAlarmService> service;private TextToSpeech speech;private Vibrator vibrator;
    @Before public void before()throws Exception{
        app=RuntimeEnvironment.getApplication();ConsentTestSupport.accept(app);AppText.configure(Locale.GERMANY);
        for(String name:new String[]{"live_controls_v1",SoundDefaults.PREFS,AppAppearance.PREFS,CamApsListener.PREFS})app.getSharedPreferences(name,0).edit().clear().commit();
        for(String name:new String[]{LevelStore.FILE_NAME,OutputPreferenceStore.FILE_NAME,ProfileStore.FILE_NAME,SettingsTransaction.FILE_NAME})new File(app.getFilesDir(),name).delete();
        new File(new File(app.getFilesDir(),"live-receipt-runtime-v1"),AlarmStateStore.FILE_NAME).delete();ProbeState.clear();
        app.getSharedPreferences(CamApsListener.PREFS,0).edit().putBoolean(CamApsListener.ENABLED,true).commit();
        NotificationManager n=app.getSystemService(NotificationManager.class);shadowOf(n).setNotificationsEnabled(true);shadowOf(n).setNotificationListenerAccessGranted(new ComponentName(app,CamApsListener.class),true);
        shadowOf(app.getSystemService(PowerManager.class)).setIgnoringBatteryOptimizations(app.getPackageName(),true);shadowOf(app.getSystemService(ActivityManager.class)).setBackgroundRestricted(false);
        vibrator=Build.VERSION.SDK_INT>=31?app.getSystemService(VibratorManager.class).getDefaultVibrator():app.getSystemService(Vibrator.class);shadowOf(vibrator).setHasVibrator(true);
        ShadowTextToSpeech.addVoice(new Voice("synthetic-de",Locale.GERMANY,Voice.QUALITY_NORMAL,Voice.LATENCY_NORMAL,false,Collections.emptySet()));
        AlarmSpeechCompletionTest.TestSpeech.texts.clear();AlarmSpeechCompletionTest.TestSpeech.result=TextToSpeech.SUCCESS;
        AndroidUiTest.TestBellPlayer.latest=null;AndroidUiTest.TestBellPlayer.created=0;AndroidUiTest.TestBellPlayer.completionDelayMs=0;
    }
    @After public void after(){if(service!=null)service.destroy();ProbeState.clear();}
    private void configure(AlarmEngine.Kind kind,boolean tones,boolean vibration)throws Exception{
        AlarmEngine.Rule r=new AlarmEngine.Rule("x",kind,true,kind==AlarmEngine.Kind.HIGH?180:80,0,true,0,1440,new AlarmEngine.Outputs(tones,true,30,30));
        LevelStore levels=new LevelStore(app.getFilesDir());levels.save(levels.load().revision,Collections.singletonList(new AlarmLevel(r,"Synthetic",ToneCatalog.uri("samt",kind),Collections.singletonList("Alarm {wert}"),vibration?"LONG_LONG":null,false)));
        OutputPreferenceStore out=new OutputPreferenceStore(app.getFilesDir());out.save(out.load().revision,new OutputPreferences(AlarmEngine.OutputMode.SPOKEN_ALARMS,0,"Wert {wert}",100,100,0).withChannels(tones,true,false,vibration));
    }
    private void idle(){shadowOf(Looper.getMainLooper()).idle();}
    private void advance(long millis){shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis));}
    private void start(){LivePreferences.set(app,true,true);service=Robolectric.buildService(LiveAlarmService.class).create();service.startCommand(0,1);idle();speech=ShadowTextToSpeech.getLastTextToSpeechInstance();shadowOf(speech).getOnInitListener().onInit(TextToSpeech.SUCCESS);idle();}
    private void receive(int value){LiveAlarmService.receive("synthetic",RuntimeClock.read(app).wallMs,value,NotificationParser.Trend.FLAT);idle();advance(1000);finishSpeech();}
    private void finishSpeech(){PreviewAudio audio=ReflectionHelpers.getField(service.get(),"audio");String id=ReflectionHelpers.getField(audio,"activeSpeechId");if(id!=null){shadowOf(speech).getUtteranceProgressListener().onDone(id);idle();}}
    private int spoken(){return AlarmSpeechCompletionTest.TestSpeech.texts.size();}
    private Notification alarm(){return shadowOf(app.getSystemService(NotificationManager.class)).getNotification(LiveNotifications.ALARM_ID);}
    @Test public void aNewHighEpisodeSpeaksAgainWithToneAndVibrationOff()throws Exception{
        configure(AlarmEngine.Kind.HIGH,false,false);start();receive(200);assertEquals(1,spoken());assertNotNull(alarm());
        advance(60000);receive(170);assertNull(alarm());advance(60000);receive(200);assertEquals(2,spoken());assertNotNull(alarm());assertEquals(0,AndroidUiTest.TestBellPlayer.created);
    }
    @Test public void aNewLowEpisodeSpeaksAgain()throws Exception{
        configure(AlarmEngine.Kind.LOW,false,false);start();receive(70);advance(60000);receive(90);advance(60000);receive(70);assertEquals(2,spoken());assertNotNull(alarm());
    }
    @Test public void aNewEpisodeUsesAllConfiguredPhoneChannels()throws Exception{
        configure(AlarmEngine.Kind.HIGH,true,true);start();receive(200);assertEquals(1,spoken());assertEquals(1,AndroidUiTest.TestBellPlayer.created);
        PreviewAudio audio=ReflectionHelpers.getField(service.get(),"audio");long firstUntil=ReflectionHelpers.getField(ReflectionHelpers.getField(audio,"vibration"),"until");
        advance(60000);receive(170);advance(60000);receive(200);assertEquals(2,spoken());assertEquals(2,AndroidUiTest.TestBellPlayer.created);
        long secondUntil=ReflectionHelpers.getField(ReflectionHelpers.getField(audio,"vibration"),"until");assertTrue(secondUntil>firstUntil);assertArrayEquals(VibrationCatalog.LONG_LONG.timings(),shadowOf(vibrator).getPattern());
    }
    @Test public void continuousHighDoesNotSpeakOnEveryNewReceipt()throws Exception{
        configure(AlarmEngine.Kind.HIGH,false,false);start();receive(200);for(int i=0;i<10;i++){advance(60000);receive(210);}assertEquals(1,spoken());advance(20*60000);receive(210);assertEquals(2,spoken());
    }
    @Test public void snoozeSurvivesRecoveryAndRecrossing()throws Exception{
        configure(AlarmEngine.Kind.HIGH,false,false);start();receive(200);service.get().onStartCommand(new Intent(app,LiveAlarmService.class).setAction(LiveAlarmService.SNOOZE).putExtra("id","x"),0,2);idle();
        advance(60000);receive(170);advance(60000);receive(200);assertEquals(1,spoken());
    }
    @Test public void globalPauseStillPreventsAllOutputsAcrossANewEpisode()throws Exception{
        configure(AlarmEngine.Kind.HIGH,false,false);start();receive(200);LivePreferences.pauseUntil(app,RuntimeClock.read(app).wallMs+30*60000);LiveAlarmService.pauseChanged();idle();
        advance(60000);receive(170);advance(60000);receive(200);assertEquals(1,spoken());
        LivePreferences.pauseUntil(app,0);LiveAlarmService.pauseChanged();idle();advance(1000);receive(200);assertEquals(2,spoken());
    }
    @Test public void sourceDisconnectionWithoutAConfirmedNormalValueDoesNotRearm()throws Exception{
        configure(AlarmEngine.Kind.HIGH,false,false);start();receive(200);LiveAlarmService.sourceDisconnected();idle();advance(60000);receive(200);assertEquals(1,spoken());
        advance(60000);receive(170);advance(60000);receive(200);assertEquals(2,spoken());
    }
    @Test public void serviceRestartAfterConfirmedRecoveryAllowsANewEpisode()throws Exception{
        configure(AlarmEngine.Kind.HIGH,false,false);start();receive(200);advance(60000);receive(170);service.destroy();service=null;start();advance(60000);receive(200);assertEquals(2,spoken());
    }
}
