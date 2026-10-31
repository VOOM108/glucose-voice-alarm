// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.app.*;
import android.content.*;
import android.os.*;
import de.giesen.camaps.companion.prototype.alarm.*;
import de.giesen.gva.watch.ArrowColor;
import java.io.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.*;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

/** Real Android service, notifications and simulated output. No real measurements or device access. */
@RunWith(RobolectricTestRunner.class)
@Config(qualifiers="de",sdk={28,34},shadows={AndroidUiTest.TestRingtones.class,AndroidUiTest.TestRingtone.class,AndroidUiTest.TestBellPlayer.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class SourceLowPlatformTest {
    private Context app;private ServiceController<LiveAlarmService> service;private NotificationManager manager;
    private long posted;
    @Before public void before()throws Exception{
        app=RuntimeEnvironment.getApplication();shadowOf(Looper.getMainLooper()).idle();org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofHours(3));
        ConsentTestSupport.accept(app);AppText.configure(Locale.GERMANY);ProbeState.clear();
        for(String p:new String[]{"live_controls_v1",SoundDefaults.PREFS,CamApsListener.PREFS})app.getSharedPreferences(p,0).edit().clear().commit();
        app.getSharedPreferences(CamApsListener.PREFS,0).edit().putBoolean(CamApsListener.ENABLED,true).commit();
        app.getSharedPreferences(SoundDefaults.PREFS,0).edit().putBoolean("installed",true).commit();
        for(String file:new String[]{LevelStore.FILE_NAME,OutputPreferenceStore.FILE_NAME,ProfileStore.FILE_NAME,SettingsTransaction.FILE_NAME})new File(app.getFilesDir(),file).delete();
        new File(new File(app.getFilesDir(),"live-receipt-runtime-v1"),AlarmStateStore.FILE_NAME).delete();
        manager=app.getSystemService(NotificationManager.class);shadowOf(manager).setNotificationsEnabled(true);
        shadowOf(manager).setNotificationListenerAccessGranted(new ComponentName(app,CamApsListener.class),true);
        shadowOf(app.getSystemService(PowerManager.class)).setIgnoringBatteryOptimizations(app.getPackageName(),true);
        shadowOf(app.getSystemService(ActivityManager.class)).setBackgroundRestricted(false);
        android.provider.Settings.Global.putInt(app.getContentResolver(),android.provider.Settings.Global.BOOT_COUNT,1);
        AlarmLevel soon=LevelForm.parse("soon",AlarmEngine.Kind.SOURCE_LOW,"Bald niedrig","","","","",true,true,false,ToneCatalog.uri("samt",AlarmEngine.Kind.LOW),true,0,1440);
        AlarmLevel low=LevelForm.parse("low",AlarmEngine.Kind.LOW,"Niedrig","80","","3","5",true,true,false,ToneCatalog.uri("klar",AlarmEngine.Kind.LOW),true,0,1440);
        new LevelStore(app.getFilesDir()).save(0,Arrays.asList(soon,low));
        new OutputPreferenceStore(app.getFilesDir()).save(0,new OutputPreferences(AlarmEngine.OutputMode.TONES_ONLY,0,SpeechTemplate.VALUE_DEFAULT));
        AndroidUiTest.TestBellPlayer.latest=null;AndroidUiTest.TestBellPlayer.created=0;AndroidUiTest.TestBellPlayer.completionDelayMs=0;
    }
    @After public void after(){if(service!=null)service.destroy();ProbeState.clear();}
    private void start(boolean enabled){LivePreferences.set(app,true,enabled);service=Robolectric.buildService(LiveAlarmService.class).create();service.startCommand(0,1);shadowOf(Looper.getMainLooper()).idle();}
    private void receive(int value,ArrowColor color){
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(1));posted=RuntimeClock.read(app).wallMs;
        LiveAlarmService.receive("synthetic",posted,value,NotificationParser.Trend.DOWN_RIGHT,color);shadowOf(Looper.getMainLooper()).idle();
    }
    private Notification card(){return shadowOf(manager).getNotification(LiveNotifications.ALARM_ID);}
    @Test public void liveRedArrowStartsItsOwnCardWithoutSnoozeAndDoesNotRepeat()throws Exception{
        start(true);receive(123,ArrowColor.RED);assertNotNull(card());assertEquals("Bald niedrig",card().extras.getCharSequence(Notification.EXTRA_TITLE).toString());
        assertEquals(1,card().actions.length);assertEquals(1,AndroidUiTest.TestBellPlayer.created);assertFalse(AlarmOutputDiagnostics.summary(app).contains("nicht verfügbar"));
        AlarmStateStore store=new AlarmStateStore(new File(app.getFilesDir(),"live-receipt-runtime-v1"));assertTrue(store.load().entries.get("soon").active);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2));receive(123,ArrowColor.RED);assertEquals(1,AndroidUiTest.TestBellPlayer.created);
        assertEquals(AlarmEngine.Kind.SOURCE_LOW,org.robolectric.util.ReflectionHelpers.<AlarmOutputFlow>getField(service.get(),"flow").lastDecision().decisions.get(0).rule.kind);
    }
    @Test public void freshNonRedClearsCardAndANewRedStartsAgain(){
        start(true);receive(123,ArrowColor.RED);receive(123,ArrowColor.YELLOW);assertNull(card());assertEquals(1,AndroidUiTest.TestBellPlayer.created);
        receive(123,ArrowColor.RED);assertEquals(2,AndroidUiTest.TestBellPlayer.created);assertNotNull(card());
    }
    @Test public void existingLowTakesOverAndRisingBackAboveDoesNotRearmRed(){
        start(true);receive(123,ArrowColor.RED);receive(65,ArrowColor.RED);assertEquals("Niedrig",card().extras.getCharSequence(Notification.EXTRA_TITLE).toString());
        assertEquals(2,card().actions.length);assertEquals(2,AndroidUiTest.TestBellPlayer.created);
        receive(123,ArrowColor.RED);assertNull(card());assertEquals(2,AndroidUiTest.TestBellPlayer.created);
    }
    @Test public void serviceRestartKeepsThePhaseReservation(){
        start(true);receive(123,ArrowColor.RED);assertEquals(1,AndroidUiTest.TestBellPlayer.created);service.destroy();service=null;
        start(true);receive(123,ArrowColor.RED);assertNull(card());assertEquals(1,AndroidUiTest.TestBellPlayer.created);
    }
    @Test public void unarmedServiceDoesNotEmitTheNewAlarm(){
        start(false);receive(123,ArrowColor.RED);assertNull(card());assertEquals(0,AndroidUiTest.TestBellPlayer.created);
    }

    @Test public void defaultSourceNeutralSpeechReachesTheRealSpeechAdapter()throws Exception{
        LevelStore store=new LevelStore(app.getFilesDir());LevelStore.Snapshot old=store.load();AlarmLevel existing=old.find("soon");
        AlarmEngine.Rule r=existing.rule;AlarmEngine.Rule spoken=new AlarmEngine.Rule(r.id,r.kind,true,1,0,true,0,1440,new AlarmEngine.Outputs(false,true,0,0));
        store.save(old.revision,Arrays.asList(new AlarmLevel(spoken,existing.name,existing.toneUri),old.find("low")));
        OutputPreferenceStore outputs=new OutputPreferenceStore(app.getFilesDir());OutputPreferenceStore.Snapshot p=outputs.load();outputs.save(p.revision,OutputPreferences.defaults().withChannels(true,true,false));
        org.robolectric.shadows.ShadowTextToSpeech.addVoice(new android.speech.tts.Voice("synthetic-de",Locale.GERMANY,android.speech.tts.Voice.QUALITY_NORMAL,android.speech.tts.Voice.LATENCY_NORMAL,false,Collections.emptySet()));
        start(true);android.speech.tts.TextToSpeech tts=org.robolectric.shadows.ShadowTextToSpeech.getLastTextToSpeechInstance();assertNotNull(tts);
        shadowOf(tts).getOnInitListener().onInit(android.speech.tts.TextToSpeech.SUCCESS);shadowOf(Looper.getMainLooper()).idle();
        receive(123,ArrowColor.RED);assertEquals("Laut Datenquelle bald niedrig",shadowOf(tts).getLastSpokenText());assertEquals(0,AndroidUiTest.TestBellPlayer.created);assertNotNull(card());
    }

    @Test public void sourceWarningWaitsForAnAlreadyPlayingNumericalAlarmInsteadOfCuttingItOff()throws Exception{
        LevelStore store=new LevelStore(app.getFilesDir());LevelStore.Snapshot old=store.load();List<AlarmLevel> levels=new ArrayList<>(old.levels);
        levels.add(LevelForm.parse("high",AlarmEngine.Kind.HIGH,"Hoch","200","","3","5",true,true,false,ToneCatalog.uri("samt",AlarmEngine.Kind.HIGH),true,0,1440));store.save(old.revision,levels);
        AndroidUiTest.TestBellPlayer.completionDelayMs=5000;start(true);receive(250,ArrowColor.NORMAL);
        AndroidUiTest.TestBellPlayer first=AndroidUiTest.TestBellPlayer.latest;assertNotNull(first);assertTrue(first.playing);
        receive(250,ArrowColor.RED);assertSame(first,AndroidUiTest.TestBellPlayer.latest);assertTrue(first.playing);assertFalse(first.released);
    }
}
