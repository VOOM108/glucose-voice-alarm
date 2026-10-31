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
@Config(sdk={28,34},qualifiers="de",instrumentedPackages={"de.giesen.camaps.companion.prototype.RuntimeClock"},shadows={RecoveryGapPlatformTest.TestClock.class,AlarmSpeechCompletionTest.TestSpeech.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class ProcessingDiagnosticsPlatformTest {
 private Context app;private ServiceController<LiveAlarmService> service;private TextToSpeech speech;
 @Before public void setup()throws Exception{
  app=RuntimeEnvironment.getApplication();ConsentTestSupport.accept(app);AppText.configure(Locale.GERMANY);
  for(String name:new String[]{"live_controls_v1",SoundDefaults.PREFS,AppAppearance.PREFS,CamApsListener.PREFS})app.getSharedPreferences(name,0).edit().clear().commit();
  for(String name:new String[]{LevelStore.FILE_NAME,OutputPreferenceStore.FILE_NAME,ProfileStore.FILE_NAME,SettingsTransaction.FILE_NAME})new File(app.getFilesDir(),name).delete();
  new File(new File(app.getFilesDir(),"live-receipt-runtime-v1"),AlarmStateStore.FILE_NAME).delete();ProbeState.clear();
  app.getSharedPreferences(CamApsListener.PREFS,0).edit().putBoolean(CamApsListener.ENABLED,true).commit();
  NotificationManager n=app.getSystemService(NotificationManager.class);shadowOf(n).setNotificationsEnabled(true);shadowOf(n).setNotificationListenerAccessGranted(new ComponentName(app,CamApsListener.class),true);
  shadowOf(app.getSystemService(PowerManager.class)).setIgnoringBatteryOptimizations(app.getPackageName(),true);shadowOf(app.getSystemService(ActivityManager.class)).setBackgroundRestricted(false);
  AlarmEngine.Rule r=new AlarmEngine.Rule("synthetic_high",AlarmEngine.Kind.HIGH,true,180,0,true,0,1440,new AlarmEngine.Outputs(false,true,30,30));
  new LevelStore(app.getFilesDir()).save(0,Collections.singletonList(new AlarmLevel(r,"Synthetic high",AlarmLevel.DEFAULT_TONE,Collections.singletonList("Hoch bei {wert}"),null,false)));
  new OutputPreferenceStore(app.getFilesDir()).save(0,new OutputPreferences(AlarmEngine.OutputMode.SPOKEN_ALARMS_AND_VALUES,1,"Wert {wert}",100,100,10).withChannels(false,true,true,false));
  ShadowTextToSpeech.addVoice(new Voice("synthetic-de",Locale.GERMANY,Voice.QUALITY_NORMAL,Voice.LATENCY_NORMAL,false,Collections.emptySet()));AlarmSpeechCompletionTest.TestSpeech.texts.clear();AlarmSpeechCompletionTest.TestSpeech.result=TextToSpeech.SUCCESS;
  LivePreferences.set(app,true,true);service=Robolectric.buildService(LiveAlarmService.class).create();service.startCommand(0,1);idle();speech=ShadowTextToSpeech.getLastTextToSpeechInstance();shadowOf(speech).getOnInitListener().onInit(TextToSpeech.SUCCESS);idle();
 }
 @After public void cleanup(){if(service!=null)service.destroy();ProbeState.clear();}
 private void idle(){shadowOf(Looper.getMainLooper()).idle();}
 private void advance(long ms){shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));}
 private void receive(int value){LiveAlarmService.receive("synthetic",RuntimeClock.read(app).wallMs,value,NotificationParser.Trend.UP);idle();}
 private List<String> texts(){return AlarmSpeechCompletionTest.TestSpeech.texts;}
 private void finish(){PreviewAudio audio=ReflectionHelpers.getField(service.get(),"audio");String id=ReflectionHelpers.getField(audio,"activeSpeechId");if(id!=null){shadowOf(speech).getUtteranceProgressListener().onDone(id);idle();}}
 private Notification alarm(){return shadowOf(app.getSystemService(NotificationManager.class)).getNotification(LiveNotifications.ALARM_ID);}
 @Test public void aNewHighAlarmInterruptsAnUnfinishedNormalValueAnnouncement()throws Exception{
  receive(160);advance(60000);assertEquals(Collections.singletonList("Wert 160"),texts());advance(1);receive(200);
  assertEquals(Arrays.asList("Wert 160","Hoch bei 200"),texts());assertNotNull(alarm());
  Map<String,String>d=ProcessingDiagnostics.read(app);assertEquals("ACCEPTED",d.get("threshold_outcome"));assertEquals("STARTED",d.get("card"));assertEquals("ALARM",d.get("last_output_type"));assertEquals("HIGH",d.get("last_output_kind"));assertEquals("true",d.get("last_output_speech"));
 }
 @Test public void aHighAlarmDefersTheNextOrdinaryValueButNotTheOtherWayRound()throws Exception{
  receive(200);finish();for(int i=0;i<9;i++){advance(60000);receive(200);finish();}
  assertEquals(Collections.singletonList("Hoch bei 200"),texts());advance(60000);receive(200);
  assertEquals(Arrays.asList("Hoch bei 200","Wert 200"),texts());assertNotNull(alarm());assertTrue((alarm().flags&Notification.FLAG_ONLY_ALERT_ONCE)!=0);
  Map<String,String>d=ProcessingDiagnostics.read(app);assertEquals("NOT_DUE",d.get("threshold_outcome"));assertEquals("REPEAT",d.get("threshold_wait"));assertEquals("REFRESHED",d.get("card"));assertEquals("VALUE",d.get("last_output_type"));assertEquals("ALARM",d.get("last_alarm_type"));assertNotEquals(d.get("last_alarm_number"),d.get("last_output_number"));
 }
 @Test public void aNewCrossingSpeaksDespiteARecentOrdinaryValueAndAnOldRepeat()throws Exception{
  receive(200);finish();advance(60000);receive(160);for(int i=0;i<10;i++){advance(60000);receive(160);finish();}
  assertTrue(texts().contains("Wert 160"));advance(1);receive(200);assertEquals("Hoch bei 200",texts().get(texts().size()-1));
  assertEquals(2,texts().stream().filter(s->s.startsWith("Hoch bei")).count());
 }

 private Map<String,String> diagnostic()throws Exception{return ProcessingDiagnostics.read(app);}
 private File cache(){return new File(app.getCacheDir(),ProcessingDiagnostics.FILE);}
 @Test public void ticksAndLaterRoutineSpeechDoNotOverwriteReceiptSnapshot()throws Exception{
  receive(160);Map<String,String>before=diagnostic();advance(60000);
  Map<String,String>after=diagnostic();for(String key:new String[]{"receipt","evaluation","threshold","threshold_outcome","card","receipt_output","value_outcome"})assertEquals(key,before.get(key),after.get(key));
  assertEquals("VALUE",after.get("last_output_type"));assertEquals(before.get("receipt"),after.get("last_output_receipt"));assertEquals("0",after.get("last_alarm_number"));
 }
 @Test public void manualSnoozeHasItsOwnReasonAndNoNewOutput()throws Exception{
  receive(200);finish();AlarmOutputFlow flow=ReflectionHelpers.getField(service.get(),"flow");assertEquals(AlarmOutputFlow.Outcome.ACCEPTED,flow.snooze("synthetic_high",30,RuntimeClock.read(app)));advance(60000);receive(200);
  assertEquals("NOT_DUE",diagnostic().get("threshold_outcome"));assertEquals("SNOOZE",diagnostic().get("threshold_wait"));assertEquals(1,texts().size());
 }
 @Test public void globalPauseIsDistinctFromRepeatAndDoesNotCreateAnAttempt()throws Exception{
  LivePreferences.pauseUntil(app,RuntimeClock.read(app).wallMs+60000);receive(200);
  assertEquals("SUPPRESSED",diagnostic().get("threshold_outcome"));assertEquals("GLOBAL_PAUSE",diagnostic().get("threshold_guard"));assertEquals("0",diagnostic().get("last_output_number"));assertTrue(texts().isEmpty());
 }
 @Test public void numericalRecoveryRecordsClearedCardAndNextCrossingStarts()throws Exception{
  receive(200);finish();advance(60000);receive(160);assertEquals("CLEARED",diagnostic().get("card"));assertEquals("NONE",diagnostic().get("threshold"));advance(60000);receive(200);
  assertEquals("STARTED",diagnostic().get("card"));assertEquals("ACCEPTED",diagnostic().get("threshold_outcome"));
 }
 @Test public void unavailableVoiceIsRecordedWithoutPretendingToStartAnAlarm()throws Exception{
  PreviewAudio audio=ReflectionHelpers.getField(service.get(),"audio");ReflectionHelpers.setField(audio,"ready",false);receive(200);
  assertEquals("OUTPUT_UNAVAILABLE",diagnostic().get("threshold_outcome"));assertEquals("0",diagnostic().get("last_alarm_number"));assertNull(alarm());assertTrue(texts().isEmpty());
 }
 @Test public void duplicateReceiptDoesNotAdvanceSequenceOrReplaceEvidence()throws Exception{
  receive(200);Map<String,String>before=diagnostic();receive(200);assertEquals(before,diagnostic());
 }
 @Test public void serviceRestartResetsCorrelationInsteadOfJoiningOldAndNewRuns()throws Exception{
  receive(200);service.destroy();service=null;ProcessingDiagnostics next=new ProcessingDiagnostics(app);assertEquals("0",diagnostic().get("receipt"));assertEquals("0",diagnostic().get("last_alarm_number"));assertTrue(ProcessingDiagnostics.summary(app).contains("wartet auf neuen Eingang"));assertTrue(AlarmOutputDiagnostics.summary(app).contains("noch kein Ausgabestatus"));
 }
 @Test public void cacheContainsOnlyWhitelistedCodesAndCounters()throws Exception{
  receive(200);String encoded=new String(java.nio.file.Files.readAllBytes(cache().toPath()),java.nio.charset.StandardCharsets.UTF_8);
  assertFalse(encoded.contains("Hoch bei"));assertFalse(encoded.contains("synthetic_high"));assertFalse(encoded.contains("200"));assertEquals(25,diagnostic().size());
 }
 @Test public void malformedAndWrongVersionCacheIsNotDisplayed()throws Exception{
  receive(200);String encoded=new String(java.nio.file.Files.readAllBytes(cache().toPath()),java.nio.charset.StandardCharsets.UTF_8);
  for(String invalid:new String[]{encoded.replace("threshold=HIGH","threshold=arbitrary private text"),encoded.replace("version="+BuildConfig.VERSION_CODE,"version=0"),encoded+"unexpected=private\n",encoded.replace("last_alarm_kind=HIGH","last_alarm_type=HIGH")}){
   java.nio.file.Files.write(cache().toPath(),invalid.getBytes(java.nio.charset.StandardCharsets.UTF_8));assertEquals("Verarbeitung: Diagnose nicht verfügbar",ProcessingDiagnostics.summary(app));
  }
 }
 @Test public void englishSummaryTranslatesAllExplanations()throws Exception{
  receive(200);finish();advance(60000);receive(200);AppText.configure(Locale.US);String shown=ProcessingDiagnostics.summary(app);
  assertTrue(shown,shown.contains("Last new input"));assertTrue(shown,shown.contains("repeat interval"));assertTrue(shown,shown.contains("silently refreshed"));assertTrue(shown,shown.contains("Last alarm attempt"));assertFalse(shown,shown.contains("Wiederholungsfrist"));
 }
 @Test public void diagnosticReadFailureDoesNotChangeStoredAlarmOrDispatch()throws Exception{
  receive(200);finish();File state=new File(new File(app.getFilesDir(),"live-receipt-runtime-v1"),AlarmStateStore.FILE_NAME);byte[] before=java.nio.file.Files.readAllBytes(state.toPath());
  java.nio.file.Files.write(cache().toPath(),new byte[5000]);assertTrue(ProcessingDiagnostics.summary(app).contains("nicht verfügbar"));assertArrayEquals(before,java.nio.file.Files.readAllBytes(state.toPath()));assertEquals(1,texts().size());
 }
 @Test public void speechStatusKeepsTheSameAlarmAttemptWhenOrdinaryValuesResume()throws Exception{
  receive(200);finish();String attempt=diagnostic().get("last_alarm_number"),receipt=diagnostic().get("last_alarm_receipt");
  for(int i=0;i<10;i++){advance(60000);receive(200);finish();}
  List<String> status=java.nio.file.Files.readAllLines(new File(app.getCacheDir(),AlarmOutputDiagnostics.FILE).toPath());
  assertEquals("GVA_ALARM_OUTPUT_V2",status.get(0));assertTrue(status.contains("speech=COMPLETED"));assertTrue(status.contains("attempt="+attempt));assertTrue(status.contains("receipt="+receipt));assertEquals("VALUE",diagnostic().get("last_output_type"));
 }
 @Test public void aNewAlarmAttemptGetsANewSpeechCorrelation()throws Exception{
  receive(200);finish();String original=diagnostic().get("last_alarm_number");advance(60000);receive(160);advance(60000);receive(200);
  assertNotEquals(original,diagnostic().get("last_alarm_number"));String shown=AlarmOutputDiagnostics.summary(app);
  assertTrue(shown,shown.contains("#"+diagnostic().get("last_alarm_number")+" · Eingang #"+diagnostic().get("last_alarm_receipt")));
 }
 @Test public void rejectedSpeechAttemptIsNotReportedAsAcceptedOrAsASilentRefresh()throws Exception{
  AlarmSpeechCompletionTest.TestSpeech.result=TextToSpeech.ERROR;receive(200);
  assertEquals("ATTEMPT_FAILED",diagnostic().get("threshold_outcome"));assertEquals("false",diagnostic().get("last_alarm_accepted"));assertEquals("NONE",diagnostic().get("card"));assertNull(alarm());
 }
 @Test public void unwritableDiagnosticCacheDoesNotPreventHighSpeech()throws Exception{
  assertTrue(cache().delete());assertTrue(cache().mkdir());
  try{receive(200);assertEquals(Collections.singletonList("Hoch bei 200"),texts());assertNotNull(alarm());}finally{cache().delete();}
 }
 @Test public void explainingARepeatDoesNotWriteAlarmState()throws Exception{
  receive(200);finish();File state=new File(new File(app.getFilesDir(),"live-receipt-runtime-v1"),AlarmStateStore.FILE_NAME);byte[] before=java.nio.file.Files.readAllBytes(state.toPath());
  AlarmOutputFlow flow=ReflectionHelpers.getField(service.get(),"flow");assertEquals(AlarmOutputFlow.WaitReason.REPEAT,flow.diagnosticWait("synthetic_high",RuntimeClock.read(app)));assertArrayEquals(before,java.nio.file.Files.readAllBytes(state.toPath()));
 }
 @Test public void gapWarningIsIdentifiedSeparatelyAndRetainsInputCorrelation()throws Exception{
  OutputPreferenceStore store=new OutputPreferenceStore(app.getFilesDir());OutputPreferenceStore.Snapshot saved=store.load();
  store.save(saved.revision,saved.preferences.withGap(new GapSettings(true,false,4,5,false,true,AlarmLevel.DEFAULT_TONE,null)));
  LiveAlarmService.settingsChanged();idle();speech=ShadowTextToSpeech.getLastTextToSpeechInstance();shadowOf(speech).getOnInitListener().onInit(TextToSpeech.SUCCESS);idle();
  receive(160);advance(4*60000+2000);
  assertEquals("GAP",diagnostic().get("last_output_type"));assertEquals("GAP",diagnostic().get("last_alarm_type"));assertEquals(diagnostic().get("receipt"),diagnostic().get("last_alarm_receipt"));
  assertTrue(java.nio.file.Files.readAllLines(new File(app.getCacheDir(),AlarmOutputDiagnostics.FILE).toPath()).contains("attempt="+diagnostic().get("last_alarm_number")));
 }
}
