// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import android.app.*;
import android.content.*;
import android.os.*;
import android.speech.tts.*;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
        shadows={AlarmSpeechCompletionTest.TestClock.class,AlarmSpeechCompletionTest.TestSpeech.class,AndroidUiTest.TestBellPlayer.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class AlarmSpeechCompletionTest {
    private Context app;private ServiceController<LiveAlarmService> service;private TextToSpeech speech;private Vibrator vibrator;
    @Before public void before()throws Exception {
        app=RuntimeEnvironment.getApplication();ConsentTestSupport.accept(app);AppText.configure(Locale.GERMANY);
        TestClock.offset=0;TestClock.boot=1;TestSpeech.texts.clear();TestSpeech.result=TextToSpeech.SUCCESS;
        for(String name:new String[]{"live_controls_v1",SoundDefaults.PREFS,AppAppearance.PREFS,CamApsListener.PREFS})app.getSharedPreferences(name,0).edit().clear().commit();
        for(String name:new String[]{LevelStore.FILE_NAME,OutputPreferenceStore.FILE_NAME,ProfileStore.FILE_NAME,SettingsTransaction.FILE_NAME})new File(app.getFilesDir(),name).delete();
        new File(new File(app.getFilesDir(),"live-receipt-runtime-v1"),AlarmStateStore.FILE_NAME).delete();
        new File(app.getCacheDir(),AlarmOutputDiagnostics.FILE).delete();ProbeState.clear();
        app.getSharedPreferences(CamApsListener.PREFS,0).edit().putBoolean(CamApsListener.ENABLED,true).commit();
        NotificationManager n=app.getSystemService(NotificationManager.class);shadowOf(n).setNotificationsEnabled(true);shadowOf(n).setNotificationListenerAccessGranted(new ComponentName(app,CamApsListener.class),true);
        shadowOf(app.getSystemService(PowerManager.class)).setIgnoringBatteryOptimizations(app.getPackageName(),true);shadowOf(app.getSystemService(ActivityManager.class)).setBackgroundRestricted(false);
        vibrator=Build.VERSION.SDK_INT>=31?app.getSystemService(VibratorManager.class).getDefaultVibrator():app.getSystemService(Vibrator.class);shadowOf(vibrator).setHasVibrator(true);
        new LevelStore(app.getFilesDir()).save(0,Collections.singletonList(level(false)));
        new OutputPreferenceStore(app.getFilesDir()).save(0,new OutputPreferences(AlarmEngine.OutputMode.SPOKEN_ALARMS,0,"Wert {wert}",100,100,0).withChannels(false,true,false,true));
        ShadowTextToSpeech.addVoice(new Voice("synthetic-de",Locale.GERMANY,Voice.QUALITY_NORMAL,Voice.LATENCY_NORMAL,false,Collections.emptySet()));
        AndroidUiTest.TestBellPlayer.latest=null;AndroidUiTest.TestBellPlayer.created=0;AndroidUiTest.TestBellPlayer.completionDelayMs=0;
    }
    @After public void after(){if(service!=null)service.destroy();ProbeState.clear();AppText.configure(Locale.GERMANY);}
    private AlarmLevel level(boolean tone){
        AlarmEngine.Rule rule=new AlarmEngine.Rule("synthetic_high",AlarmEngine.Kind.HIGH,true,180,0,true,0,1440,new AlarmEngine.Outputs(tone,true,3,5));
        return new AlarmLevel(rule,"Private synthetic name",ToneCatalog.uri("samt",AlarmEngine.Kind.HIGH),Collections.singletonList("Alarmtest {wert}"),"LONG_LONG",false);
    }
    private void start(){LivePreferences.set(app,true,true);service=Robolectric.buildService(LiveAlarmService.class).create();service.startCommand(0,1);idle();speech=ShadowTextToSpeech.getLastTextToSpeechInstance();assertNotNull(speech);}
    private void idle(){shadowOf(Looper.getMainLooper()).idle();}
    private void advance(long millis){shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis));}
    private void ready(){shadowOf(speech).getOnInitListener().onInit(TextToSpeech.SUCCESS);idle();}
    private void receive(int value){LiveAlarmService.receive("synthetic",RuntimeClock.read(app).wallMs,value,NotificationParser.Trend.FLAT);idle();}
    private PreviewAudio audio(){return ReflectionHelpers.getField(service.get(),"audio");}
    private long vibrationUntil(){return ReflectionHelpers.getField(ReflectionHelpers.getField(audio(),"vibration"),"until");}
    private String status()throws Exception{return new String(Files.readAllBytes(new File(app.getCacheDir(),AlarmOutputDiagnostics.FILE).toPath()),StandardCharsets.UTF_8);}
    private void noSpeechAfterReady(){ready();advance(12000);assertTrue(TestSpeech.texts.toString(),TestSpeech.texts.isEmpty());}

    @Test public void coldVoiceCompletesOnceWithinTenSecondsWithoutRestartingVibrationOrTone()throws Exception {
        start();receive(200);long until=vibrationUntil();assertTrue(audio().alarmSpeechPending());assertTrue(status().contains("speech=WAITING"));
        advance(3000);ready();advance(1000);assertEquals(Collections.singletonList("Alarmtest 200"),TestSpeech.texts);
        assertEquals(until,vibrationUntil());assertEquals(0,AndroidUiTest.TestBellPlayer.created);assertTrue(status().contains("deferred=true"));
        advance(4000);assertEquals(1,TestSpeech.texts.size());
    }
    @Test public void completionUsesTheLatestStillValidValueRatherThanTheMissedText(){
        start();receive(200);advance(1000);receive(205);ready();advance(1000);assertEquals(Collections.singletonList("Alarmtest 205"),TestSpeech.texts);
    }
    @Test public void tenSecondBoundaryIsInclusive(){start();receive(200);advance(9000);ready();advance(1000);assertEquals(1,TestSpeech.texts.size());}
    @Test public void readinessAfterTenSecondsExpiresWithoutReplayingSignals()throws Exception {
        start();receive(200);long until=vibrationUntil();advance(10001);ready();advance(1999);
        assertTrue(TestSpeech.texts.isEmpty());assertEquals(until,vibrationUntil());assertFalse(audio().alarmSpeechPending());assertTrue(status().contains("speech=EXPIRED"));
    }
    @Test public void endingTheConditionCancelsThePendingVoice(){start();receive(200);advance(1000);receive(150);noSpeechAfterReady();}
    @Test public void invalidSourceCancelsThePendingVoice(){start();receive(200);LiveAlarmService.invalidCurrentSource();idle();noSpeechAfterReady();}
    @Test public void disconnectionCancelsThePendingVoice(){start();receive(200);LiveAlarmService.sourceDisconnected();idle();noSpeechAfterReady();}
    @Test public void pauseCancelsThePendingVoice(){start();receive(200);LivePreferences.pauseUntil(app,RuntimeClock.read(app).wallMs+600000);LiveAlarmService.pauseChanged();idle();noSpeechAfterReady();}
    @Test public void switchingAlarmsOffCancelsThePendingVoice(){start();receive(200);LivePreferences.set(app,true,false);noSpeechAfterReady();}
    @Test public void correctingAnArrowCancelsThePendingVoice(){start();receive(200);LiveAlarmService.trendReferencesChanged();idle();noSpeechAfterReady();}
    @Test public void settingsReloadCancelsThePendingVoice(){start();receive(200);LiveAlarmService.settingsChanged();idle();speech=ShadowTextToSpeech.getLastTextToSpeechInstance();noSpeechAfterReady();}
    @Test public void wallClockJumpCancelsThePendingVoice(){start();receive(200);TestClock.offset=2000;noSpeechAfterReady();}
    @Test public void bootChangeCancelsThePendingVoice(){start();receive(200);TestClock.boot=2;noSpeechAfterReady();}
    @Test public void alarmScheduleIsRecheckedWhenTheVoiceBecomesReady()throws Exception {
        Calendar c=Calendar.getInstance();int minute=c.get(Calendar.HOUR_OF_DAY)*60+c.get(Calendar.MINUTE);
        long wall=c.getTimeInMillis()/60000*60000+59000;TestClock.offset=wall-10000000-SystemClock.elapsedRealtime();
        AlarmEngine.Rule r=new AlarmEngine.Rule("synthetic_high",AlarmEngine.Kind.HIGH,true,180,0,false,(minute+1439)%1440,minute,new AlarmEngine.Outputs(false,true,3,5));
        LevelStore l=new LevelStore(app.getFilesDir());l.save(l.load().revision,Collections.singletonList(new AlarmLevel(r,"Synthetic schedule",AlarmLevel.DEFAULT_TONE,Collections.singletonList("Alarmtest {wert}"),"LONG_LONG",false)));
        start();receive(200);assertTrue("alarm starts in the inclusive xDrip time window",audio().alarmSpeechPending());
        advance(3000);ready();advance(1000);assertTrue("time window closed before deferred speech",TestSpeech.texts.isEmpty());
    }
    @Test public void lostSystemPermissionCancelsThePendingVoice(){start();receive(200);shadowOf(app.getSystemService(NotificationManager.class)).setNotificationsEnabled(false);noSpeechAfterReady();}
    @Test public void serviceRestartDoesNotRestoreTheMissedSpeech(){start();receive(200);service.destroy();service=null;start();noSpeechAfterReady();}
    @Test public void speechOnlyAlarmKeepsItsExistingRetryUntilReady()throws Exception {
        OutputPreferenceStore s=new OutputPreferenceStore(app.getFilesDir());s.save(s.load().revision,s.load().preferences.withChannels(false,true,false,false));
        start();receive(200);assertFalse(new File(app.getCacheDir(),AlarmOutputDiagnostics.FILE).exists());ready();advance(2000);assertEquals(Collections.singletonList("Alarmtest 200"),TestSpeech.texts);
    }
    @Test public void readyVoiceReportsCallbacksAndIgnoresStaleIds()throws Exception {
        start();ready();receive(200);String id=ReflectionHelpers.getField(audio(),"activeSpeechId");assertNotNull(id);assertTrue(status().contains("speech=SUBMITTED"));
        UtteranceProgressListener listener=shadowOf(speech).getUtteranceProgressListener();listener.onStart(id);idle();assertTrue(status().contains("speech=STARTED"));
        listener.onError("old-request");idle();assertTrue(status().contains("speech=STARTED"));listener.onDone(id);idle();assertTrue(status().contains("speech=COMPLETED"));
    }
    @Test public void asynchronousSpeechFailureIsReportedWithoutReplayingTheAlarm()throws Exception {
        start();ready();receive(200);String id=ReflectionHelpers.getField(audio(),"activeSpeechId");shadowOf(speech).getUtteranceProgressListener().onError(id);idle();advance(4000);
        assertTrue(status().contains("speech=FAILED"));assertEquals(1,TestSpeech.texts.size());assertFalse(audio().alarmSpeechPending());
    }
    @Test public void rejectedSpeechIsReportedEvenWhenVibrationAcceptsTheAlarm()throws Exception {
        start();ready();TestSpeech.result=TextToSpeech.ERROR;receive(200);assertTrue(status().contains("speech=FAILED"));assertTrue(status().contains("vibration=ACCEPTED"));assertFalse(audio().alarmSpeechPending());
    }
    @Test public void laterNormalValueSpeechDoesNotOverwriteTheLastAlarmReport()throws Exception {
        OutputPreferenceStore s=new OutputPreferenceStore(app.getFilesDir());s.save(s.load().revision,new OutputPreferences(AlarmEngine.OutputMode.SPOKEN_ALARMS_AND_VALUES,1,"Wert {wert}",100,100,0).withChannels(false,true,true,true));
        start();ready();receive(200);String id=ReflectionHelpers.getField(audio(),"activeSpeechId");shadowOf(speech).getUtteranceProgressListener().onDone(id);idle();String report=status();
        advance(61000);assertTrue(TestSpeech.texts.contains("Wert 200"));assertEquals(report,status());
        assertFalse(report.contains("200"));assertFalse(report.contains("Alarmtest"));assertFalse(report.contains("synthetic_high"));assertFalse(report.contains("Private synthetic name"));assertEquals(10,report.split("\n").length);
        assertTrue(report.startsWith("GVA_ALARM_OUTPUT_V2\n"));assertTrue(report.contains("\nreceipt=1\n"));assertTrue(report.contains("\nattempt=1\n"));
        AppText.configure(Locale.ENGLISH);assertTrue(AlarmOutputDiagnostics.summary(app).startsWith("Last alarm"));
    }
    @Test public void manualAndSyntheticPreviewsNeverQueueAnUnreadyVoice()throws Exception {
        PreviewAudio preview=new PreviewAudio(app,message -> {});try{
            assertFalse(preview.speak("Manual synthetic text"));TextToSpeech t=ShadowTextToSpeech.getLastTextToSpeechInstance();
            SyntheticAlarmScenario scenario=new SyntheticAlarmScenario(AlarmEngine.Kind.HIGH);
            AlarmOutputFlow flow=new AlarmOutputFlow(new AlarmStateStore(new File(app.getCacheDir(),"synthetic-preview")),Collections.singletonList(level(false)),SyntheticAlarmScenario.limits(),new OutputPreferenceStore(app.getFilesDir()).load().preferences,preview);
            List<HistoryAnalysis.Point> points=new ArrayList<>();for(HistoryAnalysis.Point p:scenario.points(0,true))points.add(new HistoryAnalysis.Point(200,p.timestampMs));
            assertEquals(AlarmOutputFlow.Outcome.ACCEPTED,flow.evaluate(AlarmOutputFlow.Origin.SYNTHETIC,points,scenario.clock(0),720,null,NotificationParser.Trend.FLAT));
            assertEquals(AlarmOutputFlow.Outcome.ACCEPTED,flow.alarm("synthetic_high",scenario.clock(0)));assertFalse(preview.alarmSpeechPending());
            shadowOf(t).getOnInitListener().onInit(TextToSpeech.SUCCESS);idle();advance(12000);assertTrue(TestSpeech.texts.isEmpty());assertFalse(new File(app.getCacheDir(),AlarmOutputDiagnostics.FILE).exists());
        }finally{preview.close();}
    }
    @Test public void coldVoiceAfterToneCompletesWithoutRestartingEitherSignal()throws Exception {
        LevelStore l=new LevelStore(app.getFilesDir());l.save(l.load().revision,Collections.singletonList(level(true)));
        OutputPreferenceStore s=new OutputPreferenceStore(app.getFilesDir());s.save(s.load().revision,s.load().preferences.withChannels(true,true,false,true));
        start();receive(200);long until=vibrationUntil();advance(1000);assertEquals(1,AndroidUiTest.TestBellPlayer.created);ready();advance(1000);
        assertEquals(Collections.singletonList("Alarmtest 200"),TestSpeech.texts);assertEquals(1,AndroidUiTest.TestBellPlayer.created);assertEquals(until,vibrationUntil());
    }
    @Test public void gapWarningDoesNotUseTheFreshMeasurementSpeechCompletionPath()throws Exception {
        OutputPreferenceStore s=new OutputPreferenceStore(app.getFilesDir());s.save(s.load().revision,s.load().preferences.withGap(new GapSettings(true,false,4,0,false,true,AlarmLevel.DEFAULT_TONE,"LONG_LONG")));
        start();receive(150);advance(240000);assertTrue(status().contains("kind=GAP"));assertFalse(audio().alarmSpeechPending());ready();advance(4000);assertTrue(TestSpeech.texts.isEmpty());
    }
    @Test public void twoLongTonesCannotReviveAnExpiredVoiceOrLeaveOutputBusy()throws Exception {
        OutputPreferenceStore s=new OutputPreferenceStore(app.getFilesDir());s.save(s.load().revision,s.load().preferences.withChannels(true,true,false,true));
        String tone=ToneCatalog.uri("samt",AlarmEngine.Kind.HIGH);
        java.lang.reflect.Constructor<AlarmOutputFlow.Plan> constructor=AlarmOutputFlow.Plan.class.getDeclaredConstructor(String.class,AlarmEngine.Kind.class,String.class,String.class,boolean.class,String.class,List.class,List.class,List.class);
        constructor.setAccessible(true);
        AlarmOutputFlow.Plan plan=constructor.newInstance("synthetic_high",AlarmEngine.Kind.HIGH,tone,"Alarmtest 200",false,"LONG_LONG",Arrays.asList("synthetic_high","synthetic_rate"),Arrays.asList(tone,tone),Collections.singletonList("LONG_LONG"));
        PreviewAudio port=new PreviewAudio(app,message -> {},() -> true);try{
            AndroidUiTest.TestBellPlayer.completionDelayMs=6500;assertTrue(port.start(plan));speech=ShadowTextToSpeech.getLastTextToSpeechInstance();
            advance(9000);ready();advance(1500);port.discardDeferredAlarmSpeech(true);advance(4000);
            assertEquals(2,AndroidUiTest.TestBellPlayer.created);assertTrue(TestSpeech.texts.isEmpty());assertFalse(port.busy());assertTrue(status().contains("speech=EXPIRED"));
            assertTrue(port.speak("New preview"));assertEquals(Collections.singletonList("New preview"),TestSpeech.texts);
        }finally{port.close();}
    }
    @Implements(value=RuntimeClock.class,isInAndroidSdk=false) public static class TestClock {
        static long offset;static int boot;
        @Implementation protected static AlarmStateStore.Clock read(Context context){long elapsed=SystemClock.elapsedRealtime();return new AlarmStateStore.Clock(10000000L+elapsed+offset,elapsed,boot);}
    }
    @Implements(TextToSpeech.class) public static class TestSpeech extends ShadowTextToSpeech {
        static final List<String> texts=new ArrayList<>();static int result;
        @Implementation protected int speak(CharSequence text,int queue,Bundle parameters,String id){texts.add(text.toString());return result;}
    }
}
